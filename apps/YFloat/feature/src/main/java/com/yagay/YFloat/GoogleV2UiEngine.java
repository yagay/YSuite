package com.yagay.YFloat;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Complete V2 host workflow for Google-supplied image and OCR events.
 * The old GoogleCtsBridgeController is never called. Reuses only shared
 * authenticated IPC and YFloat's ordinary result/menu UI primitives.
 */
final class GoogleV2UiEngine {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Map<String, Session> SESSIONS = new ConcurrentHashMap<>();
    private static final long FRAME_TIMEOUT_MS = 6500;
    private static final long SESSION_TTL_MS = 120000;
    private static final long TEXT_MENU_SETTLE_MS = 260;
    private static final long REGION_MENU_SETTLE_MS = 280;
    // Gesture hooks may miss ACTION_UP when a Google view is replaced.
    // An idle timeout is permitted only after no fresh selection/gesture input.
    private static final long GESTURE_IDLE_RECOVERY_MS = 700;

    private static final class Session {
        final String token;
        final GoogleV2State state = new GoogleV2State();
        Bitmap frame;
        Context app;
        long issuedAt = SystemClock.elapsedRealtime();
        long lastTextUpdateElapsed;
        long lastRegionUpdateElapsed;
        long lastGestureElapsed;
        Runnable pendingTextMenu;
        Runnable pendingRegionMenu;
        Session(Context app, String token) { this.app=app;this.token=token; }
    }

    /** Reject events queued by a superseded Google CTS workflow. */
    private static boolean ownsUi(Session s) {
        WorkflowSessionManager.Session workflow=WorkflowSessionManager.current();
        return s!=null && GoogleV2SessionOwnership.accepts(s.token,
                workflow==null?"":workflow.externalKey(),SESSIONS.get(s.token)==s,
                s.state.terminal()) && WorkflowSessionManager.matchesExternal(s.token);
    }

    /** Legacy release does not own V2 sessions; abandon the exact old token. */
    static void abandon(Context context,String token,String reason) {
        if(token==null||token.isBlank())return;
        Session s=SESSIONS.get(token);
        if(s==null)return;
        if(Looper.myLooper()==Looper.getMainLooper())
            release(s,"abandon_"+reason,false);
        else MAIN.post(()->release(s,"abandon_"+reason,false));
    }

    private static Session session(Context context, String token) {
        if (context == null || token == null || token.isBlank()
                || !WorkflowSessionManager.matchesExternal(token)) return null;
        Context app = context.getApplicationContext();
        return SESSIONS.computeIfAbsent(token, k -> {
            Session created = new Session(app, k);
            // A lost Google END event cannot retain the original image forever.
            MAIN.postDelayed(() -> {
                if (SESSIONS.get(k) == created) {
                    DiagnosticLog.i(created.app, "GOOGLE_V2", "session ttl expired");
                    release(created, "ttl_expired", false);
                }
            }, SESSION_TTL_MS);
            return created;
        });
    }

    static void onFrame(Context context,String token,Bitmap frame) {
        if (frame == null || frame.isRecycled()) return;
        Session s=session(context,token);
        if(s==null){frame.recycle();return;}
        MAIN.post(()->{
            if(!ownsUi(s)){frame.recycle();return;}
            Bitmap prior=s.frame;
            if(prior!=null&&!prior.isRecycled()
                    && !CanonicalFramePolicy.shouldReplace(
                    prior.getWidth(),prior.getHeight(),frame.getWidth(),frame.getHeight())){
                frame.recycle();return;
            }
            s.frame=frame;
            s.state.frameReady=true;
            if(prior!=null&&!prior.isRecycled()) prior.recycle();
            DiagnosticLog.i(s.app,"GOOGLE_V2","original frame="+frame.getWidth()+"x"+frame.getHeight());
            if(s.state.canDeliver()) deliver(s);
        });
    }

