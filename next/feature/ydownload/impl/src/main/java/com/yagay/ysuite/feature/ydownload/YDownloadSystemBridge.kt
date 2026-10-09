package com.yagay.ysuite.feature.ydownload

import android.app.DownloadManager
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import android.util.Base64
import com.yagay.ysuite.feature.ydownload.api.YDownloadItem
import com.yagay.ysuite.feature.ydownload.api.YDownloadState
import com.yagay.ysuite.logging.api.YSuiteLogger
import com.yagay.ysuite.platform.api.HookGateway
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.delay

internal class YDownloadSystemBridge(
    context: Context,
    private val repository: YDownloadRepository,
    private val logger: YSuiteLogger,
    private val hookGateway: HookGateway,
) {
    private val appContext =
        context.applicationContext
    private val manager =
        appContext.getSystemService(
            DownloadManager::class.java,
        )
    private val samples =
        ConcurrentHashMap<
            Long,
            TelemetrySample,
        >()
    private val ownDownloads =
        Uri.parse(
            "content://downloads/my_downloads",
        )

    suspend fun run(
        item: YDownloadItem,
    ) {
        val downloadManager =
            manager ?: error(
                "DownloadManager unavailable",
            )

        val systemId =
            item.systemId?.let { existing ->
                // A paused/pending system download remains owned by
                // DownloadProvider. Enqueuing again would create a
                // duplicate while the original may still resume.
                val cursor =
                    downloadManager.query(
                        DownloadManager.Query()
                            .setFilterById(existing),
                    ) ?: error(
                        "Unable to verify existing system download",
                    )
                val previousStatus =
                    cursor.use {
                        if (it.moveToFirst()) {
                            it.int(
                                DownloadManager.COLUMN_STATUS,
                                DownloadManager.STATUS_PENDING,
                            )
                        } else {
                            null
                        }
                    }
                when (previousStatus) {
                    null ->
                        enqueue(
                            downloadManager,
                            item,
                        )
                    DownloadManager.STATUS_FAILED -> {
                        check(remove(existing)) {
                            "Unable to remove failed system download"
                        }
                        enqueue(
                            downloadManager,
                            item,
                        )
                    }
                    else -> existing
                }
            } ?: enqueue(
                downloadManager,
                item,
            )

        if (item.systemId != systemId) {
            repository.bindSystemDownload(
                item.id,
                systemId,
            )
        } else {
            repository.updateState(
                id = item.id,
                state =
                    YDownloadState.Connecting,
                queued = false,
            )
        }

        monitor(
            itemId = item.id,
            systemId = systemId,
        )
    }

    suspend fun pause(
        item: YDownloadItem,
    ): Boolean {
        val systemId =
            item.systemId ?: return false
        val paused =
            setControl(
                systemId,
                CONTROL_PAUSED,
            )
        if (paused) {
            samples.remove(systemId)
            repository.updateState(
                id = item.id,
                state =
                    YDownloadState.Paused,
                queued = false,
            )
        }
        return paused
    }

    suspend fun cancel(
        item: YDownloadItem,
    ): Boolean {
        val systemId =
            item.systemId ?: return false
        val removed =
            remove(systemId)
        if (removed) {
            repository.updateState(
                id = item.id,
                state = YDownloadState.Cancelled,
                queued = false,
            )
        } else {
            repository.updateState(
                id = item.id,
                state = item.state,
                error = "System download cancellation failed",
                queued = item.queued,
            )
        }
        return removed
    }

    suspend fun remove(
        item: YDownloadItem,
    ): Boolean {
        val systemId =
            item.systemId ?: return true
        val removed =
            remove(systemId)
        repository.clearSystemDownload(
            item.id,
        )
        return removed
    }

    fun openUri(
        systemId: Long,
    ): Uri? =
        runCatching {
            manager?.getUriForDownloadedFile(
                systemId,
            )
        }.onFailure {
            logger.error(
                TAG,
                "Unable to resolve system download URI",
                it,
            )
        }.getOrNull()

    private fun enqueue(
        downloadManager: DownloadManager,
        item: YDownloadItem,
    ): Long {
        val patch =
            YDownloadSystemPatchStore(
                appContext,
                hookGateway,
            ).load()
        val request =
            DownloadManager.Request(
                Uri.parse(item.url),
            )
                .setTitle(item.fileName)
                .setDescription(item.url)
                .setAllowedOverMetered(
                    patch.allowMetered,
                )
                .setAllowedOverRoaming(
                    patch.allowRoaming,
                )
                .setNotificationVisibility(
                    if (
                        patch
                            .forceCompletionNotification
                    ) {
                        DownloadManager.Request
                            .VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                    } else {
                        DownloadManager.Request
                            .VISIBILITY_VISIBLE
                    },
                )
                .setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS,
                    item.fileName,
                )

        item.mimeType
            .takeIf(String::isNotBlank)
            ?.let(request::setMimeType)
        item.referer
            ?.takeIf(String::isNotBlank)
            ?.let {
                request.addRequestHeader(
                    "Referer",
                    it,
                )
            }
        item.userAgent
            ?.takeIf(String::isNotBlank)
            ?.let {
                request.addRequestHeader(
                    "User-Agent",
                    it,
                )
            }
        item.cookies
            ?.takeIf(String::isNotBlank)
            ?.let {
                request.addRequestHeader(
                    "Cookie",
                    it,
                )
            }
        item.customHeaders.forEach {
                (name, value) ->
            if (
                name.isNotBlank() &&
                value.isNotBlank()
            ) {
                request.addRequestHeader(
                    name,
                    value,
                )
            }
        }
        if (
            !item.username.isNullOrBlank() &&
            item.password != null
        ) {
            val raw =
                item.username +
                    ":" +
                    item.password
            request.addRequestHeader(
                "Authorization",
                "Basic " +
                    Base64.encodeToString(
                        raw.toByteArray(
                            StandardCharsets.UTF_8,
                        ),
                        Base64.NO_WRAP,
                    ),
            )
        }
        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.N
        ) {
            request.setRequiresCharging(
                patch.requireCharging,
            )
            request.setRequiresDeviceIdle(
                patch.requireDeviceIdle,
            )
        }
        return downloadManager.enqueue(
            request,
        )
    }

    private suspend fun monitor(
        itemId: String,
        systemId: Long,
    ) {
        val downloadManager =
            manager ?: return
        var missingCount = 0

        while (true) {
            val snapshot =
                query(
                    downloadManager,
                    systemId,
                )
            if (snapshot == null) {
                missingCount += 1
                if (missingCount >= 4) {
                    repository
                        .updateSystemSnapshot(
                            id = itemId,
                            state =
                                YDownloadState
                                    .Failed,
                            downloadedBytes =
                                0L,
                            totalBytes = -1L,
                            speedBytesPerSecond =
                                0L,
                            etaSeconds = -1L,
                            outputUri = null,
                            error =
                                "System download record disappeared",
                        )
                    samples.remove(
                        systemId,
                    )
                    return
                }
                delay(POLL_MILLIS)
                continue
            }
            missingCount = 0

            val state =
                when (snapshot.status) {
                    DownloadManager
                        .STATUS_PENDING ->
                        YDownloadState.Pending
                    DownloadManager
                        .STATUS_RUNNING ->
                        YDownloadState.Downloading
                    DownloadManager
                        .STATUS_PAUSED ->
                        YDownloadState.Paused
                    DownloadManager
                        .STATUS_SUCCESSFUL ->
                        YDownloadState.Completed
                    DownloadManager
                        .STATUS_FAILED ->
                        YDownloadState.Failed
                    else ->
                        YDownloadState.Connecting
                }

            val telemetry =
                telemetry(
                    systemId = systemId,
                    state = state,
                    done = snapshot.done,
                    total = snapshot.total,
                )

            repository.updateSystemSnapshot(
                id = itemId,
                state = state,
                downloadedBytes =
                    snapshot.done,
                totalBytes =
                    snapshot.total,
                speedBytesPerSecond =
                    telemetry.speed,
                etaSeconds =
                    telemetry.etaSeconds,
                outputUri =
                    snapshot.localUri
                        ?: if (
                            state ==
                            YDownloadState
                                .Completed
                        ) {
                            openUri(
                                systemId,
                            )?.toString()
                        } else {
                            null
                        },
                error =
                    if (
                        state ==
                        YDownloadState.Failed
                    ) {
                        "DownloadManager reason=" +
                            snapshot.reason
                    } else {
                        null
                    },
            )

            when (state) {
                YDownloadState.Completed,
                YDownloadState.Failed,
                YDownloadState.Cancelled,
                YDownloadState.Paused,
                -> {
                    samples.remove(systemId)
                    return
                }
                else ->
                    delay(POLL_MILLIS)
            }
        }
    }

    private fun query(
        downloadManager: DownloadManager,
        systemId: Long,
    ): SystemSnapshot? =
        runCatching {
            downloadManager.query(
                DownloadManager.Query()
                    .setFilterById(systemId),
            )?.use { cursor ->
                if (!cursor.moveToFirst()) {
                    return@use null
                }
                SystemSnapshot(
                    status =
                        cursor.int(
                            DownloadManager.COLUMN_STATUS,
                            DownloadManager.STATUS_PENDING,
                        ),
                    reason =
                        cursor.int(
                            DownloadManager.COLUMN_REASON,
                            0,
                        ),
                    done =
                        cursor.long(
                            DownloadManager
                                .COLUMN_BYTES_DOWNLOADED_SO_FAR,
                            0L,
                        ),
                    total =
                        cursor.long(
                            DownloadManager
                                .COLUMN_TOTAL_SIZE_BYTES,
                            -1L,
                        ),
                    localUri =
                        cursor.string(
                            DownloadManager
                                .COLUMN_LOCAL_URI,
                        ),
                )
            }
        }.onFailure {
            logger.error(
                TAG,
                "System download query failed",
                it,
            )
        }.getOrNull()

    private fun resume(
        systemId: Long,
    ): Boolean =
        setControl(
            systemId,
            CONTROL_RUN,
        )

    private fun remove(
        systemId: Long,
    ): Boolean =
        runCatching {
            samples.remove(systemId)
            (manager?.remove(systemId) ?: 0) > 0
        }.onFailure {
            logger.error(
                TAG,
                "System download remove failed",
                it,
            )
        }.getOrDefault(false)

    private fun setControl(
        systemId: Long,
        control: Int,
    ): Boolean =
        runCatching {
            val uri =
                ContentUris.withAppendedId(
                    ownDownloads,
                    systemId,
                )
            val values =
                ContentValues(1).apply {
                    put(
                        COLUMN_CONTROL,
                        control,
                    )
                }
            appContext.contentResolver
                .update(
                    uri,
                    values,
                    null,
                    null,
                ) > 0
        }.onFailure {
            logger.error(
                TAG,
                "System DownloadProvider control unavailable",
                it,
            )
        }.getOrDefault(false)

    private fun telemetry(
        systemId: Long,
        state: YDownloadState,
        done: Long,
        total: Long,
    ): Telemetry {
        if (
            state !=
            YDownloadState.Downloading
        ) {
            samples.remove(systemId)
            return Telemetry()
        }
        val now =
            SystemClock.elapsedRealtime()
        val previous = samples[systemId]
        var speed =
            previous?.speed ?: 0L
        if (previous != null) {
            val elapsed =
                now -
                    previous.elapsedRealtime
            val delta =
                done - previous.done
            if (
                elapsed > 0L &&
                delta >= 0L
            ) {
                val instant =
                    delta * 1000L /
                        elapsed
                speed =
                    if (speed > 0L) {
                        (
                            speed * 2L +
                                instant
                            ) / 3L
                    } else {
                        instant
                    }
            }
        }
        samples[systemId] =
            TelemetrySample(
                done = done,
                elapsedRealtime = now,
                speed = speed,
            )
        val eta =
            if (
                speed > 0L &&
                total > done &&
                total > 0L
            ) {
                (total - done) / speed
            } else {
                -1L
            }
        return Telemetry(
            speed = speed,
            etaSeconds = eta,
        )
    }

    private fun Cursor.int(
        column: String,
        fallback: Int,
    ): Int {
        val index = getColumnIndex(column)
        return if (index >= 0) {
            getInt(index)
        } else {
            fallback
        }
    }

    private fun Cursor.long(
        column: String,
        fallback: Long,
    ): Long {
        val index = getColumnIndex(column)
        return if (index >= 0) {
            getLong(index)
        } else {
            fallback
        }
    }

    private fun Cursor.string(
        column: String,
    ): String? {
        val index = getColumnIndex(column)
        return if (
            index >= 0 &&
            !isNull(index)
        ) {
            getString(index)
        } else {
            null
        }
    }

    private data class SystemSnapshot(
        val status: Int,
        val reason: Int,
        val done: Long,
        val total: Long,
        val localUri: String?,
    )

    private data class TelemetrySample(
        val done: Long,
        val elapsedRealtime: Long,
        val speed: Long,
    )

    private data class Telemetry(
        val speed: Long = 0L,
        val etaSeconds: Long = -1L,
    )

    companion object {
        private const val TAG =
            "YSuite/YDownloadSystem"
        private const val COLUMN_CONTROL =
            "control"
        private const val CONTROL_RUN = 0
        private const val CONTROL_PAUSED = 1
        private const val POLL_MILLIS = 750L
    }
}
