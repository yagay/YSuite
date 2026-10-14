package com.yagay.YFloat;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

/**
 * Native Circle entry. The only business owner for capture, frozen canvas and
 * finish/rollback. Former FLCircleInlineOverlay and GoogleCtsTrigger are not
 * installed or invoked by this workflow.
 */
final class FLCircleController {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static CaptureTransaction pending;
    private static WorkflowSessionManager.Session current;

    static synchronized void show(Context context) {
        if (context == null) return;
        Context app = context.getApplicationContext();
        NativeCircleWorkspace.dismissActive("replaced");
        cancelPrevious(app);

        WorkflowSessionManager.Session session=WorkflowSessionManager.begin(
                app,WorkflowSessionManager.Type.CIRCLE,"native_circle");
        WorkflowSessionManager.transition(app,session,
                WorkflowSessionManager.Phase.CAPTURING,"native_capture");
        CaptureTransaction tx=CaptureTransaction.begin(app,"native_circle")
                .hideFloatingIcon("native_circle_"+session.id())
                .hideCircleBorder("native_circle_capture");
        pending=tx;
        current=session;

        DiagnosticLog.i(app,"CIRCLE_NATIVE","begin workflow="+session.id()
                +" source=local_capture googleHook=false");
        FLCircleCapture.capture(app,frame->MAIN.post(()->{
            if(!session.current()||pending!=tx){
                frame.recycle();
                tx.close();
                return;
            }
            boolean opened=NativeCircleWorkspace.show(app,frame,()->{
                frame.recycle();
                complete(app,tx,session,"workspace_closed");
            });
            if(!opened){
                frame.recycle();
                WorkflowSessionManager.fail(app,session,"native_workspace_failed");
                complete(app,tx,session,"show_failed");
                Toast.makeText(app,"Circle canvas unavailable",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            WorkflowSessionManager.transition(app,session,
                    WorkflowSessionManager.Phase.SELECTING,"native_canvas_visible");
            tx.overlayReady("native_circle_ready",
                    collapsed->NativeCircleWorkspace.promoteFocus(
                            collapsed?"shade_closed":"shade_settled"));
        }),error->MAIN.post(()->{
            if(pending!=tx||!session.current()){
                tx.close();return;
            }
            WorkflowSessionManager.fail(app,session,"native_capture_failed");
            complete(app,tx,session,"capture_failed");
            String message=ScreenCaptureBackend.safeMessage(error);
            DiagnosticLog.i(app,"CIRCLE_NATIVE","capture failed="+message);
            Toast.makeText(app,"Screenshot unavailable: "+message,
                    Toast.LENGTH_LONG).show();
        }));
    }

    private static synchronized void complete(Context app,CaptureTransaction tx,
                                              WorkflowSessionManager.Session session,
                                              String reason) {
        if(pending==tx)pending=null;
        if(current==session)current=null;
        tx.close();
        if(session.current())WorkflowSessionManager.finish(app,session,reason);
        DiagnosticLog.i(app,"CIRCLE_NATIVE","finished="+reason);
    }

    private static void cancelPrevious(Context app) {
        CaptureTransaction prior=pending;
        WorkflowSessionManager.Session session=current;
        pending=null;
        current=null;
        if(prior!=null)prior.close();
        if(session!=null&&session.current())
            WorkflowSessionManager.cancel(app,session,"new_native_circle");
    }

    private FLCircleController(){}
}
