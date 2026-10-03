package com.yagay.ydownload

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.yagay.yui.YActionRow
import com.yagay.yui.YComposeActivity
import com.yagay.yui.YFeatureCard
import com.yagay.yui.YFeatureList
import com.yagay.yui.YFeatureScaffold
import com.yagay.yui.YSettingSwitch
import com.yagay.yui.YStatusRow
import com.yagay.yui.YStatusTone
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : YComposeActivity() {
    @Composable override fun YContent() {
        val store = remember { DownloadStore.get(this) }
        val items by store.items.collectAsStateWithLifecycle()
        var url by remember { mutableStateOf("") }
        var fileName by remember { mutableStateOf("") }
        var patchSettings by remember { mutableStateOf(YDownloadPatchSettings.load(this)) }

        LaunchedEffect(Unit) {
            while (true) {
                withContext(Dispatchers.IO) { SystemDownloadBridge.sync(this@MainActivity, store) }
                delay(1_000L)
            }
        }

        YFeatureScaffold(
            title = stringResource(R.string.ydownload_title),
            subtitle = stringResource(R.string.ydownload_subtitle),
        ) { padding ->
            YFeatureList(padding) {
                item {
                    YFeatureCard(
                        title = stringResource(R.string.new_download),
                        subtitle = stringResource(R.string.new_download_summary),
                    ) {
                        OutlinedTextField(
                            url,
                            { url = it },
                            Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.url)) },
                            singleLine = true,
                        )
                        OutlinedTextField(
                            fileName,
                            { fileName = it },
                            Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.file_name_optional)) },
                            singleLine = true,
                        )
                        YActionRow {
                            Button(
                                onClick = {
                                    createTask(store, url, fileName, DownloadBackend.SYSTEM)?.let { task ->
                                        lifecycleScope.launch(Dispatchers.IO) {
                                            SystemDownloadBridge.enqueue(this@MainActivity, task)
                                                .onSuccess { systemId ->
                                                    store.update(task.id) {
                                                        it.copy(systemId = systemId, state = DownloadState.QUEUED, error = null)
                                                    }
                                                }
                                                .onFailure { error ->
                                                    store.update(task.id) {
                                                        it.copy(state = DownloadState.FAILED, error = error.message)
                                                    }
                                                }
                                        }
                                        url = ""
                                        fileName = ""
                                    }
                                },
                                enabled = url.isNotBlank(),
                            ) { Text(stringResource(R.string.system_download)) }
                            OutlinedButton(
                                onClick = {
                                    createTask(store, url, fileName, DownloadBackend.ENHANCED)?.let { task ->
                                        DownloadService.start(this@MainActivity, task.id)
                                        url = ""
                                        fileName = ""
                                    }
                                },
                                enabled = url.isNotBlank(),
                            ) { Text(stringResource(R.string.enhanced_download)) }
                        }
                    }
                }

                item {
                    YFeatureCard(
                        title = stringResource(R.string.system_patch),
                        subtitle = stringResource(R.string.system_patch_summary),
                    ) {
                        YStatusRow(
                            stringResource(R.string.download_provider),
                            stringResource(R.string.preserved),
                            YStatusTone.Good,
                        )
                        YStatusRow(
                            stringResource(R.string.default_engine),
                            stringResource(R.string.android_download_manager),
                            YStatusTone.Good,
                        )
                        YSettingSwitch(
                            title = stringResource(R.string.enable_system_patch),
                            subtitle = stringResource(R.string.enable_system_patch_summary),
                            checked = patchSettings.enabled,
                            onCheckedChange = {
                                patchSettings = YDownloadPatchSettings.update(this@MainActivity) { copy(enabled = it) }
                            },
                        )
                        YSettingSwitch(
                            title = stringResource(R.string.allow_metered),
                            checked = patchSettings.allowMetered,
                            onCheckedChange = {
                                patchSettings = YDownloadPatchSettings.update(this@MainActivity) { copy(allowMetered = it) }
                            },
                        )
                        YSettingSwitch(
                            title = stringResource(R.string.allow_roaming),
                            checked = patchSettings.allowRoaming,
                            onCheckedChange = {
                                patchSettings = YDownloadPatchSettings.update(this@MainActivity) { copy(allowRoaming = it) }
                            },
                        )
                        YSettingSwitch(
                            title = stringResource(R.string.require_charging),
                            checked = patchSettings.requireCharging,
                            onCheckedChange = {
                                patchSettings = YDownloadPatchSettings.update(this@MainActivity) { copy(requireCharging = it) }
                            },
                        )
                        YSettingSwitch(
                            title = stringResource(R.string.require_idle),
                            checked = patchSettings.requireDeviceIdle,
                            onCheckedChange = {
                                patchSettings = YDownloadPatchSettings.update(this@MainActivity) { copy(requireDeviceIdle = it) }
                            },
                        )
                        YSettingSwitch(
                            title = stringResource(R.string.force_completion_notification),
                            checked = patchSettings.forceCompletionNotification,
                            onCheckedChange = {
                                patchSettings = YDownloadPatchSettings.update(this@MainActivity) {
                                    copy(forceCompletionNotification = it)
                                }
                            },
                        )
                    }
                }

                if (items.isEmpty()) {
                    item {
                        YFeatureCard(
                            title = stringResource(R.string.no_downloads),
                            subtitle = stringResource(R.string.no_downloads_summary),
                        )
                    }
                }

                items(items, key = { it.id }) { task ->
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
                    YFeatureCard(
                        title = task.fileName,
                        subtitle = task.url,
                        detail = task.error,
                    ) {
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
                        if (task.total > 0) {
                            val percent = ((task.done * 100L) / task.total).coerceIn(0L, 100L).toInt()
                            YStatusRow(stringResource(R.string.progress), stringResource(R.string.progress_percent, percent))
                        }
                        if (task.backend == DownloadBackend.SYSTEM) {
                            SystemTaskActions(task, store)
                        } else {
                            EnhancedTaskActions(task, store)
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun SystemTaskActions(task: DownloadItem, store: DownloadStore) {
        YActionRow {
            if (task.state == DownloadState.COMPLETED) {
                Button(onClick = { openSystemTask(task) }) { Text(stringResource(R.string.open)) }
            }
            if (task.state == DownloadState.FAILED || task.state == DownloadState.PAUSED) {
                Button(onClick = { retrySystemTask(task, store) }) { Text(stringResource(R.string.retry)) }
            }
            if (task.state !in setOf(DownloadState.COMPLETED, DownloadState.CANCELLED)) {
                OutlinedButton(onClick = {
                    task.systemId?.let { SystemDownloadBridge.remove(this@MainActivity, it) }
                    store.update(task.id) { it.copy(state = DownloadState.CANCELLED) }
                }) { Text(stringResource(R.string.cancel)) }
            } else {
                OutlinedButton(onClick = { store.remove(task.id) }) { Text(stringResource(R.string.remove)) }
            }
        }
    }

    @Composable
    private fun EnhancedTaskActions(task: DownloadItem, store: DownloadStore) {
        YActionRow {
            when (task.state) {
                DownloadState.RUNNING, DownloadState.QUEUED -> OutlinedButton(
                    { DownloadService.pause(this@MainActivity, task.id) },
                ) { Text(stringResource(R.string.pause)) }
                DownloadState.PAUSED, DownloadState.FAILED -> Button(
                    { DownloadService.start(this@MainActivity, task.id) },
                ) { Text(stringResource(R.string.resume)) }
                DownloadState.COMPLETED -> Button(
                    { task.uri?.let { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) } },
                ) { Text(stringResource(R.string.open)) }
                DownloadState.CANCELLED -> Unit
            }
            if (task.state !in setOf(DownloadState.COMPLETED, DownloadState.CANCELLED)) {
                OutlinedButton({ DownloadService.cancel(this@MainActivity, task.id) }) {
                    Text(stringResource(R.string.cancel))
                }
            } else {
                OutlinedButton({ store.remove(task.id) }) { Text(stringResource(R.string.remove)) }
            }
        }
    }

    private fun retrySystemTask(task: DownloadItem, store: DownloadStore) {
        lifecycleScope.launch(Dispatchers.IO) {
            task.systemId?.let { SystemDownloadBridge.remove(this@MainActivity, it) }
            store.update(task.id) { it.copy(systemId = null, state = DownloadState.QUEUED, error = null, done = 0L, total = -1L) }
            SystemDownloadBridge.enqueue(this@MainActivity, task.copy(systemId = null, state = DownloadState.QUEUED))
                .onSuccess { systemId -> store.update(task.id) { it.copy(systemId = systemId, state = DownloadState.QUEUED, error = null) } }
                .onFailure { error -> store.update(task.id) { it.copy(state = DownloadState.FAILED, error = error.message) } }
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

    private fun createTask(
        store: DownloadStore,
        rawUrl: String,
        rawFileName: String,
        backend: DownloadBackend,
    ): DownloadItem? {
        val normalized = rawUrl.trim()
        val scheme = runCatching { URI(normalized).scheme?.lowercase() }.getOrNull()
        if (scheme != "http" && scheme != "https") return null
        val derived = rawFileName.trim().takeIf { it.isNotBlank() }
            ?: normalized.substringBefore('?').substringAfterLast('/').takeIf { it.isNotBlank() }
            ?: "download-${System.currentTimeMillis()}"
        val safeName = derived.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        return store.add(normalized, safeName, backend)
    }
}
