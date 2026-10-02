package com.yagay.ypower.ui;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.yagay.ypower.R;
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
                getString(R.string.yp_recommended_title),
                getString(R.string.yp_recommended_subtitle)
        );
        LinearLayout root = screen.getContent();
        root.addView(YViewLayout.statusLine(
                this,
                XposedBridgeManager.isReady()
                        ? getString(R.string.yp_recommended_lsposed_connected)
                        : getString(R.string.yp_recommended_lsposed_disconnected),
                XposedBridgeManager.isReady() ? YViewStatusTone.Good : YViewStatusTone.Warning
        ));
        YViewLayout.sectionHeader(
                root,
                getString(R.string.yp_recommended_section),
                getString(R.string.yp_recommended_section_desc));
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
            list.addView(YViewLayout.emptyState(this, getString(R.string.yp_no_recommendations)));
        }
    }

    private void addPreset(RecommendedAppPreset preset) {
        LinearLayout card = YViewLayout.card(
                list,
                preset.displayName,
                preset.packageName
        );
        card.addView(YViewLayout.detailBlock(this, getString(R.string.yp_recommendation_reason), preset.reason));
        card.addView(YViewLayout.detailBlock(this, getString(R.string.yp_recommended_hooks), preset.hookSummary()));

        LinearLayout buttons = YViewLayout.actionRow(card);
        Button apply = YViewLayout.primaryButton(this, getString(R.string.yp_apply_recommended_short));
        apply.setOnClickListener(v -> applyPreset(preset));
        Button detail = YViewLayout.secondaryButton(this, getString(R.string.yp_settings));
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
                    ? getString(R.string.yp_scope_requested)
                    : getString(R.string.yp_scope_saved_for_later);
            Toast.makeText(
                    this,
                    getString(R.string.yp_recommended_applied, preset.displayName, scope, result.summary()),
                    Toast.LENGTH_LONG
            ).show();
            populate();
        }));
    }
}
