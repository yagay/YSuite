package com.yagay.ydownload

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yagay.yui.YHorizontalActions
import com.yagay.yui.YSection
import com.yagay.yui.YSwitchItem
import com.yagay.yui.YStatusLine
import com.yagay.yui.YStatusTone
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class YHttpProbe(
    val responseCode: Int,
    val finalUrl: String,
    val contentLength: Long,
    val contentType: String?,
    val acceptRanges: Boolean,
    val etag: String?,
    val lastModified: String?,
    val suggestedFileName: String?,
)

private data class YBulkDownloadSpec(val url: String, val fileName: String?)

private object YDownloadQueueBackend {
    fun parseBulk(text: String): List<YBulkDownloadSpec> = text.lineSequence()
        .map(String::trim)
        .filter { it.isNotBlank() && !it.startsWith('#') }
        .mapNotNull { line ->
            val parts = line.split('|', limit = 2)
            val url = parts[0].trim()
            val scheme = runCatching { URI(url).scheme?.lowercase() }.getOrNull()
            if (scheme != "http" && scheme != "https") return@mapNotNull null
            YBulkDownloadSpec(url, parts.getOrNull(1)?.trim()?.takeIf(String::isNotBlank))
        }
        .distinctBy(YBulkDownloadSpec::url)
        .toList()

    fun fileName(spec: YBulkDownloadSpec): String {
        val derived = spec.fileName
            ?: spec.url.substringBefore('?').substringBefore('#').substringAfterLast('/').takeIf(String::isNotBlank)
            ?: "download-${System.currentTimeMillis()}"
        return derived.replace(Regex("[\\\\/:*?\"<>|]"), "_")
    }

    fun probe(rawUrl: String, headers: Map<String, String>, userAgent: String): Result<YHttpProbe> = runCatching {
        val normalized = rawUrl.trim()
        val scheme = URI(normalized).scheme?.lowercase()
        require(scheme == "http" || scheme == "https") { "Only HTTP and HTTPS URLs are supported" }
        fun open(method: String): HttpURLConnection = (URL(normalized).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            instanceFollowRedirects = true
            connectTimeout = 12_000
            readTimeout = 15_000
            setRequestProperty("User-Agent", userAgent)
            headers.forEach { (name, value) -> setRequestProperty(name, value) }
            if (method == "GET") setRequestProperty("Range", "bytes=0-0")
        }
        var connection = open("HEAD")
        var code = connection.responseCode
        if (code == 405 || code == 501) {
            connection.disconnect()
            connection = open("GET")
            code = connection.responseCode
        }
        try {
            val disposition = connection.getHeaderField("Content-Disposition")
            YHttpProbe(
                responseCode = code,
                finalUrl = connection.url.toString(),
                contentLength = connection.getHeaderFieldLong("Content-Length", -1L),
                contentType = connection.contentType?.substringBefore(';')?.trim(),
                acceptRanges = connection.getHeaderField("Accept-Ranges")?.contains("bytes", ignoreCase = true) == true ||
                    code == HttpURLConnection.HTTP_PARTIAL,
                etag = connection.getHeaderField("ETag"),
                lastModified = connection.getHeaderField("Last-Modified"),
                suggestedFileName = contentDispositionName(disposition),
            )
        } finally {
            connection.disconnect()
        }
    }

    fun exportQueue(items: List<DownloadItem>): String = JSONArray().apply {
        items.forEach { item ->
            put(JSONObject().apply {
                put("url", item.url)
                put("fileName", item.fileName)
                put("backend", item.backend.name)
                put("expectedSha256", item.expectedSha256)
            })
        }
    }.toString(2)

    fun importQueue(json: String): List<Pair<YBulkDownloadSpec, DownloadBackend>> = runCatching {
        val array = JSONArray(json)
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val url = item.optString("url").trim()
                val scheme = runCatching { URI(url).scheme?.lowercase() }.getOrNull()
                if (scheme != "http" && scheme != "https") continue
                val backend = runCatching { DownloadBackend.valueOf(item.optString("backend")) }
                    .getOrDefault(DownloadBackend.SYSTEM)
                add(YBulkDownloadSpec(url, item.optString("fileName").takeIf(String::isNotBlank)) to backend)
            }
        }
    }.getOrDefault(emptyList())

    fun networkLabel(context: Context): Pair<Int, Boolean> {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return 0 to true
        val caps = manager.getNetworkCapabilities(manager.activeNetwork) ?: return 0 to manager.isActiveNetworkMetered
        val transport = when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> 1
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> 2
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> 3
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> 4
            else -> 0
        }
        return transport to manager.isActiveNetworkMetered
    }

    private fun contentDispositionName(value: String?): String? {
        if (value.isNullOrBlank()) return null
        val star = Regex("(?i)filename\\*\\s*=\\s*(?:UTF-8'')?([^;]+)").find(value)?.groupValues?.getOrNull(1)
        val plain = Regex("(?i)filename\\s*=\\s*\"?([^\";]+)\"?").find(value)?.groupValues?.getOrNull(1)
        return (star ?: plain)?.trim()?.trim('"')?.takeIf(String::isNotBlank)
    }
}

