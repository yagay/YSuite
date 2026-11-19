package com.yagay.YFloat;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Paddle-only screenshot/document OCR coordinator.
 *
 * UI requests are latest-wins, document requests share an epoch. This layer
 * only owns request validity and result delivery. PaddleRecognitionRuntime
 * exclusively owns model choice and retry, while PaddlePipelineV2 owns
 * inference and geometric text/structure. There is no ML Kit, legacy script
 * fusion, fake glyph splitting or post-recognition text reconstruction.
 */
public final class OcrEngine {
    public interface DocumentCallback {
        void onSuccess(OcrDocument document);
        void onFailure(Throwable error);
    }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final AtomicLong UI_GENERATION = new AtomicLong(0L);
    private static final AtomicLong DOCUMENT_EPOCH = new AtomicLong(0L);
    private static final AtomicLong DOCUMENT_REQUEST_SEQUENCE = new AtomicLong(0L);

    private static final class RequestToken {
        final boolean ui;
        final long requestId;
        final long epoch;
        final long workflowId;

        private RequestToken(boolean ui, long requestId, long epoch, long workflowId) {
            this.ui = ui;
            this.requestId = requestId;
            this.epoch = epoch;
            this.workflowId = workflowId;
        }

        static RequestToken ui(Context app) {
            WorkflowSessionManager.Session workflow = WorkflowSessionManager.ensureCurrent(
                    app, WorkflowSessionManager.Type.OCR, "ocr_ui_request");
            WorkflowSessionManager.transition(app, workflow,
                    WorkflowSessionManager.Phase.RECOGNIZING, "ocr_ui_request");
            long request = UI_GENERATION.incrementAndGet();
            return new RequestToken(true, request, request, workflow.id());
        }

        static RequestToken document() {
            return new RequestToken(false, DOCUMENT_REQUEST_SEQUENCE.incrementAndGet(),
                    DOCUMENT_EPOCH.get(), WorkflowSessionManager.currentId());
        }

        boolean current() {
            return ui
                    ? requestId == UI_GENERATION.get()
                            && WorkflowSessionManager.isCurrent(workflowId)
                    : epoch == DOCUMENT_EPOCH.get();
        }

        long currentMarker() { return ui ? UI_GENERATION.get() : DOCUMENT_EPOCH.get(); }
        String lane() { return ui ? "ui" : "document"; }
    }

    public static void invalidatePending(Context c, String reason) {
        long generation = UI_GENERATION.incrementAndGet();
        if (c != null) {
            DiagnosticLog.i(c.getApplicationContext(), "OCR_SESSION", "invalidate lane=ui generation="
                    + generation + " reason=" + (reason == null ? "unknown" : reason));
        }
    }

    public static void invalidateDocumentPending(Context c, String reason) {
        long epoch = DOCUMENT_EPOCH.incrementAndGet();
        if (c != null) {
            DiagnosticLog.i(c.getApplicationContext(), "OCR_SESSION", "invalidate lane=document epoch="
                    + epoch + " reason=" + (reason == null ? "unknown" : reason));
        }
    }

    public static void recognize(Context c, Bitmap b) { recognize(c, b, null); }

    public static void recognize(Context c, Bitmap b, Rect anchor) {
        start(c, b, anchor == null ? null : new Rect(anchor), null, true);
    }

    public static void recognizeDocument(Context c, Bitmap b, DocumentCallback callback) {
        start(c, b, null, callback, false);
    }

    private static void start(Context c, Bitmap b, Rect anchor,
                              DocumentCallback callback, boolean deliverUi) {
        if (c == null) return;
        Context app = c.getApplicationContext();
        FloatService service = deliverUi ? FloatService.get() : null;
        RequestToken request = deliverUi ? RequestToken.ui(app) : RequestToken.document();
        DiagnosticLog.i(app, "OCR_REQUEST", "request=" + request.requestId
                + " lane=" + request.lane()
                + " workflow=" + request.workflowId
                + (request.ui ? "" : " epoch=" + request.epoch)
                + " bitmap=" + bitmapSize(b)
                + " anchor=" + (anchor == null ? "none" : anchor.toShortString()));

        if (deliverUi && service != null) service.onCircleRecognizeStarted();
        if (b == null || b.isRecycled() || b.getWidth() <= 0 || b.getHeight() <= 0) {
            fail(app, service, callback, deliverUi, request, "ocr_invalid_bitmap",
                    com.yagay.suite.api.YLocale.text(com.yagay.YFloat.R.string.yfloat_generated_57397f700acc), new IllegalArgumentException("invalid bitmap"));
            return;
        }
        startSelectedEngine(app, service, b, anchor, callback, deliverUi, request);
    }


