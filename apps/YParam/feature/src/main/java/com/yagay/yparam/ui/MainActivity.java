package com.yagay.yparam.ui;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatEditText;
import androidx.appcompat.widget.SwitchCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.yagay.yparam.R;
import com.yagay.yparam.YParamApp;
import com.yagay.yparam.data.ConfigRepository;
import com.yagay.yui.YViewLayout;
import com.yagay.yui.YViewStatusTone;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Executors;

public final class MainActivity extends AppCompatActivity implements YParamApp.ServiceObserver {
    private final List<AppEntry> all = new ArrayList<>();
    private AppAdapter adapter;
    private AppCompatEditText search;
    private SwitchCompat includeSystem;
    private SwitchCompat configuredOnly;
    private TextView serviceState;
    private ProgressBar progress;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        YParamApp.addObserver(this);
        loadApps();
        updateServiceState();
    }

    @Override protected void onDestroy() {
        YParamApp.removeObserver(this);
        super.onDestroy();
    }

    private void buildUi() {
        LinearLayout root = YViewLayout.installFixed(
                this,
                getString(R.string.yparam_title),
                getString(R.string.yparam_subtitle));

        LinearLayout filters = YViewLayout.card(
                root,
                getString(R.string.yparam_filters_title),
                getString(R.string.yparam_filters_desc));

        serviceState = YViewLayout.statusLine(this, getString(R.string.yparam_service_checking));
        filters.addView(serviceState);

        search = YViewLayout.searchField(this, getString(R.string.yparam_search_hint));
        filters.addView(
                search,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));

        includeSystem = YViewLayout.switchRow(
                filters,
                getString(R.string.yparam_system_apps),
                getString(R.string.yparam_system_apps_desc),
                false,
                (button, checked) -> applyFilter());
        configuredOnly = YViewLayout.switchRow(
                filters,
                getString(R.string.yparam_modified_only),
                getString(R.string.yparam_modified_only_desc),
                false,
                (button, checked) -> applyFilter());

        progress = YViewLayout.progressIndicator(this);
        filters.addView(
                progress,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));

        RecyclerView list = new RecyclerView(this);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setClipToPadding(false);
        adapter = new AppAdapter(e -> {
            Intent i = new Intent(this, AppDetailActivity.class);
            i.putExtra("package", e.packageName);
            startActivity(i);
        });
        list.setAdapter(adapter);
        root.addView(
                list,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        0,
                        1f));

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { applyFilter(); }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void loadApps() {
        progress.setVisibility(android.view.View.VISIBLE);
        Executors.newSingleThreadExecutor().execute(() -> {
            PackageManager pm = getPackageManager();
            Set<String> configured = new HashSet<>(ConfigRepository.configuredPackages());
            List<AppEntry> data = new ArrayList<>();
            for (ApplicationInfo ai : pm.getInstalledApplications(PackageManager.MATCH_DISABLED_COMPONENTS)) {
                if (getPackageName().equals(ai.packageName)) continue;
                boolean sys = (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                String name = String.valueOf(pm.getApplicationLabel(ai));
                data.add(new AppEntry(name, ai.packageName, pm.getApplicationIcon(ai), sys, configured.contains(ai.packageName)));
            }
            Collator collator = Collator.getInstance(Locale.getDefault());
            data.sort(Comparator.comparing((AppEntry a) -> !a.configured).thenComparing(a -> a.name, collator));
            runOnUiThread(() -> {
                all.clear();
                all.addAll(data);
                progress.setVisibility(android.view.View.GONE);
                applyFilter();
            });
        });
    }

    private void applyFilter() {
        if (adapter == null) return;
        String q = search == null ? "" : search.getText().toString().trim().toLowerCase(Locale.ROOT);
        boolean sys = includeSystem != null && includeSystem.isChecked();
        boolean only = configuredOnly != null && configuredOnly.isChecked();
        List<AppEntry> out = new ArrayList<>();
        for (AppEntry e : all) {
            if (!sys && e.system) continue;
            if (only && !e.configured) continue;
            if (!q.isEmpty()
                    && !e.name.toLowerCase(Locale.ROOT).contains(q)
                    && !e.packageName.toLowerCase(Locale.ROOT).contains(q)) {
                continue;
            }
            out.add(e);
        }
        adapter.submit(out);
    }

    @Override protected void onResume() {
        super.onResume();
        if (!all.isEmpty()) loadApps();
        updateServiceState();
    }

    private void updateServiceState() {
        if (serviceState == null) return;
        var service = YParamApp.getService();
        if (service == null) {
            YViewLayout.setStatus(
                    serviceState,
                    getString(R.string.yparam_service_disconnected),
                    YViewStatusTone.Warning);
        } else {
            YViewLayout.setStatus(
                    serviceState,
                    getString(R.string.yparam_service_connected, service.getApiVersion()),
                    YViewStatusTone.Good);
        }
    }

    @Override public void onServiceChanged() {
        runOnUiThread(() -> {
            updateServiceState();
            loadApps();
        });
    }
}
