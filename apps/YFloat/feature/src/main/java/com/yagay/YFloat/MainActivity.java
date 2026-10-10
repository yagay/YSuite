package com.yagay.YFloat;

import com.yagay.yui.YViewLayout;
import com.yagay.yui.YViewSection;
import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;

/** Compact YFloat control center. Detailed configuration lives in short category pages. */
public class MainActivity extends AppCompatActivity {
    private MaterialSwitch overlaySwitch;
    private TextView serviceStatus;
    private boolean syncingOverlaySwitch;
    private boolean awaitingAccessibilityGrant;

    private PermissionRow overlayPermission;
    private PermissionRow accessibilityPermission;
    private PermissionRow notificationPermission;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable delayedAccessibilityRefresh = () -> {
        if (isFinishing() || isDestroyed()) return;
        maybeStartEnabledFloatService();
        refreshStatus();
    };
    private final Runnable accessibilityStateListener = () -> {
        if (isFinishing() || isDestroyed()) return;
        maybeStartEnabledFloatService();
        refreshStatus();
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);

        LinearLayout root = YViewLayout.pageRoot(this,
                getString(R.string.yfloat_title),
                getString(R.string.yfloat_subtitle));

        YViewSection service = YViewLayout.section(this,
                getString(R.string.yfloat_service_title),
                getString(R.string.yfloat_service_desc));
        overlaySwitch = YViewLayout.switchRow(this,
                getString(R.string.yfloat_enable_float_icon),
                getString(R.string.yfloat_enable_float_icon_desc),
                FloatServiceState.isEnabled(this),
                (button, checked) -> onOverlayToggle(checked));
        YViewLayout.addRow(service.body, YViewLayout.switchContainer(overlaySwitch));

        LinearLayout statusRow = YViewLayout.baseRow(this);
        statusRow.addView(YViewLayout.text(this, getString(R.string.yfloat_current_status), 15, false),
                new LinearLayout.LayoutParams(0, -2, 1f));
        serviceStatus = YViewLayout.statusPill(this, getString(R.string.yfloat_status_loading), false);
        statusRow.addView(serviceStatus, new LinearLayout.LayoutParams(-2, -2));
        YViewLayout.addRow(service.body, statusRow);
        YViewLayout.addSection(root, service);

