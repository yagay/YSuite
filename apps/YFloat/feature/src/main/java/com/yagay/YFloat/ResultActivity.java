package com.yagay.YFloat;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Bundle;
import android.net.Uri;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.yagay.yui.YUiWindowOptOut;

import java.util.List;

/**
 * Transparent native-selection host for the one UnifiedResultDialogFragment.
 *
 * This Activity no longer owns result UI, OCR state, popup geometry or result-ready timing. Every
 * screenshot / View / OCR result is rendered by one DialogFragment + one UnifiedResultPanel.
 */
public final class ResultActivity extends AppCompatActivity implements YUiWindowOptOut {
    private static final String DIALOG_TAG = "yfloat_result_dialog";
    static final int EXPORT_MARKDOWN=1;
    static final int EXPORT_JSON=2;
    static final int EXPORT_HTML=3;
    static final int EXPORT_CSV=4;
    private OcrDocument pendingExport;
    private int pendingExportType;
    private final ActivityResultLauncher<String> saveMarkdown =
        registerForActivityResult(new ActivityResultContracts.CreateDocument("text/markdown"),
                this::writeExport);
    private final ActivityResultLauncher<String> saveJson =
        registerForActivityResult(new ActivityResultContracts.CreateDocument("application/json"),
                this::writeExport);
    private final ActivityResultLauncher<String> saveHtml =
        registerForActivityResult(new ActivityResultContracts.CreateDocument("text/html"),
                this::writeExport);
    private final ActivityResultLauncher<String> saveCsv =
        registerForActivityResult(new ActivityResultContracts.CreateDocument("text/csv"),
                this::writeExport);

    public interface InlineResultSink {
        void onResult(String text, List<String> blocks);
    }

    /** Compatibility entry points; all routes converge on ResultController. */
    public static boolean showScreenshot(Context c, Bitmap image, Rect anchor) {
        if (c == null || image == null || image.isRecycled()) return false;
        return ResultSurfaceRouter.showScreenshot(c, image, anchor);
    }

    public static boolean showViewText(Context c, String text, Bitmap image, Rect anchor) {
        if (c == null || text == null || text.isBlank()) return false;
        return ResultSurfaceRouter.showViewText(c, text, image, anchor);
    }

    public static boolean showViewImage(Context c, Bitmap image, ViewNodeCandidate view, Rect anchor) {
        if (c == null || image == null || image.isRecycled()) return false;
        return ResultSurfaceRouter.showViewImage(c, image, view, anchor);
    }

    public static boolean showOcr(Context c, String text, List<String> blocks,
                                  Bitmap image, Rect anchor) {
        if (c == null) return false;
        return ResultSurfaceRouter.showOcr(c, text, blocks, image, anchor);
    }

    public static void captureNextForImage(Bitmap image, InlineResultSink sink) {
        OcrResultDispatcher.register(image, sink == null ? null :
                (text,blocks,document)->sink.onResult(text,blocks));
    }

    public static void clearInlineForImage(Bitmap image) {
        OcrResultDispatcher.cancel(image);
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (!acceptIntent(getIntent(), false)) {
            finishNoAnim();
            return;
        }
        overridePendingTransition(0, 0);
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (!acceptIntent(intent, true)) finishNoAnim();
    }

