package com.yagay.YSuite

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.lifecycleScope
import com.yagay.suite.core.FeatureRegistry
import com.yagay.suite.core.FeatureSpec
import com.yagay.suite.core.FeatureStateStore
import com.yagay.suite.core.RootManager
import com.yagay.suite.core.SuiteCrashTracker
import com.yagay.suite.core.SuiteLog
import com.yagay.suite.core.SuiteXposedServiceBroker
import com.yagay.yui.YActionRow
import com.yagay.yui.YComposeActivity
import com.yagay.yui.YDimens
import com.yagay.yui.YPluginHeader
import com.yagay.yui.YPluginList
import com.yagay.yui.YPluginScaffold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : YComposeActivity() {
    @Composable
    override fun YContent() {
        FeatureManagerScreen()
    }

    override fun onResume() {
        super.onResume()
        SuiteCrashTracker.markActiveFeature(this, null)
    }

    private fun exportDiagnostic(modules: Set<String>?, label: String) {
        Toast.makeText(this, "正在收集 $label 诊断信息…", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { SuiteLog.export(this@MainActivity, modules) }
            }
            result.onSuccess { path ->
                Toast.makeText(this@MainActivity, "$label 已保存：$path", Toast.LENGTH_LONG).show()
            }.onFailure {
                Toast.makeText(
                    this@MainActivity,
                    "$label 保存失败：${it.javaClass.simpleName}",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    @Composable
    private fun FeatureManagerScreen() {
        val features = remember { FeatureRegistry.included() }
        val store = remember { FeatureStateStore(this) }
        val enabled = remember {
            mutableStateMapOf<String, Boolean>().apply {
                features.forEach { put(it.id, store.isEnabled(it)) }
            }
        }
        var rootAvailable by remember { mutableStateOf<Boolean?>(null) }
        var xposedStatus by remember { mutableStateOf(SuiteXposedServiceBroker.statusLabel()) }
        var permissions by remember { mutableStateOf(SuitePermissionState.snapshot(this)) }
        var resumeTick by remember { mutableIntStateOf(0) }

        LifecycleResumeEffect(Unit) {
            resumeTick++
            onPauseOrDispose { }
        }

        LaunchedEffect(resumeTick) {
            rootAvailable = withContext(Dispatchers.IO) {
                RootManager.isAvailable(this@MainActivity)
            }
            permissions = SuitePermissionState.snapshot(this@MainActivity)
        }

        LaunchedEffect(Unit) {
            while (true) {
                xposedStatus = SuiteXposedServiceBroker.statusLabel()
                permissions = SuitePermissionState.snapshot(this@MainActivity)
                delay(1_000L)
            }
        }

        YPluginScaffold(
            title = "YSuite",
            subtitle = "统一宿主 · 插件共享系统能力",
        ) { scaffoldPadding ->
            YPluginList(padding = scaffoldPadding) {
                item {
                    RuntimeEnvironmentCard(
                        featureCount = features.size,
                        rootAvailable = rootAvailable,
                        xposedStatus = xposedStatus,
                        permissions = permissions,
                        onAccessibility = {
                            if (!SuitePermissionState.openAccessibilitySettings(this@MainActivity)) {
                                Toast.makeText(this@MainActivity, "无法打开无障碍设置", Toast.LENGTH_LONG).show()
                            }
                        },
                        onOverlay = {
                            if (!SuitePermissionState.openOverlaySettings(this@MainActivity)) {
                                Toast.makeText(this@MainActivity, "无法打开悬浮窗设置", Toast.LENGTH_LONG).show()
                            }
                        },
                        onNotificationListener = {
                            if (!SuitePermissionState.openNotificationListenerSettings(this@MainActivity)) {
                                Toast.makeText(this@MainActivity, "无法打开通知监听设置", Toast.LENGTH_LONG).show()
                            }
                        },
                        onExport = { exportDiagnostic(null, "整体诊断") },
                    )
                }

                items(features.size, key = { features[it].id }) { index ->
                    val feature = features[index]
                    FeatureCard(
                        feature = feature,
                        isEnabled = enabled[feature.id] == true,
                        onEnabledChange = { next ->
                            store.setEnabled(feature, next)
                            enabled[feature.id] = next
                            if (next) {
                                runCatching { feature.initialize(this@MainActivity) }
                                    .onSuccess {
                                        SuiteLog.i(
                                            this@MainActivity,
                                            feature.id,
                                            "host enabled; plugin attached to existing YSuite capabilities",
                                        )
                                    }
                                    .onFailure {
                                        SuiteLog.e(this@MainActivity, feature.id, "host enable failed", it)
                                        Toast.makeText(
                                            this@MainActivity,
                                            "${feature.name} 启用失败：${it.javaClass.simpleName}",
                                            Toast.LENGTH_LONG,
                                        ).show()
                                    }
                            } else {
                                SuiteLog.i(
                                    this@MainActivity,
                                    feature.id,
                                    "host disabled; shared capabilities remain owned by YSuite",
                                )
                            }
                        },
                        onOpen = {
                            SuiteCrashTracker.markActiveFeature(this@MainActivity, feature.id)
                            SuiteLog.i(
                                this@MainActivity,
                                feature.id,
                                "open requested; activity=${feature.entryActivityClassName}",
                            )
                            runCatching { startActivity(feature.createIntent(this@MainActivity)) }
                                .onFailure {
                                    SuiteLog.e(this@MainActivity, feature.id, "open failed", it)
                                    SuiteCrashTracker.markActiveFeature(this@MainActivity, null)
                                    Toast.makeText(
                                        this@MainActivity,
                                        "${feature.name} 打开失败：${it.javaClass.simpleName}",
                                        Toast.LENGTH_LONG,
                                    ).show()
                                }
                        },
                        onExportLog = { exportDiagnostic(setOf(feature.id), "${feature.name} 诊断") },
                    )
                }
            }
        }
    }
}

@Composable
private fun RuntimeEnvironmentCard(
    featureCount: Int,
    rootAvailable: Boolean?,
    xposedStatus: String,
    permissions: SuitePermissionSnapshot,
    onAccessibility: () -> Unit,
    onOverlay: () -> Unit,
    onNotificationListener: () -> Unit,
    onExport: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(YDimens.CardPadding)) {
            YPluginHeader(
                name = "统一运行环境",
                description = "所有系统能力由 YSuite 持有，功能模块只作为插件消费。",
                detail = "已加入功能：$featureCount · 已注册 LSPosed 插件监听：${SuiteXposedServiceBroker.listenerCount()}",
            )
            Spacer(Modifier.height(YDimens.ControlGap))
            Text(
                "Root：" + when (rootAvailable) {
                    true -> "已授权"
                    false -> "不可用 / 未授权"
                    null -> "检测中"
                },
            )
            Text("LSPosed：$xposedStatus")
            Text("无障碍：${permissions.accessibilityLabel}")
            Text("悬浮窗：${if (permissions.overlayGranted) "已授权" else "未授权"}")
            Text("通知：${if (permissions.notificationsGranted) "已授权" else "未授权"}")
            Text(
                "通知监听：" + when {
                    permissions.notificationListenerConnected -> "已连接"
                    permissions.notificationListenerGranted -> "已授权 · 等待连接"
                    else -> "未授权"
                },
            )

            if (permissions.legacyAccessibilityEnabled && !permissions.accessibilityEnabled) {
                HostWarning("检测到旧版分模块无障碍授权，请迁移到 YSuite 统一无障碍。")
            }
            if (permissions.legacyNotificationListenerEnabled && !permissions.notificationListenerGranted) {
                HostWarning("检测到旧版 YNotify 通知监听授权，请迁移到 YSuite 统一通知监听。")
            }
            if (permissions.otherAccessibilityHostEnabled) {
                HostWarning("另一独立版本的无障碍也已开启；建议只保留当前实际使用的版本。")
            }
            if (permissions.otherNotificationListenerHostEnabled) {
                HostWarning("另一独立版本的通知监听也已开启；建议只保留当前实际使用的版本。")
            }

            Spacer(Modifier.height(YDimens.ControlGap))
            YActionRow {
                OutlinedButton(onClick = onAccessibility) {
                    Text(if (permissions.accessibilityEnabled) "无障碍设置" else "开启无障碍")
                }
                OutlinedButton(onClick = onOverlay) { Text("悬浮窗") }
            }
            Spacer(Modifier.height(YDimens.ControlGap))
            YActionRow {
                OutlinedButton(onClick = onNotificationListener) {
                    Text(if (permissions.notificationListenerGranted) "通知监听设置" else "开启通知监听")
                }
                OutlinedButton(onClick = onExport) { Text("导出整体诊断") }
            }
        }
    }
}

@Composable
private fun HostWarning(message: String) {
    Text(
        message,
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun FeatureCard(
    feature: FeatureSpec,
    isEnabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onOpen: () -> Unit,
    onExportLog: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(YDimens.CardPadding)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                YPluginHeader(
                    name = feature.name,
                    description = feature.description,
                    detail = feature.sharedCapabilities
                        .map { it.displayName }
                        .takeIf { it.isNotEmpty() }
                        ?.joinToString(prefix = "共享能力：", separator = " + "),
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = isEnabled, onCheckedChange = onEnabledChange)
            }
            Spacer(Modifier.height(YDimens.ControlGap))
            YActionRow {
                Button(onClick = onOpen, enabled = isEnabled) { Text("打开") }
                OutlinedButton(onClick = onExportLog) { Text("诊断包") }
            }
        }
    }
}
