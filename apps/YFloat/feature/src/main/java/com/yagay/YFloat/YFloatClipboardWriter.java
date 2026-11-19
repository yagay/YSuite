package com.yagay.YFloat;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

/** One clipboard entry point for OCR results and floating text-selection actions. */
final class YFloatClipboardWriter {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    static void copyText(Context context, String text, String source) {
        if (context == null) return;
        Context app = context.getApplicationContext();
        if (app == null) app = context;
        final Context target = app;
        final String value = text == null ? "" : text;
        final String origin = source == null ? "unknown" : source;
        if (Looper.myLooper() != Looper.getMainLooper()) {
            MAIN.post(() -> copyText(target, value, origin));
            return;
        }
        if (value.isEmpty()) {
            DiagnosticLog.i(target, "TEXT_CLIPBOARD", "FAILED source=" + origin + " reason=empty");
            failure(target);
            return;
        }
        try {
            ClipboardManager clipboard = (ClipboardManager)
                    target.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard == null) {
                throw new IllegalStateException("ClipboardManager unavailable");
            }
            clipboard.setPrimaryClip(ClipData.newPlainText("YFloat", value));
            // Android may deny clipboard reads to background apps; do not read it back.
            // A successful API return means the write was accepted, not paste-verified.
            DiagnosticLog.i(target, "TEXT_CLIPBOARD",
                    "WRITE_ACCEPTED source=" + origin + " chars=" + value.length()
                            + " package=" + target.getPackageName());
            Toast.makeText(target, com.yagay.suite.api.YLocale.text(
                    com.yagay.YFloat.R.string.yfloat_generated_2e84b8e9c556),
                    Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            DiagnosticLog.i(target, "TEXT_CLIPBOARD",
                    "FAILED source=" + origin + " chars=" + value.length()
                            + " error=" + e.getClass().getSimpleName() + ": " + e.getMessage());
            failure(target);
        }
    }

    private static void failure(Context context) {
        Toast.makeText(context, R.string.yfloat_clipboard_copy_failed, Toast.LENGTH_SHORT).show();
    }

    private YFloatClipboardWriter() {}
}
