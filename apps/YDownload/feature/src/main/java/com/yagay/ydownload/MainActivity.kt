package com.yagay.ydownload

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.yagay.yui.YActionRow
import com.yagay.yui.YFeatureCard
import com.yagay.yui.YFilterBar
import com.yagay.yui.YManagerScaffold
import com.yagay.yui.YPrimaryActionButton
import com.yagay.yui.YSecondaryActionButton
import com.yagay.yui.YPageList
import com.yagay.yui.YComposeActivity
import com.yagay.yui.YSettingSwitch
import com.yagay.yui.YStatusRow
import com.yagay.yui.YStatusTone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : YComposeActivity() {
    @Composable
    override fun YContent() {
        val store = remember { DownloadStore.get(this) }
        val items by store.items.collectAsStateWithLifecycle()
        var patchSettings by remember { mutableStateOf(YDownloadPatchSettings.load(this)) }
        var enhancedSettings by remember { mutableStateOf(YDownloadEnhancedSettings.load(this)) }
        var page by remember { mutableIntStateOf(0) }

        LaunchedEffect(Unit) {
            while (true) {
                withContext(Dispatchers.IO) { SystemDownloadBridge.sync(this@MainActivity, store) }
                delay(1_000L)
            }
        }

        YManagerScaffold(
            title = stringResource(R.string.ydownload_title),
            subtitle = stringResource(R.string.ydownload_subtitle),
        ) { padding ->
            YPageList(padding) {
                item {
                    YFilterBar(
                        options = listOf(
                            stringResource(R.string.ydownload_tab_tasks),
                            stringResource(R.string.ydownload_tab_settings),
                        ),
                        selectedIndex = page,
                        onSelected = { page = it },
                    )
                }

                if (page == 0) {
                    item { YDownloadNewTaskCard(store, enhancedSettings) }
                    if (items.isNotEmpty()) item { YDownloadBatchControls(items, store) }
                    if (items.isEmpty()) {
                        item {
                            YFeatureCard(
                                title = stringResource(R.string.no_downloads),
                                subtitle = stringResource(R.string.no_downloads_summary),
                            )
                        }
                    }
                    items(items, key = { it.id }) { task -> DownloadTaskCard(task, store) }
                } else {
                    item { SystemPatchCard(patchSettings) { patchSettings = it } }
                    item { YDownloadHookScopeCard(this@MainActivity) }
                    item { EnhancedSettingsCard(enhancedSettings) { enhancedSettings = it } }
                }
            }
        }
    }

    @Composable
    private fun SystemPatchCard(
        settings: YDownloadPatchSettings,
        onChanged: (YDownloadPatchSettings) -> Unit,
    ) {
        YFeatureCard(
            title = stringResource(R.string.system_patch),
            subtitle = stringResource(R.string.system_patch_summary),
        ) {
            YStatusRow(stringResource(R.string.download_provider), stringResource(R.string.preserved), YStatusTone.Good)
            YStatusRow(stringResource(R.string.default_engine), stringResource(R.string.android_download_manager), YStatusTone.Good)
            YSettingSwitch(
                title = stringResource(R.string.enable_system_patch),
                subtitle = stringResource(R.string.enable_system_patch_summary),
                checked = settings.enabled,
                onCheckedChange = {
                    onChanged(YDownloadPatchSettings.update(this@MainActivity) { copy(enabled = it) })
                },
            )
            YSettingSwitch(
                title = stringResource(R.string.allow_metered),
                checked = settings.allowMetered,
                onCheckedChange = {
                    onChanged(YDownloadPatchSettings.update(this@MainActivity) { copy(allowMetered = it) })
                },
            )
            YSettingSwitch(
                title = stringResource(R.string.allow_roaming),
                checked = settings.allowRoaming,
                onCheckedChange = {
                    onChanged(YDownloadPatchSettings.update(this@MainActivity) { copy(allowRoaming = it) })
                },
            )
            YSettingSwitch(
                title = stringResource(R.string.require_charging),
                checked = settings.requireCharging,
                onCheckedChange = {
                    onChanged(YDownloadPatchSettings.update(this@MainActivity) { copy(requireCharging = it) })
                },
            )
            YSettingSwitch(
                title = stringResource(R.string.require_idle),
                checked = settings.requireDeviceIdle,
                onCheckedChange = {
                    onChanged(YDownloadPatchSettings.update(this@MainActivity) { copy(requireDeviceIdle = it) })
                },
            )
            YSettingSwitch(
                title = stringResource(R.string.force_completion_notification),
                checked = settings.forceCompletionNotification,
                onCheckedChange = {
                    onChanged(YDownloadPatchSettings.update(this@MainActivity) { copy(forceCompletionNotification = it) })
                },
            )
        }
    }

    @Composable
    private fun EnhancedSettingsCard(
        settings: YDownloadEnhancedSettings,
        onChanged: (YDownloadEnhancedSettings) -> Unit,
    ) {
        YFeatureCard(
            title = stringResource(R.string.enhanced_engine_settings),
            subtitle = stringResource(R.string.enhanced_engine_settings_summary),
        ) {
            YStatusRow(
                stringResource(R.string.default_engine),
                if (settings.defaultBackend == DownloadBackend.SYSTEM) {
                    stringResource(R.string.android_download_manager)
                } else {
                    stringResource(R.string.enhanced_engine)
                },
                YStatusTone.Neutral,
            )
            YActionRow {
                YSecondaryActionButton(onClick = {
                    onChanged(YDownloadEnhancedSettings.update(this@MainActivity) { copy(defaultBackend = DownloadBackend.SYSTEM) })
                }) { Text(stringResource(R.string.system_download)) }
                YSecondaryActionButton(onClick = {
                    onChanged(YDownloadEnhancedSettings.update(this@MainActivity) { copy(defaultBackend = DownloadBackend.ENHANCED) })
                }) { Text(stringResource(R.string.enhanced_download)) }
            }
            YStatusRow(stringResource(R.string.concurrent_downloads), settings.maxConcurrent.toString(), YStatusTone.Neutral)
            YActionRow {
                (1..4).forEach { count ->
                    YSecondaryActionButton(onClick = {
                        onChanged(YDownloadEnhancedSettings.update(this@MainActivity) { copy(maxConcurrent = count) })
                    }) { Text(count.toString()) }
                }
            }
            YSettingSwitch(
                title = stringResource(R.string.auto_retry),
                subtitle = stringResource(R.string.auto_retry_summary),
                checked = settings.autoRetry,
                onCheckedChange = {
                    onChanged(YDownloadEnhancedSettings.update(this@MainActivity) { copy(autoRetry = it) })
                },
            )
            if (settings.autoRetry) {
                YStatusRow(stringResource(R.string.max_retries), settings.maxRetries.toString(), YStatusTone.Neutral)
                YActionRow {
                    (0..3).forEach { retries ->
                        YSecondaryActionButton(onClick = {
                            onChanged(YDownloadEnhancedSettings.update(this@MainActivity) { copy(maxRetries = retries) })
                        }) { Text(retries.toString()) }
                    }
                }
            }
            Text(stringResource(R.string.enhanced_engine_reference_note))
        }
    }

    @Composable
    private fun DownloadTaskCard(task: DownloadItem, store: DownloadStore) {
        val status = when (task.state) {
            DownloadState.QUEUED -> stringResource(R.string.queued)
            DownloadState.RUNNING -> stringResource(R.string.running)
            DownloadState.PAUSED -> stringResource(R.string.paused)
            DownloadState.COMPLETED -> stringResource(R.string.completed)
            DownloadState.FAILED -> stringResource(R.string.failed)
            DownloadState.CANCELLED -> stringResource(R.string.cancelled)
        }
        val engine = if (task.backend == DownloadBackend.SYSTEM) {
            stringResource(R.string.android_download_manager)
        } else {
            stringResource(R.string.enhanced_engine)
        }
        YFeatureCard(title = task.fileName, subtitle = task.url, detail = task.error) {
            YStatusRow(stringResource(R.string.engine), engine, YStatusTone.Neutral)
            YStatusRow(
                stringResource(R.string.status),
                status,
                when (task.state) {
                    DownloadState.FAILED -> YStatusTone.Error
                    DownloadState.COMPLETED -> YStatusTone.Good
                    else -> YStatusTone.Neutral
                },
            )
            if (task.backend == DownloadBackend.ENHANCED && task.retryCount > 0) {
                YStatusRow(stringResource(R.string.retry_count), task.retryCount.toString(), YStatusTone.Warning)
            }
            if (task.expectedSha256 != null) {
                YStatusRow(
                    stringResource(R.string.expected_sha256),
                    task.expectedSha256,
                    if (task.sha256?.equals(task.expectedSha256, ignoreCase = true) == true) YStatusTone.Good else YStatusTone.Neutral,
                )
            }
            if (task.total > 0) {
                val percent = ((task.done * 100L) / task.total).coerceIn(0L, 100L).toInt()
                YStatusRow(stringResource(R.string.progress), stringResource(R.string.progress_percent, percent))
            }
            if (task.speedBytesPerSecond > 0L) {
                YStatusRow(stringResource(R.string.ydownload_speed), formatSpeed(task.speedBytesPerSecond), YStatusTone.Neutral)
            }
            if (task.etaMillis >= 0L && task.state == DownloadState.RUNNING) {
                YStatusRow(stringResource(R.string.ydownload_eta), formatEta(task.etaMillis), YStatusTone.Neutral)
            }
            if (task.backend == DownloadBackend.SYSTEM) SystemTaskActions(task, store) else EnhancedTaskActions(task, store)
        }
    }

    @Composable
    private fun SystemTaskActions(task: DownloadItem, store: DownloadStore) {
        YActionRow {
            when (task.state) {
                DownloadState.RUNNING, DownloadState.QUEUED -> YSecondaryActionButton(
                    onClick = { controlSystemTask(task, store, pause = true) },
                ) { Text(stringResource(R.string.pause)) }
                DownloadState.PAUSED -> YPrimaryActionButton(
                    onClick = { controlSystemTask(task, store, pause = false) },
                ) { Text(stringResource(R.string.resume)) }
                DownloadState.FAILED -> YPrimaryActionButton(
                    onClick = { retrySystemTask(task, store) },
                ) { Text(stringResource(R.string.retry)) }
                DownloadState.COMPLETED -> YPrimaryActionButton(
                    onClick = { openSystemTask(task) },
                ) { Text(stringResource(R.string.open)) }
                DownloadState.CANCELLED -> Unit
            }
            if (task.state !in setOf(DownloadState.COMPLETED, DownloadState.CANCELLED)) {
                YSecondaryActionButton(onClick = {
                    task.systemId?.let { SystemDownloadBridge.remove(this@MainActivity, it) }
                    store.update(task.id) {
                        it.copy(
                            state = DownloadState.CANCELLED,
                            requestHeaders = emptyMap(),
                            speedBytesPerSecond = 0L,
                            etaMillis = -1L,
                        )
                    }
                }) { Text(stringResource(R.string.cancel)) }
            } else {
                YSecondaryActionButton(onClick = { store.remove(task.id) }) { Text(stringResource(R.string.remove)) }
            }
        }
    }

    @Composable
    private fun EnhancedTaskActions(task: DownloadItem, store: DownloadStore) {
        YActionRow {
            when (task.state) {
                DownloadState.RUNNING, DownloadState.QUEUED -> YSecondaryActionButton(
                    { DownloadService.pause(this@MainActivity, task.id) },
                ) { Text(stringResource(R.string.pause)) }
                DownloadState.PAUSED -> YPrimaryActionButton(
                    { DownloadService.start(this@MainActivity, task.id) },
                ) { Text(stringResource(R.string.resume)) }
                DownloadState.FAILED -> YPrimaryActionButton(
                    {
                        store.update(task.id) { it.copy(retryCount = 0, error = null, state = DownloadState.QUEUED) }
                        DownloadService.start(this@MainActivity, task.id)
                    },
                ) { Text(stringResource(R.string.retry)) }
                DownloadState.COMPLETED -> YPrimaryActionButton(
                    { task.uri?.let { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) } },
                ) { Text(stringResource(R.string.open)) }
                DownloadState.CANCELLED -> Unit
            }
            if (task.state !in setOf(DownloadState.COMPLETED, DownloadState.CANCELLED)) {
                YSecondaryActionButton({ DownloadService.cancel(this@MainActivity, task.id) }) { Text(stringResource(R.string.cancel)) }
            } else {
                YSecondaryActionButton({ store.remove(task.id) }) { Text(stringResource(R.string.remove)) }
            }
        }
    }

    private fun controlSystemTask(task: DownloadItem, store: DownloadStore, pause: Boolean) {
        val systemId = task.systemId ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            val supported = if (pause) SystemDownloadBridge.pause(this@MainActivity, systemId)
            else SystemDownloadBridge.resume(this@MainActivity, systemId)
            if (supported) {
                store.update(task.id) {
                    it.copy(
                        state = if (pause) DownloadState.PAUSED else DownloadState.QUEUED,
                        error = null,
                        speedBytesPerSecond = 0L,
                        etaMillis = -1L,
                    )
                }
            } else {
                store.update(task.id) { it.copy(error = getString(R.string.ydownload_system_control_unsupported)) }
            }
        }
    }

    private fun retrySystemTask(task: DownloadItem, store: DownloadStore) {
        lifecycleScope.launch(Dispatchers.IO) {
            task.systemId?.let { SystemDownloadBridge.remove(this@MainActivity, it) }
            store.update(task.id) {
                it.copy(
                    systemId = null,
                    state = DownloadState.QUEUED,
                    error = null,
                    done = 0L,
                    total = -1L,
                    speedBytesPerSecond = 0L,
                    etaMillis = -1L,
                )
            }
            SystemDownloadBridge.enqueue(this@MainActivity, task.copy(systemId = null, state = DownloadState.QUEUED))
                .onSuccess { systemId ->
                    store.update(task.id) { it.copy(systemId = systemId, state = DownloadState.QUEUED, error = null) }
                }
                .onFailure { error ->
                    store.update(task.id) { it.copy(state = DownloadState.FAILED, error = error.message) }
                }
        }
    }

    private fun openSystemTask(task: DownloadItem) {
        val uri = task.systemId?.let { SystemDownloadBridge.openUri(this, it) }
            ?: task.uri?.let(Uri::parse)
            ?: return
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        }
    }

    @Composable
    private fun formatSpeed(bytesPerSecond: Long): String {
        val value = bytesPerSecond.toDouble()
        return when {
            bytesPerSecond < 1024L -> stringResource(R.string.ydownload_speed_bps, bytesPerSecond)
            bytesPerSecond < 1024L * 1024L -> stringResource(R.string.ydownload_speed_kbps, value / 1024.0)
            bytesPerSecond < 1024L * 1024L * 1024L -> stringResource(R.string.ydownload_speed_mbps, value / (1024.0 * 1024.0))
            else -> stringResource(R.string.ydownload_speed_gbps, value / (1024.0 * 1024.0 * 1024.0))
        }
    }

    @Composable
    private fun formatEta(etaMillis: Long): String {
        val seconds = (etaMillis / 1000L).coerceAtLeast(0L)
        return when {
            seconds < 60L -> stringResource(R.string.ydownload_eta_seconds, seconds)
            seconds < 3600L -> stringResource(R.string.ydownload_eta_minutes, seconds / 60L, seconds % 60L)
            else -> stringResource(R.string.ydownload_eta_hours, seconds / 3600L, (seconds % 3600L) / 60L)
        }
    }
}
