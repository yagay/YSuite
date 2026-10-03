package com.yagay.ydownload

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.core.content.ContextCompat
import com.yagay.suite.api.HostLogLevel
import java.io.BufferedInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class DownloadService : Service() {
    private lateinit var executor: java.util.concurrent.ExecutorService
    private val active = ConcurrentHashMap.newKeySet<Long>()
    private val pauses = ConcurrentHashMap<Long, AtomicBoolean>()
    private val cancels = ConcurrentHashMap<Long, AtomicBoolean>()
    private lateinit var store: DownloadStore
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        store = DownloadStore.get(this)
        val concurrency = YDownloadEnhancedSettings.load(this).maxConcurrent
        executor = Executors.newFixedThreadPool(concurrency)
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.download_channel), NotificationManager.IMPORTANCE_LOW)
        )
        wakeLock = getSystemService(PowerManager::class.java)
            ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "YDownload:active")
            ?.apply { setReferenceCounted(false) }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(FOREGROUND_NOTIFICATION_ID, serviceNotification())
        val id = intent?.getLongExtra(EXTRA_ID, -1L) ?: -1L
        when (intent?.action) {
            ACTION_START -> if (id > 0L) startDownload(id)
            ACTION_PAUSE -> if (id > 0L) pauseDownload(id)
            ACTION_CANCEL -> if (id > 0L) cancelDownload(id)
        }
        stopIfIdle()
        return START_NOT_STICKY
    }

    private fun startDownload(id: Long) {
        if (!active.add(id)) return
        ensureWakeLock()
        pauses.getOrPut(id) { AtomicBoolean() }.set(false)
        cancels.getOrPut(id) { AtomicBoolean() }.set(false)
        executor.execute {
            var connection: HttpURLConnection? = null
            var retryRequested = false
            var retryDelayMs = 0L
            try {
                val initial = store.get(id) ?: return@execute
                val taskSettings = YDownloadEnhancedSettings.load(this)
                if (cancels[id]?.get() == true) {
                    markCancelled(id, initial.uri?.let(Uri::parse))
                    return@execute
                }
                if (pauses[id]?.get() == true) {
                    val paused = store.update(id) {
                        it.copy(state = DownloadState.PAUSED, speedBytesPerSecond = 0L, etaMillis = -1L)
                    }
                    paused?.let(::notifyTask)
                    return@execute
                }

                store.update(id) {
                    it.copy(
                        state = DownloadState.RUNNING,
                        error = null,
                        sha256 = null,
                        speedBytesPerSecond = 0L,
                        etaMillis = -1L,
                    )
                }?.let(::notifyTask)

                var uri = initial.uri?.let(Uri::parse)
                if (uri == null) uri = createDestination(initial.fileName)
                if (uri == null) throw IllegalStateException("Unable to create destination")
                val destination = uri

                contentResolver.openFileDescriptor(destination, "rw")?.use { descriptor ->
                    FileOutputStream(descriptor.fileDescriptor).channel.use { channel ->
                        var existing = minOf(initial.done.coerceAtLeast(0L), channel.size())
                        connection = openConnection(initial, existing, taskSettings)
                        val response = connection!!.responseCode

                        if (response == HTTP_RANGE_NOT_SATISFIABLE && existing > 0L && initial.total > 0L && existing >= initial.total) {
                            finalizeDestination(destination)
                            val sha256 = if (taskSettings.calculateSha256) calculateSha256(destination, id) else null
                            store.update(id) {
                                it.copy(
                                    state = DownloadState.COMPLETED,
                                    uri = destination.toString(),
                                    done = existing,
                                    total = initial.total,
                                    error = null,
                                    retryCount = 0,
                                    sha256 = sha256,
                                    speedBytesPerSecond = 0L,
                                    etaMillis = -1L,
                                )
                            }?.let(::notifyTask)
                            return@execute
                        }

                        if (response !in 200..299) throw IllegalStateException("HTTP $response")
                        if (existing > 0L && response == HttpURLConnection.HTTP_OK) {
                            channel.truncate(0L)
                            channel.position(0L)
                            existing = 0L
                        } else {
                            channel.position(existing)
                        }

                        if (response == HttpURLConnection.HTTP_PARTIAL) {
                            val range = parseContentRange(connection!!.getHeaderField("Content-Range"))
                            val start = range?.first
                            if (start != null && start != existing) {
                                throw IllegalStateException("Server resumed from byte $start instead of $existing")
                            }
                        }

                        val responseEtag = connection!!.getHeaderField("ETag")?.takeIf(String::isNotBlank)
                        val responseLastModified = connection!!.getHeaderField("Last-Modified")?.takeIf(String::isNotBlank)
                        updateDestinationMime(destination, connection!!.contentType, initial.fileName)

                        val length = connection!!.contentLengthLong
                        val rangeTotal = parseContentRange(connection!!.getHeaderField("Content-Range"))?.second
                        val total = when {
                            rangeTotal != null && rangeTotal > 0L -> rangeTotal
                            length >= 0L && response == HttpURLConnection.HTTP_PARTIAL -> existing + length
                            length >= 0L -> length
                            existing > 0L && initial.total > 0L -> initial.total
                            else -> -1L
                        }
                        var done = existing
                        var lastTelemetryDone = done
                        var lastTelemetryTime = SystemClock.elapsedRealtime()
                        var throttleWindowStarted = lastTelemetryTime
                        var throttleWindowBytes = 0L
                        val speedLimitBytesPerSecond = taskSettings.speedLimitKib.toLong() * 1024L

                        store.update(id) {
                            it.copy(
                                uri = destination.toString(),
                                done = done,
                                total = total,
                                state = DownloadState.RUNNING,
                                etag = responseEtag ?: it.etag,
                                lastModified = responseLastModified ?: it.lastModified,
                                speedBytesPerSecond = 0L,
                                etaMillis = -1L,
                            )
                        }?.let(::notifyTask)

                        BufferedInputStream(connection!!.inputStream, BUFFER_SIZE).use { input ->
                            val buffer = ByteArray(BUFFER_SIZE)
                            var lastUpdate = 0L
                            while (true) {
                                if (cancels[id]?.get() == true) {
                                    markCancelled(id, destination)
                                    return@execute
                                }
                                if (pauses[id]?.get() == true) {
                                    store.update(id) {
                                        it.copy(
                                            state = DownloadState.PAUSED,
                                            uri = destination.toString(),
                                            done = done,
                                            total = total,
                                            speedBytesPerSecond = 0L,
                                            etaMillis = -1L,
                                        )
                                    }?.let(::notifyTask)
                                    return@execute
                                }
                                val count = input.read(buffer)
                                if (count < 0) break
                                channel.write(java.nio.ByteBuffer.wrap(buffer, 0, count))
                                done += count

                                if (speedLimitBytesPerSecond > 0L) {
                                    throttleWindowBytes += count
                                    val now = SystemClock.elapsedRealtime()
                                    val elapsed = now - throttleWindowStarted
                                    val expected = throttleWindowBytes * 1000L / speedLimitBytesPerSecond
                                    val sleepMillis = expected - elapsed
                                    if (sleepMillis > 0L) {
                                        SystemClock.sleep(sleepMillis.coerceAtMost(MAX_THROTTLE_SLEEP_MS))
                                    }
                                    val afterSleep = SystemClock.elapsedRealtime()
                                    if (afterSleep - throttleWindowStarted >= THROTTLE_WINDOW_RESET_MS) {
                                        throttleWindowStarted = afterSleep
                                        throttleWindowBytes = 0L
                                    }
                                }

                                val now = SystemClock.elapsedRealtime()
                                if (now - lastUpdate >= 400L) {
                                    val elapsed = (now - lastTelemetryTime).coerceAtLeast(1L)
                                    val speed = ((done - lastTelemetryDone).coerceAtLeast(0L) * 1000L / elapsed)
                                    val eta = if (speed > 0L && total > done && total > 0L) {
                                        (total - done) * 1000L / speed
                                    } else {
                                        -1L
                                    }
                                    store.update(id) {
                                        it.copy(
                                            state = DownloadState.RUNNING,
                                            uri = destination.toString(),
                                            done = done,
                                            total = total,
                                            speedBytesPerSecond = speed,
                                            etaMillis = eta,
                                        )
                                    }?.let(::notifyTask)
                                    lastUpdate = now
                                    lastTelemetryTime = now
                                    lastTelemetryDone = done
                                }
                            }
                        }
                        finalizeDestination(destination)
                        val sha256 = if (taskSettings.calculateSha256) calculateSha256(destination, id) else null
                        store.update(id) {
                            it.copy(
                                state = DownloadState.COMPLETED,
                                uri = destination.toString(),
                                done = done,
                                total = total,
                                error = null,
                                retryCount = 0,
                                sha256 = sha256,
                                speedBytesPerSecond = 0L,
                                etaMillis = -1L,
                            )
                        }?.let(::notifyTask)
                        YDownloadSuiteRuntime.log(HostLogLevel.INFO, "download completed id=$id")
                    }
                } ?: throw IllegalStateException("Unable to open destination")
            } catch (t: Throwable) {
                val current = store.get(id)
                val settings = YDownloadEnhancedSettings.load(this)
                val canRetry = current != null &&
                    current.backend == DownloadBackend.ENHANCED &&
                    settings.autoRetry &&
                    current.retryCount < settings.maxRetries &&
                    pauses[id]?.get() != true &&
                    cancels[id]?.get() != true

                if (canRetry) {
                    val nextRetry = current!!.retryCount + 1
                    retryRequested = true
                    retryDelayMs = (500L * (1L shl (nextRetry - 1).coerceIn(0, 4))).coerceAtMost(5_000L)
                    store.update(id) {
                        it.copy(
                            state = DownloadState.QUEUED,
                            retryCount = nextRetry,
                            error = t.message ?: t.javaClass.simpleName,
                            speedBytesPerSecond = 0L,
                            etaMillis = -1L,
                        )
                    }?.let(::notifyTask)
                    YDownloadSuiteRuntime.log(
                        HostLogLevel.WARN,
                        "download retry scheduled id=$id attempt=$nextRetry/${settings.maxRetries}",
                        t,
                    )
                } else {
                    store.update(id) {
                        it.copy(
                            state = DownloadState.FAILED,
                            error = t.message ?: t.javaClass.simpleName,
                            speedBytesPerSecond = 0L,
                            etaMillis = -1L,
                        )
                    }?.let(::notifyTask)
                    YDownloadSuiteRuntime.log(HostLogLevel.ERROR, "download failed id=$id", t)
                }
            } finally {
                connection?.disconnect()
                active.remove(id)
                pauses.remove(id)
                cancels.remove(id)
                if (retryRequested) {
                    if (retryDelayMs > 0L) SystemClock.sleep(retryDelayMs)
                    startDownload(id)
                } else {
                    stopIfIdle()
                }
            }
        }
    }

    private fun openConnection(
        item: DownloadItem,
        existing: Long,
        settings: YDownloadEnhancedSettings,
    ): HttpURLConnection = (URL(item.url).openConnection() as HttpURLConnection).apply {
        instanceFollowRedirects = true
        connectTimeout = 15_000
        readTimeout = 30_000
        setRequestProperty("User-Agent", settings.userAgent)
        if (existing > 0L) {
            setRequestProperty("Range", "bytes=$existing-")
            (item.etag ?: item.lastModified)?.let { validator ->
                setRequestProperty("If-Range", validator)
            }
        }
        connect()
    }

    private fun pauseDownload(id: Long) {
        if (id in active) {
            pauses.getOrPut(id) { AtomicBoolean() }.set(true)
        } else {
            store.update(id) { current ->
                if (current.state == DownloadState.QUEUED || current.state == DownloadState.RUNNING) {
                    current.copy(state = DownloadState.PAUSED, speedBytesPerSecond = 0L, etaMillis = -1L)
                } else {
                    current
                }
            }?.let(::notifyTask)
        }
    }

    private fun cancelDownload(id: Long) {
        if (id in active) {
            cancels.getOrPut(id) { AtomicBoolean() }.set(true)
            return
        }
        val current = store.get(id) ?: return
        markCancelled(id, current.uri?.let(Uri::parse))
    }

    private fun markCancelled(id: Long, destination: Uri?) {
        destination?.let { runCatching { contentResolver.delete(it, null, null) } }
        store.update(id) {
            it.copy(
                state = DownloadState.CANCELLED,
                uri = null,
                done = 0L,
                total = -1L,
                error = null,
                retryCount = 0,
                etag = null,
                lastModified = null,
                sha256 = null,
                speedBytesPerSecond = 0L,
                etaMillis = -1L,
            )
        }
        getSystemService(NotificationManager::class.java).cancel(taskNotificationId(id))
    }

    @Synchronized
    private fun stopIfIdle() {
        if (active.isNotEmpty()) return
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun ensureWakeLock() {
        val lock = wakeLock ?: return
        if (!lock.isHeld) runCatching { lock.acquire() }
    }

    private fun releaseWakeLock() {
        val lock = wakeLock ?: return
        if (lock.isHeld) runCatching { lock.release() }
    }

    private fun createDestination(fileName: String): Uri? {
        val safeName = sanitizeFileName(fileName)
        return contentResolver.insert(
            MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
            ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, safeName)
                put(MediaStore.MediaColumns.MIME_TYPE, guessMimeType(safeName) ?: "application/octet-stream")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/YDownload")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        )
    }

    private fun finalizeDestination(destination: Uri) {
        runCatching {
            contentResolver.update(
                destination,
                ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                null,
                null,
            )
        }
    }

    private fun updateDestinationMime(destination: Uri, contentType: String?, fileName: String) {
        val mime = contentType
            ?.substringBefore(';')
            ?.trim()
            ?.takeIf { it.contains('/') }
            ?: guessMimeType(fileName)
            ?: return
        runCatching {
            contentResolver.update(
                destination,
                ContentValues().apply { put(MediaStore.MediaColumns.MIME_TYPE, mime) },
                null,
                null,
            )
        }
    }

    private fun calculateSha256(destination: Uri, id: Long): String? = runCatching {
        val digest = MessageDigest.getInstance("SHA-256")
        val input = contentResolver.openInputStream(destination)
            ?: error("Unable to reopen downloaded file for SHA-256")
        input.use { stream ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                if (count > 0) digest.update(buffer, 0, count)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }.onFailure {
        YDownloadSuiteRuntime.log(HostLogLevel.WARN, "SHA-256 calculation failed id=$id", it)
    }.getOrNull()

    private fun serviceNotification(): Notification = Notification.Builder(this, CHANNEL)
        .setSmallIcon(android.R.drawable.stat_sys_download)
        .setContentTitle(getString(R.string.download_service))
        .setContentText(getString(R.string.download_running))
        .setOnlyAlertOnce(true)
        .setOngoing(true)
        .build()

    private fun notifyTask(item: DownloadItem) {
        if (item.state == DownloadState.CANCELLED) {
            getSystemService(NotificationManager::class.java).cancel(taskNotificationId(item.id))
            return
        }
        getSystemService(NotificationManager::class.java).notify(taskNotificationId(item.id), taskNotification(item))
    }

    private fun taskNotification(item: DownloadItem): Notification {
        val progressText = when {
            item.state == DownloadState.COMPLETED -> getString(R.string.completed)
            item.state == DownloadState.FAILED -> item.error ?: getString(R.string.failed)
            item.state == DownloadState.PAUSED -> getString(R.string.paused)
            item.state == DownloadState.QUEUED -> getString(R.string.queued)
            item.total > 0L -> "${((item.done * 100L) / item.total).coerceIn(0L, 100L)}%"
            else -> getString(R.string.download_running)
        }
        val text = if (item.state == DownloadState.RUNNING && item.speedBytesPerSecond > 0L) {
            "$progressText · ${formatSpeed(item.speedBytesPerSecond)}"
        } else {
            progressText
        }
        val completed = item.state == DownloadState.COMPLETED
        val terminal = item.state == DownloadState.COMPLETED || item.state == DownloadState.FAILED
        val builder = Notification.Builder(this, CHANNEL)
            .setSmallIcon(if (completed) android.R.drawable.stat_sys_download_done else android.R.drawable.stat_sys_download)
            .setContentTitle(item.fileName)
            .setContentText(text)
            .setOnlyAlertOnce(true)
            .setOngoing(!terminal && item.state != DownloadState.PAUSED)
            .setAutoCancel(terminal || item.state == DownloadState.PAUSED)

        when (item.state) {
            DownloadState.RUNNING, DownloadState.QUEUED -> {
                builder.addAction(
                    android.R.drawable.ic_media_pause,
                    getString(R.string.pause),
                    actionPendingIntent(ACTION_PAUSE, item.id, 1),
                )
                builder.addAction(
                    android.R.drawable.ic_delete,
                    getString(R.string.cancel),
                    actionPendingIntent(ACTION_CANCEL, item.id, 2),
                )
            }
            DownloadState.PAUSED -> {
                builder.addAction(
                    android.R.drawable.ic_media_play,
                    getString(R.string.resume),
                    actionPendingIntent(ACTION_START, item.id, 3),
                )
                builder.addAction(
                    android.R.drawable.ic_delete,
                    getString(R.string.cancel),
                    actionPendingIntent(ACTION_CANCEL, item.id, 4),
                )
            }
            else -> Unit
        }
        return builder.build()
    }

    private fun actionPendingIntent(action: String, id: Long, salt: Int): PendingIntent {
        val requestCode = (taskNotificationId(id) * 10) + salt
        val intent = Intent(this, DownloadService::class.java)
            .setAction(action)
            .putExtra(EXTRA_ID, id)
        return PendingIntent.getService(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun taskNotificationId(id: Long): Int =
        TASK_NOTIFICATION_BASE + ((id xor (id ushr 32)).toInt() and 0x3fffffff) % TASK_NOTIFICATION_RANGE

    private fun parseContentRange(value: String?): Pair<Long?, Long?>? {
        if (value.isNullOrBlank()) return null
        val body = value.substringAfter(' ', value).trim()
        val rangePart = body.substringBefore('/')
        val totalPart = body.substringAfter('/', "*")
        val start = rangePart.substringBefore('-').trim().toLongOrNull()
        val total = totalPart.trim().takeUnless { it == "*" }?.toLongOrNull()
        return start to total
    }

    private fun sanitizeFileName(raw: String): String {
        val clean = raw.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_")
        return clean.takeIf { it.isNotBlank() } ?: "download-${System.currentTimeMillis()}"
    }

    private fun guessMimeType(fileName: String): String? {
        val extension = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        if (extension.isBlank()) return null
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
    }

    private fun formatSpeed(bytesPerSecond: Long): String {
        val units = arrayOf("B/s", "KB/s", "MB/s", "GB/s")
        var value = bytesPerSecond.toDouble()
        var index = 0
        while (value >= 1024.0 && index < units.lastIndex) {
            value /= 1024.0
            index++
        }
        return if (index == 0) "${bytesPerSecond} B/s" else "%.1f %s".format(value, units[index])
    }

    override fun onDestroy() {
        if (::executor.isInitialized) executor.shutdownNow()
        releaseWakeLock()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.yagay.ydownload.START"
        const val ACTION_PAUSE = "com.yagay.ydownload.PAUSE"
        const val ACTION_CANCEL = "com.yagay.ydownload.CANCEL"
        const val EXTRA_ID = "id"
        private const val CHANNEL = "ydownload"
        private const val FOREGROUND_NOTIFICATION_ID = 23121
        private const val TASK_NOTIFICATION_BASE = 24000
        private const val TASK_NOTIFICATION_RANGE = 900_000
        private const val BUFFER_SIZE = 128 * 1024
        private const val HTTP_RANGE_NOT_SATISFIABLE = 416
        private const val MAX_THROTTLE_SLEEP_MS = 1_000L
        private const val THROTTLE_WINDOW_RESET_MS = 5_000L

        private fun send(context: Context, action: String, id: Long) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, DownloadService::class.java).setAction(action).putExtra(EXTRA_ID, id),
            )
        }
        fun start(context: Context, id: Long) = send(context, ACTION_START, id)
        fun pause(context: Context, id: Long) = send(context, ACTION_PAUSE, id)
        fun cancel(context: Context, id: Long) = send(context, ACTION_CANCEL, id)
    }
}
