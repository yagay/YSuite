package com.yagay.ypower.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;
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
        YViewScreen screen = YViewLayout.install(this, "应用增强", packageName);
        LinearLayout root = screen.getContent();

        LinearLayout stateCard = YViewLayout.card(root, "当前状态", "增强开关、Root 规则和 LSPosed Scope 统一从这里管理");
        stateCard.addView(YViewLayout.statusLine(
                this,
                profile.enabled ? "YPower 增强已启用" : "YPower 增强未启用",
                profile.enabled ? YViewStatusTone.Good : YViewStatusTone.Neutral
        ));
        stateCard.addView(YViewLayout.statusLine(
                this,
                XposedBridgeManager.isReady() ? "LSPosed Service 已连接" : "LSPosed Service 未连接",
                XposedBridgeManager.isReady() ? YViewStatusTone.Good : YViewStatusTone.Warning
        ));

        CheckBox enabled = addCheck(root, "启用 YPower 增强", profile.enabled);
        enabled.setOnCheckedChangeListener((v, checked) -> {
            profile.enabled = checked;
            ProfileStore.get(this).setEnabled(packageName, checked);
        });

        RecommendedAppPreset recommendedPreset = RecommendedAppRegistry.find(packageName);
        if (recommendedPreset != null) {
            YViewLayout.sectionHeader(root, "推荐配置", "根据已知兼容需求生成，可随时在下方手动调整。");
            LinearLayout recommended = YViewLayout.card(
                    root,
                    recommendedPreset.displayName,
                    "推荐 Hook：" + recommendedPreset.hookSummary()
            );
            recommended.addView(YViewLayout.detailBlock(this, "推荐原因", recommendedPreset.reason));
            recommended.addView(YViewLayout.statusLine(
                    this,
                    XposedBridgeManager.isReady() ? "应用后会同步请求 LSPosed Scope" : "LSPosed 未连接；配置先保存，连接后再同步",
                    XposedBridgeManager.isReady() ? YViewStatusTone.Good : YViewStatusTone.Warning
            ));
            Button applyRecommended = YViewLayout.primaryButton(this, "应用推荐配置并同步 LSPosed");
            applyRecommended.setOnClickListener(v -> {
                profile = ProfileStore.get(this).applyRecommendedPreset(recommendedPreset);
                EnhancementEngine.applyAsync(this, profile, result -> runOnUiThread(() -> {
                    String scope = XposedBridgeManager.isReady()
                            ? "LSPosed Scope 已请求同步"
                            : "LSPosed 未连接；连接后会自动再次请求 Scope";
                    Toast.makeText(this,
                            "推荐配置已应用\n" + scope + "\n" + result.summary(),
                            Toast.LENGTH_LONG).show();
                    recreate();
                }));
            });
            recommended.addView(applyRecommended);
        }

        YViewLayout.sectionHeader(root, "无需目标 App Hook", "由 Root/系统侧完成，不依赖目标进程内 Hook。");
        CheckBox doze = addCheck(root, "Doze 白名单", profile.dozeWhitelist);
        CheckBox bg = addCheck(root, "后台 AppOps 放宽", profile.backgroundOps);
        CheckBox standby = addCheck(root, "App Standby Active", profile.standbyActive);
        CheckBox data = addCheck(root, "后台数据白名单", profile.backgroundData);
        CheckBox grant = addCheck(root, "自动授予可正常 grant 的危险权限", profile.autoGrantDangerous);

        YViewLayout.sectionHeader(root, "目标进程兼容层", "需要 LSPosed；用于兼容特定应用环境检查。");
        CheckBox system = addCheck(root, "模拟 System App 身份", profile.simulateSystemApp);
        CheckBox perm = addCheck(root, "模拟权限状态（默认位置权限；不等于真正 privileged 权限）", profile.simulatePermissions);

        YViewLayout.sectionHeader(root, "目标进程诊断追踪", "默认只观察并记录，不修改原始检测结果。");
        CheckBox packageScan = addCheck(root, "包扫描追踪（Magisk / KernelSU / LSPosed / Frida 等）", profile.tracePackageScan);
        CheckBox files = addCheck(root, "文件与 /proc 访问追踪", profile.traceFiles);
        CheckBox commands = addCheck(root, "命令执行与主动退出追踪", profile.traceCommands);
        CheckBox properties = addCheck(root, "系统属性 / Boot 状态查询追踪", profile.traceProperties);
        CheckBox permissions = addCheck(root, "权限状态查询追踪（只记录真实结果）", profile.tracePermissions);
        CheckBox debugger = addCheck(root, "调试器状态检测追踪", profile.traceDebugger);
        CheckBox exceptions = addCheck(root, "异常传播追踪（Java / Coroutine / RxJava，只观察）", profile.traceExceptions);
        CheckBox securityApis = addCheck(root,
                "现代安全 API 追踪（Attestation / Play Integrity / 自完整性，只观察）",
                profile.traceSecurityApis);
        CheckBox nativeTrace = addCheck(root, "Native 深度追踪（ByteHook，风险更高）", profile.traceNative);
        CheckBox syscallTrace = addCheck(root,
                "Raw syscall 实验追踪（strace/ptrace；可能触发反调试，默认关闭）",
                profile.traceSyscalls);
        CheckBox stacks = addCheck(root, "记录短调用栈", profile.traceStacks);

        TextView note = YViewLayout.statusLine(
                this,
                "修改 Hook 开关后需要重新启动目标 App，新的 Hook 组合才会重新安装。",
                YViewStatusTone.Warning
        );
        root.addView(note);

        LinearLayout actions = YViewLayout.actionRow(root);
        Button apply = YViewLayout.primaryButton(this, "保存并应用增强");
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
        Button diagnose = YViewLayout.secondaryButton(this, "打开诊断中心");
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

    private CheckBox addCheck(LinearLayout root, String text, boolean checked) {
        CheckBox box = new CheckBox(this);
        box.setText(text);
        box.setChecked(checked);
        box.setPadding(0, dp(6), 0, dp(6));
        root.addView(box);
        return box;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