        YViewSection permissions = YViewLayout.section(this,
                getString(R.string.yfloat_permissions_title),
                getString(R.string.yfloat_permissions_desc));
        overlayPermission = permissionRow(
                getString(R.string.yfloat_overlay_permission),
                getString(R.string.yfloat_overlay_permission_desc),
                () -> startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()))));
        YViewLayout.addRow(permissions.body, overlayPermission.view);

        boolean suiteHost = "com.yagay.YSuite".equals(getPackageName());
        accessibilityPermission = permissionRow(
                getString(suiteHost
                        ? R.string.yfloat_suite_accessibility
                        : R.string.yfloat_accessibility_service),
                getString(suiteHost
                        ? R.string.yfloat_suite_accessibility_desc
                        : R.string.yfloat_accessibility_service_desc),
                this::openAccessibilitySettings);
        YViewLayout.addRow(permissions.body, accessibilityPermission.view);

        if (Build.VERSION.SDK_INT >= 33) {
            notificationPermission = permissionRow(
                    getString(R.string.yfloat_notification_permission),
                    getString(R.string.yfloat_notification_permission_desc),
                    this::handleNotificationPermission);
            YViewLayout.addRow(permissions.body, notificationPermission.view);
        }
        YViewLayout.addSection(root, permissions);

        YViewSection settings = YViewLayout.section(this,
                getString(R.string.yfloat_feature_settings),
                getString(R.string.yfloat_feature_settings_desc));
        YViewLayout.addRow(settings.body, YViewLayout.navRow(this,
                getString(R.string.yfloat_float_icon_settings),
                getString(R.string.yfloat_float_icon_settings_desc),
                () -> startActivity(SettingsActivity.intent(this, SettingsActivity.SECTION_ICON))));
        YViewLayout.addRow(settings.body, YViewLayout.navRow(this,
                getString(R.string.yfloat_gesture_settings),
                getString(R.string.yfloat_gesture_settings_desc),
                () -> startActivity(new Intent(this, GestureHubActivity.class))));
        YViewLayout.addRow(settings.body, YViewLayout.navRow(this,
                getString(R.string.yfloat_capture_ocr_settings),
                getString(R.string.yfloat_capture_ocr_settings_desc),
                () -> startActivity(SettingsActivity.intent(this, SettingsActivity.SECTION_CAPTURE))));
        YViewLayout.addRow(settings.body, YViewLayout.navRow(this,
                getString(R.string.yfloat_environment_settings),
                getString(R.string.yfloat_environment_settings_desc),
                () -> startActivity(SettingsActivity.intent(this, SettingsActivity.SECTION_ENVIRONMENT))));
        YViewLayout.addRow(settings.body, YViewLayout.navRow(this,
                getString("com.yagay.YSuite".equals(getPackageName())
                        ? R.string.yfloat_menu_management : R.string.yfloat_interface_settings),
                getString("com.yagay.YSuite".equals(getPackageName())
                        ? R.string.yfloat_menu_management_desc : R.string.yfloat_interface_settings_desc),
                () -> startActivity(new Intent(this, AppearanceSettingsActivity.class))));
        YViewLayout.addSection(root, settings);

        YViewSection advanced = YViewLayout.section(this, getString(R.string.yfloat_advanced_title), null);
        YViewLayout.addRow(advanced.body, YViewLayout.navRow(this,
                getString(R.string.yfloat_advanced_permissions),
                getString(R.string.yfloat_advanced_permissions_desc),
                () -> startActivity(SettingsActivity.intent(this, SettingsActivity.SECTION_PRIVILEGE))));
        YViewLayout.addRow(advanced.body, YViewLayout.navRow(this,
                getString(R.string.yfloat_diagnostics),
                getString(R.string.yfloat_diagnostics_desc),
                () -> startActivity(new Intent(this, DiagnosticsActivity.class))));
        YViewLayout.addRow(advanced.body, YViewLayout.sectionNote(this,
                getString(R.string.yfloat_version, BuildConfig.VERSION_NAME)));
        YViewLayout.addSection(root, advanced);

        setContentView(YViewLayout.scrollPage(this, root));
    }

    @Override protected void onResume() {
        super.onResume();
        boolean returnedFromAccessibilitySettings = awaitingAccessibilityGrant;
        awaitingAccessibilityGrant = false;

        AccessibilityState.addListener(accessibilityStateListener);
        boolean enabled = FloatServiceState.isEnabled(this);
        syncOverlaySwitch(enabled);
        maybeStartEnabledFloatService();
        refreshStatus();

        if (returnedFromAccessibilitySettings) {
            AccessibilityState.Snapshot snapshot = AccessibilityState.snapshot(this);
            if (!snapshot.hostEnabled) {
                int messageRes;
                if (snapshot.sameHostOtherAccessibilityEnabled
                        && "com.yagay.YSuite".equals(getPackageName())) {
                    messageRes = R.string.yfloat_old_suite_accessibility_warning;
                } else if (snapshot.otherYFloatEnabled
                        && "com.yagay.YSuite".equals(getPackageName())) {
                    messageRes = R.string.yfloat_standalone_accessibility_warning;
                } else if ("com.yagay.YSuite".equals(getPackageName())) {
                    messageRes = R.string.yfloat_suite_accessibility_still_disabled;
                } else if (snapshot.otherYFloatEnabled) {
                    messageRes = R.string.yfloat_other_version_accessibility_warning;
                } else {
                    messageRes = R.string.yfloat_accessibility_still_disabled;
                }
                Toast.makeText(this, messageRes, Toast.LENGTH_LONG).show();
            }
        }

        mainHandler.removeCallbacks(delayedAccessibilityRefresh);
        mainHandler.postDelayed(delayedAccessibilityRefresh, 400L);
        mainHandler.postDelayed(delayedAccessibilityRefresh, 1400L);
        mainHandler.postDelayed(delayedAccessibilityRefresh, 3000L);
    }

    @Override protected void onPause() {
        AccessibilityState.removeListener(accessibilityStateListener);
        mainHandler.removeCallbacks(delayedAccessibilityRefresh);
        super.onPause();
    }

    private void openAccessibilitySettings() {
        if (AccessibilitySettingsNavigator.open(this)) {
            awaitingAccessibilityGrant = true;
        } else {
            Toast.makeText(this, R.string.yfloat_cannot_open_accessibility, Toast.LENGTH_LONG).show();
        }
    }

    private void maybeStartEnabledFloatService() {
        if (!FloatServiceState.isEnabled(this) || FloatService.get() != null) return;
        if (Settings.canDrawOverlays(this) || AccessibilityState.connected()) {
            FloatServiceState.start(this);
        }
    }

    private void onOverlayToggle(boolean checked) {
        if (syncingOverlaySwitch) return;
        if (checked) {
            boolean overlayGranted = Settings.canDrawOverlays(this);
            AccessibilityState.Snapshot a11y = AccessibilityState.snapshot(this);
            if (!overlayGranted && !a11y.hostEnabled) {
                int messageRes;
                if (a11y.sameHostOtherAccessibilityEnabled
                        && "com.yagay.YSuite".equals(getPackageName())) {
                    messageRes = R.string.yfloat_migrate_old_accessibility;
                } else if (a11y.otherYFloatEnabled
                        && "com.yagay.YSuite".equals(getPackageName())) {
                    messageRes = R.string.yfloat_use_suite_accessibility;
                } else if ("com.yagay.YSuite".equals(getPackageName())) {
                    messageRes = R.string.yfloat_need_overlay_or_suite_accessibility;
                } else {
                    messageRes = R.string.yfloat_need_overlay_or_accessibility;
                }
                Toast.makeText(this, messageRes, Toast.LENGTH_LONG).show();
                FloatServiceState.setEnabled(this, false);
                syncOverlaySwitch(false);
                refreshStatus();
                return;
            }

            if (!overlayGranted && a11y.hostEnabled && !a11y.connected) {
                FloatServiceState.setEnabled(this, true);
                syncOverlaySwitch(true);
                Toast.makeText(this,
                        R.string.yfloat_accessibility_waiting_connection,
                        Toast.LENGTH_SHORT).show();
                mainHandler.postDelayed(delayedAccessibilityRefresh, 500L);
                mainHandler.postDelayed(delayedAccessibilityRefresh, 1500L);
                refreshStatus();
                return;
            }

            if (FloatServiceState.start(this)) {
                Toast.makeText(this, R.string.yfloat_float_icon_enabled, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, R.string.yfloat_start_failed_retry, Toast.LENGTH_LONG).show();
            }
        } else {
            FloatServiceState.stop(this);
            Toast.makeText(this, R.string.yfloat_float_icon_disabled, Toast.LENGTH_SHORT).show();
        }
        refreshStatus();
    }

    private void refreshStatus() {
        boolean overlayGranted = Settings.canDrawOverlays(this);
        AccessibilityState.Snapshot a11y = AccessibilityState.snapshot(this);
        boolean notificationsGranted = Build.VERSION.SDK_INT < 33
                || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;

        updatePermission(overlayPermission, overlayGranted);
        updateAccessibilityPermission(accessibilityPermission, a11y);
        if (notificationPermission != null) updatePermission(notificationPermission, notificationsGranted);

        boolean enabled = FloatServiceState.isEnabled(this);
        boolean running = FloatService.get() != null;
        if (!enabled) {
            setServiceStatus(getString(R.string.yfloat_status_stopped), false);
        } else if (running && a11y.connected) {
            setServiceStatus(getString(R.string.yfloat_status_running_accessibility_connected), true);
        } else if (running && a11y.hostEnabled) {
            setServiceStatus(getString(R.string.yfloat_status_running_waiting_accessibility), false);
        } else if (running) {
            setServiceStatus(getString(R.string.yfloat_status_running_accessibility_disabled), false);
        } else if (a11y.hostEnabled && !a11y.connected && !overlayGranted) {
            setServiceStatus(getString(R.string.yfloat_status_waiting_accessibility), false);
        } else {
            setServiceStatus(getString(R.string.yfloat_status_waiting_recovery), false);
        }
    }

    private void setServiceStatus(String text, boolean positive) {
        serviceStatus.setText(text);
        serviceStatus.setTextColor(positive ? YViewLayout.success(this) : YViewLayout.warning(this));
        serviceStatus.setBackground(YViewLayout.rounded(this,
                positive ? YViewLayout.successSurface(this) : YViewLayout.warningSurface(this), 999));
    }

    private PermissionRow permissionRow(String title, String subtitle, Runnable action) {
        LinearLayout row = YViewLayout.baseRow(this);

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        TextView titleView = YViewLayout.rowTitle(this, title);
        copy.addView(titleView);
        TextView sub = YViewLayout.rowSubtitle(this, subtitle);
        copy.addView(sub);
        TextView status = YViewLayout.rowSubtitle(this,
                getString(R.string.yfloat_permission_not_authorized));
        copy.addView(status);
        row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1f));

        MaterialButton button = YViewLayout.compactButton(this, getString(R.string.yfloat_permission_authorize));
        button.setOnClickListener(v -> { if (action != null) action.run(); });
        row.addView(button, new LinearLayout.LayoutParams(-2, -2));
        return new PermissionRow(row, status, button);
    }

    private void updatePermission(PermissionRow row, boolean granted) {
        if (row == null) return;
        row.status.setText(granted
                ? R.string.yfloat_permission_authorized
                : R.string.yfloat_permission_not_authorized);
        row.status.setTextColor(granted ? YViewLayout.success(this) : YViewLayout.warning(this));
        row.button.setText(granted
                ? R.string.yfloat_permission_settings
                : R.string.yfloat_permission_authorize);
    }

    private void updateAccessibilityPermission(
            PermissionRow row,
            AccessibilityState.Snapshot snapshot
    ) {
        if (row == null || snapshot == null) return;
        row.status.setText(snapshot.statusLabel(this));
        row.status.setTextColor(snapshot.hostEnabled ? YViewLayout.success(this) : YViewLayout.warning(this));
        if (snapshot.hostEnabled) {
            row.button.setText(R.string.yfloat_permission_settings);
        } else if ("com.yagay.YSuite".equals(getPackageName())) {
            row.button.setText(R.string.yfloat_permission_enable_suite);
        } else {
            row.button.setText(R.string.yfloat_permission_authorize);
        }
    }

    private void handleNotificationPermission() {
        if (Build.VERSION.SDK_INT < 33) return;
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 20);
            return;
        }
        try {
            Intent i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
            startActivity(i);
        } catch (Throwable t) {
            Toast.makeText(this, R.string.yfloat_notification_already_authorized, Toast.LENGTH_SHORT).show();
        }
    }

    private void syncOverlaySwitch(boolean checked) {
        if (overlaySwitch == null || overlaySwitch.isChecked() == checked) return;
        syncingOverlaySwitch = true;
        overlaySwitch.setChecked(checked);
        syncingOverlaySwitch = false;
    }

    private static final class PermissionRow {
        final LinearLayout view;
        final TextView status;
        final MaterialButton button;
        PermissionRow(LinearLayout view, TextView status, MaterialButton button) {
            this.view = view;
            this.status = status;
            this.button = button;
        }
    }
}
