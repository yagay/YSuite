package com.yagay.YMiniGuard;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.yagay.yui.YViewLayout;
import com.yagay.yui.YViewScreen;
import com.yagay.yui.YViewStatusTone;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * OPlus FlexibleWindow control panel.
 *
 * OxygenOS owns all window rendering and YUI owns this app's visual/layout shell.
 */
public final class MainActivity extends Activity {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private TextView engineStatus;
    private TextView diagnosticsStatus;
    private Button diagnosticsExport;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        YViewScreen screen = YViewLayout.install(
                this,
                "小窗守护",
                "YMiniGuard · OPlus FlexibleWindow · System Scope");
        LinearLayout root = screen.getContent();

        addEngineCard(root);
        addAppListsCard(root);
        addForegroundCard(root);
        addDiagnosticsCard(root);
        refreshStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private void addEngineCard(LinearLayout parent) {
        LinearLayout card = YViewLayout.card(
                parent,
                "运行状态",
                "核心运行在 system_server，目标 App 不需要加入 LSPosed 作用域。");

        engineStatus = YViewLayout.statusLine(this, "System Engine：检测中…");
        card.addView(engineStatus);
        card.addView(YViewLayout.statusLine(this, "LSPosed 固定作用域：system / system_server"));

        addSwitch(
                card,
                "启用小窗守护",
                "总开关。关闭后不修改一加小窗支持判断，也不应用前台/防清理保护。",
                ConfigKeys.MASTER_ENABLED);
        addSwitch(
                card,
                "自动热重载",
                "更新 APK 后自动加载新的 Engine。Bootstrap Hook 结构变化仍需要重启一次。",
                ConfigKeys.ENGINE_AUTO_RELOAD);

        Button reload = button("立即重新加载 System Engine");
        reload.setOnClickListener(v -> {
            if (!GuardApp.isHotReloadAvailable()) {
                Toast.makeText(this, "当前 Bootstrap 不支持热重载，请重启一次手机。", Toast.LENGTH_LONG).show();
                return;
            }

            boolean sent = GuardApp.requestEngineReload();
            Toast.makeText(
                    this,
                    sent ? "已请求重新加载 OPlus Engine。" : "热重载请求同步失败。",
                    Toast.LENGTH_LONG).show();
            if (engineStatus != null) {
                engineStatus.postDelayed(this::refreshStatus, 2500L);
            }
        });
        card.addView(reload);

        Button refresh = button("重新检测");
        refresh.setOnClickListener(v -> refreshStatus());
        card.addView(refresh);
    }

    private void addAppListsCard(LinearLayout parent) {
        LinearLayout card = YViewLayout.card(
                parent,
                "应用名单",
                "App 的启动、进入小窗、恢复和关闭完全使用 OxygenOS 自己的方式。YMiniGuard 只监听一加小窗状态。");

        Button foreground = button("始终前台应用");
        foreground.setOnClickListener(v -> {
            Intent intent = new Intent(this, TargetAppsActivity.class);
            intent.putExtra(TargetAppsActivity.EXTRA_MODE, TargetAppsActivity.MODE_FOREGROUND);
            startActivity(intent);
        });
        card.addView(foreground);
        card.addView(YViewLayout.detailBlock(
                this,
                "始终前台",
                "勾选的 App 只有在真实 OPlus FlexibleWindow、贴边/最小化小窗，或该小窗进入锁屏状态时才保持运行。普通全屏状态不干预。"));

        Button background = button("后台播放应用");
        background.setOnClickListener(v -> {
            Intent intent = new Intent(this, TargetAppsActivity.class);
            intent.putExtra(TargetAppsActivity.EXTRA_MODE, TargetAppsActivity.MODE_BACKGROUND_PLAYBACK);
            startActivity(intent);
        });
        card.addView(background);
        card.addView(YViewLayout.detailBlock(
                this,
                "普通后台播放",
                "从普通全屏切到桌面/其他 App 或锁屏时进入 BACKGROUND_PROTECTED；返回原 App 自动解除。"));

        Button support = button("强制允许一加小窗应用");
        support.setOnClickListener(v -> {
            Intent intent = new Intent(this, TargetAppsActivity.class);
            intent.putExtra(TargetAppsActivity.EXTRA_MODE, TargetAppsActivity.MODE_FORCE_SUPPORT);
            startActivity(intent);
        });
        card.addView(support);
        card.addView(YViewLayout.detailBlock(
                this,
                "小窗支持",
                "只对勾选的 App 放行一加 FlexibleWindow 支持/黑名单检查；不会主动启动或主动切换小窗。"));
    }

    private void addForegroundCard(LinearLayout parent) {
        LinearLayout card = YViewLayout.card(
                parent,
                "运行保护",
                "小窗继续使用 OPlus 状态驱动；后台播放名单只在离开普通全屏后进入 BACKGROUND_PROTECTED。");

        addSwitch(
                card,
                "受保护进程状态保持 TOP",
                "对真实一加小窗/贴边/锁屏，以及 BACKGROUND_PROTECTED 普通后台进程返回 TOP。",
                ConfigKeys.SYSTEM_IMPORTANCE_TOP);
        addSwitch(
                card,
                "受保护任务视为存在 Resumed Activity",
                "对真实一加小窗和 BACKGROUND_PROTECTED 任务返回 true；普通前台不修改。",
                ConfigKeys.SYSTEM_HAS_RESUMED);
        addSwitch(
                card,
                "阻止系统清理受保护进程",
                "保护一加小窗和 BACKGROUND_PROTECTED 进程；强制停止/更新放行，普通后台任务被划掉时仍允许正常关闭。",
                ConfigKeys.SYSTEM_BLOCK_REMOVE_KILL);
    }