    static void onText(Context context,String token,String text,Rect bounds,String detail){
        onText(context,token,text,bounds,detail,null);
    }
    static void onText(Context context,String token,String text,Rect bounds,String detail,int[] rows){
        Session s=session(context,token);
        if(s==null||text==null||text.isBlank()) return;
        MAIN.post(()->{
            if(!ownsUi(s))return;
            Rect use=bounds!=null&&!bounds.isEmpty()?new Rect(bounds):null;
            if(!s.state.text(text,at(use,0),at(use,1),at(use,2),at(use,3)))return;
            s.lastTextUpdateElapsed=SystemClock.elapsedRealtime();
            cancelRegionMenu(s);
            GoogleRegionConfirmOverlay.dismiss(token,"v2_text");
            if(use!=null)GoogleV2TextOutline.show(s.app,use,rows);
            else GoogleV2TextOutline.dismiss();
            showTextWhenStable(s,TEXT_MENU_SETTLE_MS);
            DiagnosticLog.i(s.app,"GOOGLE_V2","text chars="+text.length()+" bounds="+use);
        });
    }
    static void onTextBounds(Context c,String token,Rect bounds){
        onTextBounds(c,token,bounds,null);
    }
    static void onTextBounds(Context c,String token,Rect bounds,int[] rows){
        Session s=session(c,token);
        if(s==null||bounds==null||bounds.isEmpty())return;
        MAIN.post(()->{
            if(!ownsUi(s)||s.state.phase!=GoogleV2State.Phase.TEXT)return;
            s.state.bounds(bounds.left,bounds.top,bounds.right,bounds.bottom);
            s.lastTextUpdateElapsed=SystemClock.elapsedRealtime();
            GoogleV2TextOutline.show(s.app,bounds,rows);
            showTextWhenStable(s,TEXT_MENU_SETTLE_MS);
        });
    }
    static void onTextGesture(Context c,String token,boolean dragging){
        Session s=session(c,token);if(s==null)return;
        MAIN.post(()->{
            if(!ownsUi(s)||s.state.phase!=GoogleV2State.Phase.TEXT)return;
            s.lastGestureElapsed=SystemClock.elapsedRealtime();
            if(s.state.drag(dragging)&&dragging) {
                // A drag temporarily dismisses actions but must not delete
                // the selected-text outline or permanently suppress the menu.
                FloatActionMenu.dismissPreservingTextOutline();
                DiagnosticLog.i(s.app,"GOOGLE_V2","text menu paused for gesture");
            }
            showTextWhenStable(s,dragging
                    ?GESTURE_IDLE_RECOVERY_MS:TEXT_MENU_SETTLE_MS);
        });
    }

    /**
     * Trailing-edge, coalesced menu presenter. The previous one-shot
     * revision/dragging guard silently dropped EVERY pending menu when Google
     * emitted another word update or missed a gesture END. Reschedule until
     * the latest content has settled, then safely recover a stale drag state.
     */
    private static void showTextWhenStable(Session s,long delay){
        if(s.pendingTextMenu!=null)MAIN.removeCallbacks(s.pendingTextMenu);
        Runnable request=()->{
            s.pendingTextMenu=null;
            if(!ownsUi(s)||s.state.phase!=GoogleV2State.Phase.TEXT
                    ||s.state.text.isBlank())return;
            long now=SystemClock.elapsedRealtime();
            long wait=GoogleV2MenuRecoveryPolicy.waitForIdle(
                    now,s.lastTextUpdateElapsed,s.lastGestureElapsed,
                    s.state.dragging,TEXT_MENU_SETTLE_MS,GESTURE_IDLE_RECOVERY_MS);
            if(wait>0){
                showTextWhenStable(s,wait);
                return;
            }
            if(s.state.dragging){
                s.state.drag(false);
                DiagnosticLog.i(s.app,"GOOGLE_V2",
                        "text gesture end missing; recovered after idle");
            }
            Rect anchor=rect(s.state);
            FloatActionMenu.showTextAt(s.app,s.state.text,null,anchor);
            DiagnosticLog.i(s.app,"GOOGLE_V2","text menu visible="
                    +FloatActionMenu.hasVisibleTextMenu()
                    +" revision="+s.state.revision+" idleRecovered=true");
        };
        s.pendingTextMenu=request;
        MAIN.postDelayed(request,Math.max(1L,delay));
    }

