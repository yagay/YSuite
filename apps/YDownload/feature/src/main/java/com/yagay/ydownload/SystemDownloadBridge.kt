package com.yagay.ydownload

import android.app.DownloadManager
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import android.webkit.MimeTypeMap
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Primary YDownload engine: keep Android DownloadProvider/DownloadManager as the owner and enrich
 * requests around it. The private HTTP engine remains available only as an explicit fallback.
 */
object SystemDownloadBridge {
    private val samples = ConcurrentHashMap<Long, TelemetrySample>()
    private val ownDownloads = Uri.parse("content://downloads/my_downloads")

    fun enqueue(context: Context, item: DownloadItem): Result<Long> = runCatching {
        val settings = YDownloadPatchSettings.load(context)
        val request = DownloadManager.Request(Uri.parse(item.url))
            .setTitle(item.fileName)
            .setDescription(item.url)
            .setAllowedOverMetered(settings.allowMetered)
            .setAllowedOverRoaming(settings.allowRoaming)
            .setNotificationVisibility(
                if (settings.forceCompletionNotification) {
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                } else {
                    DownloadManager.Request.VISIBILITY_VISIBLE
                },
            )
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, item.fileName)

        guessMimeType(item.fileName)?.let(request::setMimeType)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            request.setRequiresCharging(settings.requireCharging)
            request.setRequiresDeviceIdle(settings.requireDeviceIdle)
        }

        val manager = context.getSystemService(DownloadManager::class.java)
            ?: error("DownloadManager unavailable")
        manager.enqueue(request)
    }

    /**
     * Best-effort pause using DownloadProvider's app-owned `my_downloads` row. This deliberately
     * avoids global provider tables and never requires replacing DownloadProvider. OEM/Mainline
     * builds that reject the hidden control column simply return false and continue normally.
     */
    fun pause(context: Context, systemId: Long): Boolean = setControl(context, systemId, CONTROL_PAUSED)

    fun resume(context: Context, systemId: Long): Boolean = setControl(context, systemId, CONTROL_RUN)

    fun remove(context: Context, systemId: Long): Boolean {
        samples.remove(systemId)
        val manager = context.getSystemService(DownloadManager::class.java) ?: return false
        return manager.remove(systemId) > 0
    }

    fun openUri(context: Context, systemId: Long): Uri? =
        context.getSystemService(DownloadManager::class.java)?.getUriForDownloadedFile(systemId)

    fun sync(context: Context, store: DownloadStore) {
        val systemItems = store.items.value.filter { it.backend == DownloadBackend.SYSTEM && it.systemId != null }
        if (systemItems.isEmpty()) return
        val ids = systemItems.mapNotNull { it.systemId }.toLongArray()
        if (ids.isEmpty()) return
        val manager = context.getSystemService(DownloadManager::class.java) ?: return
        manager.query(DownloadManager.Query().setFilterById(*ids))?.use { cursor ->
            val idIndex = cursor.getColumnIndex(DownloadManager.COLUMN_ID)
            val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
            val reasonIndex = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
            val doneIndex = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
            val totalIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
            val uriIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
            while (cursor.moveToNext()) {
                if (idIndex < 0) continue
                val systemId = cursor.getLong(idIndex)
                val item = systemItems.firstOrNull { it.systemId == systemId } ?: continue
                val status = int(cursor, statusIndex, DownloadManager.STATUS_PENDING)
                val reason = int(cursor, reasonIndex, 0)
                val done = long(cursor, doneIndex, item.done)
                val total = long(cursor, totalIndex, item.total)
                val localUri = string(cursor, uriIndex) ?: item.uri
                val mappedState = when (status) {
                    DownloadManager.STATUS_PENDING -> DownloadState.QUEUED
                    DownloadManager.STATUS_RUNNING -> DownloadState.RUNNING
                    DownloadManager.STATUS_PAUSED -> DownloadState.PAUSED
                    DownloadManager.STATUS_SUCCESSFUL -> DownloadState.COMPLETED
                    DownloadManager.STATUS_FAILED -> DownloadState.FAILED
                    else -> item.state
                }
                val telemetry = telemetry(systemId, mappedState, done, total)
                val error = if (status == DownloadManager.STATUS_FAILED) "DownloadManager reason=$reason" else null
                store.update(item.id) {
                    it.copy(
                        state = mappedState,
                        done = done,
                        total = total,
                        uri = localUri,
                        error = error,
                        speedBytesPerSecond = telemetry.speedBytesPerSecond,
                        etaMillis = telemetry.etaMillis,
                    )
                }
            }
        }
    }

    private fun setControl(context: Context, systemId: Long, control: Int): Boolean = runCatching {
        val uri = ContentUris.withAppendedId(ownDownloads, systemId)
        val values = ContentValues(1).apply { put(COLUMN_CONTROL, control) }
        context.contentResolver.update(uri, values, null, null) > 0
    }.onFailure {
        YDownloadSuiteRuntime.log(
            com.yagay.suite.api.HostLogLevel.WARN,
            "system DownloadProvider control unsupported id=$systemId control=$control",
            it,
        )
    }.getOrDefault(false)

    private fun telemetry(
        systemId: Long,
        state: DownloadState,
        done: Long,
        total: Long,
    ): DownloadTelemetry {
        if (state != DownloadState.RUNNING) {
            samples.remove(systemId)
            return DownloadTelemetry()
        }

        val now = SystemClock.elapsedRealtime()
        val previous = samples[systemId]
        var speed = previous?.speedBytesPerSecond ?: 0L
        if (previous != null) {
            val elapsed = now - previous.elapsedRealtimeMs
            val delta = done - previous.done
            if (elapsed >= 400L && delta >= 0L) {
                val instant = (delta * 1000L / elapsed.coerceAtLeast(1L)).coerceAtLeast(0L)
                speed = if (previous.speedBytesPerSecond > 0L) {
                    ((previous.speedBytesPerSecond * 2L) + instant) / 3L
                } else {
                    instant
                }
            }
        }
        samples[systemId] = TelemetrySample(done, now, speed)
        val eta = if (speed > 0L && total > done && total > 0L) {
            ((total - done) * 1000L / speed).coerceAtLeast(0L)
        } else {
            -1L
        }
        return DownloadTelemetry(speed, eta)
    }

    private fun int(cursor: Cursor, index: Int, fallback: Int): Int =
        if (index >= 0 && !cursor.isNull(index)) cursor.getInt(index) else fallback

    private fun long(cursor: Cursor, index: Int, fallback: Long): Long =
        if (index >= 0 && !cursor.isNull(index)) cursor.getLong(index) else fallback

    private fun string(cursor: Cursor, index: Int): String? =
        if (index >= 0 && !cursor.isNull(index)) cursor.getString(index) else null

    private fun guessMimeType(fileName: String): String? {
        val extension = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        if (extension.isBlank()) return null
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
    }

    private data class TelemetrySample(
        val done: Long,
        val elapsedRealtimeMs: Long,
        val speedBytesPerSecond: Long,
    )

    private data class DownloadTelemetry(
        val speedBytesPerSecond: Long = 0L,
        val etaMillis: Long = -1L,
    )

    private const val COLUMN_CONTROL = "control"
    private const val CONTROL_RUN = 0
    private const val CONTROL_PAUSED = 1
}
