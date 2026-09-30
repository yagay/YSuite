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
                            Toast.makeText(this, "JSON 已保存到所选位置", Toast.LENGTH_LONG).show();
                        } catch (Exception e) {
                            Toast.makeText(this, "导出失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
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
                "运行时诊断",
                packageName
        );
        LinearLayout root = screen.getContent();

        LinearLayout intro = YViewLayout.card(
                root,
                "本次运行证据",
                "只显示目标 App 在本次运行中实际触发过的检测、退出、崩溃或 ANR；未发生的项目不会伪装成“通过”。"
        );
        intro.addView(YViewLayout.statusLine(
                this,
                XposedBridgeManager.isReady()
                        ? "LSPosed Service 已连接"
                        : "LSPosed Service 未连接，Java 运行时检测可能不完整",
                XposedBridgeManager.isReady() ? YViewStatusTone.Good : YViewStatusTone.Warning
        ));

        LinearLayout control = YViewLayout.card(
                root,
                "诊断会话",
                "选择采集级别后开始会话，启动目标 App 复现问题，再回到这里结束并分析。"
        );

        levelSpinner = new Spinner(this);
        levelSpinner.setAdapter(new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{"快速", "标准", "深度"}
        ));
        levelSpinner.setSelection(1);
        control.addView(YViewLayout.detailBlock(this, "采集级别", "快速 / 标准 / 深度"));
        control.addView(levelSpinner);

        sessionStatus = YViewLayout.statusLine(this, "");
        control.addView(sessionStatus);

        LinearLayout row1 = YViewLayout.actionRow(control);
        Button start = YViewLayout.primaryButton(this, "开始诊断");
        start.setOnClickListener(v -> startSession());
        Button launch = YViewLayout.secondaryButton(this, "启动目标 App");
        launch.setOnClickListener(v -> launchTarget());
        Button finish = YViewLayout.secondaryButton(this, "结束并分析");
        finish.setOnClickListener(v -> finishAndAnalyze());
        YViewLayout.addAction(row1, start);
        YViewLayout.addAction(row1, launch);
        YViewLayout.addAction(row1, finish);

        LinearLayout result = YViewLayout.card(
                root,
                "诊断结果",
                "可在简要、详细、归因说明和原始事件之间切换；只有实际命中的检测会突出显示。"
        );
        viewSpinner = new Spinner(this);
        viewSpinner.setAdapter(new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{"简要", "详细", "归因说明", "原始"}
        ));
        result.addView(viewSpinner);

        LinearLayout row2 = YViewLayout.actionRow(result);
        Button show = YViewLayout.secondaryButton(this, "切换结果");
        show.setOnClickListener(v -> render());
        Button export = YViewLayout.secondaryButton(this, "导出 JSON");
        export.setOnClickListener(v -> exportReport());
        YViewLayout.addAction(row2, show);
        YViewLayout.addAction(row2, export);

        ScrollView scroll = new ScrollView(this);
        output = new TextView(this);
        output.setTextIsSelectable(true);
        output.setText("使用方法：\n1. 开始诊断\n2. 启动并正常使用目标 App\n3. 返回 YPower\n4. 结束并分析");
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
                ? "LSPosed 已连接，已请求目标 App Scope。"
                : "LSPosed Service 未连接；运行时 Java 检测可能无法记录。";

        String systemTrace = "";
        if (state.level == DiagnosticLevel.DEEP) {
            systemTrace = "\n\n系统级采集："
                    + "\nPerfetto："
                    + (state.systemTrace.perfettoStarted ? "已启动" :
                    state.systemTrace.perfettoAvailable ? "启动失败" : "设备不可用")
                    + "\nsimpleperf："
                    + (state.systemTrace.simpleperfStarted ? "已启动" :
                    state.systemTrace.simpleperfAvailable ? "启动失败" : "设备不可用");
            if (state.systemTrace.syscallStarted || state.systemTrace.syscallAvailable) {
                systemTrace += "\nRaw syscall（实验）："
                        + (state.systemTrace.syscallStarted ? "已启动；可能改变 TracerPid/反调试行为"
                        : "启动失败");
            }
        }

        output.setText("诊断会话已开始。\n"
                + "YPower 已临时打开本次诊断需要的追踪 Hook，并已停止目标 App，"
                + "确保下次启动时重新加载 Hook。\n\n"
                + lsposed
                + systemTrace
                + "\n\n现在点击“启动目标 App”，正常使用并复现问题。");
        refreshSessionStatus();
    }

    private void launchTarget() {
        RuntimeDiagnosticSession.SessionState state =
                RuntimeDiagnosticSession.state(this, packageName);
        if (!state.active) {
            Toast.makeText(this, "请先开始诊断", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!RuntimeDiagnosticSession.launchTarget(this, packageName)) {
            Toast.makeText(this, "目标 App 没有可启动的主界面", Toast.LENGTH_LONG).show();
        }
    }

    private void finishAndAnalyze() {
        RuntimeDiagnosticSession.SessionState before =
                RuntimeDiagnosticSession.state(this, packageName);
        if (!before.active || before.startMs <= 0) {
            Toast.makeText(this, "当前没有正在进行的诊断会话", Toast.LENGTH_SHORT).show();
            return;
        }

        RuntimeDiagnosticSession.SessionState finished =
                RuntimeDiagnosticSession.finishAndRestore(this, packageName);

        output.setText("正在分析本次运行实际发生的事件…");
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

        if (mode == 1) {
            Pattern statePattern = Pattern.compile("应用检测状态：([^\\s\\n]+)");
            Matcher matcher = statePattern.matcher(text);
            while (matcher.find()) {
                if (isFalseState(matcher.group(1))) continue;

                int blockStart = text.lastIndexOf("\n[ ", matcher.start());
                blockStart = blockStart >= 0 ? blockStart + 1 : 0;

                int blockEnd = text.indexOf("\n\n", matcher.end());
                if (blockEnd < 0) blockEnd = text.length();

                applyRed(styled, blockStart, blockEnd);
            }
            return styled;
        }

        if (mode == 2) {
            Pattern statePattern = Pattern.compile("应用检测状态：([^\\s\\n]+)");
            Matcher matcher = statePattern.matcher(text);
            while (matcher.find()) {
                if (isFalseState(matcher.group(1))) continue;

                int blockStart = text.lastIndexOf("\n\n", matcher.start());
                blockStart = blockStart >= 0 ? blockStart + 2 : 0;

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
            Toast.makeText(this, "请先完成一次运行时诊断", Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("导出 JSON")
                .setItems(
                        new String[]{"保存到下载文件夹", "选择保存位置"},
                        (dialog, which) -> {
                            if (which == 0) {
                                exportToDownloads();
                            } else {
                                createJsonDocument.launch(
                                        ReportExporter.suggestedFileName(lastReport)
                                );
                            }
                        }
                )
                .setNegativeButton("取消", null)
                .show();
    }

    private void exportToDownloads() {
        try {
            Uri uri = ReportExporter.exportToDownloads(this, lastReport);
            Toast.makeText(
                    this,
                    "已保存到 下载/YPower/\n" + ReportExporter.suggestedFileName(lastReport),
                    Toast.LENGTH_LONG
            ).show();
        } catch (Exception e) {
            Toast.makeText(this, "导出失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void refreshSessionStatus() {
        RuntimeDiagnosticSession.SessionState state =
                RuntimeDiagnosticSession.state(this, packageName);
        if (state.active && state.startMs > 0) {
            YViewLayout.setStatus(
                    sessionStatus,
                    "诊断中 · " + state.level + " · 开始 " + formatTime(state.startMs),
                    YViewStatusTone.Good
            );
        } else {
            YViewLayout.setStatus(sessionStatus, "未开始", YViewStatusTone.Neutral);
        }
    }

    private static String formatTime(long timestamp) {
        return new SimpleDateFormat("HH:mm:ss", Locale.ROOT).format(new Date(timestamp));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