    static void onRegion(Context context,String token,Rect bounds,String detail){
        Session s=session(context,token);if(s==null||bounds==null||bounds.isEmpty())return;
        MAIN.post(()->{
            if(!ownsUi(s)||!s.state.region(bounds.left,bounds.top,bounds.right,bounds.bottom))return;
            s.lastRegionUpdateElapsed=SystemClock.elapsedRealtime();
            cancelTextMenu(s);
            FloatActionMenu.dismiss();
            GoogleV2TextOutline.dismiss();
            showDoneWhenStable(s,REGION_MENU_SETTLE_MS);
        });
    }
    static void onRegionGesture(Context context,String token,boolean dragging){
        Session s=session(context,token);if(s==null)return;
        MAIN.post(()->{
            if(!ownsUi(s)||s.state.phase!=GoogleV2State.Phase.REGION)return;
            s.lastGestureElapsed=SystemClock.elapsedRealtime();
            s.state.drag(dragging);
            if(dragging)GoogleRegionConfirmOverlay.dismiss(token,"v2_drag");
            showDoneWhenStable(s,dragging
                    ?GESTURE_IDLE_RECOVERY_MS:REGION_MENU_SETTLE_MS);
        });
    }
    /** Screenshot completion must never be synthesized without a real Region. */
    private static void showDoneWhenStable(Session s,long delay){
        if(s.pendingRegionMenu!=null)MAIN.removeCallbacks(s.pendingRegionMenu);
        Runnable request=()->{
            s.pendingRegionMenu=null;
            if(!ownsUi(s)||s.state.phase!=GoogleV2State.Phase.REGION
                    ||!s.state.validBounds())return;
            long now=SystemClock.elapsedRealtime();
            long wait=GoogleV2MenuRecoveryPolicy.waitForIdle(
                    now,s.lastRegionUpdateElapsed,s.lastGestureElapsed,
                    s.state.dragging,REGION_MENU_SETTLE_MS,GESTURE_IDLE_RECOVERY_MS);
            if(wait>0){
                showDoneWhenStable(s,wait);
                return;
            }
            if(s.state.dragging){
                s.state.drag(false);
                DiagnosticLog.i(s.app,"GOOGLE_V2",
                        "region gesture end missing; recovered after idle");
            }
            GoogleRegionConfirmOverlay.show(s.app,s.token,rect(s.state),()->confirm(s));
            DiagnosticLog.i(s.app,"GOOGLE_V2","region confirm visible "+rect(s.state));
        };
        s.pendingRegionMenu=request;
        MAIN.postDelayed(request,Math.max(1L,delay));
    }
    private static void cancelTextMenu(Session s){
        if(s.pendingTextMenu!=null)MAIN.removeCallbacks(s.pendingTextMenu);
        s.pendingTextMenu=null;
    }
    private static void cancelRegionMenu(Session s){
        if(s.pendingRegionMenu!=null)MAIN.removeCallbacks(s.pendingRegionMenu);
        s.pendingRegionMenu=null;
    }

