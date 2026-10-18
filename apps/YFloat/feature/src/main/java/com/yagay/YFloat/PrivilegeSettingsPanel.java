package com.yagay.YFloat;

import com.yagay.yui.YViewLayout;
import com.yagay.yui.YViewSection;
import android.content.Intent;
import android.text.format.DateFormat;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.os.Handler;
import android.os.Looper;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;

/** Builds the optional Root and LSPosed settings page. */
public final class PrivilegeSettingsPanel {
    private PrivilegeSettingsPanel() {}

    public static LinearLayout build(AppCompatActivity activity, FloatSettings fs) {
        LinearLayout root = YViewLayout.pageRoot(activity,
                activity.getString(R.string.yfloat_priv_title),
                activity.getString(R.string.yfloat_priv_desc));

        TextView modeStatus = YViewLayout.rowSubtitle(activity, "");
        TextView rootStatus = YViewLayout.rowSubtitle(activity, "");

        YViewSection master = YViewLayout.section(activity,
                activity.getString(R.string.yfloat_priv_master_title),
                activity.getString(R.string.yfloat_priv_master_desc));
        MaterialSwitch enhanced = preferenceSwitch(activity, fs,
                activity.getString(R.string.yfloat_priv_enable_enhanced),
                activity.getString(R.string.yfloat_priv_enable_enhanced_desc),
                FloatSettings.K_ENHANCED_MODE, fs.enhancedMode(),
                () -> {
                    refresh(activity, fs, modeStatus, rootStatus);
                });
        YViewLayout.addRow(master.body, YViewLayout.switchContainer(enhanced));
        YViewLayout.addRow(master.body, statusBlock(activity,
                activity.getString(R.string.yfloat_priv_active_mode), modeStatus));
        YViewLayout.addSection(root, master);

        YViewSection rootSection = YViewLayout.section(activity, "Root",
                activity.getString(R.string.yfloat_priv_root_desc));
        MaterialSwitch rootSwitch = preferenceSwitch(activity, fs,
                activity.getString(R.string.yfloat_priv_use_root),
                activity.getString(R.string.yfloat_priv_use_root_desc),
                FloatSettings.K_ROOT_ENABLED, fs.rootEnabled(),
                () -> {
                    refresh(activity, fs, modeStatus, rootStatus);
                });
        YViewLayout.addRow(rootSection.body, YViewLayout.switchContainer(rootSwitch));

        MaterialSwitch rootScreenshotSwitch = preferenceSwitch(activity, fs,
                activity.getString(R.string.yfloat_priv_root_screenshot),
                activity.getString(R.string.yfloat_priv_root_screenshot_desc),
                FloatSettings.K_ROOT_SCREENSHOT, fs.rootScreenshot(),
                () -> refresh(activity, fs, modeStatus, rootStatus));
        YViewLayout.addRow(rootSection.body, YViewLayout.switchContainer(rootScreenshotSwitch));
        YViewLayout.addRow(rootSection.body, statusBlock(activity,
                activity.getString(R.string.yfloat_priv_root_status), rootStatus));

        LinearLayout rootButtons = YViewLayout.buttonRow(activity);
        MaterialButton checkRoot = YViewLayout.secondaryButton(activity,
                activity.getString(R.string.yfloat_priv_check_root));
        checkRoot.setOnClickListener(v -> {
            checkRoot.setEnabled(false);
            rootStatus.setText(R.string.yfloat_priv_checking_root);
            PrivilegeManager.checkRootAsync(activity, result -> {
                checkRoot.setEnabled(true);
                refresh(activity, fs, modeStatus, rootStatus);
                Toast.makeText(activity,
                        result.granted
                                ? R.string.yfloat_priv_root_granted
                                : R.string.yfloat_priv_root_unavailable,
                        Toast.LENGTH_SHORT).show();
            });
        });
        YViewLayout.addAction(rootButtons, checkRoot);
        YViewLayout.addRow(rootSection.body, rootButtons);

        YViewLayout.addRow(rootSection.body, YViewLayout.sectionNote(activity,
                activity.getString(R.string.yfloat_priv_root_note)));
        YViewLayout.addSection(root, rootSection);

        TextView lsposedStatus = YViewLayout.rowSubtitle(activity, "");
        YViewSection lsposedSection = YViewLayout.section(activity, "LSPosed",
                activity.getString(R.string.yfloat_priv_lsposed_desc));
        MaterialSwitch lsposedSwitch = preferenceSwitch(activity, fs,
                activity.getString(R.string.yfloat_priv_enable_lsposed),
                activity.getString(R.string.yfloat_priv_enable_lsposed_desc),
                FloatSettings.K_LSPOSED_ENABLED, fs.lsposedEnabled(),
                () -> {
                    LsposedStatusManager.syncRuntimeConfigAsync();
                    refresh(activity, fs, modeStatus, rootStatus);
                });
        lsposedSwitch.setEnabled(PrivilegeManager.lsposedProviderAvailable());
        YViewLayout.addRow(lsposedSection.body, YViewLayout.switchContainer(lsposedSwitch));

        MaterialSwitch secureScreenshotSwitch = preferenceSwitch(activity, fs,
                activity.getString(R.string.yfloat_priv_secure_screenshot),
                activity.getString(R.string.yfloat_priv_secure_screenshot_desc),
                FloatSettings.K_LSPOSED_SECURE_SCREENSHOT, fs.lsposedSecureScreenshot(),
                () -> {
                    LsposedStatusManager.syncRuntimeConfigAsync();
                    refresh(activity, fs, modeStatus, rootStatus);
                });
        YViewLayout.addRow(lsposedSection.body, YViewLayout.switchContainer(secureScreenshotSwitch));
        YViewLayout.addRow(lsposedSection.body, statusBlock(activity,
                activity.getString(R.string.yfloat_priv_lsposed_status), lsposedStatus));

        LinearLayout lsposedButtons = YViewLayout.buttonRow(activity);
        MaterialButton refreshLsposed = YViewLayout.secondaryButton(activity,
                activity.getString(R.string.yfloat_priv_refresh_status));
        refreshLsposed.setOnClickListener(v -> {
            lsposedStatus.setText(R.string.yfloat_priv_reading_lsposed);
            LsposedStatusManager.syncRuntimeConfigAsync();
        });
        YViewLayout.addAction(lsposedButtons, refreshLsposed);

        MaterialButton reloadHooks = YViewLayout.primaryButton(activity,
                activity.getString(R.string.yfloat_priv_reload_hooks));
        reloadHooks.setOnClickListener(v -> {
            if (!fs.canUseRoot()) {
                Toast.makeText(activity, R.string.yfloat_priv_reload_requires_root,
                        Toast.LENGTH_LONG).show();
                return;
            }
            reloadHooks.setEnabled(false);
            lsposedStatus.setText(R.string.yfloat_priv_reloading_hooks);
            boolean started = HookReloadManager.reloadChangedTargetsAsync(activity, result -> {
                reloadHooks.setEnabled(true);
                Toast.makeText(activity, result.userMessage(activity), Toast.LENGTH_LONG).show();
                LsposedStatusManager.refreshAsync();
                refreshLsposed(activity, lsposedStatus);
            });
            if (!started) {
                reloadHooks.setEnabled(true);
                Toast.makeText(activity, R.string.yfloat_priv_reload_running,
                        Toast.LENGTH_SHORT).show();
            }
        });
        YViewLayout.addAction(lsposedButtons, reloadHooks);
        YViewLayout.addRow(lsposedSection.body, lsposedButtons);

        LinearLayout lsposedTools = YViewLayout.buttonRow(activity);
        MaterialButton probeSecure = YViewLayout.secondaryButton(activity,
                activity.getString(R.string.yfloat_priv_test_secure_capture));
        probeSecure.setOnClickListener(v -> activity.startActivity(
                new Intent(activity, SecureCaptureProbeActivity.class)));
        YViewLayout.addAction(lsposedTools, probeSecure);
        YViewLayout.addRow(lsposedSection.body, lsposedTools);

        YViewLayout.addRow(lsposedSection.body, YViewLayout.sectionNote(activity,
                activity.getString(R.string.yfloat_priv_lsposed_note)));

        LsposedStatusManager.Listener lsposedListener = snapshot -> {
            lsposedSwitch.setEnabled(PrivilegeManager.lsposedProviderAvailable());
            refresh(activity, fs, modeStatus, rootStatus);
            refreshLsposed(activity, lsposedStatus);
        };
        lsposedSection.body.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) {
                LsposedStatusManager.addListener(lsposedListener, true);
                LsposedStatusManager.syncRuntimeConfigAsync();
            }

