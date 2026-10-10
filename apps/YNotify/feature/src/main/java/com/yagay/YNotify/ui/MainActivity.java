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
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatEditText;
import androidx.lifecycle.LiveData;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

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
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.chip.ChipGroup;
import com.yagay.yui.YView;
import com.yagay.yui.YViewFilterBar;
import com.yagay.yui.YViewLayout;
import com.yagay.yui.YViewDialogs;
import com.yagay.yui.YViewPage;
import com.yagay.yui.YViewSection;
import com.yagay.yui.YViewRadioGroup;
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

    private MaterialToolbar toolbar;
    private LinearLayout captureStatus;
    private Button btnNotificationAccess;
    private Button btnAccessibility;
    private Button btnExportDiagnostics;
    private Button btnHistoryRepair;
    private Button btnClearAll;
    private AppCompatEditText searchEdit;
    private View searchBox;
    private View filterScroll;
    private ScrollView settingsPanel;
    private ChipGroup chipGroup;
    private YViewFilterBar filterBar;
    private RecyclerView list;
    private BottomNavigationView bottomNav;
    private TextView diagnosticsStatus;
    private TextView repairStatus;
    private TextView runtimeStatus;
    private RadioGroup retentionGroup;
    private RadioButton retention7;
    private RadioButton retention30;
    private RadioButton retention90;
    private RadioButton retentionForever;
    private final EventAdapter eventAdapter = new EventAdapter();
    private final AppAdapter appAdapter = new AppAdapter();
    private LiveData<List<EventRecord>> eventSource;
    private LiveData<List<AppSummary>> appSource;
    private String selectedType = FILTER_ALL;
    private int mode = R.id.nav_timeline;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        buildUi();
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(eventAdapter);

        btnNotificationAccess.setOnClickListener(v -> openNotificationListenerSettings());
        btnAccessibility.setOnClickListener(v -> openAccessibilityServiceSettings());

        bottomNav.setOnItemSelectedListener(item -> {
            mode = item.getItemId();
            renderMode();
            return true;
        });

        chipGroup.setOnCheckedStateChangeListener((group, ids) -> {
            if (ids.isEmpty()) return;
            selectedType = typeForFilterIndex(filterBar.indexForId(ids.get(0)));
            observeTimeline();
        });

        searchEdit.addTextChangedListener(new TextWatcher() {
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
        btnClearAll.setOnClickListener(v -> YViewDialogs.builder(this)
                .setTitle(R.string.ynotify_clear_confirm_title)
                .setMessage(R.string.ynotify_clear_confirm_message)
                .setNegativeButton(R.string.ynotify_cancel, null)
                .setPositiveButton(R.string.ynotify_clear, (d, w) -> EventStore.clearAll(this))
                .show());

        renderMode();
        observeTimeline();
        HistoryRepairEngine.runOnceAfterUpgrade(this);
    }

    private void buildUi() {
        YViewPage page = YViewLayout.installPage(
                this,
                getString(R.string.ynotify_app_name),
                null);
        toolbar = page.toolbar;

        LinearLayout main = YViewLayout.contentColumn(this, false);
        page.content.addView(main, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        captureStatus = YViewLayout.buttonRow(this);
        btnNotificationAccess = YViewLayout.secondaryButton(
                this,
                getString(R.string.ynotify_notification_access));
        btnAccessibility = YViewLayout.secondaryButton(
                this,
                getString(R.string.ynotify_ui_messages));
        YViewLayout.addAction(captureStatus, btnNotificationAccess);
        YViewLayout.addAction(captureStatus, btnAccessibility);
        main.addView(captureStatus, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout searchHolder = new LinearLayout(this);
        searchHolder.setPadding(
                YView.screenHorizontal(this),
                YView.controlGap(this) / 2,
                YView.screenHorizontal(this),
                YView.controlGap(this) / 2);
        searchEdit = YViewLayout.searchField(this, getString(R.string.ynotify_search_timeline));
        searchHolder.addView(searchEdit, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        searchBox = searchHolder;
        main.addView(searchHolder);

        filterBar = YViewLayout.filterBar(
                this,
                List.of(
                        getString(R.string.ynotify_filter_all),
                        getString(R.string.ynotify_filter_notification),
                        getString(R.string.ynotify_filter_heads_up),
                        getString(R.string.ynotify_filter_bubble),
                        getString(R.string.ynotify_filter_full_screen),
                        getString(R.string.ynotify_filter_toast),
                        getString(R.string.ynotify_filter_dialog),
                        getString(R.string.ynotify_filter_popup),
                        getString(R.string.ynotify_filter_snackbar)),
                0);
        chipGroup = filterBar.group;
        filterScroll = filterBar.view;
        main.addView(filterScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        list = new RecyclerView(this);
        list.setClipToPadding(false);
        main.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout settingsRoot = YViewLayout.contentColumn(this, true);
        settingsPanel = YViewLayout.scrollPage(this, settingsRoot);
        settingsPanel.setVisibility(View.GONE);
        main.addView(settingsPanel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        YViewSection runtime = YViewLayout.section(
                this,
                getString(R.string.ynotify_runtime_status),
                null);
        runtimeStatus = YViewLayout.text(this, "", 14, false);
        runtimeStatus.setTextIsSelectable(true);
        runtime.body.addView(runtimeStatus);
        YViewLayout.addSection(settingsRoot, runtime);

        YViewSection diagnostics = YViewLayout.section(
                this,
                getString(R.string.ynotify_diagnostic_logs),
                getString(R.string.ynotify_diagnostic_logs_desc));
        btnExportDiagnostics = YViewLayout.secondaryButton(
                this,
                getString(R.string.ynotify_export_diagnostics));
        diagnostics.body.addView(btnExportDiagnostics, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        diagnosticsStatus = YViewLayout.caption(
                this,
                getString(R.string.ynotify_diagnostics_location),
                13);
        diagnostics.body.addView(diagnosticsStatus);
        YViewLayout.addSection(settingsRoot, diagnostics);

        YViewSection repair = YViewLayout.section(
                this,
                getString(R.string.ynotify_history_repair),
                getString(R.string.ynotify_history_repair_desc));
        btnHistoryRepair = YViewLayout.secondaryButton(
                this,
                getString(R.string.ynotify_repair_history));
        repair.body.addView(btnHistoryRepair, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        repairStatus = YViewLayout.caption(
                this,
                getString(R.string.ynotify_not_scanned),
                13);
        repair.body.addView(repairStatus);
        YViewLayout.addSection(settingsRoot, repair);

        YViewSection retention = YViewLayout.section(
                this,
                getString(R.string.ynotify_retention),
                null);
        YViewRadioGroup retentionChoices = YViewLayout.radioGroup(
                this,
                new String[] {
                        getString(R.string.ynotify_days_7),
                        getString(R.string.ynotify_days_30),
                        getString(R.string.ynotify_days_90),
                        getString(R.string.ynotify_forever)
                });
        retentionGroup = retentionChoices.group;
        retention7 = retentionChoices.buttons.get(0);
        retention30 = retentionChoices.buttons.get(1);
        retention90 = retentionChoices.buttons.get(2);
        retentionForever = retentionChoices.buttons.get(3);
        retention.body.addView(retentionGroup);
        btnClearAll = YViewLayout.secondaryButton(
                this,
                getString(R.string.ynotify_clear_all));
        retention.body.addView(btnClearAll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        YViewLayout.addSection(settingsRoot, retention);

        TextView scope = YViewLayout.text(
                this,
                getString(R.string.ynotify_scope_help),
                14,
                false);
        settingsRoot.addView(scope);

        bottomNav = YViewLayout.bottomNavigation(this, R.menu.bottom_nav);
        page.root.addView(bottomNav, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
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
        btnExportDiagnostics.setOnClickListener(v -> {
            btnExportDiagnostics.setEnabled(false);
            diagnosticsStatus.setText(R.string.ynotify_collecting_diagnostics);
            DiagnosticsExporter.export(this, new DiagnosticsExporter.Callback() {
                @Override
                public void onSuccess(String fileName, String location, android.net.Uri uri, boolean rootCollected) {
                    if (isFinishing()) return;
                    btnExportDiagnostics.setEnabled(true);
                    diagnosticsStatus.setText(rootCollected
                            ? getString(R.string.ynotify_exported_root, location)
                            : getString(R.string.ynotify_exported_no_root, location));
                    Toast.makeText(MainActivity.this, R.string.ynotify_diagnostics_saved, Toast.LENGTH_LONG).show();
                }

                @Override
                public void onFailure(String message) {
                    if (isFinishing()) return;
                    btnExportDiagnostics.setEnabled(true);
                    diagnosticsStatus.setText(getString(R.string.ynotify_export_failed_detail, message));
                    Toast.makeText(MainActivity.this, R.string.ynotify_diagnostics_export_failed, Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    private void setupHistoryRepair() {
        btnHistoryRepair.setOnClickListener(v -> {
            btnHistoryRepair.setEnabled(false);
            repairStatus.setText(R.string.ynotify_repair_scanning);
            HistoryRepairEngine.repairAsync(this, report -> {
                if (isFinishing()) return;
                btnHistoryRepair.setEnabled(true);
                repairStatus.setText(HistoryRepairText.summary(this, report));
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
                btnNotificationAccess.setText(R.string.ynotify_migrate_suite_notification);
            } else if (otherNotificationHost) {
                btnNotificationAccess.setText(suiteHost
                        ? R.string.ynotify_enable_suite_notification
                        : R.string.ynotify_enable_current_notification);
            } else {
                btnNotificationAccess.setText(suiteHost
                        ? R.string.ynotify_enable_suite_notification
                        : R.string.ynotify_enable_notification);
            }
        } else if (connected) {
            btnNotificationAccess.setText(otherNotificationHost
                    ? getString(R.string.ynotify_connected_other_enabled, notificationName)
                    : getString(R.string.ynotify_connected_name, notificationName));
        } else {
            btnNotificationAccess.setText(R.string.ynotify_authorized_reconnecting);
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
                btnAccessibility.setText(R.string.ynotify_migrate_suite_accessibility);
            } else if (otherA11yHost) {
                btnAccessibility.setText(suiteHost
                        ? R.string.ynotify_enable_suite_accessibility
                        : R.string.ynotify_enable_current_ui_capture);
            } else {
                btnAccessibility.setText(suiteHost
                        ? R.string.ynotify_enable_suite_accessibility
                        : R.string.ynotify_enable_ui_capture);
            }
        } else if (a11yConnected) {
            btnAccessibility.setText(otherA11yHost
                    ? getString(R.string.ynotify_connected_other_enabled, a11yName)
                    : getString(R.string.ynotify_connected_name, a11yName));
        } else {
            btnAccessibility.setText(R.string.ynotify_authorized_waiting);
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
        runtimeStatus.setText(status.toString());

        if (mode == R.id.nav_settings) captureStatus.setVisibility(View.VISIBLE);
        else if (mode == R.id.nav_timeline) captureStatus.setVisibility((connected && a11yConnected) ? View.GONE : View.VISIBLE);
        else captureStatus.setVisibility(View.GONE);
    }

    private void renderMode() {
        boolean settings = mode == R.id.nav_settings;
        boolean apps = mode == R.id.nav_apps;
        settingsPanel.setVisibility(settings ? View.VISIBLE : View.GONE);
        list.setVisibility(settings ? View.GONE : View.VISIBLE);
        searchBox.setVisibility(settings ? View.GONE : View.VISIBLE);
        filterScroll.setVisibility(mode == R.id.nav_timeline ? View.VISIBLE : View.GONE);
        runtimeStatus.setVisibility(settings ? View.VISIBLE : View.GONE);
        updatePermissionStatus();
        if (apps) {
            toolbar.setTitle(R.string.ynotify_apps_title);
            searchEdit.setHint(R.string.ynotify_search_apps);
            list.setAdapter(appAdapter);
            observeApps();
        } else if (!settings) {
            toolbar.setTitle(R.string.ynotify_app_name);
            searchEdit.setHint(R.string.ynotify_search_timeline);
            list.setAdapter(eventAdapter);
            observeTimeline();
        } else {
            toolbar.setTitle(R.string.ynotify_settings_title);
        }
    }

    private void observeTimeline() {
        if (mode != R.id.nav_timeline) return;
        if (eventSource != null) eventSource.removeObservers(this);
        String q = searchEdit.getText() == null ? "" : searchEdit.getText().toString().trim();

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
            appAdapter.setQuery(searchEdit.getText() == null ? "" : searchEdit.getText().toString());
        });
    }

    private String typeForFilterIndex(int index) {
        return switch (index) {
            case 1 -> EventTypes.NOTIFICATION;
            case 2 -> FILTER_HEADS_UP;
            case 3 -> FILTER_BUBBLE;
            case 4 -> FILTER_FULL_SCREEN;
            case 5 -> EventTypes.TOAST;
            case 6 -> EventTypes.DIALOG;
            case 7 -> EventTypes.POPUP;
            case 8 -> EventTypes.SNACKBAR;
            default -> FILTER_ALL;
        };
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
        RadioButton selected = days == 7
                ? retention7
                : days == 30
                ? retention30
                : days == 0
                ? retentionForever
                : retention90;
        retentionGroup.check(selected.getId());
        retentionGroup.setOnCheckedChangeListener((group, id) -> {
            int d = id == retention7.getId()
                    ? 7
                    : id == retention30.getId()
                    ? 30
                    : id == retentionForever.getId()
                    ? 0
                    : 90;
            retentionPreferences().edit().putInt("retention_days", d).apply();
            EventStore.cleanup(this, d);
        });
    }
}
