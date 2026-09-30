package com.yagay.ypower.ui;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatEditText;

import com.yagay.ypower.data.ProfileStore;
import com.yagay.ypower.data.RecommendedAppRegistry;
import com.yagay.ypower.root.RootShell;
import com.yagay.ypower.xposed.XposedBridgeManager;
import com.yagay.yui.YView;
import com.yagay.yui.YViewLayout;
import com.yagay.yui.YViewScreen;
import com.yagay.yui.YViewStatusTone;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private LinearLayout list;
    private AppCompatEditText search;
    private TextView status;
    private final List<ApplicationInfo> apps = new ArrayList<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        loadApps("");
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
                "YPower",
                "应用增强 · 运行时参数、检测与诊断");
        LinearLayout root = screen.getContent();

        LinearLayout runtimeCard = YViewLayout.card(
                root,
                "运行环境",
                "Root 和 LSPosed 状态由同一套 YSuite 运行时框架管理。");
        status = YViewLayout.statusLine(this, "正在检测…");
        runtimeCard.addView(status);
        refreshRuntimeStatus();

        LinearLayout appsCard = YViewLayout.card(
                root,
                "应用列表",
                "已启用应用优先显示；点击应用或设置按钮进入详细配置。");
        search = YViewLayout.searchField(this, "搜索应用或包名");
        appsCard.addView(search);

        LinearLayout actions = YViewLayout.actionRow(appsCard);
        Button refresh = YViewLayout.primaryButton(this, "搜索 / 刷新");
        refresh.setOnClickListener(v -> {
            refreshRuntimeStatus();
            loadApps(search.getText().toString());
        });
        YViewLayout.addAction(actions, refresh);

        Button recommended = YViewLayout.secondaryButton(this, "推荐应用");
        recommended.setOnClickListener(v -> startActivity(new Intent(this, RecommendedAppsActivity.class)));
        YViewLayout.addAction(actions, recommended);

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        appsCard.addView(list, new LinearLayout.LayoutParams(-1, -2));
    }

    private void refreshRuntimeStatus() {
        if (status == null) return;
        boolean root = RootShell.isRootAvailable();
        boolean xposed = XposedBridgeManager.isReady();
        YViewLayout.setStatus(
                status,
                "Root：" + (root ? "已连接" : "未授权")
                        + "    LSPosed：" + (xposed ? "已连接" : "未连接"),
                root && xposed ? YViewStatusTone.Good : YViewStatusTone.Warning);
    }

    @SuppressWarnings("deprecation")
    private void loadApps(String query) {
        PackageManager pm = getPackageManager();
        if (apps.isEmpty()) {
            apps.addAll(pm.getInstalledApplications(0));
            apps.removeIf(a -> getPackageName().equals(a.packageName));
        }

        ProfileStore store = ProfileStore.get(this);
        apps.sort(
                Comparator
                        .comparing((ApplicationInfo a) -> !store.getProfile(a.packageName).enabled)
                        .thenComparing(
                                a -> String.valueOf(pm.getApplicationLabel(a)),
                                String.CASE_INSENSITIVE_ORDER
                        )
                        .thenComparing(a -> a.packageName, String.CASE_INSENSITIVE_ORDER)
        );

        String q = query == null ? "" : query.toLowerCase(Locale.ROOT).trim();
        list.removeAllViews();
        for (ApplicationInfo app : apps) {
            String label = String.valueOf(pm.getApplicationLabel(app));
            if (!q.isEmpty()
                    && !label.toLowerCase(Locale.ROOT).contains(q)
                    && !app.packageName.toLowerCase(Locale.ROOT).contains(q)) {
                continue;
            }
            addRow(label, app.packageName);
        }
    }

    private void addRow(String label, String packageName) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, YView.dp(this, 5), 0, YView.dp(this, 5));

        CheckBox enabled = new CheckBox(this);
        enabled.setChecked(ProfileStore.get(this).getProfile(packageName).enabled);
        enabled.setOnCheckedChangeListener((buttonView, isChecked) -> {
            ProfileStore.get(this).setEnabled(packageName, isChecked);
            buttonView.post(() -> loadApps(search == null ? "" : search.getText().toString()));
        });
        row.addView(enabled);

        TextView text = new TextView(this);
        boolean recommended = RecommendedAppRegistry.find(packageName) != null;
        text.setText((recommended ? "★ " : "") + label + "\n" + packageName
                + (recommended ? "\n推荐配置可用" : ""));
        YView.styleBody(text);
        text.setTextSize(15f);
        text.setOnClickListener(v -> openDetails(packageName));
        row.addView(text, new LinearLayout.LayoutParams(0, -2, 1));

        Button detail = YViewLayout.secondaryButton(this, "设置");
        detail.setOnClickListener(v -> openDetails(packageName));
        row.addView(detail);
        list.addView(row);
    }

    private void openDetails(String packageName) {
        Intent i = new Intent(this, AppDetailActivity.class);
        i.putExtra("package", packageName);
        startActivity(i);
    }
}