@Composable
fun YDownloadQueueToolsCard(context: Context) {
    val store = remember(context) { DownloadStore.get(context) }
    val items by store.items.collectAsState()
    val scope = rememberCoroutineScope()
    var bulkText by remember { mutableStateOf("") }
    var useEnhanced by remember { mutableStateOf(false) }
    var skipDuplicates by remember { mutableStateOf(true) }
    var headersText by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var probeUrl by remember { mutableStateOf("") }
    var probe by remember { mutableStateOf<YHttpProbe?>(null) }

    val network = remember(items.size) { YDownloadQueueBackend.networkLabel(context) }
    val byHost = remember(items) {
        items.groupingBy { runCatching { URI(it.url).host ?: "?" }.getOrDefault("?") }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
    }

    fun clipboardText(): String? = context.getSystemService(ClipboardManager::class.java)
        ?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()

    fun copyToClipboard(label: String, text: String) {
        context.getSystemService(ClipboardManager::class.java)
            ?.setPrimaryClip(ClipData.newPlainText(label, text))
        message = text.take(240)
    }

    fun enqueueSpecs(specs: List<Pair<YBulkDownloadSpec, DownloadBackend>>, headers: Map<String, String>) {
        busy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    var added = 0
                    specs.forEach { (spec, backend) ->
                        if (skipDuplicates && store.items.value.any { it.url == spec.url && it.state !in setOf(DownloadState.CANCELLED, DownloadState.FAILED) }) {
                            return@forEach
                        }
                        val task = store.add(
                            url = spec.url,
                            fileName = YDownloadQueueBackend.fileName(spec),
                            backend = backend,
                            requestHeaders = headers,
                        )
                        if (backend == DownloadBackend.SYSTEM) {
                            SystemDownloadBridge.enqueue(context, task).onSuccess { systemId ->
                                store.update(task.id) { it.copy(systemId = systemId, state = DownloadState.QUEUED, error = null) }
                            }.onFailure { error ->
                                store.update(task.id) { it.copy(state = DownloadState.FAILED, error = error.message) }
                            }
                        } else {
                            DownloadService.start(context, task.id)
                        }
                        added++
                    }
                    added
                }
            }
            result.onSuccess { message = context.getString(R.string.ydownload_queue_added, it) }
                .onFailure { message = it.message }
            busy = false
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        YSection(
            title = stringResource(R.string.ydownload_queue_title),
            subtitle = stringResource(R.string.ydownload_queue_summary),
        ) {
            YStatusLine(stringResource(R.string.ydownload_queue_total), items.size.toString(), YStatusTone.Neutral)
            YStatusLine(
                stringResource(R.string.ydownload_queue_active),
                items.count { it.state == DownloadState.RUNNING || it.state == DownloadState.QUEUED }.toString(),
                YStatusTone.Good,
            )
            YStatusLine(
                stringResource(R.string.ydownload_queue_failed),
                items.count { it.state == DownloadState.FAILED }.toString(),
                if (items.any { it.state == DownloadState.FAILED }) YStatusTone.Warning else YStatusTone.Neutral,
            )
            val transportLabel = when (network.first) {
                1 -> stringResource(R.string.ydownload_queue_wifi)
                2 -> stringResource(R.string.ydownload_queue_cellular)
                3 -> stringResource(R.string.ydownload_queue_ethernet)
                4 -> stringResource(R.string.ydownload_queue_vpn)
                else -> stringResource(R.string.ydownload_queue_unknown_network)
            }
            YStatusLine(
                stringResource(R.string.ydownload_queue_network),
                transportLabel + " · " + if (network.second) stringResource(R.string.ydownload_queue_metered) else stringResource(R.string.ydownload_queue_unmetered),
                if (network.second) YStatusTone.Warning else YStatusTone.Good,
            )
            byHost.take(6).forEach { host ->
                YStatusLine(host.key, host.value.toString(), YStatusTone.Neutral)
            }

            OutlinedTextField(
                value = bulkText,
                onValueChange = { bulkText = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.ydownload_queue_bulk_urls)) },
                supportingText = { Text(stringResource(R.string.ydownload_queue_bulk_urls_hint)) },
                minLines = 3,
                maxLines = 8,
            )
            YSwitchItem(
                title = stringResource(R.string.ydownload_queue_use_enhanced),
                checked = useEnhanced,
                onCheckedChange = { useEnhanced = it },
            )
            YSwitchItem(
                title = stringResource(R.string.ydownload_queue_skip_duplicates),
                checked = skipDuplicates,
                onCheckedChange = { skipDuplicates = it },
            )
            OutlinedTextField(
                value = headersText,
                onValueChange = { headersText = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.custom_headers)) },
                supportingText = { Text(stringResource(R.string.custom_headers_summary)) },
                minLines = 2,
                maxLines = 5,
            )
            YHorizontalActions {
                Button(
                    onClick = {
                        val headers = DownloadRequestOptions.parseHeaders(headersText).getOrElse {
                            message = it.message
                            return@Button
                        }
                        val specs = YDownloadQueueBackend.parseBulk(bulkText)
                            .map { it to if (useEnhanced) DownloadBackend.ENHANCED else DownloadBackend.SYSTEM }
                        enqueueSpecs(specs, headers)
                    },
                    enabled = !busy && bulkText.isNotBlank(),
                ) { Text(stringResource(R.string.ydownload_queue_add_all)) }
                OutlinedButton(onClick = { clipboardText()?.let { bulkText = it } }, enabled = !busy) {
                    Text(stringResource(R.string.ydownload_queue_paste))
                }
            }
            YHorizontalActions {
                OutlinedButton(onClick = {
                    val seen = mutableSetOf<String>()
                    items.sortedByDescending(DownloadItem::id).forEach { item ->
                        if (!seen.add(item.url) && item.state != DownloadState.RUNNING) store.remove(item.id)
                    }
                    message = context.getString(R.string.ydownload_queue_deduped)
                }, enabled = items.isNotEmpty() && !busy) { Text(stringResource(R.string.ydownload_queue_dedupe)) }
                OutlinedButton(onClick = {
                    items.filter { it.state == DownloadState.FAILED || it.state == DownloadState.CANCELLED }.forEach { store.remove(it.id) }
                    message = context.getString(R.string.ydownload_queue_cleaned)
                }, enabled = items.any { it.state == DownloadState.FAILED || it.state == DownloadState.CANCELLED } && !busy) {
                    Text(stringResource(R.string.ydownload_queue_clear_failed_cancelled))
                }
            }
            YHorizontalActions {
                OutlinedButton(onClick = {
                    copyToClipboard("YDownload URLs", items.map(DownloadItem::url).distinct().joinToString("\n"))
                }, enabled = items.isNotEmpty()) { Text(stringResource(R.string.ydownload_queue_copy_urls)) }
                OutlinedButton(onClick = {
                    copyToClipboard("YDownload queue", YDownloadQueueBackend.exportQueue(items))
                }, enabled = items.isNotEmpty()) { Text(stringResource(R.string.ydownload_queue_export_json)) }
                OutlinedButton(onClick = {
                    val imported = clipboardText()?.let(YDownloadQueueBackend::importQueue).orEmpty()
                    enqueueSpecs(imported, emptyMap())
                }, enabled = !busy) { Text(stringResource(R.string.ydownload_queue_import_json)) }
            }
            message?.let { Text(it) }
        }

        YSection(
            title = stringResource(R.string.ydownload_probe_title),
            subtitle = stringResource(R.string.ydownload_probe_summary),
        ) {
            OutlinedTextField(
                value = probeUrl,
                onValueChange = { probeUrl = it; probe = null },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.url)) },
                singleLine = true,
            )
            Button(onClick = {
                busy = true
                scope.launch {
                    val settings = YDownloadEnhancedSettings.load(context)
                    val headers = DownloadRequestOptions.parseHeaders(headersText).getOrDefault(emptyMap())
                    val result = withContext(Dispatchers.IO) {
                        YDownloadQueueBackend.probe(probeUrl, headers, settings.userAgent)
                    }
                    result.onSuccess { probe = it; message = null }.onFailure { message = it.message }
                    busy = false
                }
            }, enabled = !busy && probeUrl.isNotBlank()) { Text(stringResource(R.string.ydownload_probe_run)) }
            probe?.let { result ->
                YStatusLine(stringResource(R.string.ydownload_probe_http), result.responseCode.toString(), if (result.responseCode in 200..299) YStatusTone.Good else YStatusTone.Warning)
                YStatusLine(stringResource(R.string.ydownload_probe_size), if (result.contentLength >= 0) formatQueueBytes(result.contentLength) else stringResource(R.string.ydownload_probe_unknown), YStatusTone.Neutral)
                YStatusLine(stringResource(R.string.ydownload_probe_ranges), if (result.acceptRanges) stringResource(R.string.ydownload_queue_yes) else stringResource(R.string.ydownload_queue_no), if (result.acceptRanges) YStatusTone.Good else YStatusTone.Neutral)
                result.contentType?.let { YStatusLine(stringResource(R.string.ydownload_probe_type), it, YStatusTone.Neutral) }
                result.suggestedFileName?.let { YStatusLine(stringResource(R.string.file_name_optional), it, YStatusTone.Neutral) }
                result.etag?.let { Text(it) }
                result.lastModified?.let { Text(it) }
                Text(result.finalUrl)
            }
        }
    }
}

private fun formatQueueBytes(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble() / 1024.0
    var index = 0
    while (value >= 1024.0 && index < units.lastIndex) {
        value /= 1024.0
        index++
    }
    return String.format(java.util.Locale.getDefault(), "%.2f %s", value, units[index])
}
