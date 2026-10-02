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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yagay.yui.YActionRow
import com.yagay.yui.YComposeActivity
import com.yagay.yui.YFeatureCard
import com.yagay.yui.YFeatureList
import com.yagay.yui.YFeatureScaffold
import com.yagay.yui.YStatusRow
import com.yagay.yui.YStatusTone
import java.net.URI

class MainActivity : YComposeActivity() {
    @Composable override fun YContent() {
        val store = remember { DownloadStore.get(this) }
        val items by store.items.collectAsStateWithLifecycle()
        var url by remember { mutableStateOf("") }
        var fileName by remember { mutableStateOf("") }
        YFeatureScaffold(title = stringResource(R.string.ydownload_title), subtitle = stringResource(R.string.ydownload_subtitle)) { padding ->
            YFeatureList(padding) {
                item {
                    YFeatureCard(title = stringResource(R.string.new_download), subtitle = stringResource(R.string.new_download_summary)) {
                        OutlinedTextField(url, { url = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.url)) }, singleLine = true)
                        OutlinedTextField(fileName, { fileName = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.file_name_optional)) }, singleLine = true)
                        YActionRow {
                            Button(onClick = {
                                val normalized = url.trim()
                                val scheme = runCatching { URI(normalized).scheme?.lowercase() }.getOrNull()
                                if (scheme == "http" || scheme == "https") {
                                    val derived = fileName.trim().takeIf { it.isNotBlank() } ?: normalized.substringBefore('?').substringAfterLast('/').takeIf { it.isNotBlank() } ?: "download-${System.currentTimeMillis()}"
                                    val task = store.add(normalized, derived.replace(Regex("[\\\\/:*?\"<>|]"), "_"))
                                    DownloadService.start(this@MainActivity, task.id); url = ""; fileName = ""
                                }
                            }, enabled = url.isNotBlank()) { Text(stringResource(R.string.start)) }
                        }
                    }
                }
                item {
                    YFeatureCard(title = stringResource(R.string.system_integration), subtitle = stringResource(R.string.system_integration_summary)) {
                        YStatusRow(stringResource(R.string.download_provider), stringResource(R.string.preserved), YStatusTone.Good)
                        YStatusRow(stringResource(R.string.engine), stringResource(R.string.native_engine), YStatusTone.Good)
                    }
                }
                if (items.isEmpty()) item { YFeatureCard(title = stringResource(R.string.no_downloads), subtitle = stringResource(R.string.no_downloads_summary)) }
                items(items, key = { it.id }) { task ->
                    val status = when (task.state) {
                        DownloadState.QUEUED -> stringResource(R.string.queued); DownloadState.RUNNING -> stringResource(R.string.running)
                        DownloadState.PAUSED -> stringResource(R.string.paused); DownloadState.COMPLETED -> stringResource(R.string.completed)
                        DownloadState.FAILED -> stringResource(R.string.failed); DownloadState.CANCELLED -> stringResource(R.string.cancelled)
                    }
                    YFeatureCard(title = task.fileName, subtitle = task.url, detail = task.error) {
                        YStatusRow(stringResource(R.string.status), status, if (task.state == DownloadState.FAILED) YStatusTone.Error else if (task.state == DownloadState.COMPLETED) YStatusTone.Good else YStatusTone.Neutral)
                        if (task.total > 0) {
                            val percent = ((task.done * 100L) / task.total).coerceIn(0L, 100L).toInt()
                            YStatusRow(stringResource(R.string.progress), stringResource(R.string.progress_percent, percent))
                        }
                        YActionRow {
                            when (task.state) {
                                DownloadState.RUNNING, DownloadState.QUEUED -> OutlinedButton({ DownloadService.pause(this@MainActivity, task.id) }) { Text(stringResource(R.string.pause)) }
                                DownloadState.PAUSED, DownloadState.FAILED -> Button({ DownloadService.start(this@MainActivity, task.id) }) { Text(stringResource(R.string.resume)) }
                                DownloadState.COMPLETED -> Button({ task.uri?.let { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) } }) { Text(stringResource(R.string.open)) }
                                DownloadState.CANCELLED -> Unit
                            }
                            if (task.state !in setOf(DownloadState.COMPLETED, DownloadState.CANCELLED)) OutlinedButton({ DownloadService.cancel(this@MainActivity, task.id) }) { Text(stringResource(R.string.cancel)) }
                            else OutlinedButton({ store.remove(task.id) }) { Text(stringResource(R.string.remove)) }
                        }
                    }
                }
            }
        }
    }
}
