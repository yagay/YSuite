package com.yagay.ypower.ui;

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatEditText;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.checkbox.MaterialCheckBox;
import com.yagay.ypower.R;
import com.yagay.ypower.data.ProfileStore;
import com.yagay.ypower.data.RecommendedAppRegistry;
import com.yagay.ypower.root.RootShell;
import com.yagay.ypower.xposed.XposedBridgeManager;
import com.yagay.suite.api.InstalledAppCatalog;
import com.yagay.yui.YView;
import com.yagay.yui.YViewLayout;
import com.yagay.yui.YViewListRow;
import com.yagay.yui.YViewPage;
import com.yagay.yui.YViewStatusTone;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * YUI-hosted, recyclable app selector. Loading identities and disk profiles happens once
 * off the main thread; filtering and scrolling never rebuild the whole Android View tree.
 */
public class MainActivity extends AppCompatActivity {
    private RecyclerView list;
    private AppAdapter adapter;
    private AppCompatEditText search;
    private TextView status;
    private final ExecutorService appLoader = Executors.newSingleThreadExecutor();
    private final ThreadPoolExecutor iconLoader = new ThreadPoolExecutor(
            2, 2, 0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(32), new ThreadPoolExecutor.DiscardOldestPolicy());
    private final AtomicInteger loadVersion = new AtomicInteger();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private Runnable pendingSearch;
    private volatile List<AppRow> snapshot = java.util.Collections.emptyList();

