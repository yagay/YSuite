package com.yagay.YNotify.ui;

import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.LiveData;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.yagay.YNotify.YNotifyApp;
import com.yagay.YNotify.R;
import com.yagay.YNotify.collector.UiAccessibilityService;
import com.yagay.YNotify.data.AppSummary;
import com.yagay.YNotify.data.EventRecord;
import com.yagay.YNotify.data.EventStore;
import com.yagay.YNotify.data.EventTypes;
import com.yagay.YNotify.data.HistoryRepairEngine;
import com.yagay.YNotify.data.ListenerStateStore;
import com.yagay.YNotify.data.NotifyDatabase;
import com.yagay.YNotify.databinding.ActivityMainBinding;
import com.yagay.YNotify.util.DiagnosticsExporter;
import com.yagay.YNotify.util.SearchQuery;
import com.yagay.YNotify.util.ServiceGrantStatus;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final String FILTER_ALL = "all";
    private static final String FILTER_HEADS_UP = "heads_up";
    private static final String FILTER_BUBBLE = "bubble";
    private static final String FILTER_FULL_SCREEN = "full_screen";
    private static final String PREFS = "ynotify_settings";
    private static final String ACTION_ACCESSIBILITY_DETAILS_SETTINGS =
            "android.settings.ACCESSIBILITY_DETAILS_SETTINGS";

    private ActivityMainBinding b;
    private final EventAdapter eventAdapter = new EventAdapter();
    private final AppAdapter appAdapter = new AppAdapter();
    private LiveData<List<EventRecord>> eventSource;
    private LiveData<List<AppSummary>> appSource;
    private String selectedType = FILTER_ALL;
    private int mode = R.id.nav_timeline;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        b = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        b.list.setLayoutManager(new LinearLayoutManager(this));
        b.list.setAdapter(eventAdapter);

        b.btnNotificationAccess.setOnClickListener(v -> openNotificationListenerSettings());
        b.btnAccessibility.setOnClickListener(v -> openAccessibilityServiceSettings());

        b.bottomNav.setOnItemSelectedListener(item -> {
            mode = item.getItemId();
            renderMode();
            return true;
        });

        b.chipGroup.setOnCheckedStateChangeListener((group, ids) -> {
            if (ids.isEmpty()) return;
            selectedType = typeForChip(ids.get(0));
            observeTimeline();
        });

        b.searchEdit.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            public void onTextChanged(CharSequence s, int st, int before, int count) {
                if (mode == R.id.nav_timeline) observeTimeline();
                else if (mode == R.id.nav_apps) appAdapter.setQuery(s.toString());
            }
            public void afterTextChanged(Editable e) {}
        });

        setupRetention();
        setupHistoryRepair();
        setupDiagnosticsExport();
        b.btnClearAll.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle(R.string.ynotify_clear_confirm_title)
                .setMessage(R.string.ynotify_clear_confirm_message)
                .setNegativeButton(R.string.ynotify_cancel, null)
                .setPositiveButton(R.string.ynotify_clear, (d, w) -> EventStore.clearAll(this))
                .show());

        renderMode();
        observeTimeline();
        HistoryRepairEngine.runOnceAfterUpgrade(this);
    }

    private void openNotificationListenerSettings() {
        ComponentName component = ServiceGrantStatus.notificationListenerComponent(this);
        try {
            Intent detail = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                    .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                            component.flattenToString());
            startActivity(detail);
            return;
        } catch (Throwable ignored) { }
        try {
            startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
        } catch (Throwable t) {
            Toast.makeText(this, R.string.ynotify_cannot_open_notification_settings, Toast.LENGTH_LONG).show();
        }
    }

    private void openAccessibilityServiceSettings() {
        ComponentName component = ServiceGrantStatus.accessibilityComponent(this);
        try {
            Intent detail = new Intent(ACTION_ACCESSIBILITY_DETAILS_SETTINGS)
                    .putExtra(Intent.EXTRA_COMPONENT_NAME, component);
            startActivity(detail);
            return;
        } catch (Throwable ignored) { }
        try {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        } catch (Throwable t) {
            Toast.makeText(this, R.string.ynotify_cannot_open_accessibility_settings, Toast.LENGTH_LONG).show();
        }
    }

    private void setupDiagnosticsExport() {
        b.btnExportDiagnostics.setOnClickListener(v -> {
            b.btnExportDiagnostics.setEnabled(false);
            b.diagnosticsStatus.setText(R.string.ynotify_collecting_diagnostics);
            DiagnosticsExporter.export(this, new DiagnosticsExporter.Callback() {
                @Override
                public void onSuccess(String fileName, String location, android.net.Uri uri, boolean rootCollected) {
                    if (isFinishing()) return;
                    b.btnExportDiagnostics.setEnabled(true);
                    b.diagnosticsStatus.setText(rootCollected
                            ? getString(R.string.ynotify_exported_root, location)
                            : getString(R.string.ynotify_exported_no_root, location));
                    Toast.makeText(MainActivity.this, R.string.ynotify_diagnostics_saved, Toast.LENGTH_LONG).show();
                }

                @Override
                public void onFailure(String message) {
                    if (isFinishing()) return;
                    b.btnExportDiagnostics.setEnabled(true);
                    b.diagnosticsStatus.setText(getString(R.string.ynotify_export_failed_detail, message));
                    Toast.makeText(MainActivity.this, R.string.ynotify_diagnostics_export_failed, Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    private void setupHistoryRepair() {
        b.btnHistoryRepair.setOnClickListener(v -> {
            b.btnHistoryRepair.setEnabled(false);
            b.repairStatus.setText(R.string.ynotify_repair_scanning);
            HistoryRepairEngine.repairAsync(this, report -> {
                if (isFinishing()) return;
                b.btnHistoryRepair.setEnabled(true);
                b.repairStatus.setText(HistoryRepairText.summary(this, report));
            });
        });
    }

    @Override protected void onResume() {
        super.onResume();
        ensureNotificationListenerBound();
        updatePermissionStatus();
    }

    private void ensureNotificationListenerBound() {
        boolean granted = ServiceGrantStatus.notificationListenerEnabled(this);
        if (!granted || ListenerStateStore.isConnected(this)) return;
        try {
            NotificationListenerService.requestRebind(
                    ServiceGrantStatus.notificationListenerComponent(this));
        } catch (Throwable t) {
            ListenerStateStore.markError(this, "uiRebind", getPackageName(), t);
        }
    }

    private void updatePermissionStatus() {
        boolean suiteHost = "com.yagay.YSuite".equals(getPackageName());

        boolean granted = ServiceGrantStatus.notificationListenerEnabled(this);
        boolean connected = ListenerStateStore.isConnected(this);
        boolean otherNotificationHost = ServiceGrantStatus.otherHostNotificationListenerEnabled(this);
        boolean legacySuiteNotification = ServiceGrantStatus.legacySuiteNotificationListenerEnabled(this);
        String notificationName = getString(suiteHost
                ? R.string.ynotify_suite_notification_listener
                : R.string.ynotify_notification_listener);
        if (!granted) {
            if (legacySuiteNotification) {
                b.btnNotificationAccess.setText(R.string.ynotify_migrate_suite_notification);
            } else if (otherNotificationHost) {
                b.btnNotificationAccess.setText(suiteHost
                        ? R.string.ynotify_enable_suite_notification
                        : R.string.ynotify_enable_current_notification);
            } else {
                b.btnNotificationAccess.setText(suiteHost
                        ? R.string.ynotify_enable_suite_notification
                        : R.string.ynotify_enable_notification);
            }
        } else if (connected) {
            b.btnNotificationAccess.setText(otherNotificationHost
                    ? getString(R.string.ynotify_connected_other_enabled, notificationName)
                    : getString(R.string.ynotify_connected_name, notificationName));
        } else {
            b.btnNotificationAccess.setText(R.string.ynotify_authorized_reconnecting);
        }

        boolean a11yGranted = ServiceGrantStatus.accessibilityEnabled(this);
        boolean a11yConnected = UiAccessibilityService.isConnected();
        boolean otherA11yHost = ServiceGrantStatus.otherHostAccessibilityEnabled(this);
        boolean legacySuiteA11y = ServiceGrantStatus.legacySuiteAccessibilityEnabled(this);
        String a11yName = getString(suiteHost
                ? R.string.ynotify_suite_accessibility
                : R.string.ynotify_ui_capture);
        if (!a11yGranted) {
            if (legacySuiteA11y) {
                b.btnAccessibility.setText(R.string.ynotify_migrate_suite_accessibility);
            } else if (otherA11yHost) {
                b.btnAccessibility.setText(suiteHost
                        ? R.string.ynotify_enable_suite_accessibility
                        : R.string.ynotify_enable_current_ui_capture);
            } else {
                b.btnAccessibility.setText(suiteHost
                        ? R.string.ynotify_enable_suite_accessibility
                        : R.string.ynotify_enable_ui_capture);
            }
        } else if (a11yConnected) {
            b.btnAccessibility.setText(otherA11yHost
                    ? getString(R.string.ynotify_connected_other_enabled, a11yName)
                    : getString(R.string.ynotify_connected_name, a11yName));
        } else {
            b.btnAccessibility.setText(R.string.ynotify_authorized_waiting);
        }

        long lastEvent = ListenerStateStore.lastEvent(this);
        long lastConnected = ListenerStateStore.lastConnected(this);
        long lastReceived = ListenerStateStore.lastNotificationReceived(this);
        long lastSaved = ListenerStateStore.lastNotificationSaved(this);
        String lastPkg = ListenerStateStore.lastNotificationPackage(this);
        String lastError = ListenerStateStore.lastError(this);
        long lastErrorTime = ListenerStateStore.lastErrorTime(this);

        StringBuilder status = new StringBuilder();
        status.append(notificationName).append(": ")
                .append(getString(!granted
                        ? R.string.ynotify_status_not_authorized
                        : connected
                        ? R.string.ynotify_status_connected
                        : R.string.ynotify_status_reconnecting));
        if (legacySuiteNotification && !granted) {
            status.append("\n").append(getString(R.string.ynotify_legacy_notification_warning));
        }
        if (otherNotificationHost) {
            status.append("\n").append(getString(granted
                    ? R.string.ynotify_other_notification_warning
                    : R.string.ynotify_other_notification_not_current));
        }
        status.append("\n").append(a11yName).append(": ")
                .append(getString(!a11yGranted
                        ? R.string.ynotify_status_not_authorized
                        : a11yConnected
                        ? R.string.ynotify_accessibility_connected
                        : R.string.ynotify_accessibility_waiting));
        if (legacySuiteA11y && !a11yGranted) {
            status.append("\n").append(getString(R.string.ynotify_legacy_accessibility_warning));
        }
        if (otherA11yHost) {
            status.append("\n").append(getString(a11yGranted
                    ? R.string.ynotify_other_accessibility_warning
                    : R.string.ynotify_other_accessibility_not_current));
        }
        if (lastConnected > 0) status.append("\n").append(getString(R.string.ynotify_last_connected, TimeFormat.full(lastConnected)));
        if (lastEvent > 0) status.append("\n").append(getString(R.string.ynotify_last_callback, TimeFormat.full(lastEvent)));
        if (lastReceived > 0) {
            status.append("\n").append(lastPkg != null && !lastPkg.isEmpty()
                    ? getString(R.string.ynotify_last_received_package, TimeFormat.full(lastReceived), lastPkg)
                    : getString(R.string.ynotify_last_received, TimeFormat.full(lastReceived)));
        }
        if (lastSaved > 0) status.append("\n").append(getString(R.string.ynotify_last_saved, TimeFormat.full(lastSaved)));
        if (lastError != null && !lastError.isEmpty()) {
            status.append("\n").append(lastErrorTime > 0
                    ? getString(R.string.ynotify_last_error_time, TimeFormat.full(lastErrorTime), lastError)
                    : getString(R.string.ynotify_last_error, lastError));
        }
        status.append("\n\n").append(YNotifyApp.runtimeStatus(this));
        b.runtimeStatus.setText(status.toString());

        if (mode == R.id.nav_settings) b.captureStatus.setVisibility(View.VISIBLE);
        else if (mode == R.id.nav_timeline) b.captureStatus.setVisibility((connected && a11yConnected) ? View.GONE : View.VISIBLE);
        else b.captureStatus.setVisibility(View.GONE);
    }

    private void renderMode() {
        boolean settings = mode == R.id.nav_settings;
        boolean apps = mode == R.id.nav_apps;
        b.settingsPanel.setVisibility(settings ? View.VISIBLE : View.GONE);
        b.list.setVisibility(settings ? View.GONE : View.VISIBLE);
        b.searchBox.setVisibility(settings ? View.GONE : View.VISIBLE);
        b.filterScroll.setVisibility(mode == R.id.nav_timeline ? View.VISIBLE : View.GONE);
        b.runtimeStatus.setVisibility(settings ? View.VISIBLE : View.GONE);
        updatePermissionStatus();
        if (apps) {
            b.toolbar.setTitle(R.string.ynotify_apps_title);
            b.searchBox.setHint(R.string.ynotify_search_apps);
            b.list.setAdapter(appAdapter);
            observeApps();
        } else if (!settings) {
            b.toolbar.setTitle(R.string.ynotify_app_name);
            b.searchBox.setHint(R.string.ynotify_search_timeline);
            b.list.setAdapter(eventAdapter);
            observeTimeline();
        } else {
            b.toolbar.setTitle(R.string.ynotify_settings_title);
        }
    }

    private void observeTimeline() {
        if (mode != R.id.nav_timeline) return;
        if (eventSource != null) eventSource.removeObservers(this);
        String q = b.searchEdit.getText() == null ? "" : b.searchEdit.getText().toString().trim();

        if (!q.isEmpty()) {
            String fts = SearchQuery.fts(q);
            eventSource = fts.isEmpty()
                    ? NotifyDatabase.get(this).eventDao().searchFallback(q)
                    : NotifyDatabase.get(this).eventFtsDao().search(fts);
        } else if (FILTER_ALL.equals(selectedType)) {
            eventSource = NotifyDatabase.get(this).eventDao().observeAll();
        } else if (FILTER_HEADS_UP.equals(selectedType)) {
            eventSource = NotifyDatabase.get(this).eventDao().observeHeadsUp();
        } else if (FILTER_BUBBLE.equals(selectedType)) {
            eventSource = NotifyDatabase.get(this).eventDao().observeBubbles();
        } else if (FILTER_FULL_SCREEN.equals(selectedType)) {
            eventSource = NotifyDatabase.get(this).eventDao().observeFullScreens();
        } else {
            eventSource = NotifyDatabase.get(this).eventDao().observeType(selectedType);
        }

        eventSource.observe(this, list -> {
            if (q.isEmpty() || FILTER_ALL.equals(selectedType)) {
                eventAdapter.submit(list);
                return;
            }
            List<EventRecord> filtered = new ArrayList<>();
            if (list != null) {
                for (EventRecord r : list) if (matchesSelectedFilter(r)) filtered.add(r);
            }
            eventAdapter.submit(filtered);
        });
    }

    private boolean matchesSelectedFilter(EventRecord r) {
        if (r == null) return false;
        if (FILTER_ALL.equals(selectedType)) return true;
        if (FILTER_HEADS_UP.equals(selectedType)) {
            return EventTypes.NOTIFICATION.equals(r.eventType) && r.headsUp;
        }
        if (FILTER_BUBBLE.equals(selectedType)) {
            return EventTypes.NOTIFICATION.equals(r.eventType) && r.bubbleShown;
        }
        if (FILTER_FULL_SCREEN.equals(selectedType)) {
            return EventTypes.NOTIFICATION.equals(r.eventType) && r.fullScreenShown;
        }
        return selectedType.equals(r.eventType);
    }

    private void observeApps() {
        if (appSource != null) return;
        appSource = NotifyDatabase.get(this).eventDao().observeApps();
        appSource.observe(this, list -> {
            appAdapter.submit(list);
            appAdapter.setQuery(b.searchEdit.getText() == null ? "" : b.searchEdit.getText().toString());
        });
    }

    private String typeForChip(int id) {
        if (id == R.id.chip_notification) return EventTypes.NOTIFICATION;
        if (id == R.id.chip_heads_up) return FILTER_HEADS_UP;
        if (id == R.id.chip_bubble) return FILTER_BUBBLE;
        if (id == R.id.chip_full_screen) return FILTER_FULL_SCREEN;
        if (id == R.id.chip_toast) return EventTypes.TOAST;
        if (id == R.id.chip_dialog) return EventTypes.DIALOG;
        if (id == R.id.chip_popup) return EventTypes.POPUP;
        if (id == R.id.chip_snackbar) return EventTypes.SNACKBAR;
        return FILTER_ALL;
    }

    private SharedPreferences retentionPreferences() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (!prefs.contains("retention_days")) {
            SharedPreferences legacy = getSharedPreferences("settings", MODE_PRIVATE);
            if (legacy.contains("retention_days")) {
                prefs.edit().putInt("retention_days", legacy.getInt("retention_days", 90)).apply();
            }
        }
        return prefs;
    }

    private void setupRetention() {
        SharedPreferences prefs = retentionPreferences();
        int days = prefs.getInt("retention_days", 90);
        int check = days == 7 ? R.id.retention_7 : days == 30 ? R.id.retention_30 : days == 0 ? R.id.retention_forever : R.id.retention_90;
        b.retentionGroup.check(check);
        b.retentionGroup.setOnCheckedChangeListener((group, id) -> {
            int d = id == R.id.retention_7 ? 7 : id == R.id.retention_30 ? 30 : id == R.id.retention_forever ? 0 : 90;
            retentionPreferences().edit().putInt("retention_days", d).apply();
            EventStore.cleanup(this, d);
        });
    }
}
