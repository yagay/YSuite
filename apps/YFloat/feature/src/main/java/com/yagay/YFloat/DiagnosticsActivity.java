package com.yagay.YFloat;

import com.yagay.yui.YViewLayout;
import com.yagay.yui.YViewSection;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** YFloat-only diagnostics. */
public final class DiagnosticsActivity extends AppCompatActivity {
    private FloatSettings fs;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        fs = new FloatSettings(this);

        LinearLayout root = YViewLayout.pageRoot(this,
                getString(R.string.yfloat_diag_page_title),
                getString(R.string.yfloat_diag_page_desc));

        YViewSection logging = YViewLayout.section(this,
                getString(R.string.yfloat_diag_log_title),
                getString(R.string.yfloat_diag_log_desc));
        MaterialSwitch loggingSwitch = YViewLayout.switchRow(this,
                getString(R.string.yfloat_diag_logging),
                getString(R.string.yfloat_diag_logging_desc),
                fs.diagnosticLogging(),
                (button, checked) -> fs.setBoolean(FloatSettings.K_DIAGNOSTIC, checked));
        YViewLayout.addRow(logging.body, YViewLayout.switchContainer(loggingSwitch));
        addButtonPair(logging.body,
                button(getString(R.string.yfloat_diag_save), this::exportDiagnostic),
                button(getString(R.string.yfloat_diag_clear), () -> {
                    DiagnosticLog.clear(this);
                    Toast.makeText(this, R.string.yfloat_diag_cleared, Toast.LENGTH_SHORT).show();
                }));
        YViewLayout.addSection(root, logging);

        setContentView(YViewLayout.scrollPage(this, root));
    }

    private MaterialButton button(String label, Runnable action) {
        MaterialButton button = YViewLayout.secondaryButton(this, label);
        button.setOnClickListener(v -> { if (action != null) action.run(); });
        return button;
    }

    private void addButtonPair(LinearLayout parent, MaterialButton left, MaterialButton right) {
        LinearLayout row = YViewLayout.buttonRow(this);
        LinearLayout.LayoutParams leftLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        leftLp.setMarginEnd(YViewLayout.dp(this, 6));
        row.addView(left, leftLp);
        LinearLayout.LayoutParams rightLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        rightLp.setMarginStart(YViewLayout.dp(this, 6));
        row.addView(right, rightLp);
        YViewLayout.addRow(parent, row);
    }

    private void exportDiagnostic() {
        String appText = DiagnosticLog.read(this);
        StringBuilder combined = new StringBuilder();
        if (!appText.isBlank()) combined.append(appText.trim()).append("\n");

        LsposedStatusManager.Snapshot s = LsposedStatusManager.snapshot();
        combined.append("\n===== LSPosed status =====\n")
                .append("serviceConnected=").append(s.serviceConnected)
                .append(" framework=").append(s.frameworkName)
                .append(" version=").append(s.frameworkVersion)
                .append(" api=").append(s.apiVersion).append("\n")
                .append("remoteConfigReady=").append(s.remoteConfigReady)
                .append(" provider=").append(s.remoteProviderEnabled()).append("\n")
                .append("googleScope=").append(s.googleScopeEnabled())
                .append(" googleLoaded=").append(s.googleTargetLoaded())
                .append(" googleStale=").append(s.googleTargetStale()).append("\n")
                .append("scope=").append(s.scope).append("\n")
                .append("runningProcesses=").append(s.runningProcesses).append("\n");
        if (!s.detail.isBlank()) combined.append("detail=").append(s.detail).append("\n");

        saveDiagnosticText(combined.toString());
    }

    private void saveDiagnosticText(String text) {
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
        String fileName = "YFloat-diagnostic-" + stamp + ".txt";
        Uri uri = null;
        try {
            ContentResolver resolver = getContentResolver();
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
            values.put(MediaStore.MediaColumns.MIME_TYPE, "text/plain");
            values.put(MediaStore.MediaColumns.RELATIVE_PATH,
                    Environment.DIRECTORY_DOWNLOADS + "/YFloat");
            values.put(MediaStore.MediaColumns.IS_PENDING, 1);

            uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri == null) throw new java.io.IOException("MediaStore insert returned null");

            try (OutputStream out = resolver.openOutputStream(uri, "w")) {
                if (out == null) throw new java.io.IOException("MediaStore returned null output stream");
                out.write(text.getBytes(StandardCharsets.UTF_8));
                out.flush();
            }

            ContentValues ready = new ContentValues();
            ready.put(MediaStore.MediaColumns.IS_PENDING, 0);
            resolver.update(uri, ready, null, null);

            DiagnosticLog.i(this, "DIAGNOSTIC_EXPORT",
                    "saved uri=" + uri + " name=" + fileName + " bytes="
                            + text.getBytes(StandardCharsets.UTF_8).length);
            Toast.makeText(this,
                    getString(R.string.yfloat_diag_saved, fileName),
                    Toast.LENGTH_LONG).show();
        } catch (Throwable t) {
            if (uri != null) {
                try { getContentResolver().delete(uri, null, null); } catch (Throwable ignored) {}
            }
            String message = t.getMessage();
            if (message == null || message.isBlank()) message = t.getClass().getSimpleName();
            Toast.makeText(this,
                    getString(R.string.yfloat_diag_save_failed,
                            t.getClass().getSimpleName(), message),
                    Toast.LENGTH_LONG).show();
            DiagnosticLog.i(this, "DIAGNOSTIC_EXPORT",
                    "failed=" + t.getClass().getName() + ":" + message);
        }
    }
}
