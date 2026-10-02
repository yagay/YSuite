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

/** OPlus FlexibleWindow control panel. */
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
                getString(R.string.ymg_title),
                getString(R.string.ymg_subtitle));
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
                getString(R.string.ymg_runtime_title),
                getString(R.string.ymg_runtime_desc));

        engineStatus = YViewLayout.statusLine(this, getString(R.string.ymg_engine_checking));
        card.addView(engineStatus);
        card.addView(YViewLayout.statusLine(this, getString(R.string.ymg_lsposed_scope)));

        addSwitch(card, getString(R.string.ymg_enable), getString(R.string.ymg_enable_desc),
                ConfigKeys.MASTER_ENABLED);
        addSwitch(card, getString(R.string.ymg_hot_reload), getString(R.string.ymg_hot_reload_desc),
                ConfigKeys.ENGINE_AUTO_RELOAD);

        Button reload = button(R.string.ymg_reload_now);
        reload.setOnClickListener(v -> {
            if (!GuardApp.isHotReloadAvailable()) {
                Toast.makeText(this, R.string.ymg_hot_reload_unavailable, Toast.LENGTH_LONG).show();
                return;
            }

            boolean sent = GuardApp.requestEngineReload();
            Toast.makeText(
                    this,
                    sent ? R.string.ymg_reload_requested : R.string.ymg_reload_failed,
                    Toast.LENGTH_LONG).show();
            if (engineStatus != null) {
                engineStatus.postDelayed(this::refreshStatus, 2500L);
            }
        });
        card.addView(reload);

        Button refresh = button(R.string.ymg_recheck);
        refresh.setOnClickListener(v -> refreshStatus());
        card.addView(refresh);
    }

    private void addAppListsCard(LinearLayout parent) {
        LinearLayout card = YViewLayout.card(
                parent,
                getString(R.string.ymg_app_lists_title),
                getString(R.string.ymg_app_lists_desc));

        Button foreground = button(R.string.ymg_foreground_apps);
        foreground.setOnClickListener(v -> {
            Intent intent = new Intent(this, TargetAppsActivity.class);
            intent.putExtra(TargetAppsActivity.EXTRA_MODE, TargetAppsActivity.MODE_FOREGROUND);
            startActivity(intent);
        });
        card.addView(foreground);
        card.addView(YViewLayout.detailBlock(
                this,
                getString(R.string.ymg_foreground_title),
                getString(R.string.ymg_foreground_desc)));

        Button background = button(R.string.ymg_background_apps);
        background.setOnClickListener(v -> {
            Intent intent = new Intent(this, TargetAppsActivity.class);
            intent.putExtra(TargetAppsActivity.EXTRA_MODE, TargetAppsActivity.MODE_BACKGROUND_PLAYBACK);
            startActivity(intent);
        });
        card.addView(background);
        card.addView(YViewLayout.detailBlock(
                this,
                getString(R.string.ymg_background_title),
                getString(R.string.ymg_background_desc)));

        Button support = button(R.string.ymg_force_support_apps);
        support.setOnClickListener(v -> {
            Intent intent = new Intent(this, TargetAppsActivity.class);
            intent.putExtra(TargetAppsActivity.EXTRA_MODE, TargetAppsActivity.MODE_FORCE_SUPPORT);
            startActivity(intent);
        });
        card.addView(support);
        card.addView(YViewLayout.detailBlock(
                this,
                getString(R.string.ymg_support_title),
                getString(R.string.ymg_support_desc)));
    }

    private void addForegroundCard(LinearLayout parent) {
        LinearLayout card = YViewLayout.card(
                parent,
                getString(R.string.ymg_protection_title),
                getString(R.string.ymg_protection_desc));

        addSwitch(card, getString(R.string.ymg_keep_top), getString(R.string.ymg_keep_top_desc),
                ConfigKeys.SYSTEM_IMPORTANCE_TOP);
        addSwitch(card, getString(R.string.ymg_has_resumed), getString(R.string.ymg_has_resumed_desc),
                ConfigKeys.SYSTEM_HAS_RESUMED);
        addSwitch(card, getString(R.string.ymg_block_cleanup), getString(R.string.ymg_block_cleanup_desc),
                ConfigKeys.SYSTEM_BLOCK_REMOVE_KILL);
    }

    private void addDiagnosticsCard(LinearLayout parent) {
        LinearLayout card = YViewLayout.card(
                parent,
                getString(R.string.ymg_diag_title),
                getString(R.string.ymg_diag_desc));

        diagnosticsStatus = YViewLayout.statusLine(this, getString(R.string.ymg_diag_checking));
        card.addView(diagnosticsStatus);

        Button start = button(R.string.ymg_diag_start);
        start.setOnClickListener(v -> {
            DiagnosticsManager.startSession();
            refreshDiagnosticsStatus();
            Toast.makeText(this, R.string.ymg_diag_started, Toast.LENGTH_SHORT).show();
        });
        card.addView(start);

        Button stop = button(R.string.ymg_diag_stop);
        stop.setOnClickListener(v -> {
            DiagnosticsManager.stopSession();
            refreshDiagnosticsStatus();
            Toast.makeText(this, R.string.ymg_diag_stopped, Toast.LENGTH_SHORT).show();
        });
        card.addView(stop);

        diagnosticsExport = button(R.string.ymg_diag_export);
        diagnosticsExport.setOnClickListener(v -> exportDiagnostics());
        card.addView(diagnosticsExport);
    }

    private void exportDiagnostics() {
        if (diagnosticsExport != null) diagnosticsExport.setEnabled(false);
        Toast.makeText(this, R.string.ymg_diag_collecting, Toast.LENGTH_SHORT).show();

        executor.execute(() -> {
            DiagnosticsManager.ExportResult result = DiagnosticsManager.export(this);
            runOnUiThread(() -> {
                if (diagnosticsExport != null) diagnosticsExport.setEnabled(true);
                refreshDiagnosticsStatus();

                if (!result.ok()) {
                    Toast.makeText(
                            this,
                            getString(R.string.ymg_diag_export_failed, result.error),
                            Toast.LENGTH_LONG).show();
                    return;
                }

                Toast.makeText(
                        this,
                        getString(R.string.ymg_diag_saved, result.fileName),
                        Toast.LENGTH_LONG).show();
                try {
                    Intent share = new Intent(Intent.ACTION_SEND);
                    share.setType("application/zip");
                    share.putExtra(Intent.EXTRA_STREAM, result.uri);
                    share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(Intent.createChooser(share, getString(R.string.ymg_diag_share)));
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
                YViewLayout.setStatus(engineStatus,
                        getString(R.string.ymg_engine_lsposed_disconnected), YViewStatusTone.Error);
            } else if (!hasSystem) {
                YViewLayout.setStatus(
                        engineStatus,
                        getString(R.string.ymg_engine_scope_missing, GuardApp.getFrameworkScope()),
                        YViewStatusTone.Error);
            } else if (current) {
                YViewLayout.setStatus(
                        engineStatus,
                        getString(
                                R.string.ymg_engine_current,
                                loaded,
                                GuardApp.getBootstrapVersionCode(),
                                GuardApp.getEngineGeneration(),
                                GuardApp.getEngineActiveSessions(),
                                GuardApp.getEngineReloadMessage()),
                        YViewStatusTone.Good);
            } else if (active) {
                YViewLayout.setStatus(
                        engineStatus,
                        getString(R.string.ymg_engine_old, loaded, expected),
                        YViewStatusTone.Warning);
            } else {
                YViewLayout.setStatus(engineStatus,
                        getString(R.string.ymg_engine_no_heartbeat), YViewStatusTone.Error);
            }
        }
        refreshDiagnosticsStatus();
    }

    private void refreshDiagnosticsStatus() {
        if (diagnosticsStatus == null) return;

        boolean active = GuardApp.getBoolean(ConfigKeys.DIAGNOSTICS_ACTIVE);
        String started = GuardApp.getString(ConfigKeys.DIAGNOSTICS_STARTED_AT);
        if (active) {
            String startedSuffix = started.isEmpty()
                    ? ""
                    : getString(R.string.ymg_diag_started_at, started);
            YViewLayout.setStatus(
                    diagnosticsStatus,
                    getString(R.string.ymg_diag_running, startedSuffix),
                    YViewStatusTone.Warning);
        } else {
            YViewLayout.setStatus(
                    diagnosticsStatus,
                    getString(R.string.ymg_diag_disabled),
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

    private Button button(int textRes) {
        return YViewLayout.primaryButton(this, getString(textRes));
    }
}
