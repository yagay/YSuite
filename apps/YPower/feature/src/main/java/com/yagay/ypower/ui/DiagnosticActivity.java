package com.yagay.ypower.ui;

import android.app.AlertDialog;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.yagay.ypower.R;
import com.yagay.ypower.diag.DiagnosticEngine;
import com.yagay.ypower.diag.ReportExporter;
import com.yagay.ypower.diag.RuntimeDiagnosticSession;
import com.yagay.ypower.model.DiagnosticLevel;
import com.yagay.ypower.model.DiagnosticReport;
import com.yagay.ypower.xposed.XposedBridgeManager;
import com.yagay.yui.YViewLayout;
import com.yagay.yui.YViewScreen;
import com.yagay.yui.YViewStatusTone;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DiagnosticActivity extends AppCompatActivity {
    private String packageName;
    private Spinner levelSpinner;
    private Spinner viewSpinner;
    private TextView sessionStatus;
    private TextView output;
    private DiagnosticReport lastReport;

    private final ActivityResultLauncher<String> createJsonDocument =
            registerForActivityResult(
                    new ActivityResultContracts.CreateDocument("application/json"),
                    uri -> {
                        if (uri == null || lastReport == null) return;
                        try {
                            ReportExporter.writeToUri(this, uri, lastReport);
                            Toast.makeText(this, R.string.yp_json_saved, Toast.LENGTH_LONG).show();
                        } catch (Exception e) {
                            Toast.makeText(
                                    this,
                                    getString(R.string.yp_export_failed, e.getMessage()),
                                    Toast.LENGTH_LONG).show();
                        }
                    }
            );

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        packageName = getIntent().getStringExtra("package");
        if (packageName == null || packageName.isBlank()) {
            finish();
            return;
        }
        buildUi();
        refreshSessionStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (sessionStatus != null) refreshSessionStatus();
    }

    private void buildUi() {
        YViewScreen screen = YViewLayout.install(
                this,
                getString(R.string.yp_diag_title),
                packageName
        );
        LinearLayout root = screen.getContent();

        LinearLayout intro = YViewLayout.card(
                root,
                getString(R.string.yp_evidence_title),
                getString(R.string.yp_evidence_desc)
        );
        intro.addView(YViewLayout.statusLine(
                this,
                XposedBridgeManager.isReady()
                        ? getString(R.string.yp_diag_lsposed_connected)
                        : getString(R.string.yp_diag_lsposed_disconnected),
                XposedBridgeManager.isReady() ? YViewStatusTone.Good : YViewStatusTone.Warning
        ));

        LinearLayout control = YViewLayout.card(
                root,
                getString(R.string.yp_session_title),
                getString(R.string.yp_session_desc)
        );

        levelSpinner = new Spinner(this);
        levelSpinner.setAdapter(new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{
                        getString(R.string.yp_level_fast),
                        getString(R.string.yp_level_standard),
                        getString(R.string.yp_level_deep)
                }
        ));
        levelSpinner.setSelection(1);
        control.addView(YViewLayout.detailBlock(
                this,
                getString(R.string.yp_collection_level),
                getString(R.string.yp_collection_level_desc)));
        control.addView(levelSpinner);

        sessionStatus = YViewLayout.statusLine(this, "");
        control.addView(sessionStatus);

        LinearLayout row1 = YViewLayout.actionRow(control);
        Button start = YViewLayout.primaryButton(this, getString(R.string.yp_start_diagnostics));
        start.setOnClickListener(v -> startSession());
        Button launch = YViewLayout.secondaryButton(this, getString(R.string.yp_launch_target));
        launch.setOnClickListener(v -> launchTarget());
        Button finish = YViewLayout.secondaryButton(this, getString(R.string.yp_finish_analyze));
        finish.setOnClickListener(v -> finishAndAnalyze());
        YViewLayout.addAction(row1, start);
        YViewLayout.addAction(row1, launch);
        YViewLayout.addAction(row1, finish);

        LinearLayout result = YViewLayout.card(
                root,
                getString(R.string.yp_results_title),
                getString(R.string.yp_results_desc)
        );
        viewSpinner = new Spinner(this);
        viewSpinner.setAdapter(new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{
                        getString(R.string.yp_view_summary),
                        getString(R.string.yp_view_detailed),
                        getString(R.string.yp_view_attribution),
                        getString(R.string.yp_view_raw)
                }
        ));
        result.addView(viewSpinner);

        LinearLayout row2 = YViewLayout.actionRow(result);
        Button show = YViewLayout.secondaryButton(this, getString(R.string.yp_switch_result));
        show.setOnClickListener(v -> render());
        Button export = YViewLayout.secondaryButton(this, getString(R.string.yp_export_json));
        export.setOnClickListener(v -> exportReport());
        YViewLayout.addAction(row2, show);
        YViewLayout.addAction(row2, export);

        ScrollView scroll = new ScrollView(this);
        output = new TextView(this);
        output.setTextIsSelectable(true);
        output.setText(getString(R.string.yp_usage_steps));
        output.setPadding(0, dp(8), 0, dp(12));
        scroll.addView(output);
        result.addView(scroll, new LinearLayout.LayoutParams(-1, dp(420)));
    }

    private void startSession() {
        DiagnosticLevel level = DiagnosticLevel.values()[levelSpinner.getSelectedItemPosition()];
        RuntimeDiagnosticSession.SessionState state =
                RuntimeDiagnosticSession.start(this, packageName, level);

        lastReport = null;

        String lsposed = XposedBridgeManager.isReady()
                ? getString(R.string.yp_session_lsposed_ready)
                : getString(R.string.yp_session_lsposed_missing);

        String systemTrace = "";
        if (state.level == DiagnosticLevel.DEEP) {
            String perfetto = state.systemTrace.perfettoStarted
                    ? getString(R.string.yp_started)
                    : state.systemTrace.perfettoAvailable
                    ? getString(R.string.yp_start_failed)
                    : getString(R.string.yp_device_unavailable);
            String simpleperf = state.systemTrace.simpleperfStarted
                    ? getString(R.string.yp_started)
                    : state.systemTrace.simpleperfAvailable
                    ? getString(R.string.yp_start_failed)
                    : getString(R.string.yp_device_unavailable);
            systemTrace = "\n\n" + getString(R.string.yp_system_collection)
                    + "\n" + getString(R.string.yp_perfetto_status, perfetto)
                    + "\n" + getString(R.string.yp_simpleperf_status, simpleperf);
            if (state.systemTrace.syscallStarted || state.systemTrace.syscallAvailable) {
                String syscall = state.systemTrace.syscallStarted
                        ? getString(R.string.yp_syscall_started_warning)
                        : getString(R.string.yp_start_failed);
                systemTrace += "\n" + getString(R.string.yp_syscall_status, syscall);
            }
        }

        output.setText(getString(R.string.yp_session_started, lsposed, systemTrace));
        refreshSessionStatus();
    }

    private void launchTarget() {
        RuntimeDiagnosticSession.SessionState state =
                RuntimeDiagnosticSession.state(this, packageName);
        if (!state.active) {
            Toast.makeText(this, R.string.yp_start_first, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!RuntimeDiagnosticSession.launchTarget(this, packageName)) {
            Toast.makeText(this, R.string.yp_no_launcher, Toast.LENGTH_LONG).show();
        }
    }

    private void finishAndAnalyze() {
        RuntimeDiagnosticSession.SessionState before =
                RuntimeDiagnosticSession.state(this, packageName);
        if (!before.active || before.startMs <= 0) {
            Toast.makeText(this, R.string.yp_no_active_session, Toast.LENGTH_SHORT).show();
            return;
        }

        RuntimeDiagnosticSession.SessionState finished =
                RuntimeDiagnosticSession.finishAndRestore(this, packageName);

        output.setText(getString(R.string.yp_analyzing));
        DiagnosticEngine.runAsync(
                this,
                packageName,
                finished.level,
                finished.sessionId,
                finished.startMs,
                finished.endMs,
                report -> runOnUiThread(() -> {
                    lastReport = report;
                    render();
                    refreshSessionStatus();
                })
        );
    }

    private void render() {
        if (lastReport == null) return;
        int mode = viewSpinner.getSelectedItemPosition();
        String text = mode == 0
                ? lastReport.simpleText()
                : mode == 1
                ? lastReport.detailedText()
                : mode == 2
                ? lastReport.recommendationText()
                : lastReport.rawText();

        output.setText(styleDetectionItems(text, mode));
    }

    private CharSequence styleDetectionItems(String text, int mode) {
        SpannableStringBuilder styled = new SpannableStringBuilder(text);

        if (mode == 0) {
            Pattern linePattern = Pattern.compile("(?m)^• .*?检测状态=([^\\s\\n]+).*$");
            Matcher matcher = linePattern.matcher(text);
            while (matcher.find()) {
                if (isFalseState(matcher.group(1))) continue;
                applyRed(styled, matcher.start(), matcher.end());
            }
            return styled;
        }

        if (mode == 1 || mode == 2) {
            Pattern statePattern = Pattern.compile("应用检测状态：([^\\s\\n]+)");
            Matcher matcher = statePattern.matcher(text);
            while (matcher.find()) {
                if (isFalseState(matcher.group(1))) continue;

                int blockStart = mode == 1
                        ? text.lastIndexOf("\n[ ", matcher.start())
                        : text.lastIndexOf("\n\n", matcher.start());
                blockStart = blockStart >= 0 ? blockStart + (mode == 1 ? 1 : 2) : 0;

                int blockEnd = text.indexOf("\n\n", matcher.end());
                if (blockEnd < 0) blockEnd = text.length();

                applyRed(styled, blockStart, blockEnd);
            }
            return styled;
        }

        Pattern rawPattern = Pattern.compile("(?m)^.*?hitState=([^\\s]+).*$");
        Matcher rawMatcher = rawPattern.matcher(text);
        while (rawMatcher.find()) {
            if (isFalseState(rawMatcher.group(1))) continue;
            applyRed(styled, rawMatcher.start(), rawMatcher.end());
        }

        return styled;
    }

    private static boolean isFalseState(String value) {
        return "false".equalsIgnoreCase(value)
                || "NOT_HIT".equalsIgnoreCase(value);
    }

    private static void applyRed(SpannableStringBuilder styled, int start, int end) {
        if (start < 0 || end <= start || end > styled.length()) return;
        styled.setSpan(
                new ForegroundColorSpan(Color.RED),
                start,
                end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        );
    }

    private void exportReport() {
        if (lastReport == null) {
            Toast.makeText(this, R.string.yp_complete_runtime_first, Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.yp_export_json)
                .setItems(
                        new String[]{
                                getString(R.string.yp_save_downloads),
                                getString(R.string.yp_choose_location)
                        },
                        (dialog, which) -> {
                            if (which == 0) {
                                exportToDownloads();
                            } else {
                                createJsonDocument.launch(ReportExporter.suggestedFileName(lastReport));
                            }
                        }
                )
                .setNegativeButton(R.string.yp_cancel, null)
                .show();
    }

    private void exportToDownloads() {
        try {
            Uri uri = ReportExporter.exportToDownloads(this, lastReport);
            Toast.makeText(
                    this,
                    getString(R.string.yp_saved_downloads, ReportExporter.suggestedFileName(lastReport)),
                    Toast.LENGTH_LONG
            ).show();
        } catch (Exception e) {
            Toast.makeText(
                    this,
                    getString(R.string.yp_export_failed, e.getMessage()),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void refreshSessionStatus() {
        RuntimeDiagnosticSession.SessionState state =
                RuntimeDiagnosticSession.state(this, packageName);
        if (state.active && state.startMs > 0) {
            YViewLayout.setStatus(
                    sessionStatus,
                    getString(
                            R.string.yp_session_active,
                            levelLabel(state.level),
                            formatTime(state.startMs)),
                    YViewStatusTone.Good
            );
        } else {
            YViewLayout.setStatus(
                    sessionStatus,
                    getString(R.string.yp_session_not_started),
                    YViewStatusTone.Neutral);
        }
    }

    private String levelLabel(DiagnosticLevel level) {
        if (level == DiagnosticLevel.FAST) return getString(R.string.yp_level_fast);
        if (level == DiagnosticLevel.DEEP) return getString(R.string.yp_level_deep);
        return getString(R.string.yp_level_standard);
    }

    private static String formatTime(long timestamp) {
        return new SimpleDateFormat("HH:mm:ss", Locale.ROOT).format(new Date(timestamp));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
