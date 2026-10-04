package com.yagay.ysuite.feature.ydownload

import android.content.ContentValues
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import com.yagay.ysuite.feature.ydownload.api.YDownloadItem
import com.yagay.ysuite.feature.ydownload.api.YDownloadState
import com.yagay.ysuite.logging.api.YSuiteLogger
import java.io.FileOutputStream
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request

class YDownloadEngine(
    context: Context,
    private val repository: YDownloadRepository,
    private val settings: YDownloadSettingsRepository,
    private val client: OkHttpClient,
    private val logger: YSuiteLogger,
) {
    private val appContext =
        context.applicationContext
    private val resolver =
        appContext.contentResolver
    private val connectivity =
        appContext.getSystemService(
            Context.CONNECTIVITY_SERVICE,
        ) as ConnectivityManager
    private val scope =
        CoroutineScope(
            SupervisorJob() + Dispatchers.IO,
        )
    private val startMutex = Mutex()
    private val activeJobs =
        ConcurrentHashMap<String, Job>()
    private val activeCalls =
        ConcurrentHashMap<String, okhttp3.Call>()
    private val bandwidthLimiter =
        GlobalBandwidthLimiter()

    fun start(id: String) {
        scope.launch {
            startMutex.withLock {
                if (activeJobs[id]?.isActive == true) {
                    return@withLock
                }

                val item =
                    repository.find(id)
                        ?: return@withLock
                val config = settings.settings.value

                if (
                    config.wifiOnly &&
                    !isWifiConnected()
                ) {
                    repository.updateState(
                        id = id,
                        state = YDownloadState.Pending,
                        queued = true,
                    )
                    return@withLock
                }

                if (
                    activeJobs.values.count { it.isActive } >=
                    config.maxConcurrentDownloads
                ) {
                    repository.updateState(
                        id = id,
                        state = YDownloadState.Pending,
                        queued = true,
                    )
                    return@withLock
                }

                val job =
                    scope.launch(
                        start = CoroutineStart.LAZY,
                    ) {
                        runDownload(item.id)
                    }
                activeJobs[id] = job
                job.start()
            }
        }
    }

    fun pumpQueue() {
        scope.launch {
            val config = settings.settings.value
            val freeSlots =
                (
                    config.maxConcurrentDownloads -
                        activeJobs.values
                            .count { it.isActive }
                ).coerceAtLeast(0)

            repeat(freeSlots) {
                val next =
                    repository.nextQueued()
                        ?: return@launch
                repository.updateState(
                    id = next.id,
                    state = YDownloadState.Pending,
                    queued = false,
                )
                start(next.id)
                delay(10L)
            }
        }
    }

    suspend fun pause(id: String) {
        activeCalls.remove(id)?.cancel()
        activeJobs.remove(id)
            ?.cancelAndJoin()
        repository.updateState(
            id = id,
            state = YDownloadState.Paused,
            queued = false,
        )
        pumpQueue()
    }

    suspend fun cancel(id: String) {
        activeCalls.remove(id)?.cancel()
        activeJobs.remove(id)
            ?.cancelAndJoin()
        repository.updateState(
            id = id,
            state = YDownloadState.Cancelled,
            queued = false,
        )
        pumpQueue()
    }

    suspend fun remove(
        item: YDownloadItem,
        deleteFile: Boolean,
    ) {
        activeCalls.remove(item.id)?.cancel()
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

    fun hasActiveDownloads(): Boolean =
        activeJobs.values.any { it.isActive }

    private suspend fun runDownload(
        id: String,
    ) {
        try {
            val item =
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

            val call =
                client.newCall(
                    requestBuilder.build(),
                )
            activeCalls[id] = call

            call.execute().use { response ->
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
                                currentCoroutineContext()
                                    .ensureActive()
                                val count =
                                    input.read(buffer)
                                if (count < 0) break

                                bandwidthLimiter.acquire(
                                    byteCount = count,
                                    bytesPerSecond =
                                        settings.settings.value
                                            .globalSpeedLimitBytesPerSecond,
                                )

                                val byteBuffer =
                                    java.nio.ByteBuffer.wrap(
                                        buffer,
                                        0,
                                        count,
                                    )
                                while (
                                    byteBuffer.hasRemaining()
                                ) {
                                    channel.write(byteBuffer)
                                }
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
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            currentCoroutineContext().ensureActive()

            val networkInterruption =
                error is SocketException ||
                    error is ConnectException ||
                    error is UnknownHostException ||
                    error is SocketTimeoutException
            val wifiOnly =
                settings.settings.value.wifiOnly

            repository.updateState(
                id = id,
                state =
                    if (
                        networkInterruption &&
                        wifiOnly
                    ) {
                        YDownloadState.Pending
                    } else if (networkInterruption) {
                        YDownloadState.Paused
                    } else {
                        YDownloadState.Failed
                    },
                error =
                    if (networkInterruption) {
                        null
                    } else {
                        error.message
                            ?: error.javaClass.simpleName
                    },
                queued =
                    networkInterruption &&
                        wifiOnly,
            )

            if (networkInterruption) {
                logger.debug(
                    TAG,
                    "Network interruption paused " + id,
                )
            } else {
                logger.error(
                    TAG,
                    "Download failed: " + id,
                    error,
                )
            }
        } finally {
            activeCalls.remove(id)
            activeJobs.remove(id)
            pumpQueue()
        }
    }

    private fun createOutput(
        item: YDownloadItem,
    ): Uri {
        val customTree =
            settings.settings.value.defaultTreeUri
        if (!customTree.isNullOrBlank()) {
            val treeUri = Uri.parse(customTree)
            val parent =
                DocumentsContract.buildDocumentUriUsingTree(
                    treeUri,
                    DocumentsContract.getTreeDocumentId(
                        treeUri,
                    ),
                )
            val created =
                DocumentsContract.createDocument(
                    resolver,
                    parent,
                    item.mimeType.ifBlank {
                        "application/octet-stream"
                    },
                    item.fileName,
                )
            if (created != null) {
                return created
            }
        }

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
        if (uri.authority != MediaStore.AUTHORITY) {
            return
        }
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

    private fun isWifiConnected(): Boolean {
        val network =
            connectivity.activeNetwork
                ?: return false
        val capabilities =
            connectivity.getNetworkCapabilities(network)
                ?: return false
        return capabilities.hasTransport(
            NetworkCapabilities.TRANSPORT_WIFI,
        )
    }

    private class GlobalBandwidthLimiter {
        private val mutex = Mutex()
        private var availableAtNanos = 0L

        suspend fun acquire(
            byteCount: Int,
            bytesPerSecond: Long,
        ) {
            if (
                byteCount <= 0 ||
                bytesPerSecond <= 0L
            ) {
                return
            }

            mutex.withLock {
                val now = System.nanoTime()
                val base =
                    maxOf(now, availableAtNanos)
                val waitNanos =
                    (base - now).coerceAtLeast(0L)
                if (waitNanos > 0L) {
                    delay(
                        (waitNanos / 1_000_000L)
                            .coerceAtLeast(1L),
                    )
                }

                val durationNanos =
                    (
                        byteCount.toDouble() /
                            bytesPerSecond.toDouble() *
                            1_000_000_000.0
                    ).toLong()
                availableAtNanos =
                    base + durationNanos
            }
        }
    }

    private companion object {
        const val TAG = "YDownload/Engine"
    }
}
