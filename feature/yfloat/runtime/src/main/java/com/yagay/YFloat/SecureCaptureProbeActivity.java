package com.yagay.YFloat;

import com.yagay.yui.YViewLayout;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.yagay.yui.YUiWindowOptOut;

/** End-to-end FLAG_SECURE probe for the controlled LSPosed screenshot provider. */
public final class SecureCaptureProbeActivity extends AppCompatActivity implements YUiWindowOptOut {
    private static final long STATUS_REFRESH_TIMEOUT_MS = 1_500L;

    private final Handler main = new Handler(Looper.getMainLooper());
    private TextView status;
    private MaterialButton retry;
    private boolean running;
    private int refreshGeneration;
    private LsposedStatusManager.Listener pendingStatusListener;
    private Runnable pendingStatusTimeout;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        ThemeSettings.applySystemBars(this);
        setContentView(buildContent());
        getWindow().getDecorView().postDelayed(this::runProbe, 700L);
    }

    @Override
    protected void onDestroy() {
        cancelPendingStatusRefresh();
        super.onDestroy();
    }

    private LinearLayout buildContent() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(YViewLayout.dp(this, 28), YViewLayout.dp(this, 28),
                YViewLayout.dp(this, 28), YViewLayout.dp(this, 28));
        root.setBackgroundColor(Color.rgb(
                SecureCaptureProbePolicy.MARKER_RED,
                SecureCaptureProbePolicy.MARKER_GREEN,
                SecureCaptureProbePolicy.MARKER_BLUE));

        TextView title = YViewLayout.text(this, com.yagay.suite.api.YLocale.text(com.yagay.YFloat.R.string.yfloat_dynamic_5c13aa73bf0a), 20, true);
        title.setTextColor(Color.WHITE);
        root.addView(title, new LinearLayout.LayoutParams(-2, -2));

        status = YViewLayout.caption(this,
                com.yagay.suite.api.YLocale.text(com.yagay.YFloat.R.string.yfloat_dynamic_436814f4594d),
                14);
        status.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(-1, -2);
        statusLp.topMargin = YViewLayout.dp(this, 16);
        root.addView(status, statusLp);

        LinearLayout buttons = YViewLayout.buttonRow(this);
        retry = YViewLayout.secondaryButton(this, com.yagay.suite.api.YLocale.text(com.yagay.YFloat.R.string.yfloat_dynamic_3c2358b2ec16));
        retry.setEnabled(false);
        retry.setOnClickListener(v -> runProbe());
        buttons.addView(retry, new LinearLayout.LayoutParams(0, -2, 1f));

        MaterialButton close = YViewLayout.secondaryButton(this, com.yagay.suite.api.YLocale.text(com.yagay.YFloat.R.string.yfloat_dynamic_11d024154013));
        close.setOnClickListener(v -> finish());
        buttons.addView(close, new LinearLayout.LayoutParams(0, -2, 1f));

        LinearLayout.LayoutParams buttonsLp = new LinearLayout.LayoutParams(-1, -2);
        buttonsLp.topMargin = YViewLayout.dp(this, 18);
        root.addView(buttons, buttonsLp);
        return root;
    }

    private void runProbe() {
        if (running) return;
        running = true;
        retry.setEnabled(false);
        status.setText(com.yagay.suite.api.YLocale.text(com.yagay.YFloat.R.string.yfloat_dynamic_cede0dc015d9));

        final int generation = ++refreshGeneration;
        cancelPendingStatusRefresh();

        pendingStatusListener = snapshot -> {
            if (!running || generation != refreshGeneration) return;
            cancelPendingStatusRefresh();
            evaluatePreconditionsAndCapture();
        };
        LsposedStatusManager.addListener(pendingStatusListener, false);

        pendingStatusTimeout = () -> {
            if (!running || generation != refreshGeneration) return;
            cancelPendingStatusRefresh();
            evaluatePreconditionsAndCapture();
        };
        main.postDelayed(pendingStatusTimeout, STATUS_REFRESH_TIMEOUT_MS);
        LsposedStatusManager.syncRuntimeConfigAsync();
    }

    private void evaluatePreconditionsAndCapture() {
        FloatSettings fs = new FloatSettings(this);
        LsposedStatusManager.Snapshot s = LsposedStatusManager.snapshot();
        if (!PrivilegeManager.canUseLsposedSecureScreenshot(fs)) {
            showResult(false, com.yagay.suite.api.YLocale.text(com.yagay.YFloat.R.string.yfloat_dynamic_7290872c4b2e) + gateSummary(fs, s), null);
            return;
        }
        if (LensAccessibilityService.get() == null) {
            showResult(false, com.yagay.suite.api.YLocale.text(com.yagay.YFloat.R.string.yfloat_dynamic_b6f2dd9329a6) + gateSummary(fs, s), null);
            return;
        }

        status.setText(com.yagay.suite.api.YLocale.text(com.yagay.YFloat.R.string.yfloat_dynamic_b8c7c7cf1374));
        DiagnosticLog.i(this, "SECURE_CAPTURE_PROBE", "start " + gateSummary(fs, s));

        ScreenCaptureBackend.captureSecureAccessibility(getApplicationContext(), bitmap ->
                runOnUiThread(() -> evaluate(bitmap)), error ->
                runOnUiThread(() -> showResult(false,
                        com.yagay.suite.api.YLocale.text(com.yagay.YFloat.R.string.yfloat_dynamic_68e9ce66bfd7) + ScreenCaptureBackend.safeMessage(error), error)));
    }

    private String gateSummary(FloatSettings fs, LsposedStatusManager.Snapshot s) {
        String runningTargets = s.runningProcesses.isEmpty()
                ? com.yagay.suite.api.YLocale.text(com.yagay.YFloat.R.string.yfloat_dynamic_72077749f794) : String.join(", ", s.runningProcesses);
        return com.yagay.suite.api.YLocale.text(com.yagay.YFloat.R.string.yfloat_dynamic_18ee4377025f) + fs.enhancedMode()
                + " lsposed=" + fs.lsposedEnabled()
                + " secureScreenshot=" + fs.lsposedSecureScreenshot()
                + com.yagay.suite.api.YLocale.text(com.yagay.YFloat.R.string.yfloat_dynamic_418c5e5de3a3) + s.serviceConnected
                + " remoteConfig=" + s.remoteConfigReady
                + " systemScope=" + s.systemScopeEnabled
                + " systemLoaded=" + s.systemLoaded
                + com.yagay.suite.api.YLocale.text(com.yagay.YFloat.R.string.yfloat_dynamic_589f129026cd) + s.remoteEnhancedMode
                + " lsposed=" + s.remoteLsposedEnabled
                + " secureScreenshot=" + s.remoteSecureScreenshotEnabled
                + "\nloadedTargets=" + runningTargets
                + (s.detail.isBlank() ? "" : "\ndetail=" + s.detail);
    }

    private void cancelPendingStatusRefresh() {
        LsposedStatusManager.Listener listener = pendingStatusListener;
        pendingStatusListener = null;
        if (listener != null) LsposedStatusManager.removeListener(listener);
        Runnable timeout = pendingStatusTimeout;
        pendingStatusTimeout = null;
        if (timeout != null) main.removeCallbacks(timeout);
    }

    private void evaluate(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled() || bitmap.getWidth() <= 0 || bitmap.getHeight() <= 0) {
            showResult(false, com.yagay.suite.api.YLocale.text(com.yagay.YFloat.R.string.yfloat_dynamic_2174c79259a2), null);
            return;
        }

        int total = 0;
        int matches = 0;
        int centerX = bitmap.getWidth() / 2;
        int centerY = bitmap.getHeight() / 2;
        int stepX = Math.max(1, bitmap.getWidth() / 40);
        int stepY = Math.max(1, bitmap.getHeight() / 40);
        for (int dy = -2; dy <= 2; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                int x = clamp(centerX + dx * stepX, 0, bitmap.getWidth() - 1);
                int y = clamp(centerY + dy * stepY, 0, bitmap.getHeight() - 1);
                int pixel = bitmap.getPixel(x, y);
                if (SecureCaptureProbePolicy.isMarkerColor(
                        Color.red(pixel), Color.green(pixel), Color.blue(pixel))) {
                    matches++;
                }
                total++;
            }
        }
        boolean success = SecureCaptureProbePolicy.isSuccessful(matches, total);
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        bitmap.recycle();

        if (success) {
            showResult(true,
                    com.yagay.suite.api.YLocale.text(
                            R.string.yfloat_capture_probe_success, matches, total, width, height),
                    null);
        } else {
            showResult(false,
                    com.yagay.suite.api.YLocale.text(
                            R.string.yfloat_capture_probe_failure, matches, total),
                    null);
        }
    }

    private void showResult(boolean success, String message, Throwable error) {
        cancelPendingStatusRefresh();
        running = false;
        retry.setEnabled(true);
        status.setText((success ? "✅ " : "❌ ") + message);
        DiagnosticLog.i(this, "SECURE_CAPTURE_PROBE",
                (success ? "PASS " : "FAIL ") + message
                        + (error == null ? "" : " error=" + error.getClass().getSimpleName()));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
