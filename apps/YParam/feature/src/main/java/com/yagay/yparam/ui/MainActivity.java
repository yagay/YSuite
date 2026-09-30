package com.yagay.yparam.ui;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.yagay.yparam.YParamApp;
import com.yagay.yparam.data.ConfigRepository;

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
    private EditText search;
    private SwitchCompat includeSystem;
    private SwitchCompat configuredOnly;
    private TextView serviceState;
    private ProgressBar progress;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("YParam");
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
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = dp(12);
        root.setPadding(p, p, p, 0);

        TextView title = new TextView(this);
        title.setText("YParam · 应用参数");
        title.setTextSize(24);
        root.addView(title);

        serviceState = new TextView(this);
        serviceState.setTextSize(13);
        root.addView(serviceState);

        search = new EditText(this);
        search.setHint("搜索应用名称或包名");
        search.setSingleLine(true);
        root.addView(search, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout filters = new LinearLayout(this);
        filters.setOrientation(LinearLayout.HORIZONTAL);
        includeSystem = new SwitchCompat(this);
        includeSystem.setText("系统应用");
        configuredOnly = new SwitchCompat(this);
        configuredOnly.setText("只看已修改");
        filters.addView(includeSystem, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        filters.addView(configuredOnly, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(filters);

        progress = new ProgressBar(this);
        root.addView(progress, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        RecyclerView list = new RecyclerView(this);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new AppAdapter(e -> {
            Intent i = new Intent(this, AppDetailActivity.class);
            i.putExtra("package", e.packageName);
            startActivity(i);
        });
        list.setAdapter(adapter);
        root.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { applyFilter(); }
            @Override public void afterTextChanged(Editable s) {}
        });
        includeSystem.setOnCheckedChangeListener((b, c) -> applyFilter());
        configuredOnly.setOnCheckedChangeListener((b, c) -> applyFilter());
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
                all.clear(); all.addAll(data); progress.setVisibility(android.view.View.GONE); applyFilter();
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
            if (!q.isEmpty() && !e.name.toLowerCase(Locale.ROOT).contains(q) && !e.packageName.toLowerCase(Locale.ROOT).contains(q)) continue;
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
        var s = YParamApp.getService();
        serviceState.setText(s == null ? "LSPosed 服务：未连接（配置不可写入）" : "LSPosed 服务：已连接 · API " + s.getApiVersion());
    }

    @Override public void onServiceChanged() { runOnUiThread(() -> { updateServiceState(); loadApps(); }); }
    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