            @Override public void onViewDetachedFromWindow(View v) {
                LsposedStatusManager.removeListener(lsposedListener);
            }
        });
        YViewLayout.addSection(root, lsposedSection);

        YViewSection fallback = YViewLayout.section(activity,
                activity.getString(R.string.yfloat_priv_fallback_title),
                activity.getString(R.string.yfloat_priv_fallback_desc));
        MaterialSwitch fallbackSwitch = preferenceSwitch(activity, fs,
                activity.getString(R.string.yfloat_priv_fallback_switch),
                activity.getString(R.string.yfloat_priv_fallback_switch_desc),
                FloatSettings.K_PRIVILEGE_FALLBACK, fs.privilegeFallback(),
                () -> refresh(activity, fs, modeStatus, rootStatus));
        YViewLayout.addRow(fallback.body, YViewLayout.switchContainer(fallbackSwitch));
        YViewLayout.addSection(root, fallback);

        refresh(activity, fs, modeStatus, rootStatus);
        refreshLsposed(activity, lsposedStatus);
        return root;
    }

    private static MaterialSwitch preferenceSwitch(AppCompatActivity activity, FloatSettings fs,
                                                    String title, String subtitle,
                                                    String key, boolean current, Runnable changed) {
        return YViewLayout.switchRow(activity, title, subtitle, current, (button, checked) -> {
            fs.setBoolean(key, checked);
            DiagnosticLog.i(activity, "PRIVILEGE", "setting " + key + "=" + checked);
            if (changed != null) changed.run();
        });
    }

    private static LinearLayout statusBlock(AppCompatActivity activity, String title, TextView status) {
        LinearLayout block = YViewLayout.settingBlock(activity);
        block.addView(YViewLayout.rowTitle(activity, title));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = com.yagay.yui.YView.controlGap(activity) / 3;
        block.addView(status, lp);
        return block;
    }

    private static void refresh(AppCompatActivity activity, FloatSettings fs,
                                TextView modeStatus, TextView rootStatus) {
        StringBuilder mode = new StringBuilder(PrivilegeManager.modeLabel(activity, fs));
        mode.append("\n").append(activity.getString(fs.enhancedMode()
                ? R.string.yfloat_priv_enhanced_enabled
                : R.string.yfloat_priv_enhanced_disabled));
        if (!PrivilegeManager.lsposedProviderAvailable() && fs.lsposedEnabled()) {
            mode.append("\n").append(activity.getString(
                    R.string.yfloat_priv_lsposed_selected_unavailable));
        }
        modeStatus.setText(mode.toString());
        modeStatus.setTextColor(fs.enhancedMode()
                ? YViewLayout.success(activity) : YViewLayout.textPrimary(activity));

        String providerLine = activity.getString(fs.canUseRoot()
                ? R.string.yfloat_priv_provider_root_allowed
                : R.string.yfloat_priv_provider_root_disallowed);
        long rootAt = fs.rootLastCheckMs();
        if (rootAt <= 0L) {
            rootStatus.setText(activity.getString(R.string.yfloat_priv_root_not_checked)
                    + "\n" + providerLine);
        } else {
            String when = DateFormat.format("yyyy-MM-dd HH:mm", rootAt).toString();
            rootStatus.setText(activity.getString(fs.rootLastGranted()
                    ? R.string.yfloat_priv_root_last_granted
                    : R.string.yfloat_priv_root_last_denied, when)
                    + "\n" + providerLine);
        }
        rootStatus.setTextColor(fs.rootLastGranted()
                ? YViewLayout.success(activity) : YViewLayout.textPrimary(activity));
    }

    private static void refreshLsposed(AppCompatActivity activity, TextView status) {
        LsposedStatusManager.Snapshot s = LsposedStatusManager.snapshot();
        if (!s.serviceConnected) {
            String detail = s.detail.isBlank()
                    ? activity.getString(R.string.yfloat_priv_restart_hint)
                    : s.detail;
            status.setText(activity.getString(R.string.yfloat_priv_framework_disconnected)
                    + "\n" + activity.getString(R.string.yfloat_priv_config_unavailable)
                    + "\n" + activity.getString(R.string.yfloat_priv_system_unknown)
                    + "\n" + detail);
            status.setTextColor(YViewLayout.textPrimary(activity));
            return;
        }

        String framework = s.frameworkName.isBlank() ? "Xposed" : s.frameworkName;
        String version = s.frameworkVersion.isBlank() ? "" : " " + s.frameworkVersion;
        String scopeLine = "system_server: "+enabled(activity,s.systemScopeEnabled)
                +" | SystemUI: "+enabled(activity,s.systemUiScopeEnabled);
        String loadedLine = activity.getString(R.string.yfloat_priv_loaded_line,
                loaded(activity, s.systemLoaded), loaded(activity, s.systemUiLoaded));
        String remoteLine = activity.getString(R.string.yfloat_priv_remote_line,
                s.remoteConfigReady
                        ? activity.getString(R.string.yfloat_priv_synced)
                        : activity.getString(R.string.yfloat_priv_unavailable),
                enabled(activity, s.remoteProviderEnabled()));
        String secureLine = activity.getString(R.string.yfloat_priv_secure_line,
                enabled(activity, s.remoteSecureScreenshotEnabled),
                s.remoteSecureCaptureArmed()
                        ? activity.getString(R.string.yfloat_priv_active)
                        : activity.getString(R.string.yfloat_priv_idle));
        String updatedLine = s.remoteUpdatedAt <= 0L ? ""
                : " " + DateFormat.format("HH:mm:ss", s.remoteUpdatedAt);
        String hookGenerationLine = HookReloadManager.statusSummary(activity, s);
        String processLine = s.runningProcesses.isEmpty()
                ? activity.getString(R.string.yfloat_priv_process_none)
                : activity.getString(R.string.yfloat_priv_process_line,
                        String.join(", ", s.runningProcesses));
        String detailLine = s.detail.isBlank() ? "" : "\n" + s.detail;
        status.setText(activity.getString(R.string.yfloat_priv_framework_connected,
                        framework, version, s.apiVersion)
                + "\n" + remoteLine + updatedLine
                + "\n" + secureLine
                + "\n" + hookGenerationLine
                + "\n" + scopeLine
                + "\n" + loadedLine
                + "\n" + processLine
                + detailLine);
        status.setTextColor(PrivilegeManager.lsposedProviderAvailable()
                ? YViewLayout.success(activity) : YViewLayout.textPrimary(activity));
    }

    private static String enabled(AppCompatActivity activity, boolean value) {
        return activity.getString(value
                ? R.string.yfloat_priv_enabled
                : R.string.yfloat_priv_disabled);
    }

    private static String loaded(AppCompatActivity activity, boolean value) {
        return activity.getString(value
                ? R.string.yfloat_priv_loaded
                : R.string.yfloat_priv_not_loaded);
    }
}
