package com.yagay.ydownload

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.IBinder
import android.os.SystemClock
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.yagay.suite.api.HostLogLevel
import java.io.BufferedInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class DownloadService : Service() {
    private val executor = Executors.newFixedThreadPool(2)
    private val active = ConcurrentHashMap.newKeySet<Long>()
    private val pauses = ConcurrentHashMap<Long, AtomicBoolean>()
    private val cancels = ConcurrentHashMap<Long, AtomicBoolean>()
    private lateinit var store: DownloadStore

    override fun onCreate() {
        super.onCreate()
        store = DownloadStore.get(this)
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.download_channel), NotificationManager.IMPORTANCE_LOW)
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, notification(null))
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
        pauses.getOrPut(id) { AtomicBoolean() }.set(false)
        cancels.getOrPut(id) { AtomicBoolean() }.set(false)
        executor.execute {
            var connection: HttpURLConnection? = null
            try {
                val initial = store.get(id) ?: return@execute
                store.update(id) {
                    it.copy(state = DownloadState.RUNNING, error = null, speedBytesPerSecond = 0L, etaMillis = -1L)
                }
                var uri = initial.uri?.let(Uri::parse)
                if (uri == null) uri = createDestination(initial.fileName)
                if (uri == null) throw IllegalStateException("Unable to create destination")
                val destination = uri

                contentResolver.openFileDescriptor(destination, "rw")?.use { descriptor ->
                    FileOutputStream(descriptor.fileDescriptor).channel.use { channel ->
                        var existing = minOf(initial.done.coerceAtLeast(0L), channel.size())
                        connection = (URL(initial.url).openConnection() as HttpURLConnection).apply {
                            instanceFollowRedirects = true
                            connectTimeout = 15_000
                            readTimeout = 30_000
                            setRequestProperty("User-Agent", "YDownload/0.1")
                            if (existing > 0L) setRequestProperty("Range", "bytes=$existing-")
                            connect()
                        }
                        val response = connection!!.responseCode
                        if (response !in 200..299) throw IllegalStateException("HTTP $response")
                        if (existing > 0L && response == HttpURLConnection.HTTP_OK) {
                            channel.truncate(0L)
                            channel.position(0L)
                            existing = 0L
                        } else {
                            channel.position(existing)
                        }
                        val length = connection!!.contentLengthLong
                        val total = if (length >= 0L) existing + length else initial.total
                        var done = existing
                        var lastTelemetryDone = done
                        var lastTelemetryTime = SystemClock.elapsedRealtime()

                        store.update(id) {
                            it.copy(
                                uri = destination.toString(),
                                done = done,
                                total = total,
                                state = DownloadState.RUNNING,
                                speedBytesPerSecond = 0L,
                                etaMillis = -1L,
                            )
                        }
                        BufferedInputStream(connection!!.inputStream, BUFFER_SIZE).use { input ->
                            val buffer = ByteArray(BUFFER_SIZE)
                            var lastUpdate = 0L
                            while (true) {
                                if (cancels[id]?.get() == true) {
                                    contentResolver.delete(destination, null, null)
                                    store.update(id) {
                                        it.copy(
                                            state = DownloadState.CANCELLED,
                                            uri = null,
                                            done = 0L,
                                            total = -1L,
                                            speedBytesPerSecond = 0L,
                                            etaMillis = -1L,
                                        )
                                    }
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
                                    }
                                    return@execute
                                }
                                val count = input.read(buffer)
                                if (count < 0) break
                                channel.write(java.nio.ByteBuffer.wrap(buffer, 0, count))
                                done += count
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
                                    }
                                    getSystemService(NotificationManager::class.java).notify(
                                        NOTIFICATION_ID,
                                        notification(store.get(id)),
                                    )
                                    lastUpdate = now
                                    lastTelemetryTime = now
                                    lastTelemetryDone = done
                                }
                            }
                        }
                        contentResolver.update(
                            destination,
                            ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                            null,
                            null,
                        )
                        store.update(id) {
                            it.copy(
                                state = DownloadState.COMPLETED,
                                uri = destination.toString(),
                                done = done,
                                total = total,
                                error = null,
                                speedBytesPerSecond = 0L,
                                etaMillis = -1L,
                            )
                        }
                        YDownloadSuiteRuntime.log(HostLogLevel.INFO, "download completed id=$id")
                    }
                } ?: throw IllegalStateException("Unable to open destination")
            } catch (t: Throwable) {
                store.update(id) {
                    it.copy(
                        state = DownloadState.FAILED,
                        error = t.message ?: t.javaClass.simpleName,
                        speedBytesPerSecond = 0L,
                        etaMillis = -1L,
                    )
                }
                YDownloadSuiteRuntime.log(HostLogLevel.ERROR, "download failed id=$id", t)
            } finally {
                connection?.disconnect()
                active.remove(id)
                pauses.remove(id)
                cancels.remove(id)
                stopIfIdle()
            }
        }
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
            }
        }
    }

    private fun cancelDownload(id: Long) {
        if (id in active) {
            cancels.getOrPut(id) { AtomicBoolean() }.set(true)
            return
        }
        val current = store.get(id) ?: return
        current.uri?.let { runCatching { contentResolver.delete(Uri.parse(it), null, null) } }
        store.update(id) {
            it.copy(
                state = DownloadState.CANCELLED,
                uri = null,
                done = 0L,
                total = -1L,
                error = null,
                speedBytesPerSecond = 0L,
                etaMillis = -1L,
            )
        }
    }

    @Synchronized
    private fun stopIfIdle() {
        if (active.isNotEmpty()) return
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createDestination(fileName: String): Uri? = contentResolver.insert(
        MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
        ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/YDownload")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
    )

    private fun notification(item: DownloadItem?): Notification {
        val progress = if ((item?.total ?: -1L) > 0L) {
            "${((item!!.done * 100L) / item.total).coerceIn(0L, 100L)}%"
        } else {
            getString(R.string.download_running)
        }
        val text = if ((item?.speedBytesPerSecond ?: 0L) > 0L) {
            "$progress · ${formatSpeed(item!!.speedBytesPerSecond)}"
        } else {
            progress
        }
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(item?.fileName ?: getString(R.string.download_service))
            .setContentText(text)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .build()
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
        executor.shutdownNow()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.yagay.ydownload.START"
        const val ACTION_PAUSE = "com.yagay.ydownload.PAUSE"
        const val ACTION_CANCEL = "com.yagay.ydownload.CANCEL"
        const val EXTRA_ID = "id"
        private const val CHANNEL = "ydownload"
        private const val NOTIFICATION_ID = 23121
        private const val BUFFER_SIZE = 128 * 1024

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
