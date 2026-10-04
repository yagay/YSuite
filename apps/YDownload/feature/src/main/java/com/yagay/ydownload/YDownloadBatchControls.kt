package com.yagay.ydownload

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.yagay.yui.YPrimaryActionButton
import com.yagay.yui.YSecondaryActionButton
import com.yagay.yui.YTextField
import com.yagay.yui.YHorizontalActions
import com.yagay.yui.YSection
import com.yagay.yui.YStatusLine
import com.yagay.yui.YStatusTone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun YDownloadBatchControls(
    items: List<DownloadItem>,
    store: DownloadStore,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val activeCount = items.count { it.state == DownloadState.RUNNING || it.state == DownloadState.QUEUED }
    val pausedCount = items.count { it.state == DownloadState.PAUSED }
    val failedCount = items.count { it.state == DownloadState.FAILED }
    val finishedCount = items.count { it.state == DownloadState.COMPLETED || it.state == DownloadState.CANCELLED }
    val hashedItems = items.filter { !it.sha256.isNullOrBlank() }

    YSection(
        title = stringResource(R.string.batch_controls),
        subtitle = stringResource(R.string.batch_controls_summary),
    ) {
        YStatusLine(
            stringResource(R.string.batch_overview),
            stringResource(R.string.batch_overview_value, activeCount, pausedCount, failedCount),
            YStatusTone.Neutral,
        )
        YHorizontalActions {
            YSecondaryActionButton(
                onClick = {
                    items.filter { it.state == DownloadState.RUNNING || it.state == DownloadState.QUEUED }
                        .forEach { task ->
                            if (task.backend == DownloadBackend.ENHANCED) {
                                DownloadService.pause(context, task.id)
                            } else {
                                val systemId = task.systemId ?: return@forEach
                                scope.launch {
                                    val supported = withContext(Dispatchers.IO) {
                                        SystemDownloadBridge.pause(context, systemId)
                                    }
                                    if (supported) {
                                        store.update(task.id) {
                                            it.copy(
                                                state = DownloadState.PAUSED,
                                                error = null,
                                                speedBytesPerSecond = 0L,
                                                etaMillis = -1L,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                },
                enabled = activeCount > 0,
            ) { Text(stringResource(R.string.pause_all)) }
            YPrimaryActionButton(
                onClick = {
                    items.filter { it.state == DownloadState.PAUSED }.forEach { task ->
                        if (task.backend == DownloadBackend.ENHANCED) {
                            DownloadService.start(context, task.id)
                        } else {
                            val systemId = task.systemId ?: return@forEach
                            scope.launch {
                                val supported = withContext(Dispatchers.IO) {
                                    SystemDownloadBridge.resume(context, systemId)
                                }
                                if (supported) {
                                    store.update(task.id) {
                                        it.copy(
                                            state = DownloadState.QUEUED,
                                            error = null,
                                            speedBytesPerSecond = 0L,
                                            etaMillis = -1L,
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                enabled = pausedCount > 0,
            ) { Text(stringResource(R.string.resume_all)) }
        }
        YHorizontalActions {
            YSecondaryActionButton(
                onClick = {
                    items.filter { it.state == DownloadState.FAILED }.forEach { task ->
                        if (task.backend == DownloadBackend.ENHANCED) {
                            store.update(task.id) {
                                it.copy(
                                    retryCount = 0,
                                    error = null,
                                    state = DownloadState.QUEUED,
                                    speedBytesPerSecond = 0L,
                                    etaMillis = -1L,
                                )
                            }
                            DownloadService.start(context, task.id)
                        } else {
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    task.systemId?.let { SystemDownloadBridge.remove(context, it) }
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
                                    SystemDownloadBridge.enqueue(
                                        context,
                                        task.copy(systemId = null, state = DownloadState.QUEUED),
                                    ).onSuccess { systemId ->
                                        store.update(task.id) {
                                            it.copy(systemId = systemId, state = DownloadState.QUEUED, error = null)
                                        }
                                    }.onFailure { error ->
                                        store.update(task.id) {
                                            it.copy(state = DownloadState.FAILED, error = error.message)
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                enabled = failedCount > 0,
            ) { Text(stringResource(R.string.retry_failed)) }
            YSecondaryActionButton(
                onClick = {
                    items.filter {
                        it.state == DownloadState.COMPLETED || it.state == DownloadState.CANCELLED
                    }.forEach { store.remove(it.id) }
                },
                enabled = finishedCount > 0,
            ) { Text(stringResource(R.string.clear_finished)) }
        }
        if (hashedItems.isNotEmpty()) {
            Text(stringResource(R.string.ydownload_sha256))
            hashedItems.take(3).forEach { task ->
                YStatusLine(task.fileName, task.sha256.orEmpty(), YStatusTone.Good)
            }
        }
    }
}
