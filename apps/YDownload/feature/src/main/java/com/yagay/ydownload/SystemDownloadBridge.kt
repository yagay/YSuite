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
import com.yagay.suite.api.HostLogLevel
import java.io.FileInputStream
import java.security.MessageDigest
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

        item.requestHeaders.forEach { (name, value) -> request.addRequestHeader(name, value) }
        guessMimeType(item.fileName)?.let(request::setMimeType)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            request.setRequiresCharging(settings.requireCharging)
            request.setRequiresDeviceIdle(settings.requireDeviceIdle)
        }

        val manager = context.getSystemService(DownloadManager::class.java)
            ?: error("DownloadManager unavailable")
        manager.enqueue(request)
    }.onFailure {
        YDownloadSuiteRuntime.log(HostLogLevel.WARN, "system enqueue failed safely", it)
    }

    fun pause(context: Context, systemId: Long): Boolean = setControl(context, systemId, CONTROL_PAUSED)

    fun resume(context: Context, systemId: Long): Boolean = setControl(context, systemId, CONTROL_RUN)

    fun remove(context: Context, systemId: Long): Boolean = runCatching {
        samples.remove(systemId)
        val manager = context.getSystemService(DownloadManager::class.java) ?: return@runCatching false
        manager.remove(systemId) > 0
    }.onFailure {
        YDownloadSuiteRuntime.log(HostLogLevel.WARN, "system remove failed safely id=$systemId", it)
    }.getOrDefault(false)

    fun openUri(context: Context, systemId: Long): Uri? = runCatching {
        context.getSystemService(DownloadManager::class.java)?.getUriForDownloadedFile(systemId)
    }.onFailure {
        YDownloadSuiteRuntime.log(HostLogLevel.WARN, "system open URI failed safely id=$systemId", it)
    }.getOrNull()

    /** Polling is UI telemetry only. Provider/OEM errors must never cancel the Compose effect. */
    fun sync(context: Context, store: DownloadStore) {
        runCatching { syncInternal(context, store) }
            .onFailure {
                YDownloadSuiteRuntime.log(HostLogLevel.WARN, "system download sync failed safely", it)
            }
    }

    private fun syncInternal(context: Context, store: DownloadStore) {
        val systemItems = store.items.value.filter { it.backend == DownloadBackend.SYSTEM && it.systemId != null }
        if (systemItems.isEmpty()) return
        val ids = systemItems.mapNotNull { it.systemId }.toLongArray()
        if (ids.isEmpty()) return
        val manager = context.getSystemService(DownloadManager::class.java) ?: return
        val calculateFingerprints = YDownloadEnhancedSettings.load(context).calculateSha256
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
                val providerState = when (status) {
                    DownloadManager.STATUS_PENDING -> DownloadState.QUEUED
                    DownloadManager.STATUS_RUNNING -> DownloadState.RUNNING
                    DownloadManager.STATUS_PAUSED -> DownloadState.PAUSED
                    DownloadManager.STATUS_SUCCESSFUL -> DownloadState.COMPLETED
                    DownloadManager.STATUS_FAILED -> DownloadState.FAILED
                    else -> item.state
                }
                val telemetry = telemetry(systemId, providerState, done, total)

                var sha256 = item.sha256
                if (providerState == DownloadState.COMPLETED &&
                    (calculateFingerprints || item.expectedSha256 != null) &&
                    shouldRefreshCompletedHash(item)
                ) {
                    sha256 = calculateSha256(manager, systemId)
                }
                val checksumMismatch = providerState == DownloadState.COMPLETED &&
                    item.expectedSha256 != null &&
                    (sha256 == null || !sha256.equals(item.expectedSha256, ignoreCase = true))
                val state = if (checksumMismatch) DownloadState.FAILED else providerState
                val error = when {
                    checksumMismatch -> context.getString(R.string.sha256_mismatch)
                    status == DownloadManager.STATUS_FAILED -> "DownloadManager reason=$reason"
                    else -> null
                }

                store.update(item.id) {
                    it.copy(
                        state = state,
                        done = done,
                        total = total,
                        uri = localUri,
                        error = error,
                        sha256 = sha256,
                        requestHeaders = if (state == DownloadState.COMPLETED) emptyMap() else it.requestHeaders,
                        speedBytesPerSecond = telemetry.speedBytesPerSecond,
                        etaMillis = telemetry.etaMillis,
                    )
                }
                if (checksumMismatch) {
                    YDownloadSuiteRuntime.log(
                        HostLogLevel.WARN,
                        "system SHA-256 mismatch id=${item.id} expected=${item.expectedSha256} actual=${sha256 ?: "unavailable"}",
                    )
                }
            }
        }
    }

    private fun shouldRefreshCompletedHash(item: DownloadItem): Boolean =
        item.sha256 == null || item.state == DownloadState.RUNNING || item.state == DownloadState.QUEUED || item.state == DownloadState.PAUSED

    private fun calculateSha256(manager: DownloadManager, systemId: Long): String? = runCatching {
        val digest = MessageDigest.getInstance("SHA-256")
        manager.openDownloadedFile(systemId).use { descriptor ->
            FileInputStream(descriptor.fileDescriptor).use { input ->
                val buffer = ByteArray(HASH_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (count > 0) digest.update(buffer, 0, count)
                }
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }.onFailure {
        YDownloadSuiteRuntime.log(HostLogLevel.WARN, "system SHA-256 calculation failed id=$systemId", it)
    }.getOrNull()

    private fun setControl(context: Context, systemId: Long, control: Int): Boolean = runCatching {
        val uri = ContentUris.withAppendedId(ownDownloads, systemId)
        val values = ContentValues(1).apply { put(COLUMN_CONTROL, control) }
        context.contentResolver.update(uri, values, null, null) > 0
    }.onFailure {
        YDownloadSuiteRuntime.log(
            HostLogLevel.WARN,
            "system DownloadProvider control unsupported id=$systemId control=$control",
            it,
        )
    }.getOrDefault(false)

    private fun telemetry(systemId: Long, state: DownloadState, done: Long, total: Long): DownloadTelemetry {
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
            if (elapsed > 0L && delta >= 0L) {
                val instant = delta * 1000L / elapsed
                speed = if (speed > 0L) (speed * 2L + instant) / 3L else instant
            }
        }
        samples[systemId] = TelemetrySample(done, now, speed)
        val eta = if (speed > 0L && total > done && total > 0L) (total - done) * 1000L / speed else -1L
        return DownloadTelemetry(speed, eta)
    }

    private fun int(cursor: Cursor, index: Int, fallback: Int): Int = if (index >= 0) cursor.getInt(index) else fallback
    private fun long(cursor: Cursor, index: Int, fallback: Long): Long = if (index >= 0) cursor.getLong(index) else fallback
    private fun string(cursor: Cursor, index: Int): String? = if (index >= 0) cursor.getString(index) else null

    private fun guessMimeType(fileName: String): String? {
        val extension = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        if (extension.isBlank()) return null
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
    }

    private const val COLUMN_CONTROL = "control"
    private const val CONTROL_RUN = 0
    private const val CONTROL_PAUSED = 1
    private const val HASH_BUFFER_SIZE = 256 * 1024

    private data class TelemetrySample(
        val done: Long,
        val elapsedRealtimeMs: Long,
        val speedBytesPerSecond: Long,
    )

    private data class DownloadTelemetry(
        val speedBytesPerSecond: Long = 0L,
        val etaMillis: Long = -1L,
    )
}
