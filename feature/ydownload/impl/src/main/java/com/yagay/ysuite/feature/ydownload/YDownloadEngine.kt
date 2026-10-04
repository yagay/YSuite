package com.yagay.ysuite.feature.ydownload

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import com.yagay.ysuite.feature.ydownload.api.YDownloadItem
import com.yagay.ysuite.feature.ydownload.api.YDownloadState
import com.yagay.ysuite.logging.api.YSuiteLogger
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class YDownloadEngine(
    context: Context,
    private val repository: YDownloadRepository,
    private val client: OkHttpClient,
    private val logger: YSuiteLogger,
) {
    private val appContext =
        context.applicationContext
    private val resolver =
        appContext.contentResolver
    private val scope =
        CoroutineScope(
            SupervisorJob() + Dispatchers.IO,
        )
    private val activeJobs =
        ConcurrentHashMap<String, Job>()

    fun start(id: String) {
        if (activeJobs[id]?.isActive == true) return

        val job =
            scope.launch {
                runDownload(id)
            }
        activeJobs[id] = job
    }

    suspend fun pause(id: String) {
        activeJobs.remove(id)
            ?.cancelAndJoin()
        repository.updateState(
            id = id,
            state = YDownloadState.Paused,
            queued = false,
        )
    }

    suspend fun cancel(id: String) {
        activeJobs.remove(id)
            ?.cancelAndJoin()
        repository.updateState(
            id = id,
            state = YDownloadState.Cancelled,
            queued = false,
        )
    }

    suspend fun remove(
        item: YDownloadItem,
        deleteFile: Boolean,
    ) {
        activeJobs.remove(item.id)
            ?.cancelAndJoin()

        if (deleteFile) {
            item.outputUri
                ?.let(Uri::parse)
                ?.let { uri ->
                    runCatching {
                        resolver.delete(uri, null, null)
                    }
                }
        }
        repository.remove(item.id)
    }

    fun isActive(id: String): Boolean =
        activeJobs[id]?.isActive == true

    fun hasActiveDownloads(): Boolean =
        activeJobs.values.any { it.isActive }

    private suspend fun runDownload(
        id: String,
    ) {
        try {
            var item =
                repository.find(id)
                    ?: return

            repository.updateState(
                id = id,
                state = YDownloadState.Connecting,
                queued = false,
            )

            val outputUri =
                item.outputUri
                    ?.let(Uri::parse)
                    ?: createOutput(item)
                        .also {
                            repository.setOutputUri(
                                id,
                                it.toString(),
                            )
                        }

            var resumeFrom =
                item.downloadedBytes
                    .coerceAtLeast(0L)

            val requestBuilder =
                Request.Builder()
                    .url(item.url)
                    .get()
                    .applyHeaders(
                        item.referer,
                        item.userAgent,
                        item.cookies,
                        item.username,
                        item.password,
                    )

            if (resumeFrom > 0L) {
                requestBuilder.header(
                    "Range",
                    "bytes=" + resumeFrom + "-",
                )
            }

            client.newCall(
                requestBuilder.build(),
            ).execute().use { response ->
                if (!response.isSuccessful) {
                    error("HTTP " + response.code)
                }

                val responseBody =
                    response.body
                        ?: error("Empty response body")
                val resumed = response.code == 206
                val supportsRanges =
                    resumed ||
                        response.header("Accept-Ranges")
                            ?.equals(
                                "bytes",
                                ignoreCase = true,
                            ) == true

                if (resumeFrom > 0L && !resumed) {
                    resumeFrom = 0L
                    repository.resetProgress(id)
                }

                val totalFromRange =
                    response.header("Content-Range")
                        ?.substringAfterLast('/')
                        ?.toLongOrNull()
                val totalBytes =
                    totalFromRange
                        ?: responseBody.contentLength()
                            .takeIf { it >= 0L }
                            ?.let { it + resumeFrom }
                        ?: item.totalBytes

                resolver.openFileDescriptor(
                    outputUri,
                    "rw",
                )?.use { descriptor ->
                    FileOutputStream(
                        descriptor.fileDescriptor,
                    ).channel.use { channel ->
                        if (resumeFrom == 0L) {
                            channel.truncate(0L)
                        }
                        channel.position(resumeFrom)

                        var downloaded = resumeFrom
                        var windowBytes = 0L
                        var windowStarted =
                            System.currentTimeMillis()
                        var lastUiUpdate = 0L

                        responseBody.byteStream().use { input ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                scope.ensureActive()
                                val count =
                                    input.read(buffer)
                                if (count < 0) break
                                channel.write(
                                    java.nio.ByteBuffer.wrap(
                                        buffer,
                                        0,
                                        count,
                                    ),
                                )
                                downloaded += count
                                windowBytes += count

                                val now =
                                    System.currentTimeMillis()
                                if (now - lastUiUpdate >= 250L) {
                                    val elapsed =
                                        (now - windowStarted)
                                            .coerceAtLeast(1L)
                                    val speed =
                                        windowBytes * 1000L /
                                            elapsed
                                    val remaining =
                                        if (totalBytes > 0L) {
                                            (
                                                totalBytes -
                                                    downloaded
                                            ).coerceAtLeast(0L)
                                        } else {
                                            0L
                                        }
                                    val eta =
                                        if (
                                            speed > 0L &&
                                            remaining > 0L
                                        ) {
                                            remaining / speed
                                        } else {
                                            0L
                                        }

                                    repository.updateProgress(
                                        id = id,
                                        downloadedBytes = downloaded,
                                        totalBytes = totalBytes,
                                        speed = speed,
                                        etaSeconds = eta,
                                        supportsRanges =
                                            supportsRanges,
                                    )
                                    lastUiUpdate = now

                                    if (elapsed >= 1000L) {
                                        windowBytes = 0L
                                        windowStarted = now
                                    }
                                }
                            }
                        }

                        channel.force(true)
                        val finalTotal =
                            if (totalBytes > 0L) {
                                totalBytes
                            } else {
                                downloaded
                            }
                        repository.markCompleted(
                            id = id,
                            totalBytes = finalTotal,
                        )
                        publishOutput(outputUri)
                        logger.debug(
                            TAG,
                            "Completed " + item.fileName,
                        )
                    }
                } ?: error("Unable to open destination")
            }

            startNextQueued()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            repository.updateState(
                id = id,
                state = YDownloadState.Failed,
                error =
                    error.message
                        ?: "Download failed",
                queued = false,
            )
            logger.error(
                TAG,
                "Download failed: " + id,
                error,
            )
            startNextQueued()
        } finally {
            activeJobs.remove(id)
        }
    }

    private suspend fun startNextQueued() {
        val next = repository.nextQueued()
            ?: return
        start(next.id)
    }

    private fun createOutput(
        item: YDownloadItem,
    ): Uri {
        val collection =
            MediaStore.Downloads.getContentUri(
                MediaStore.VOLUME_EXTERNAL_PRIMARY,
            )
        val values = ContentValues().apply {
            put(
                MediaStore.Downloads.DISPLAY_NAME,
                item.fileName,
            )
            put(
                MediaStore.Downloads.MIME_TYPE,
                item.mimeType.ifBlank {
                    "application/octet-stream"
                },
            )
            put(
                MediaStore.Downloads.RELATIVE_PATH,
                Environment.DIRECTORY_DOWNLOADS +
                    "/YDownload",
            )
            put(
                MediaStore.Downloads.IS_PENDING,
                1,
            )
        }

        return resolver.insert(
            collection,
            values,
        ) ?: error("Unable to create destination")
    }

    private fun publishOutput(uri: Uri) {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.IS_PENDING, 0)
        }
        resolver.update(
            uri,
            values,
            null,
            null,
        )
    }

    private companion object {
        const val TAG = "YDownload/Engine"
    }
}