    @Override
    protected void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        buildUi();
        // onResume performs the initial load; do not load twice during startup.
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshRuntimeStatus();
        loadApps(false);
    }

    @Override
    protected void onDestroy() {
        loadVersion.incrementAndGet();
        if (pendingSearch != null) ui.removeCallbacks(pendingSearch);
        appLoader.shutdownNow();
        iconLoader.shutdownNow();
        super.onDestroy();
    }

    private void buildUi() {
        // A bounded window with one scroll owner lets RecyclerView recycle rows. Nesting it
        // in the old YViewScreen ScrollView would force full-height measurement/eager bind.
        YViewPage page = YViewLayout.installPage(this,
                getString(R.string.yp_title), getString(R.string.yp_subtitle));
        LinearLayout root = YViewLayout.contentColumn(this, true);
        page.content.addView(root, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout runtimeCard = YViewLayout.card(root,
                getString(R.string.yp_runtime_title), getString(R.string.yp_runtime_desc));
        status = YViewLayout.statusLine(this, getString(R.string.yp_checking));
        runtimeCard.addView(status);

        LinearLayout appsCard = YViewLayout.card(root,
                getString(R.string.yp_apps_title), getString(R.string.yp_apps_desc));
        search = YViewLayout.searchField(this, getString(R.string.yp_search_hint));
        appsCard.addView(search);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (pendingSearch != null) ui.removeCallbacks(pendingSearch);
                final String value = s == null ? "" : s.toString();
                pendingSearch = () -> filterApps(value);
                ui.postDelayed(pendingSearch, 180L);
            }
            @Override public void afterTextChanged(Editable e) {}
        });

        LinearLayout actions = YViewLayout.actionRow(appsCard);
        Button refresh = YViewLayout.primaryButton(this, getString(R.string.yp_search_refresh));
        refresh.setOnClickListener(v -> { refreshRuntimeStatus(); loadApps(true); });
        YViewLayout.addAction(actions, refresh);
        Button recommended = YViewLayout.secondaryButton(this, getString(R.string.yp_recommended_apps));
        recommended.setOnClickListener(v -> startActivity(new Intent(this, RecommendedAppsActivity.class)));
        YViewLayout.addAction(actions, recommended);

        list = new RecyclerView(this);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setHasFixedSize(false);
        list.setItemAnimator(null); // Avoid re-layout stutter on frequent toggle/search updates.
        adapter = new AppAdapter();
        list.setAdapter(adapter);
        root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1f));
    }

    private void refreshRuntimeStatus() {
        // RootManager may create a libsu session; never probe it on the UI thread.
        appLoader.execute(() -> {
            final boolean root = RootShell.isRootAvailable();
            final boolean xposed = XposedBridgeManager.isReady();
            runOnUiThread(() -> {
                if (isDestroyed() || status == null) return;
                YViewLayout.setStatus(status, getString(
                                R.string.yp_runtime_status,
                                root ? getString(R.string.yp_connected) : getString(R.string.yp_not_authorized),
                                xposed ? getString(R.string.yp_connected) : getString(R.string.yp_not_connected)),
                        root && xposed ? YViewStatusTone.Good : YViewStatusTone.Warning);
            });
        });
    }

    private void loadApps(boolean forceRefresh) {
        final int request = loadVersion.incrementAndGet();
        if (!forceRefresh && !snapshot.isEmpty()) {
            filterApps(search.getText().toString());
            return;
        }
        appLoader.execute(() -> {
            // Shared catalog handles PackageManager caching; do not query it per row.
            List<InstalledAppCatalog.Entry> apps = InstalledAppCatalog.list(this, 0);
            Set<String> enabled = new HashSet<>(ProfileStore.get(this).getEnabledPackages());
            List<AppRow> collected = new ArrayList<>(apps.size());
            for (InstalledAppCatalog.Entry app : apps) {
                if (request != loadVersion.get()) return;
                if (!getPackageName().equals(app.packageName)) {
                    collected.add(new AppRow(app.packageName, app.label,
                            enabled.contains(app.packageName)));
                }
            }
            collected.sort(Comparator.comparing((AppRow a) -> !a.enabled)
                    .thenComparing(a -> a.label, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(a -> a.packageName, String.CASE_INSENSITIVE_ORDER));
            if (request != loadVersion.get()) return;
            snapshot = java.util.Collections.unmodifiableList(collected);
            runOnUiThread(() -> {
                if (!isDestroyed() && request == loadVersion.get()) {
                    filterApps(search.getText().toString());
                }
            });
        });
    }

    private void filterApps(String text) {
        String query = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
        List<AppRow> result = new ArrayList<>();
        for (AppRow app : snapshot) {
            if (query.isEmpty() || app.label.toLowerCase(Locale.ROOT).contains(query)
                    || app.packageName.toLowerCase(Locale.ROOT).contains(query)) result.add(app);
        }
        adapter.submitList(result);
    }

    private void toggle(AppRow row, boolean enabled) {
        List<AppRow> changed = new ArrayList<>(snapshot.size());
        for (AppRow item : snapshot) {
            changed.add(item.packageName.equals(row.packageName)
                    ? new AppRow(item.packageName, item.label, enabled) : item);
        }
        snapshot = java.util.Collections.unmodifiableList(changed);
        filterApps(search.getText().toString());
        appLoader.execute(() -> ProfileStore.get(this).setEnabled(row.packageName, enabled));
    }

    private void openDetails(String packageName) {
        Intent intent = new Intent(this, AppDetailActivity.class);
        intent.putExtra("package", packageName);
        startActivity(intent);
    }

    private YViewListRow createAppRow() {
        return YViewLayout.listItem(this, true);
    }

    private static final class AppRow {
        final String packageName;
        final String label;
        final boolean enabled;
        AppRow(String packageName, String label, boolean enabled) {
            this.packageName = packageName;
            this.label = label;
            this.enabled = enabled;
        }
    }

    private final class AppAdapter extends ListAdapter<AppRow, AppHolder> {
        AppAdapter() {
            super(new DiffUtil.ItemCallback<AppRow>() {
                @Override public boolean areItemsTheSame(@NonNull AppRow a, @NonNull AppRow b) {
                    return a.packageName.equals(b.packageName);
                }
                @Override public boolean areContentsTheSame(@NonNull AppRow a, @NonNull AppRow b) {
                    return a.enabled == b.enabled && a.label.equals(b.label);
                }
            });
        }

        @NonNull @Override
        public AppHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            // YUI still owns icon geometry, typography, checkboxes and action buttons.
            YViewListRow row = createAppRow();
            MaterialCheckBox check = YViewLayout.checkBoxControl(MainActivity.this, false, null);
            Button detail = YViewLayout.secondaryButton(MainActivity.this, getString(R.string.yp_settings));
            row.trailing.addView(check);
            row.trailing.addView(detail);
            return new AppHolder(row, check, detail);
        }

        @Override
        public void onBindViewHolder(@NonNull AppHolder holder, int position) {
            AppRow data = getItem(position);
            holder.boundPackage = data.packageName;
            holder.row.title.setText(data.label);
            boolean recommended = RecommendedAppRegistry.find(data.packageName) != null;
            holder.row.subtitle.setText(data.packageName +
                    (recommended ? "\n" + getString(R.string.yp_recommended_available) : ""));
            holder.check.setOnCheckedChangeListener(null);
            holder.check.setChecked(data.enabled);
            holder.check.setOnCheckedChangeListener((button, checked) -> toggle(data, checked));
            holder.detail.setOnClickListener(v -> openDetails(data.packageName));
            holder.row.root.setOnClickListener(v -> openDetails(data.packageName));

            // No icon decoding while binding on the main thread. Discard callbacks
            // belonging to a previously recycled holder.
            holder.row.icon.setImageDrawable(null);
            final int iconPx = Math.min(96, Math.max(24, YView.listIconSize(MainActivity.this)));
            final String packageName = data.packageName;
            if (iconLoader.isShutdown()) return;
            iconLoader.execute(() -> {
                Drawable icon = InstalledAppCatalog.icon(MainActivity.this, packageName, iconPx);
                runOnUiThread(() -> {
                    if (!isDestroyed() && packageName.equals(holder.boundPackage)) {
                        holder.row.icon.setImageDrawable(icon);
                    }
                });
            });
        }

        @Override
        public void onViewRecycled(@NonNull AppHolder holder) {
            holder.boundPackage = null;
            holder.row.icon.setImageDrawable(null);
            super.onViewRecycled(holder);
        }
    }

    private static final class AppHolder extends RecyclerView.ViewHolder {
        final YViewListRow row;
        final MaterialCheckBox check;
        final Button detail;
        String boundPackage;
        AppHolder(YViewListRow row, MaterialCheckBox check, Button detail) {
            super(row.root);
            this.row = row;
            this.check = check;
            this.detail = detail;
        }
    }
}
