package com.yagay.YFloat;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;

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

    private static final class Session {
        final String token;
        final GoogleV2State state = new GoogleV2State();
        Bitmap frame;
        Context app;
        long issuedAt = android.os.SystemClock.elapsedRealtime();
        Session(Context app, String token) { this.app=app;this.token=token; }
    }

    private static Session session(Context context, String token) {
        if (context == null || token == null || token.isBlank()
                || !WorkflowSessionManager.matchesExternal(token)) return null;
        Context app = context.getApplicationContext();
        Session s = SESSIONS.computeIfAbsent(token,k->new Session(app,k));
        return s;
    }

    static void onFrame(Context context,String token,Bitmap frame) {
        if (frame == null || frame.isRecycled()) return;
        Session s=session(context,token);
        if(s==null){frame.recycle();return;}
        MAIN.post(()->{
            if(s.state.terminal()){frame.recycle();return;}
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
        Session s=session(context,token);
        if(s==null||text==null||text.isBlank()) return;
        MAIN.post(()->{
            Rect use=pickBounds(s,bounds);
            if(!s.state.text(text,at(use,0),at(use,1),at(use,2),at(use,3)))return;
            GoogleRegionConfirmOverlay.dismiss(token,"v2_text");
            if(use!=null)GoogleV2TextOutline.show(s.app,use);
            showTextWhenStable(s,100);
            DiagnosticLog.i(s.app,"GOOGLE_V2","text chars="+text.length()+" bounds="+use);
        });
    }
    static void onTextBounds(Context c,String token,Rect bounds){
        Session s=session(c,token);
        if(s==null||bounds==null||bounds.isEmpty())return;
        MAIN.post(()->{
            if(s.state.phase!=GoogleV2State.Phase.TEXT)return;
            s.state.bounds(bounds.left,bounds.top,bounds.right,bounds.bottom);
            GoogleV2TextOutline.show(s.app,bounds);
            if(!s.state.dragging)showTextWhenStable(s,90);
        });
    }
    static void onTextGesture(Context c,String token,boolean dragging){
        Session s=session(c,token);if(s==null)return;
        MAIN.post(()->{
            if(s.state.phase!=GoogleV2State.Phase.TEXT)return;
            if(s.state.drag(dragging)&&dragging)FloatActionMenu.dismiss();
            if(!dragging)showTextWhenStable(s,110);
        });
    }

    private static void showTextWhenStable(Session s,long delay){
        final int revision=s.state.revision;
        MAIN.postDelayed(()->{
            if(SESSIONS.get(s.token)!=s||revision!=s.state.revision
                    ||s.state.dragging||s.state.phase!=GoogleV2State.Phase.TEXT)return;
            Rect anchor=rect(s.state);
            FloatActionMenu.showTextAt(s.app,s.state.text,null,anchor);
            DiagnosticLog.i(s.app,"GOOGLE_V2","text menu visible="
                    +FloatActionMenu.hasVisibleTextMenu()+" revision="+revision);
        },delay);
    }

    static void onRegion(Context context,String token,Rect bounds,String detail){
        Session s=session(context,token);if(s==null||bounds==null||bounds.isEmpty())return;
        MAIN.post(()->{
            if(!s.state.region(bounds.left,bounds.top,bounds.right,bounds.bottom))return;
            FloatActionMenu.dismiss();
            GoogleV2TextOutline.dismiss();
            showDoneWhenStable(s,280);
        });
    }
    static void onRegionGesture(Context context,String token,boolean dragging){
        Session s=session(context,token);if(s==null)return;
        MAIN.post(()->{
            if(s.state.phase!=GoogleV2State.Phase.REGION)return;
            s.state.drag(dragging);
            if(dragging)GoogleRegionConfirmOverlay.dismiss(token,"v2_drag");
            else showDoneWhenStable(s,180);
        });
    }
    private static void showDoneWhenStable(Session s,long wait){
        final int revision=s.state.revision;
        MAIN.postDelayed(()->{
            if(SESSIONS.get(s.token)!=s||s.state.revision!=revision
                    ||s.state.phase!=GoogleV2State.Phase.REGION
                    ||s.state.dragging||!s.state.validBounds())return;
            GoogleRegionConfirmOverlay.show(s.app,s.token,rect(s.state),()->confirm(s));
            DiagnosticLog.i(s.app,"GOOGLE_V2","region confirm visible "+rect(s.state));
        },wait);
    }
    static void onCommit(Context context,String token,String reason){
        Session s=session(context,token);if(s!=null)MAIN.post(()->confirm(s));
    }
    private static void confirm(Session s){
        if(!s.state.confirm())return;
        GoogleRegionConfirmOverlay.dismiss(s.token,"v2_confirm");
        DiagnosticLog.i(s.app,"GOOGLE_V2","confirm requested frameReady="+s.state.frameReady);
        LsposedStatusManager.confirmGoogleCtsRegionRemoteAsync(s.token,
                success->DiagnosticLog.i(s.app,"GOOGLE_V2","remote ack="+success));
        if(s.state.canDeliver())deliver(s);
        else MAIN.postDelayed(()->{
            if(s.state.phase!=GoogleV2State.Phase.AWAITING_FRAME)return;
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
            if(s.state.phase==GoogleV2State.Phase.AWAITING_FRAME
                    ||s.state.phase==GoogleV2State.Phase.DELIVERED)return;
            if(s.state.phase==GoogleV2State.Phase.TEXT&&!s.state.text.isBlank()){
                // End the Google-owned workflow first, because closing its
                // OverlayScene would otherwise delete a just-created toolbar.
                String finalText=s.state.text;
                Rect finalBounds=rect(s.state);
                release(s,"text_handoff",true);
                MAIN.post(()->{
                    if(finalBounds!=null)GoogleV2TextOutline.show(s.app,finalBounds);
                    FloatActionMenu.showTextAt(s.app,finalText,null,finalBounds);
                    DiagnosticLog.i(s.app,"GOOGLE_V2",
                            "final text handoff visible="+FloatActionMenu.hasVisibleTextMenu());
                });
            }else release(s,"google_end:"+reason,false);
        });
    }
    private static void release(Session s,String reason,boolean keepMenu){
        if(!SESSIONS.remove(s.token,s))return;
        s.state.close();
        GoogleRegionConfirmOverlay.dismiss(s.token,"v2_"+reason);
        if(!keepMenu) {
            GoogleV2TextOutline.dismiss();
            FloatActionMenu.dismiss();
        }
        if(s.frame!=null&&!s.frame.isRecycled())s.frame.recycle();
        s.frame=null;
        new FloatSettings(s.app).clearGoogleCtsSession();
        LsposedStatusManager.clearGoogleCtsSessionRemote(s.token);
        WorkflowSessionManager.finishExternal(s.app,s.token,"v2_"+reason);
        FloatService service=FloatService.get();
        if(service!=null)service.onCircleFinished("google_v2_"+reason);
        DiagnosticLog.i(s.app,"GOOGLE_V2","released "+reason);
    }
    private static Rect pickBounds(Session s,Rect bounds){
        return bounds!=null&&!bounds.isEmpty()?new Rect(bounds):rect(s.state);
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