    private static void startSelectedEngine(Context app, FloatService service,
            Bitmap source,Rect anchor,DocumentCallback callback,boolean deliverUi,
            RequestToken request){
        if(stale(app,request,"engine_start"))return;
        int choice=PaddleRecognitionRuntime.normalModel(readOcrEngineModeSafely(app));
        DiagnosticLog.i(app,"OCR_ENGINE","request="+request.requestId+
                " mode=paddle_native model="+choice+" lane="+request.lane());
        PaddleRecognitionRuntime.recognize(app,source,choice,
                new PaddleRecognitionRuntime.Callback(){
                    @Override public void onSuccess(OcrDocument doc,int model,long elapsedMs){
                        if(stale(app,request,"paddle_success"))return;
                        if(doc==null||doc.fullText().isBlank()){
                            fail(app,service,callback,deliverUi,request,"paddle_empty",
                                    "PaddleOCR recognized no selectable text",
                                    new IllegalStateException("Empty Paddle document"));
                            return;
                        }
                        DiagnosticLog.i(app,"PADDLE_NATIVE",
                                "model="+model+" chars="+doc.chars().size()+
                                " blocks="+doc.blocks().size()+" elapsed="+elapsedMs);
                        deliver(app,service,source,anchor,callback,deliverUi,doc,request);
                    }
                    @Override public void onFailure(Throwable error){
                        if(stale(app,request,"paddle_failure"))return;
                        fail(app,service,callback,deliverUi,request,"paddle_failure",
                                error==null?"PaddleOCR failed":error.getMessage(),error);
                    }
                });
    }

    private static void deliver(Context app, FloatService service, Bitmap source, Rect anchor,
                                DocumentCallback callback, boolean deliverUi,
                                OcrDocument document, RequestToken request) {
        if (document == null || document.fullText().isBlank() || stale(app, request, "deliver")) return;
        MAIN.post(() -> {
            if (stale(app, request, "deliver_main")) return;
            if (callback != null) {
                try { callback.onSuccess(document); }
                catch (Throwable t) {
                    DiagnosticLog.i(app, "OCR_DISPATCH", "success callback failed=" + safe(t));
                    try { callback.onFailure(new IllegalStateException("OCR success callback failed", t)); }
                    catch (Throwable failureError) {
                        DiagnosticLog.i(app, "OCR_DISPATCH", "failure callback also failed=" + safe(failureError));
                    }
                }
                return;
            }
            if (!deliverUi) return;
            if (service != null) service.onOcrResults(Math.max(1, document.chars().size()));
            java.util.ArrayList<String> displayBlocks=new java.util.ArrayList<>(document.blocks());
            displayBlocks.addAll(document.tableTexts());
            displayBlocks.addAll(document.sealTexts());
            for(String chart:document.chartTexts()){
                if(chart!=null&&!chart.isBlank())displayBlocks.add("Chart labels: "+chart);
            }
            for(String formula:document.formulaTexts()){
                if(formula!=null&&!formula.isBlank())displayBlocks.add("LaTeX: "+formula);
            }
            OcrResultDispatcher.deliver(app, document.fullText(), displayBlocks, source, anchor,document);
            DiagnosticLog.i(app, "OCR_DISPATCH", "engine=" + document.engine()
                    + " chars=" + document.chars().size() + " lines=" + document.lines().size());
        });
    }

    private static void fail(Context app, FloatService service, DocumentCallback callback,
                             boolean deliverUi, RequestToken request, String reason,
                             String userMessage, Throwable error) {
        if (stale(app, request, "fail_" + reason)) return;
        MAIN.post(() -> {
            if (stale(app, request, "fail_main_" + reason)) return;
            if (callback != null) {
                try { callback.onFailure(error == null ? new IllegalStateException(reason) : error); }
                catch (Throwable ignored) { }
                return;
            }
            if (deliverUi) {
                if (service != null) service.onCircleFinished(reason);
                Toast.makeText(app, userMessage == null ? com.yagay.suite.api.YLocale.text(com.yagay.YFloat.R.string.yfloat_generated_b46129cf4767) : userMessage, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private static boolean stale(Context app, RequestToken request, String stage) {
        if (request != null && request.current()) return false;
        String lane = request == null ? "unknown" : request.lane();
        long id = request == null ? -1L : request.requestId;
        long marker = request == null ? -1L : request.currentMarker();
        long epoch = request == null ? -1L : request.epoch;
        DiagnosticLog.i(app, "OCR_SESSION", "drop stale lane=" + lane
                + " request=" + id
                + (request != null && !request.ui ? " epoch=" + epoch : "")
                + " current=" + marker + " stage=" + stage);
        return true;
    }

    private static int readOcrEngineModeSafely(Context app) {
        try { return new FloatSettings(app).ocrEngineMode(); }
        catch (Throwable t) {
            DiagnosticLog.i(app, "OCR_ENGINE", "read fallback=" + safe(t));
            return 0;
        }
    }


    private static String bitmapSize(Bitmap b) {
        if (b == null) return "null";
        if (b.isRecycled()) return "recycled";
        return b.getWidth() + "x" + b.getHeight();
    }

    private static String safe(Throwable t) {
        if (t == null) return "unknown";
        String m = t.getMessage();
        return m == null || m.isBlank() ? t.getClass().getSimpleName() : m;
    }

    private OcrEngine() {}
}
