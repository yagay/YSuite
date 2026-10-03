package com.yagay.ydownload

import android.app.DownloadManager
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.webkit.MimeTypeMap
import java.util.Locale

/**
 * Primary YDownload engine: keep Android DownloadProvider/DownloadManager as the owner and enrich
 * requests around it. The private HTTP engine remains available only as an explicit fallback.
 */
object SystemDownloadBridge {
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

    fun remove(context: Context, systemId: Long): Boolean {
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
                val error = if (status == DownloadManager.STATUS_FAILED) "DownloadManager reason=$reason" else null
                store.update(item.id) {
                    it.copy(
                        state = mappedState,
                        done = done,
                        total = total,
                        uri = localUri,
                        error = error,
                    )
                }
            }
        }
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
}