    private boolean acceptIntent(Intent intent, boolean reuse) {
        long token = intent == null ? 0L : intent.getLongExtra(ResultController.EXTRA_TOKEN, 0L);
        ResultController.Delivery delivery = ResultController.take(token);
        if (delivery == null || delivery.session == null) {
            DiagnosticLog.i(this, "RESULT_ACTIVITY", "missing session token=" + token
                    + " reuse=" + reuse);
            return false;
        }
        ResultSession next = delivery.session;
        ResultReadyCoordinator.Ticket readyTicket = delivery.readyTicket;
        Runnable firstFrameCallback = delivery.firstFrameCallback;

        Fragment existing = getSupportFragmentManager().findFragmentByTag(DIALOG_TAG);
        UnifiedResultDialogFragment dialog = existing instanceof UnifiedResultDialogFragment
                ? (UnifiedResultDialogFragment) existing : null;

        if (dialog == null) {
            dialog = new UnifiedResultDialogFragment();
            dialog.setInitialSession(next, readyTicket);
            try {
                dialog.showNow(getSupportFragmentManager(), DIALOG_TAG);
            } catch (Throwable t) {
                ResultReadyCoordinator.cancel(readyTicket, this, "dialog_show_failed");
                runCallback(firstFrameCallback);
                DiagnosticLog.i(this, "RESULT_ACTIVITY", "dialog show failed token=" + token
                        + " error=" + ScreenCaptureBackend.safeMessage(t));
                return false;
            }
        } else {
            dialog.showSession(next, readyTicket);
        }

        dialog.runAfterFirstVisibleFrame(firstFrameCallback);

        DiagnosticLog.i(this, "RESULT_ACTIVITY", (reuse ? "REUSE" : "CREATED")
                + " token=" + token + " mode=" + next.mode()
                + " origin=" + next.originMode()
                + " ready=" + (readyTicket == null ? "none" : readyTicket.id)
                + " dialogHost=true");
        return true;
    }

    /** User-initiated SAF export. Serializes only the chosen current document. */
    void exportOcrDocument(OcrDocument document,int format){
        if(document==null)return;
        pendingExport=document;
        pendingExportType=format;
        switch(format){
            case EXPORT_MARKDOWN -> saveMarkdown.launch("yfloat-ocr.md");
            case EXPORT_JSON -> saveJson.launch("yfloat-ocr.json");
            case EXPORT_HTML -> saveHtml.launch("yfloat-ocr.html");
            case EXPORT_CSV -> {
                if(document.tableTexts().isEmpty()){
                    pendingExport=null;
                    Toast.makeText(this,"No table data to export",Toast.LENGTH_SHORT).show();
                }else saveCsv.launch("yfloat-ocr-tables.csv");
            }
            default -> {
                pendingExport=null;
                throw new IllegalArgumentException("Unknown export format "+format);
            }
        }
    }

    private void writeExport(Uri destination){
        OcrDocument selected=pendingExport;
        int format=pendingExportType;
        pendingExport=null;
        if(destination==null)return;
        if(selected==null){
            Toast.makeText(this,"OCR result no longer available; recognize again",
                    Toast.LENGTH_LONG).show();
            return;
        }
        android.content.Context app=getApplicationContext();
        new Thread(()->{
            try{
                String serialized=switch(format){
                    case EXPORT_MARKDOWN -> PaddleDocumentExport.markdown(selected);
                    case EXPORT_JSON -> PaddleDocumentExport.json(selected);
                    case EXPORT_HTML -> PaddleDocumentExport.html(selected);
                    case EXPORT_CSV -> PaddleDocumentExport.csvTables(selected);
                    default -> throw new IllegalArgumentException("Unknown export format");
                };
                try(OutputStream stream=app.getContentResolver().openOutputStream(destination,"wt")){
                    if(stream==null)throw new IllegalStateException("Document output unavailable");
                    stream.write(serialized.getBytes(StandardCharsets.UTF_8));
                    stream.flush();
                }
                runOnUiThread(()->Toast.makeText(app,"Offline OCR file saved",
                        Toast.LENGTH_SHORT).show());
            }catch(Exception error){
                DiagnosticLog.i(app,"PADDLE_EXPORT","save_failed="+error.getClass().getSimpleName()+
                        " message="+error.getMessage());
                runOnUiThread(()->Toast.makeText(app,"OCR export failed: "+
                        error.getClass().getSimpleName(),Toast.LENGTH_LONG).show());
            }
        },"YFloat-Paddle-export").start();
    }

    private void runCallback(Runnable callback) {
        if (callback == null) return;
        try { callback.run(); } catch (Throwable ignored) {}
    }

    void finishFromDialog() {
        finishNoAnim();
    }

    private void finishNoAnim() {
        finish();
        overridePendingTransition(0, 0);
    }
}