    static void onCommit(Context context,String token,String reason){
        Session s=session(context,token);
        if(s!=null)MAIN.post(()->{if(ownsUi(s))confirm(s);});
    }
    private static void confirm(Session s){
        if(!ownsUi(s)||!s.state.confirm())return;
        GoogleRegionConfirmOverlay.dismiss(s.token,"v2_confirm");
        DiagnosticLog.i(s.app,"GOOGLE_V2","confirm requested frameReady="+s.state.frameReady);
        LsposedStatusManager.confirmGoogleCtsRegionRemoteAsync(s.token,
                success->DiagnosticLog.i(s.app,"GOOGLE_V2","remote ack="+success));
        if(s.state.canDeliver())deliver(s);
        else MAIN.postDelayed(()->{
            if(!ownsUi(s)||s.state.phase!=GoogleV2State.Phase.AWAITING_FRAME)return;
            DiagnosticLog.i(s.app,"GOOGLE_V2","original frame timeout; no secondary screen capture");
            release(s,"missing_google_original_frame",false);
        },FRAME_TIMEOUT_MS);
    }
    private static void deliver(Session s){
        if(!s.state.delivered()||s.frame==null||s.frame.isRecycled())return;
        Bitmap original=s.frame;
        s.frame=null;
        Rect screen=rect(s.state);
        Bitmap output=original;
        try{
            Rect crop=CanonicalSessionGeometry.forFrame(s.app,
                    original.getWidth(),original.getHeight()).screenToFrame(screen);
            if(crop!=null&&!crop.isEmpty()&&(crop.width()<original.getWidth()
                    ||crop.height()<original.getHeight())){
                Bitmap clipped=Bitmap.createBitmap(original,crop.left,crop.top,
                        crop.width(),crop.height());
                if(clipped!=original){output=clipped;original.recycle();}
            }
        }catch(Throwable t){
            DiagnosticLog.i(s.app,"GOOGLE_V2","crop error="+t.getClass().getSimpleName());
        }
        ResultSession result=ResultSession.screenshot(output,screen);
        WorkflowSessionManager.Session workflow=WorkflowSessionManager.current();
        if(workflow!=null&&s.token.equals(workflow.externalKey()))
            WorkflowSessionManager.transition(s.app,workflow,
                    WorkflowSessionManager.Phase.RESULT_PENDING,"v2_handoff");
        boolean shown=ResultController.showAfterFirstFrame(s.app,result,
                ()->release(s,"result_visible",true));
        if(!shown){
            result.close();
            release(s,"result_launch_failed",false);
        }
        DiagnosticLog.i(s.app,"GOOGLE_V2","screenshot delivered="+shown);
    }
    static void onEnd(Context c,String token,String reason){
        Session s=session(c,token);if(s==null)return;
        MAIN.post(()->{
            if(!ownsUi(s))return;
            if(s.state.phase==GoogleV2State.Phase.AWAITING_FRAME
                    ||s.state.phase==GoogleV2State.Phase.DELIVERED)return;
            // Back, gesture navigation and Google Activity stop are cancellation.
            // Never resurrect a menu/outline after the native selection surface exits.
            release(s,"google_end:"+reason,false);
        });
    }
    /** An outside tap cancels the selection instead of merely hiding its menu. */
    static void dismissActiveTextSelection(Context context,String reason){
        if(context==null)return;
        WorkflowSessionManager.Session workflow=WorkflowSessionManager.current();
        if(workflow==null)return;
        Session s=SESSIONS.get(workflow.externalKey());
        if(s==null)return;
        MAIN.post(()->{
            if(ownsUi(s)&&s.state.phase==GoogleV2State.Phase.TEXT)
                release(s,"user_dismiss:"+reason,false);
        });
    }
    private static void release(Session s,String reason,boolean keepMenu){
        if(!SESSIONS.remove(s.token,s))return;
        cancelTextMenu(s);
        cancelRegionMenu(s);
        s.state.close();
        GoogleRegionConfirmOverlay.dismiss(s.token,"v2_"+reason);
        if(!keepMenu) {
            GoogleV2TextOutline.dismiss();
            FloatActionMenu.dismiss();
        }
        if(s.frame!=null&&!s.frame.isRecycled())s.frame.recycle();
        s.frame=null;
        // Scoped remote lease may always be released; unscoped app-local
        // state and FloatService MUST belong to this exact workflow.
        boolean current=WorkflowSessionManager.matchesExternal(s.token);
        LsposedStatusManager.clearGoogleCtsSessionRemote(s.token);
        if(current){
            new FloatSettings(s.app).clearGoogleCtsSession();
            WorkflowSessionManager.finishExternal(s.app,s.token,"v2_"+reason);
            FloatService service=FloatService.get();
            if(service!=null)service.onCircleFinished("google_v2_"+reason);
        }
        DiagnosticLog.i(s.app,"GOOGLE_V2","released "+reason+" currentOwner="+current);
    }
    private static Rect rect(GoogleV2State state){
        return state.validBounds()
                ?new Rect(state.left,state.top,state.right,state.bottom):null;
    }
    private static int at(Rect r,int n){
        return r==null?0:switch(n){case 0->r.left;case 1->r.top;case 2->r.right;default->r.bottom;};
    }
    private GoogleV2UiEngine(){}
}
