package com.yagay.ysuite.feature.ydownload

import android.content.ContentValues
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import com.yagay.ysuite.feature.ydownload.api.YDownloadBackend
import com.yagay.ysuite.feature.ydownload.api.YDownloadChunk
import com.yagay.ysuite.feature.ydownload.api.YDownloadItem
import com.yagay.ysuite.feature.ydownload.api.YDownloadState
import com.yagay.ysuite.logging.api.YSuiteLogger
import com.yagay.ysuite.platform.api.HookGateway
import java.io.FileOutputStream
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
    private val hookGateway: HookGateway,
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
        ConcurrentHashMap<
            String,
            MutableSet<okhttp3.Call>,
        >()
    private val bandwidthLimiter =
        GlobalBandwidthLimiter()
    private val systemBridge =
        YDownloadSystemBridge(
            context = appContext,
            repository = repository,
            logger = logger,
            hookGateway = hookGateway,
        )

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
                    item.state == YDownloadState.Scheduled &&
                    (item.scheduledAtMillis ?: Long.MAX_VALUE) >
                    System.currentTimeMillis()
                ) {
                    return@withLock
                }

                if (!isNetworkConnected()) {
                    val shouldQueue =
                        config.wifiOnly ||
                            config.autoResumeNetwork
                    repository.updateState(
                        id = id,
                        state =
                            if (shouldQueue) {
                                YDownloadState.Pending
                            } else {
                                YDownloadState.Paused
                            },
                        queued = shouldQueue,
                    )
                    return@withLock
                }

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
                        if (
                            item.backend ==
                            YDownloadBackend.System
                        ) {
                            runSystemDownload(
                                item.id,
                            )
                        } else {
                            runDownload(item.id)
                        }
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
        val item =
            repository.find(id)
        if (
            item?.backend ==
            YDownloadBackend.System &&
            item.systemId != null
        ) {
            activeJobs.remove(id)
                ?.cancelAndJoin()
            if (!systemBridge.pause(item)) {
                repository.updateState(
                    id = id,
                    state =
                        YDownloadState.Paused,
                    error =
                        "System DownloadProvider pause is unavailable",
                    queued = false,
                )
            }
            pumpQueue()
            return
        }
        cancelCalls(id)
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
        val item =
            repository.find(id)
        if (
            item?.backend ==
            YDownloadBackend.System &&
            item.systemId != null
        ) {
            activeJobs.remove(id)
                ?.cancelAndJoin()
            systemBridge.cancel(item)
            pumpQueue()
            return
        }
        cancelCalls(id)
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
        cancelCalls(item.id)
        activeJobs.remove(item.id)
            ?.cancelAndJoin()

        if (
            item.backend ==
            YDownloadBackend.System
        ) {
            systemBridge.remove(item)
        }

        if (
            deleteFile ||
            item.state != YDownloadState.Completed
        ) {
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

    private suspend fun runSystemDownload(
        id: String,
    ) {
        try {
            val item =
                repository.find(id)
                    ?: return
            systemBridge.run(item)
        } catch (
            cancelled:
                CancellationException,
        ) {
            throw cancelled
        } catch (error: Throwable) {
            repository.updateState(
                id = id,
                state =
                    YDownloadState.Failed,
                error =
                    error.message
                        ?: error.javaClass
                            .simpleName,
                queued = false,
            )
            logger.error(
                TAG,
                "System download failed: " + id,
                error,
            )
        } finally {
            activeJobs.remove(id)
            pumpQueue()
        }
    }

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

            val finalTotal =
                if (
                    item.threadCount > 1 &&
                    item.supportsRanges &&
                    item.totalBytes > 0L
                ) {
                    try {
                        downloadSegmented(
                            id = id,
                            item = item,
                            outputUri = outputUri,
                        )
                    } catch (
                        rangeUnsupported:
                            RangeUnsupportedException,
                    ) {
                        logger.debug(
                            TAG,
                            "Server rejected segmented ranges; " +
                                "falling back to one connection",
                        )
                        cancelCalls(id)
                        repository.clearChunks(id)
                        repository.resetProgress(id)
                        truncateOutput(outputUri)
                        downloadSingle(
                            id = id,
                            item =
                                item.copy(
                                    downloadedBytes = 0L,
                                    chunks = emptyList(),
                                ),
                            outputUri = outputUri,
                            forceRestart = true,
                        )
                    }
                } else {
                    downloadSingle(
                        id = id,
                        item = item,
                        outputUri = outputUri,
                    )
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
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            currentCoroutineContext().ensureActive()

            val networkInterruption =
                error is SocketException ||
                    error is ConnectException ||
                    error is UnknownHostException ||
                    error is SocketTimeoutException
            val config =
                settings.settings.value
            val shouldQueueForNetwork =
                networkInterruption &&
                    (
                        config.wifiOnly ||
                            config.autoResumeNetwork
                    )

            repository.updateState(
                id = id,
                state =
                    if (shouldQueueForNetwork) {
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
                queued = shouldQueueForNetwork,
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
            cancelCalls(id)
            activeJobs.remove(id)
            pumpQueue()
        }
    }

    private suspend fun downloadSingle(
        id: String,
        item: YDownloadItem,
        outputUri: Uri,
        forceRestart: Boolean = false,
    ): Long {
        var resumeFrom =
            if (forceRestart) {
                0L
            } else {
                item.downloadedBytes.coerceAtLeast(0L)
            }
        val requestBuilder =
            Request.Builder()
                .url(item.url)
                .get()
                .header("Accept-Encoding", "identity")
                .applyHeaders(
                    item.referer,
                    item.userAgent,
                    item.cookies,
                    item.username,
                    item.password,
                    item.customHeaders,
                )
        if (resumeFrom > 0L) {
            requestBuilder.header(
                "Range",
                "bytes=" + resumeFrom + "-",
            )
        }

        val call =
            client.newCall(requestBuilder.build())
        registerCall(id, call)
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    error("HTTP " + response.code)
                }
                val body =
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
                        ?: body.contentLength()
                            .takeIf { it >= 0L }
                            ?.let { it + resumeFrom }
                        ?: item.totalBytes

                val taskLimiter =
                    GlobalBandwidthLimiter()
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
                        var lastUpdate = 0L

                        body.byteStream().use { input ->
                            val buffer = ByteArray(BUFFER_SIZE)
                            while (true) {
                                currentCoroutineContext()
                                    .ensureActive()
                                val count = input.read(buffer)
                                if (count < 0) break

                                throttle(
                                    count = count,
                                    item = item,
                                    taskLimiter = taskLimiter,
                                )

                                val byteBuffer =
                                    java.nio.ByteBuffer.wrap(
                                        buffer,
                                        0,
                                        count,
                                    )
                                while (byteBuffer.hasRemaining()) {
                                    channel.write(byteBuffer)
                                }
                                downloaded += count
                                windowBytes += count

                                val now =
                                    System.currentTimeMillis()
                                if (
                                    now - lastUpdate >=
                                    PROGRESS_INTERVAL_MILLIS
                                ) {
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
                                    lastUpdate = now
                                    if (elapsed >= 1000L) {
                                        windowBytes = 0L
                                        windowStarted = now
                                    }
                                }
                            }
                        }
                        channel.force(true)
                        repository.updateProgress(
                            id = id,
                            downloadedBytes = downloaded,
                            totalBytes =
                                if (totalBytes > 0L) {
                                    totalBytes
                                } else {
                                    downloaded
                                },
                            speed = 0L,
                            etaSeconds = 0L,
                            supportsRanges = supportsRanges,
                        )
                        return if (totalBytes > 0L) {
                            totalBytes
                        } else {
                            downloaded
                        }
                    }
                } ?: error("Unable to open destination")
            }
        } finally {
            unregisterCall(id, call)
        }
    }

    private suspend fun downloadSegmented(
        id: String,
        item: YDownloadItem,
        outputUri: Uri,
    ): Long = coroutineScope {
        val totalBytes = item.totalBytes
        val threadCount =
            item.threadCount.coerceIn(1, 16)
        val stored =
            item.chunks.takeIf {
                validChunks(
                    chunks = it,
                    totalBytes = totalBytes,
                )
            }
        val chunks =
            stored ?: splitIntoChunks(
                totalBytes = totalBytes,
                count = threadCount,
            ).also {
                prepareSegmentedOutput(
                    outputUri = outputUri,
                    totalBytes = totalBytes,
                )
                repository.setChunks(id, it)
            }
        val mutableChunks = chunks.toMutableList()
        val progressMutex = Mutex()
        val taskLimiter =
            GlobalBandwidthLimiter()
        var windowBytes = 0L
        var windowStarted =
            System.currentTimeMillis()
        var lastUpdate = 0L

        mutableChunks.mapIndexed { index, initial ->
            async(Dispatchers.IO) {
                val length = initial.length
                if (
                    length <= 0L ||
                    initial.downloadedBytes >= length
                ) {
                    return@async
                }
                val start =
                    initial.startByte +
                        initial.downloadedBytes
                val end = initial.endByte
                val builder =
                    Request.Builder()
                        .url(item.url)
                        .get()
                        .header(
                            "Range",
                            "bytes=$start-$end",
                        )
                        .header(
                            "Accept-Encoding",
                            "identity",
                        )
                        .applyHeaders(
                            item.referer,
                            item.userAgent,
                            item.cookies,
                            item.username,
                            item.password,
                            item.customHeaders,
                        )
                val call =
                    client.newCall(builder.build())
                registerCall(id, call)
                try {
                    call.execute().use { response ->
                        if (response.code != 206) {
                            throw RangeUnsupportedException(
                                "Expected HTTP 206 but got " +
                                    response.code,
                            )
                        }
                        val body =
                            response.body
                                ?: error("Empty response body")
                        resolver.openFileDescriptor(
                            outputUri,
                            "rw",
                        )?.use { descriptor ->
                            FileOutputStream(
                                descriptor.fileDescriptor,
                            ).channel.use { channel ->
                                var writePosition = start
                                channel.position(writePosition)
                                val input = body.byteStream()
                                input.use {
                                    val buffer =
                                        ByteArray(BUFFER_SIZE)
                                    while (
                                        writePosition <= end
                                    ) {
                                        currentCoroutineContext()
                                            .ensureActive()
                                        val count =
                                            input.read(buffer)
                                        if (count < 0) break
                                        val allowed =
                                            minOf(
                                                count.toLong(),
                                                end -
                                                    writePosition +
                                                    1L,
                                            ).toInt()
                                        if (allowed <= 0) break

                                        throttle(
                                            count = allowed,
                                            item = item,
                                            taskLimiter =
                                                taskLimiter,
                                        )

                                        val byteBuffer =
                                            java.nio.ByteBuffer.wrap(
                                                buffer,
                                                0,
                                                allowed,
                                            )
                                        while (
                                            byteBuffer
                                                .hasRemaining()
                                        ) {
                                            channel.write(
                                                byteBuffer,
                                            )
                                        }
                                        writePosition += allowed

                                        progressMutex.withLock {
                                            val old =
                                                mutableChunks[
                                                    index
                                                ]
                                            mutableChunks[index] =
                                                old.copy(
                                                    downloadedBytes =
                                                        (
                                                            old
                                                                .downloadedBytes +
                                                                allowed
                                                        ).coerceAtMost(
                                                            old.length,
                                                        ),
                                                )
                                            windowBytes += allowed
                                            val now =
                                                System
                                                    .currentTimeMillis()
                                            val completed =
                                                mutableChunks[
                                                    index
                                                ].completed
                                            if (
                                                completed ||
                                                now - lastUpdate >=
                                                PROGRESS_INTERVAL_MILLIS
                                            ) {
                                                val aggregate =
                                                    mutableChunks
                                                        .sumOf {
                                                            it.downloadedBytes
                                                        }
                                                        .coerceAtMost(
                                                            totalBytes,
                                                        )
                                                val elapsed =
                                                    (
                                                        now -
                                                            windowStarted
                                                    ).coerceAtLeast(
                                                        1L,
                                                    )
                                                val speed =
                                                    windowBytes *
                                                        1000L /
                                                        elapsed
                                                val remaining =
                                                    (
                                                        totalBytes -
                                                            aggregate
                                                    ).coerceAtLeast(
                                                        0L,
                                                    )
                                                val eta =
                                                    if (
                                                        speed > 0L &&
                                                        remaining >
                                                        0L
                                                    ) {
                                                        remaining /
                                                            speed
                                                    } else {
                                                        0L
                                                    }
                                                repository
                                                    .updateChunks(
                                                        id = id,
                                                        chunks =
                                                            mutableChunks
                                                                .toList(),
                                                        downloadedBytes =
                                                            aggregate,
                                                        totalBytes =
                                                            totalBytes,
                                                        speed = speed,
                                                        etaSeconds =
                                                            eta,
                                                    )
                                                lastUpdate = now
                                                if (
                                                    elapsed >=
                                                    1000L
                                                ) {
                                                    windowBytes = 0L
                                                    windowStarted = now
                                                }
                                            }
                                        }
                                    }
                                }
                                channel.force(false)
                            }
                        } ?: error(
                            "Unable to open destination",
                        )
                    }
                } finally {
                    unregisterCall(id, call)
                }
            }
        }.awaitAll()

        val downloaded =
            mutableChunks.sumOf {
                it.downloadedBytes
            }.coerceAtMost(totalBytes)
        if (
            downloaded != totalBytes ||
            mutableChunks.any { !it.completed }
        ) {
            error(
                "Segmented download incomplete: " +
                    "$downloaded/$totalBytes",
            )
        }
        repository.updateChunks(
            id = id,
            chunks = mutableChunks,
            downloadedBytes = totalBytes,
            totalBytes = totalBytes,
            speed = 0L,
            etaSeconds = 0L,
        )
        totalBytes
    }

    private suspend fun throttle(
        count: Int,
        item: YDownloadItem,
        taskLimiter: GlobalBandwidthLimiter,
    ) {
        bandwidthLimiter.acquire(
            byteCount = count,
            bytesPerSecond =
                settings.settings.value
                    .globalSpeedLimitBytesPerSecond,
        )
        taskLimiter.acquire(
            byteCount = count,
            bytesPerSecond =
                item.speedLimitBytesPerSecond,
        )
    }

    private fun registerCall(
        id: String,
        call: okhttp3.Call,
    ) {
        activeCalls
            .computeIfAbsent(id) {
                ConcurrentHashMap
                    .newKeySet<okhttp3.Call>()
            }
            .add(call)
    }

    private fun unregisterCall(
        id: String,
        call: okhttp3.Call,
    ) {
        activeCalls[id]?.let { calls ->
            calls.remove(call)
            if (calls.isEmpty()) {
                activeCalls.remove(id, calls)
            }
        }
    }

    private fun cancelCalls(id: String) {
        activeCalls.remove(id)
            ?.forEach { call ->
                runCatching { call.cancel() }
            }
    }

    private fun splitIntoChunks(
        totalBytes: Long,
        count: Int,
    ): List<YDownloadChunk> {
        val actualCount =
            minOf(
                count.coerceIn(1, 16),
                totalBytes.coerceAtMost(Int.MAX_VALUE.toLong())
                    .toInt()
                    .coerceAtLeast(1),
            )
        val baseSize =
            totalBytes / actualCount
        val remainder =
            totalBytes % actualCount
        var start = 0L
        return List(actualCount) { index ->
            val size =
                baseSize +
                    if (index < remainder) 1L else 0L
            val end = start + size - 1L
            YDownloadChunk(
                startByte = start,
                endByte = end,
                downloadedBytes = 0L,
            ).also {
                start = end + 1L
            }
        }
    }

    private fun validChunks(
        chunks: List<YDownloadChunk>,
        totalBytes: Long,
    ): Boolean {
        if (
            chunks.isEmpty() ||
            totalBytes <= 0L ||
            chunks.first().startByte != 0L ||
            chunks.last().endByte != totalBytes - 1L
        ) {
            return false
        }
        var expectedStart = 0L
        return chunks.all { chunk ->
            val valid =
                chunk.startByte == expectedStart &&
                    chunk.endByte >= chunk.startByte &&
                    chunk.downloadedBytes in
                    0L..chunk.length
            expectedStart = chunk.endByte + 1L
            valid
        }
    }

    private fun prepareSegmentedOutput(
        outputUri: Uri,
        totalBytes: Long,
    ) {
        resolver.openFileDescriptor(
            outputUri,
            "rw",
        )?.use { descriptor ->
            FileOutputStream(
                descriptor.fileDescriptor,
            ).channel.use { channel ->
                channel.truncate(0L)
                if (totalBytes > 0L) {
                    channel.position(totalBytes - 1L)
                    channel.write(
                        java.nio.ByteBuffer.wrap(
                            byteArrayOf(0),
                        ),
                    )
                }
                channel.force(true)
            }
        } ?: error("Unable to prepare destination")
    }

    private fun truncateOutput(
        outputUri: Uri,
    ) {
        resolver.openFileDescriptor(
            outputUri,
            "rw",
        )?.use { descriptor ->
            FileOutputStream(
                descriptor.fileDescriptor,
            ).channel.use { channel ->
                channel.truncate(0L)
                channel.force(true)
            }
        }
    }

    private class RangeUnsupportedException(
        message: String,
    ) : IllegalStateException(message)

    private fun createOutput(
        item: YDownloadItem,
    ): Uri {
        val customTree =
            item.destinationTreeUri
                ?: settings.settings.value.defaultTreeUri
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

    private fun isNetworkConnected(): Boolean {
        val network =
            connectivity.activeNetwork
                ?: return false
        val capabilities =
            connectivity.getNetworkCapabilities(network)
                ?: return false
        return capabilities.hasCapability(
            NetworkCapabilities.NET_CAPABILITY_INTERNET,
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
        const val BUFFER_SIZE = 64 * 1024
        const val PROGRESS_INTERVAL_MILLIS = 250L
    }
}
