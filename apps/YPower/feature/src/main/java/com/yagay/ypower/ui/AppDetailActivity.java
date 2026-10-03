package com.yagay.ypower.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.checkbox.MaterialCheckBox;

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

public class AppDetailActivity extends AppCompatActivity {
    private String packageName;
    private AppProfile profile;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        packageName = getIntent().getStringExtra("package");
        if (packageName == null || packageName.isBlank()) { finish(); return; }
        profile = ProfileStore.get(this).getProfile(packageName);
        buildUi();
    }

    private void buildUi() {
        YViewScreen screen = YViewLayout.install(this, getString(R.string.yp_detail_title), packageName);
        LinearLayout root = screen.getContent();

        LinearLayout stateCard = YViewLayout.card(
                root,
                getString(R.string.yp_current_status),
                getString(R.string.yp_current_status_desc));
        stateCard.addView(YViewLayout.statusLine(
                this,
                profile.enabled ? getString(R.string.yp_enabled) : getString(R.string.yp_disabled),
                profile.enabled ? YViewStatusTone.Good : YViewStatusTone.Neutral
        ));
        stateCard.addView(YViewLayout.statusLine(
                this,
                XposedBridgeManager.isReady()
                        ? getString(R.string.yp_lsposed_service_connected)
                        : getString(R.string.yp_lsposed_service_disconnected),
                XposedBridgeManager.isReady() ? YViewStatusTone.Good : YViewStatusTone.Warning
        ));

        MaterialCheckBox enabled = addCheck(root, getString(R.string.yp_enable_enhancements), profile.enabled);
        enabled.setOnCheckedChangeListener((v, checked) -> {
            profile.enabled = checked;
            ProfileStore.get(this).setEnabled(packageName, checked);
        });

        RecommendedAppPreset recommendedPreset = RecommendedAppRegistry.find(packageName);
        if (recommendedPreset != null) {
            YViewLayout.sectionHeader(
                    root,
                    getString(R.string.yp_recommended_config),
                    getString(R.string.yp_recommended_config_desc));
            LinearLayout recommended = YViewLayout.card(
                    root,
                    recommendedPreset.displayName,
                    getString(R.string.yp_recommended_hook_format, recommendedPreset.hookSummary())
            );
            recommended.addView(YViewLayout.detailBlock(
                    this,
                    getString(R.string.yp_recommendation_reason),
                    recommendedPreset.reason));
            recommended.addView(YViewLayout.statusLine(
                    this,
                    XposedBridgeManager.isReady()
                            ? getString(R.string.yp_apply_scope_ready)
                            : getString(R.string.yp_apply_scope_waiting),
                    XposedBridgeManager.isReady() ? YViewStatusTone.Good : YViewStatusTone.Warning
            ));
            Button applyRecommended = YViewLayout.primaryButton(
                    this,
                    getString(R.string.yp_apply_recommended));
            applyRecommended.setOnClickListener(v -> {
                profile = ProfileStore.get(this).applyRecommendedPreset(recommendedPreset);
                EnhancementEngine.applyAsync(this, profile, result -> runOnUiThread(() -> {
                    String scope = XposedBridgeManager.isReady()
                            ? getString(R.string.yp_scope_sync_requested)
                            : getString(R.string.yp_scope_sync_later);
                    Toast.makeText(
                            this,
                            getString(R.string.yp_recommended_apply_result, scope, result.summary()),
                            Toast.LENGTH_LONG).show();
                    recreate();
                }));
            });
            recommended.addView(applyRecommended);
        }

        YViewLayout.sectionHeader(
                root,
                getString(R.string.yp_no_target_hook),
                getString(R.string.yp_no_target_hook_desc));
        MaterialCheckBox doze = addCheck(root, getString(R.string.yp_doze_whitelist), profile.dozeWhitelist);
        MaterialCheckBox bg = addCheck(root, getString(R.string.yp_background_appops), profile.backgroundOps);
        MaterialCheckBox standby = addCheck(root, getString(R.string.yp_standby_active), profile.standbyActive);
        MaterialCheckBox data = addCheck(root, getString(R.string.yp_background_data), profile.backgroundData);
        MaterialCheckBox grant = addCheck(root, getString(R.string.yp_auto_grant), profile.autoGrantDangerous);

        YViewLayout.sectionHeader(
                root,
                getString(R.string.yp_target_compat),
                getString(R.string.yp_target_compat_desc));
        MaterialCheckBox system = addCheck(root, getString(R.string.yp_simulate_system), profile.simulateSystemApp);
        MaterialCheckBox perm = addCheck(root, getString(R.string.yp_simulate_permissions), profile.simulatePermissions);

        YViewLayout.sectionHeader(
                root,
                getString(R.string.yp_trace_title),
                getString(R.string.yp_trace_desc));
        MaterialCheckBox packageScan = addCheck(root, getString(R.string.yp_trace_packages), profile.tracePackageScan);
        MaterialCheckBox files = addCheck(root, getString(R.string.yp_trace_files), profile.traceFiles);
        MaterialCheckBox commands = addCheck(root, getString(R.string.yp_trace_commands), profile.traceCommands);
        MaterialCheckBox properties = addCheck(root, getString(R.string.yp_trace_properties), profile.traceProperties);
        MaterialCheckBox permissions = addCheck(root, getString(R.string.yp_trace_permissions), profile.tracePermissions);
        MaterialCheckBox debugger = addCheck(root, getString(R.string.yp_trace_debugger), profile.traceDebugger);
        MaterialCheckBox exceptions = addCheck(root, getString(R.string.yp_trace_exceptions), profile.traceExceptions);
        MaterialCheckBox securityApis = addCheck(root, getString(R.string.yp_trace_security), profile.traceSecurityApis);
        MaterialCheckBox nativeTrace = addCheck(root, getString(R.string.yp_trace_native), profile.traceNative);
        MaterialCheckBox syscallTrace = addCheck(root, getString(R.string.yp_trace_syscalls), profile.traceSyscalls);
        MaterialCheckBox stacks = addCheck(root, getString(R.string.yp_trace_stacks), profile.traceStacks);

        TextView note = YViewLayout.statusLine(
                this,
                getString(R.string.yp_restart_target_note),
                YViewStatusTone.Warning
        );
        root.addView(note);

        LinearLayout actions = YViewLayout.actionRow(root);
        Button apply = YViewLayout.primaryButton(this, getString(R.string.yp_save_apply));
        apply.setOnClickListener(v -> {
            profile.enabled = enabled.isChecked();
            profile.dozeWhitelist = doze.isChecked();
            profile.backgroundOps = bg.isChecked();
            profile.standbyActive = standby.isChecked();
            profile.backgroundData = data.isChecked();
            profile.autoGrantDangerous = grant.isChecked();
            profile.simulateSystemApp = system.isChecked();
            profile.simulatePermissions = perm.isChecked();
            profile.tracePackageScan = packageScan.isChecked();
            profile.traceFiles = files.isChecked();
            profile.traceCommands = commands.isChecked();
            profile.traceProperties = properties.isChecked();
            profile.tracePermissions = permissions.isChecked();
            profile.traceDebugger = debugger.isChecked();
            profile.traceExceptions = exceptions.isChecked();
            profile.traceSecurityApis = securityApis.isChecked();
            profile.traceNative = nativeTrace.isChecked();
            profile.traceSyscalls = syscallTrace.isChecked();
            profile.traceStacks = stacks.isChecked();
            profile.traceJava = profile.anyTraceEnabled();
            profile.traceEnvironment = profile.anyTraceEnabled();
            save();
            EnhancementEngine.applyAsync(this, profile, result -> runOnUiThread(() ->
                    Toast.makeText(this, result.summary(), Toast.LENGTH_LONG).show()));
        });
        Button diagnose = YViewLayout.secondaryButton(this, getString(R.string.yp_open_diagnostics));
        diagnose.setOnClickListener(v -> {
            Intent i = new Intent(this, DiagnosticActivity.class);
            i.putExtra("package", packageName);
            startActivity(i);
        });
        YViewLayout.addAction(actions, apply);
        YViewLayout.addAction(actions, diagnose);
    }

    private void save() {
        ProfileStore.get(this).save(profile);
        if (profile.enabled) XposedBridgeManager.requestScope(packageName);
    }

    private MaterialCheckBox addCheck(LinearLayout root, String text, boolean checked) {
        LinearLayout row = YViewLayout.listRow(this);
        TextView title = YViewLayout.listTitle(this);
        title.setText(text);
        row.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));

        MaterialCheckBox box = new MaterialCheckBox(this);
        box.setChecked(checked);
        row.addView(box);
        row.setOnClickListener(v -> box.setChecked(!box.isChecked()));
        root.addView(row);
        return box;
    }
}
