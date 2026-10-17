package com.yagay.YFloat;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

/**
 * One host-side authority for Google CTS ownership and terminal cleanup.
 * The UI engine owns image/text rendering only; the Hook owns Google's
 * Activity/voice objects only. Both refer to the same exact external token.
 */
final class GoogleCtsSessionCoordinator {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final long STARTUP_DIAGNOSTIC_MS = 12_000L;
    private static final Object LOCK = new Object();

    private static final class Owned {
        final String token;
        final WorkflowSessionManager.Session workflow;
        final GoogleCtsSessionState state = new GoogleCtsSessionState();
        final Context app;
        Owned(Context app,String token,WorkflowSessionManager.Session workflow){
            this.app=app;this.token=token;this.workflow=workflow;
        }
    }

    private static Owned current;

    static WorkflowSessionManager.Session begin(Context context,String token,long nowElapsed) {
        if(context==null||token==null||token.isBlank())return null;
        Context app=context.getApplicationContext();
        WorkflowSessionManager.Session workflow=WorkflowSessionManager.beginExternal(
                app,WorkflowSessionManager.Type.GOOGLE_CTS,token,"google_cts_trigger");
        WorkflowSessionManager.transition(app,workflow,
                WorkflowSessionManager.Phase.CAPTURING,"google_cts_trigger");
        Owned owned=new Owned(app,token,workflow);
        synchronized(LOCK){current=owned;}
        new FloatSettings(app).armGoogleCtsSession(
                token,nowElapsed+GoogleCtsContract.TRACE_SESSION_TTL_MS);
        boolean armed=LsposedStatusManager.armGoogleCtsSessionRemote(token,
                nowElapsed,nowElapsed+LsposedRuntimeConfig.GOOGLE_CTS_FALLBACK_WINDOW_MS);
        DiagnosticLog.i(app,"GOOGLE_CTS_ARM","remote="+armed
                +" session="+shortToken(token)
                +" fallbackMs="+LsposedRuntimeConfig.GOOGLE_CTS_FALLBACK_WINDOW_MS);
        if(!armed) {
            end(app,token,"remote_arm_failed",false);
            return null;
        }
        synchronized(LOCK){if(current==owned)owned.state.arm();}
        LensAccessibilityService.onNewGoogleCtsWorkflow(workflow);
        return workflow;
    }

    static void launchResult(Context context,String token,boolean success) {
        Owned owned=owned(token);
        if(owned==null)return;
        if(!success){
            end(context,token,"google_launch_failed",false);
            return;
        }
        WorkflowSessionManager.transition(owned.app,owned.workflow,
                WorkflowSessionManager.Phase.SELECTING,"google_cts_visible");
        MAIN.postDelayed(()->{
            if(owned(token)!=owned)return;
            synchronized(LOCK){
                if(owned.state.acknowledged())return;
            }
            DiagnosticLog.i(owned.app,"GOOGLE_CTS_SESSION",
                    "startup_pending session="+shortToken(token)
                    +" bootstrap_confirmed=false googleLoaded="
                    +LsposedStatusManager.snapshot().googleTargetLoaded()
                    +" no_automatic_hide=true");
        },STARTUP_DIAGNOSTIC_MS);
        MAIN.postDelayed(()->{
            if(owned(token)!=owned)return;
            DiagnosticLog.i(owned.app,"GOOGLE_CTS_SESSION",
                    "session_ttl_expired token="+shortToken(token));
            GoogleV2UiEngine.abandon(owned.app,token,"session_ttl");
            end(owned.app,token,"session_ttl",false);
        },GoogleCtsContract.TRACE_SESSION_TTL_MS);
    }

    static void hookReady(Context context,String token,String detail) {
        Owned owned=owned(token);
        if(owned==null)return;
        synchronized(LOCK){owned.state.acknowledge();}
        DiagnosticLog.i(owned.app,"GOOGLE_CTS_SESSION",
                "hook_ready="+shortToken(token)+" detail="+String.valueOf(detail));
    }

    static void selecting(String token) {
        Owned owned=owned(token);
        if(owned==null)return;
        synchronized(LOCK){owned.state.selecting();}
    }

    static void resultCommitted(String token) {
        Owned owned=owned(token);
        if(owned==null)return;
        synchronized(LOCK){
            if(!owned.state.commitResult())return;
        }
        DiagnosticLog.i(owned.app,"GOOGLE_CTS_SESSION",
                "result_committed="+shortToken(token));
    }

    static void resultVisible(String token) {
        Owned owned=owned(token);
        if(owned==null)return;
        synchronized(LOCK){owned.state.handoffVisible();}
    }

    static boolean mayCancelOnNavigation(String token) {
        Owned owned=owned(token);
        if(owned==null)return false;
        synchronized(LOCK){return owned.state.mayCancelOnNavigation();}
    }

    static void cancelCurrent(Context context,String reason) {
        Owned owned;
        synchronized(LOCK){owned=current;}
        if(owned==null)return;
        GoogleV2UiEngine.abandon(context,owned.token,reason);
        end(context,owned.token,reason,false);
    }

    static boolean end(Context context,String token,String reason,boolean success) {
        Owned owned;
        synchronized(LOCK) {
            owned=current;
            if(owned==null||!owned.token.equals(token)||!owned.state.close())return false;
            current=null;
        }
        // Closing the exact token is idempotent; stale callbacks cannot revoke
        // the remotely armed token of a newly started Google session.
        LsposedStatusManager.clearGoogleCtsSessionRemote(token);
        if(WorkflowSessionManager.matchesExternal(token)){
            new FloatSettings(owned.app).clearGoogleCtsSession(token);
            if(success)WorkflowSessionManager.finish(owned.app,owned.workflow,reason);
            else WorkflowSessionManager.cancel(owned.app,owned.workflow,reason);
            FloatService floatService=FloatService.get();
            if(floatService!=null)floatService.onCircleFinished("google_"+reason);
        }
        DiagnosticLog.i(owned.app,"GOOGLE_CTS_SESSION",
                "terminal="+reason+" success="+success+" session="+shortToken(token));
        return true;
    }

    static boolean owns(String token) { return owned(token)!=null; }

    private static Owned owned(String token) {
        if(token==null||token.isBlank())return null;
        synchronized(LOCK) {
            return current!=null&&current.token.equals(token)&&current.state.active()
                    &&current.workflow.current()?current:null;
        }
    }

    private static String shortToken(String token) {
        return token.substring(0,Math.min(8,token.length()));
    }
    private GoogleCtsSessionCoordinator(){}
}
