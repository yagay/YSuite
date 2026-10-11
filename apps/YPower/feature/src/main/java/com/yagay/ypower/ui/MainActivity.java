package com.yagay.ypower.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatEditText;

import com.yagay.ypower.R;
import com.yagay.suite.api.InstalledAppCatalog;
import com.yagay.ypower.data.ProfileStore;
import com.yagay.ypower.data.RecommendedAppRegistry;
import com.yagay.ypower.root.RootShell;
import com.yagay.ypower.xposed.XposedBridgeManager;
import com.yagay.yui.YView;
import com.yagay.yui.YViewLayout;
import com.yagay.yui.YViewScreen;
import com.yagay.yui.YViewListRow;
import com.yagay.yui.YViewStatusTone;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class MainActivity extends AppCompatActivity {
    private LinearLayout list;
    private AppCompatEditText search;
    private TextView status;
    private final ExecutorService appLoader = Executors.newSingleThreadExecutor();
    private final AtomicInteger loadVersion = new AtomicInteger();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        loadApps("");
    }

    @Override
    protected void onDestroy() {
        loadVersion.incrementAndGet();
        appLoader.shutdownNow();
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshRuntimeStatus();
        if (list != null) loadApps(search == null ? "" : search.getText().toString());
    }

    private void buildUi() {
        YViewScreen screen = YViewLayout.install(
                this,
                getString(R.string.yp_title),
                getString(R.string.yp_subtitle));
        LinearLayout root = screen.getContent();

        LinearLayout runtimeCard = YViewLayout.card(
                root,
                getString(R.string.yp_runtime_title),
                getString(R.string.yp_runtime_desc));
        status = YViewLayout.statusLine(this, getString(R.string.yp_checking));
        runtimeCard.addView(status);
        refreshRuntimeStatus();

        LinearLayout appsCard = YViewLayout.card(
                root,
                getString(R.string.yp_apps_title),
                getString(R.string.yp_apps_desc));
        search = YViewLayout.searchField(this, getString(R.string.yp_search_hint));
        appsCard.addView(search);

        LinearLayout actions = YViewLayout.actionRow(appsCard);
        Button refresh = YViewLayout.primaryButton(this, getString(R.string.yp_search_refresh));
        refresh.setOnClickListener(v -> {
            refreshRuntimeStatus();
            loadApps(search.getText().toString());
        });
        YViewLayout.addAction(actions, refresh);

        Button recommended = YViewLayout.secondaryButton(this, getString(R.string.yp_recommended_apps));
        recommended.setOnClickListener(v -> startActivity(new Intent(this, RecommendedAppsActivity.class)));
        YViewLayout.addAction(actions, recommended);

        list = YViewLayout.contentColumn(this, false);
        appsCard.addView(list, new LinearLayout.LayoutParams(-1, -2));
    }

    private void refreshRuntimeStatus() {
        if (status == null) return;
        boolean root = RootShell.isRootAvailable();
        boolean xposed = XposedBridgeManager.isReady();
        YViewLayout.setStatus(
                status,
                getString(
                        R.string.yp_runtime_status,
                        root ? getString(R.string.yp_connected) : getString(R.string.yp_not_authorized),
                        xposed ? getString(R.string.yp_connected) : getString(R.string.yp_not_connected)),
                root && xposed ? YViewStatusTone.Good : YViewStatusTone.Warning);
    }

    private void loadApps(String query) {
        final int request = loadVersion.incrementAndGet();
        final String q = query == null ? "" : query.toLowerCase(Locale.ROOT).trim();
        // PackageManager and label lookup can be slow on large app inventories.
        // Always load on our serial worker and let the shared catalog reuse a snapshot.
        appLoader.execute(() -> {
            ProfileStore store = ProfileStore.get(this);
            List<InstalledAppCatalog.Entry> apps = new ArrayList<>(
                    InstalledAppCatalog.list(this, 0));
            apps.removeIf(a -> getPackageName().equals(a.packageName));
            apps.sort(
                    Comparator
                            .comparing((InstalledAppCatalog.Entry a) -> !store.getProfile(a.packageName).enabled)
                            .thenComparing(a -> a.label, String.CASE_INSENSITIVE_ORDER)
                            .thenComparing(a -> a.packageName, String.CASE_INSENSITIVE_ORDER)
            );
            List<InstalledAppCatalog.Entry> visible = new ArrayList<>();
            for (InstalledAppCatalog.Entry app : apps) {
                if (!q.isEmpty()
                        && !app.label.toLowerCase(Locale.ROOT).contains(q)
                        && !app.packageName.toLowerCase(Locale.ROOT).contains(q)) {
                    continue;
                }
                visible.add(app);
            }
            runOnUiThread(() -> {
                if (isDestroyed() || request != loadVersion.get()) return;
                list.removeAllViews();
                for (InstalledAppCatalog.Entry app : visible) {
                    addRow(app.label, app.packageName);
                }
            });
        });
    }

    private void addRow(String label, String packageName) {
        YViewListRow row = YViewLayout.listItem(this, false);
        boolean recommended = RecommendedAppRegistry.find(packageName) != null;
        row.title.setText(label);
        row.subtitle.setText(packageName
                + (recommended ? "\n" + getString(R.string.yp_recommended_available) : ""));

        com.google.android.material.checkbox.MaterialCheckBox enabled =
                YViewLayout.checkBoxControl(
                        this,
                        ProfileStore.get(this).getProfile(packageName).enabled,
                        null);
        enabled.setOnCheckedChangeListener((buttonView, isChecked) -> {
            ProfileStore.get(this).setEnabled(packageName, isChecked);
            buttonView.post(() -> loadApps(search == null ? "" : search.getText().toString()));
        });
        row.trailing.addView(enabled);

        Button detail = YViewLayout.secondaryButton(this, getString(R.string.yp_settings));
        detail.setOnClickListener(v -> openDetails(packageName));
        row.trailing.addView(detail);
        row.root.setOnClickListener(v -> openDetails(packageName));
        list.addView(row.root);
    }

    private void openDetails(String packageName) {
        Intent i = new Intent(this, AppDetailActivity.class);
        i.putExtra("package", packageName);
        startActivity(i);
    }
}
