package com.yagay.YSuite

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.lifecycleScope
import com.yagay.suite.core.FeatureRegistry
import com.yagay.suite.core.FeatureSpec
import com.yagay.suite.core.FeatureStateStore
import com.yagay.suite.core.RootManager
import com.yagay.suite.core.SuiteCrashTracker
import com.yagay.suite.core.SuiteLog
import com.yagay.suite.core.SuiteXposedServiceBroker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { MaterialTheme { FeatureManagerScreen() } }
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

    @OptIn(ExperimentalMaterial3Api::class)
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

        // Process-global framework state and special-access bindings can change while Settings,
        // KernelSU or LSPosed is in the foreground. Keep this inexpensive host snapshot fresh.
        LaunchedEffect(Unit) {
            while (true) {
                xposedStatus = SuiteXposedServiceBroker.statusLabel()
                permissions = SuitePermissionState.snapshot(this@MainActivity)
                delay(1_000L)
            }
        }

        Scaffold(topBar = { TopAppBar(title = { Text("YSuite") }) }) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Spacer(Modifier.height(4.dp))
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text("统一运行环境", style = MaterialTheme.typography.titleMedium)
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
                                Text(
                                    "检测到旧版分模块无障碍授权，请迁移到 YSuite 统一无障碍。",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            if (permissions.legacyNotificationListenerEnabled && !permissions.notificationListenerGranted) {
                                Text(
                                    "检测到旧版 YNotify 通知监听授权，请迁移到 YSuite 统一通知监听。",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            if (permissions.otherAccessibilityHostEnabled) {
                                Text(
                                    "另一独立版本的无障碍也已开启；建议只保留当前实际使用的版本。",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            if (permissions.otherNotificationListenerHostEnabled) {
                                Text(
                                    "另一独立版本的通知监听也已开启；建议只保留当前实际使用的版本。",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }

                            Text("已注册功能监听：${SuiteXposedServiceBroker.listenerCount()}")
                            Text("已加入功能：${features.size}")
                            Text(
                                "共享能力：Root、LSPosed、无障碍、悬浮窗、通知、通知监听。新增模块只需在 FeatureSpec 声明需要的能力；需要无障碍或通知事件流时再注册 Bridge，不再新增系统授权 Service。",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = {
                                    if (!SuitePermissionState.openAccessibilitySettings(this@MainActivity)) {
                                        Toast.makeText(
                                            this@MainActivity,
                                            "无法打开无障碍设置",
                                            Toast.LENGTH_LONG,
                                        ).show()
                                    }
                                }) {
                                    Text(if (permissions.accessibilityEnabled) "无障碍设置" else "开启无障碍")
                                }
                                OutlinedButton(onClick = {
                                    if (!SuitePermissionState.openOverlaySettings(this@MainActivity)) {
                                        Toast.makeText(
                                            this@MainActivity,
                                            "无法打开悬浮窗设置",
                                            Toast.LENGTH_LONG,
                                        ).show()
                                    }
                                }) { Text("悬浮窗") }
                            }
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = {
                                    if (!SuitePermissionState.openNotificationListenerSettings(this@MainActivity)) {
                                        Toast.makeText(
                                            this@MainActivity,
                                            "无法打开通知监听设置",
                                            Toast.LENGTH_LONG,
                                        ).show()
                                    }
                                }) {
                                    Text(
                                        if (permissions.notificationListenerGranted) {
                                            "通知监听设置"
                                        } else {
                                            "开启通知监听"
                                        },
                                    )
                                }
                                OutlinedButton(onClick = {
                                    exportDiagnostic(null, "整体诊断")
                                }) { Text("导出整体诊断") }
                            }
                        }
                    }
                }

                items(features, key = { it.id }) { feature ->
                    FeatureCard(
                        feature = feature,
                        isEnabled = enabled[feature.id] == true,
                        onEnabledChange = { next ->
                            store.setEnabled(feature, next)
                            enabled[feature.id] = next
                            if (next) {
                                runCatching { feature.initialize(this@MainActivity) }
                                    .onSuccess { runtime ->
                                        SuiteXposedServiceBroker.capture(this@MainActivity, runtime)
                                        SuiteXposedServiceBroker.takeOwnership(this@MainActivity)
                                        RootManager.reclaim(this@MainActivity)
                                        SuiteCrashTracker.reclaim(this@MainActivity)
                                        SuiteLog.i(this@MainActivity, feature.id, "host enabled")
                                    }
                                    .onFailure {
                                        SuiteLog.e(
                                            this@MainActivity,
                                            feature.id,
                                            "host enable failed",
                                            it,
                                        )
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
                                    "host disabled; LSPosed scope is managed separately",
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
                        onExportLog = {
                            exportDiagnostic(setOf(feature.id), "${feature.name} 诊断")
                        },
                    )
                }

                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
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
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(feature.name, style = MaterialTheme.typography.titleMedium)
                    Text(feature.description, style = MaterialTheme.typography.bodyMedium)
                    val needs = feature.sharedCapabilities.map { it.displayName }
                    if (needs.isNotEmpty()) {
                        Text(
                            "共享：${needs.joinToString(" + ")}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                Switch(checked = isEnabled, onCheckedChange = onEnabledChange)
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onOpen, enabled = isEnabled) { Text("打开") }
                OutlinedButton(onClick = onExportLog) { Text("诊断包") }
            }
        }
    }
}
