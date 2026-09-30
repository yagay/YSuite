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
                .setTitle("清空全部历史？")
                .setMessage("通知版本记录、Toast/弹窗和断连记录也会一起删除。此操作不可撤销。")
                .setNegativeButton("取消", null)
                .setPositiveButton("清空", (d, w) -> EventStore.clearAll(this))
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
            Toast.makeText(this, "无法打开通知监听设置", Toast.LENGTH_LONG).show();
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
            Toast.makeText(this, "无法打开无障碍设置", Toast.LENGTH_LONG).show();
        }
    }

    private void setupDiagnosticsExport() {
        b.btnExportDiagnostics.setOnClickListener(v -> {
            b.btnExportDiagnostics.setEnabled(false);
            b.diagnosticsStatus.setText("正在收集 App、系统、LSPosed 和 Root 日志…");
            DiagnosticsExporter.export(this, new DiagnosticsExporter.Callback() {
                @Override
                public void onSuccess(String fileName, String location, android.net.Uri uri, boolean rootCollected) {
                    if (isFinishing()) return;
                    b.btnExportDiagnostics.setEnabled(true);
                    b.diagnosticsStatus.setText("已导出：" + location + (rootCollected ? " · Root/LSPosed 已收集" : " · 未获得 Root"));
                    Toast.makeText(MainActivity.this, "诊断日志已保存到 Download/YNotify", Toast.LENGTH_LONG).show();
                }

                @Override
                public void onFailure(String message) {
                    if (isFinishing()) return;
                    b.btnExportDiagnostics.setEnabled(true);
                    b.diagnosticsStatus.setText("导出失败：" + message);
                    Toast.makeText(MainActivity.this, "诊断日志导出失败", Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    private void setupHistoryRepair() {
        b.btnHistoryRepair.setOnClickListener(v -> {
            b.btnHistoryRepair.setEnabled(false);
            b.repairStatus.setText("正在扫描并修复历史…");
            HistoryRepairEngine.repairAsync(this, report -> {
                if (isFinishing()) return;
                b.btnHistoryRepair.setEnabled(true);
                b.repairStatus.setText(report.summary());
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
        String notificationName = suiteHost ? "YSuite 统一通知监听" : "通知监听";
        if (!granted) {
            if (legacySuiteNotification) {
                b.btnNotificationAccess.setText("迁移到 YSuite 统一通知监听");
            } else if (otherNotificationHost) {
                b.btnNotificationAccess.setText(suiteHost ? "开启 YSuite 通知监听" : "开启当前版通知权限");
            } else {
                b.btnNotificationAccess.setText(suiteHost ? "开启 YSuite 通知监听" : "开启通知权限");
            }
        } else if (connected) {
            b.btnNotificationAccess.setText(otherNotificationHost
                    ? notificationName + " ✓ · 另一版也开启"
                    : notificationName + " ✓");
        } else {
            b.btnNotificationAccess.setText("已授权 · 正在重连");
        }

        boolean a11yGranted = ServiceGrantStatus.accessibilityEnabled(this);
        boolean a11yConnected = UiAccessibilityService.isConnected();
        boolean otherA11yHost = ServiceGrantStatus.otherHostAccessibilityEnabled(this);
        boolean legacySuiteA11y = ServiceGrantStatus.legacySuiteAccessibilityEnabled(this);
        String a11yName = suiteHost ? "YSuite 统一无障碍" : "界面提示";
        if (!a11yGranted) {
            if (legacySuiteA11y) {
                b.btnAccessibility.setText("迁移到 YSuite 统一无障碍");
            } else if (otherA11yHost) {
                b.btnAccessibility.setText(suiteHost ? "开启 YSuite 无障碍" : "开启当前版界面提示");
            } else {
                b.btnAccessibility.setText(suiteHost ? "开启 YSuite 无障碍" : "开启界面提示");
            }
        } else if (a11yConnected) {
            b.btnAccessibility.setText(otherA11yHost
                    ? a11yName + " ✓ · 另一版也开启"
                    : a11yName + " ✓");
        } else {
            b.btnAccessibility.setText("已授权 · 等待连接");
        }

        long lastEvent = ListenerStateStore.lastEvent(this);
        long lastConnected = ListenerStateStore.lastConnected(this);
        long lastReceived = ListenerStateStore.lastNotificationReceived(this);
        long lastSaved = ListenerStateStore.lastNotificationSaved(this);
        String lastPkg = ListenerStateStore.lastNotificationPackage(this);
        String lastError = ListenerStateStore.lastError(this);
        long lastErrorTime = ListenerStateStore.lastErrorTime(this);

        StringBuilder status = new StringBuilder();
        status.append(notificationName).append("：")
                .append(granted ? (connected ? "已连接" : "权限已授予，正在请求重连") : "未授权");
        if (legacySuiteNotification && !granted) {
            status.append("\n旧版 YNotify 通知监听授权仍存在；请改为开启 YSuite 统一通知监听");
        }
        if (otherNotificationHost) {
            status.append(granted
                    ? "\n⚠ 另一版本的通知监听也已开启，建议只保留当前使用的版本"
                    : "\n另一版本的通知监听已开启；当前版本仍未授权");
        }
        status.append("\n").append(a11yName).append("：")
                .append(!a11yGranted ? "未授权" : a11yConnected ? "已连接" : "已授权，等待系统连接");
        if (legacySuiteA11y && !a11yGranted) {
            status.append("\n旧版 YFloat/YNotify 无障碍授权仍存在；请改为开启 YSuite 统一无障碍服务");
        }
        if (otherA11yHost) {
            status.append(a11yGranted
                    ? "\n⚠ 另一版本的无障碍也已开启，可能造成重复采集，建议只保留当前版本"
                    : "\n另一版本的无障碍已开启；当前版本仍未授权");
        }
        if (lastConnected > 0) status.append("\n最后连接：").append(TimeFormat.full(lastConnected));
        if (lastEvent > 0) status.append("\n最后回调：").append(TimeFormat.full(lastEvent));
        if (lastReceived > 0) {
            status.append("\n最后收到通知：").append(TimeFormat.full(lastReceived));
            if (lastPkg != null && !lastPkg.isEmpty()) status.append(" · ").append(lastPkg);
        }
        if (lastSaved > 0) status.append("\n最后成功保存：").append(TimeFormat.full(lastSaved));
        if (lastError != null && !lastError.isEmpty()) {
            status.append("\n最后错误：");
            if (lastErrorTime > 0) status.append(TimeFormat.full(lastErrorTime)).append(" · ");
            status.append(lastError);
        }
        status.append("\n\n").append(YNotifyApp.runtimeStatus());
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
            b.toolbar.setTitle("按应用查看");
            b.searchBox.setHint("搜索应用或包名");
            b.list.setAdapter(appAdapter);
            observeApps();
        } else if (!settings) {
            b.toolbar.setTitle("YNotify");
            b.searchBox.setHint("搜索应用、标题、内容或包名");
            b.list.setAdapter(eventAdapter);
            observeTimeline();
        } else {
            b.toolbar.setTitle("设置");
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
