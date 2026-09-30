package com.yagay.ypower.ui;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.yagay.ypower.data.ProfileStore;
import com.yagay.ypower.data.RecommendedAppRegistry;
import com.yagay.ypower.model.AppProfile;
import com.yagay.ypower.model.RecommendedAppPreset;
import com.yagay.ypower.root.EnhancementEngine;
import com.yagay.ypower.xposed.XposedBridgeManager;
import com.yagay.yui.YViewLayout;
import com.yagay.yui.YViewScreen;
import com.yagay.yui.YViewStatusTone;

public class RecommendedAppsActivity extends AppCompatActivity {
    private LinearLayout list;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        populate();
    }

    private void buildUi() {
        YViewScreen screen = YViewLayout.install(
                this,
                "推荐应用",
                "只显示已安装且 YPower 有明确推荐规则的应用"
        );
        LinearLayout root = screen.getContent();
        root.addView(YViewLayout.statusLine(
                this,
                XposedBridgeManager.isReady()
                        ? "LSPosed Service 已连接；一键推荐会同步 Scope"
                        : "LSPosed Service 未连接；推荐配置会先保存",
                XposedBridgeManager.isReady() ? YViewStatusTone.Good : YViewStatusTone.Warning
        ));
        YViewLayout.sectionHeader(root, "可用推荐", "应用后会启用 YPower、保存推荐 Hook，并请求 LSPosed Scope。");
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        root.addView(list);
    }

    @SuppressWarnings("deprecation")
    private void populate() {
        list.removeAllViews();
        int count = 0;
        PackageManager pm = getPackageManager();

        for (RecommendedAppPreset preset : RecommendedAppRegistry.BUILTIN) {
            try {
                pm.getPackageInfo(preset.packageName, 0);
            } catch (PackageManager.NameNotFoundException e) {
                continue;
            }
            count++;
            addPreset(preset);
        }

        if (count == 0) {
            list.addView(YViewLayout.emptyState(this, "当前已安装应用中暂时没有命中内置推荐规则。"));
        }
    }

    private void addPreset(RecommendedAppPreset preset) {
        LinearLayout card = YViewLayout.card(
                list,
                preset.displayName,
                preset.packageName
        );
        card.addView(YViewLayout.detailBlock(this, "推荐原因", preset.reason));
        card.addView(YViewLayout.detailBlock(this, "推荐 Hook", preset.hookSummary()));

        LinearLayout buttons = YViewLayout.actionRow(card);
        Button apply = YViewLayout.primaryButton(this, "一键推荐 + LSPosed");
        apply.setOnClickListener(v -> applyPreset(preset));
        Button detail = YViewLayout.secondaryButton(this, "设置");
        detail.setOnClickListener(v -> {
            Intent i = new Intent(this, AppDetailActivity.class);
            i.putExtra("package", preset.packageName);
            startActivity(i);
        });
        YViewLayout.addAction(buttons, apply);
        YViewLayout.addAction(buttons, detail, 0.45f);
    }

    private void applyPreset(RecommendedAppPreset preset) {
        AppProfile profile = ProfileStore.get(this).applyRecommendedPreset(preset);
        EnhancementEngine.applyAsync(this, profile, result -> runOnUiThread(() -> {
            String scope = XposedBridgeManager.isReady()
                    ? "已请求同步到 LSPosed Scope"
                    : "LSPosed 未连接；配置已保存，连接后会再次请求 Scope";
            Toast.makeText(
                    this,
                    preset.displayName + "：推荐配置已应用\n" + scope + "\n" + result.summary(),
                    Toast.LENGTH_LONG
            ).show();
            populate();
        }));
    }
}