    private void addDiagnosticsCard(LinearLayout parent) {
        LinearLayout card = YViewLayout.card(
                parent,
                "详细诊断",
                "记录 OPlus FlexibleWindow 回调、支持判断、Task 状态、前台查询和 OEM 清理链路。");

        diagnosticsStatus = YViewLayout.statusLine(this, "详细日志：检测中…");
        card.addView(diagnosticsStatus);

        Button start = button("开始详细日志");
        start.setOnClickListener(v -> {
            DiagnosticsManager.startSession();
            refreshDiagnosticsStatus();
            Toast.makeText(this, "已开始记录 OPlus 小窗详细日志。", Toast.LENGTH_SHORT).show();
        });
        card.addView(start);

        Button stop = button("停止详细日志");
        stop.setOnClickListener(v -> {
            DiagnosticsManager.stopSession();
            refreshDiagnosticsStatus();
            Toast.makeText(this, "详细日志已停止。", Toast.LENGTH_SHORT).show();
        });
        card.addView(stop);

        diagnosticsExport = button("导出诊断 ZIP");
        diagnosticsExport.setOnClickListener(v -> exportDiagnostics());
        card.addView(diagnosticsExport);
    }

    private void exportDiagnostics() {
        if (diagnosticsExport != null) diagnosticsExport.setEnabled(false);
        Toast.makeText(
                this,
                "正在收集 OPlus FlexibleWindow、system_server 和系统状态…",
                Toast.LENGTH_SHORT).show();

        executor.execute(() -> {
            DiagnosticsManager.ExportResult result = DiagnosticsManager.export(this);
            runOnUiThread(() -> {
                if (diagnosticsExport != null) diagnosticsExport.setEnabled(true);
                refreshDiagnosticsStatus();

                if (!result.ok()) {
                    Toast.makeText(this, "导出失败：" + result.error, Toast.LENGTH_LONG).show();
                    return;
                }

                Toast.makeText(
                        this,
                        "已保存到 Download/YMiniGuard/" + result.fileName,
                        Toast.LENGTH_LONG).show();
                try {
                    Intent share = new Intent(Intent.ACTION_SEND);
                    share.setType("application/zip");
                    share.putExtra(Intent.EXTRA_STREAM, result.uri);
                    share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(Intent.createChooser(share, "分享诊断 ZIP"));
                } catch (Throwable ignored) {
                }
            });
        });
    }

    private void refreshStatus() {
        if (engineStatus != null) {
            long expected = GuardApp.getExpectedVersionCode();
            long loaded = GuardApp.getLoadedEngineVersionCode();
            boolean connected = GuardApp.isXposedServiceConnected();
            boolean hasSystem = GuardApp.hasSystemScope();
            boolean active = GuardApp.isSystemEngineActive();
            boolean current = GuardApp.isSystemEngineCurrent();

            if (!connected) {
                YViewLayout.setStatus(
                        engineStatus,
                        "System Engine：LSPosed 服务未连接",
                        YViewStatusTone.Error);
            } else if (!hasSystem) {
                YViewLayout.setStatus(
                        engineStatus,
                        "System Engine：作用域缺少 system · 实际=" + GuardApp.getFrameworkScope(),
                        YViewStatusTone.Error);
            } else if (current) {
                YViewLayout.setStatus(
                        engineStatus,
                        "System Engine：code " + loaded
                                + " · Bootstrap " + GuardApp.getBootstrapVersionCode()
                                + " · gen " + GuardApp.getEngineGeneration()
                                + " · 跟踪 Task " + GuardApp.getEngineActiveSessions()
                                + "\n" + GuardApp.getEngineReloadMessage(),
                        YViewStatusTone.Good);
            } else if (active) {
                YViewLayout.setStatus(
                        engineStatus,
                        "System Engine：旧 Engine " + loaded
                                + " / 已安装 APK " + expected
                                + "\n请重新加载 Engine；若 Bootstrap Hook 已改变，需要重启一次。",
                        YViewStatusTone.Warning);
            } else {
                YViewLayout.setStatus(
                        engineStatus,
                        "System Engine：未检测到有效心跳",
                        YViewStatusTone.Error);
            }
        }
        refreshDiagnosticsStatus();
    }

    private void refreshDiagnosticsStatus() {
        if (diagnosticsStatus == null) return;

        boolean active = GuardApp.getBoolean(ConfigKeys.DIAGNOSTICS_ACTIVE);
        String started = GuardApp.getString(ConfigKeys.DIAGNOSTICS_STARTED_AT);
        if (active) {
            YViewLayout.setStatus(
                    diagnosticsStatus,
                    "详细日志：持续开启" + (started.isEmpty() ? "" : " · start=" + started),
                    YViewStatusTone.Warning);
        } else {
            YViewLayout.setStatus(
                    diagnosticsStatus,
                    "详细日志：关闭",
                    YViewStatusTone.Good);
        }
    }

    private void addSwitch(
            LinearLayout parent,
            String title,
            String description,
            String key
    ) {
        YViewLayout.switchRow(
                parent,
                title,
                description,
                GuardApp.getBoolean(key),
                (button, checked) -> GuardApp.putBoolean(key, checked));
    }

    private Button button(String text) {
        return YViewLayout.primaryButton(this, text);
    }
}
