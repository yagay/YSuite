package com.yagay.ydownload

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.yagay.yui.YHorizontalActions
import com.yagay.yui.YSection
import com.yagay.yui.YSwitchItem
import com.yagay.yui.YStatusLine
import com.yagay.yui.YStatusTone
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun YDownloadNewTaskCard(
    store: DownloadStore,
    enhancedSettings: YDownloadEnhancedSettings,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf("") }
    var fileName by remember { mutableStateOf("") }
    var showAdvanced by remember { mutableStateOf(false) }
    var customHeaders by remember { mutableStateOf("") }
    var expectedSha256 by remember { mutableStateOf("") }
    var requestOptionsInvalid by remember { mutableStateOf(false) }

    fun parsedHeaders(): Map<String, String>? {
        if (!showAdvanced) return emptyMap()
        return DownloadRequestOptions.parseHeaders(customHeaders).getOrNull()
    }

    fun parsedExpectedSha256(): String? {
        if (!showAdvanced || expectedSha256.isBlank()) return null
        return DownloadRequestOptions.normalizeSha256(expectedSha256).getOrNull()
    }

    fun clearInput() {
        url = ""
        fileName = ""
        customHeaders = ""
        expectedSha256 = ""
        requestOptionsInvalid = false
    }

    YSection(
        title = stringResource(R.string.new_download),
        subtitle = stringResource(R.string.new_download_summary),
    ) {
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.url)) },
            singleLine = true,
        )
        OutlinedTextField(
            value = fileName,
            onValueChange = { fileName = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.file_name_optional)) },
            singleLine = true,
        )
        YSwitchItem(
            title = stringResource(R.string.advanced_request_options),
            subtitle = stringResource(R.string.advanced_request_options_summary),
            checked = showAdvanced,
            onCheckedChange = {
                showAdvanced = it
                requestOptionsInvalid = false
            },
        )
        if (showAdvanced) {
            OutlinedTextField(
                value = customHeaders,
                onValueChange = { customHeaders = it; requestOptionsInvalid = false },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.custom_headers)) },
                supportingText = { Text(stringResource(R.string.custom_headers_summary)) },
                minLines = 2,
                maxLines = 5,
            )
            OutlinedTextField(
                value = expectedSha256,
                onValueChange = { expectedSha256 = it.take(64); requestOptionsInvalid = false },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.expected_sha256)) },
                supportingText = { Text(stringResource(R.string.expected_sha256_all_summary)) },
                singleLine = true,
            )
            if (requestOptionsInvalid) Text(stringResource(R.string.invalid_request_options))
        }
        YStatusLine(
            stringResource(R.string.default_engine),
            if (enhancedSettings.defaultBackend == DownloadBackend.SYSTEM) {
                stringResource(R.string.android_download_manager)
            } else {
                stringResource(R.string.enhanced_engine)
            },
            YStatusTone.Neutral,
        )
        YHorizontalActions {
            val systemClick: () -> Unit = system@{
                val headers = parsedHeaders() ?: run {
                    requestOptionsInvalid = true
                    return@system
                }
                val expected = parsedExpectedSha256()
                if (showAdvanced && expectedSha256.isNotBlank() && expected == null) {
                    requestOptionsInvalid = true
                    return@system
                }
                val task = createTask(
                    store = store,
                    rawUrl = url,
                    rawFileName = fileName,
                    backend = DownloadBackend.SYSTEM,
                    requestHeaders = headers,
                    expectedSha256 = expected,
                ) ?: return@system
                scope.launch {
                    val result = withContext(Dispatchers.IO) { SystemDownloadBridge.enqueue(context, task) }
                    result.onSuccess { systemId ->
                        store.update(task.id) { it.copy(systemId = systemId, state = DownloadState.QUEUED, error = null) }
                    }.onFailure { error ->
                        store.update(task.id) { it.copy(state = DownloadState.FAILED, error = error.message) }
                    }
                }
                clearInput()
            }
            val enhancedClick: () -> Unit = enhanced@{
                val headers = parsedHeaders() ?: run {
                    requestOptionsInvalid = true
                    return@enhanced
                }
                val expected = parsedExpectedSha256()
                if (showAdvanced && expectedSha256.isNotBlank() && expected == null) {
                    requestOptionsInvalid = true
                    return@enhanced
                }
                val task = createTask(
                    store = store,
                    rawUrl = url,
                    rawFileName = fileName,
                    backend = DownloadBackend.ENHANCED,
                    requestHeaders = headers,
                    expectedSha256 = expected,
                ) ?: return@enhanced
                DownloadService.start(context, task.id)
                clearInput()
            }
            if (enhancedSettings.defaultBackend == DownloadBackend.SYSTEM) {
                Button(onClick = systemClick, enabled = url.isNotBlank()) {
                    Text(stringResource(R.string.system_download))
                }
                OutlinedButton(onClick = enhancedClick, enabled = url.isNotBlank()) {
                    Text(stringResource(R.string.enhanced_download))
                }
            } else {
                Button(onClick = enhancedClick, enabled = url.isNotBlank()) {
                    Text(stringResource(R.string.enhanced_download))
                }
                OutlinedButton(onClick = systemClick, enabled = url.isNotBlank()) {
                    Text(stringResource(R.string.system_download))
                }
            }
        }
    }
}

private fun createTask(
    store: DownloadStore,
    rawUrl: String,
    rawFileName: String,
    backend: DownloadBackend,
    requestHeaders: Map<String, String>,
    expectedSha256: String?,
): DownloadItem? {
    val normalized = rawUrl.trim()
    val scheme = runCatching { URI(normalized).scheme?.lowercase() }.getOrNull()
    if (scheme != "http" && scheme != "https") return null
    val derived = rawFileName.trim().takeIf { it.isNotBlank() }
        ?: normalized.substringBefore('?').substringBefore('#').substringAfterLast('/').takeIf { it.isNotBlank() }
        ?: "download-${System.currentTimeMillis()}"
    val safeName = derived.replace(Regex("[\\\\/:*?\"<>|]"), "_")
    return store.add(
        url = normalized,
        fileName = safeName,
        backend = backend,
        requestHeaders = requestHeaders,
        expectedSha256 = expectedSha256,
    )
}
