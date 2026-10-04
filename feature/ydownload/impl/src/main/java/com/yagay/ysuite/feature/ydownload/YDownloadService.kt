package com.yagay.ysuite.feature.ydownload

import android.content.Context
import android.content.Intent
import android.os.PowerManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.yagay.ysuite.feature.ydownload.api.YDownloadState
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class YDownloadService : LifecycleService() {
    private lateinit var environment:
        YDownloadEnvironment
    private lateinit var notifications:
        YDownloadNotificationManager
    private var wakeLock: PowerManager.WakeLock? = null
    @Volatile
    private var commandReceived = false

    override fun onCreate() {
        super.onCreate()

        environment =
            YDownloadRuntime.obtain(
                applicationContext,
            )
        notifications =
            YDownloadNotificationManager(
                applicationContext,
            ).also {
                it.createChannels()
            }

        startForeground(
            YDownloadNotificationManager
                .SERVICE_NOTIFICATION_ID,
            notifications.serviceNotification(),
        )
        acquireWakeLock()

        lifecycleScope.launch {
            environment.repository.refresh()
            environment.repository.items
                .collectLatest { items ->
                    notifications.update(items)
                    val hasActive =
                        items.any {
                            it.state ==
                                YDownloadState.Downloading ||
                                it.state ==
                                YDownloadState.Connecting
                        }
                    if (
                        commandReceived &&
                        !hasActive &&
                        !environment.engine
                            .hasActiveDownloads()
                    ) {
                        releaseWakeLock()
                        stopSelf()
                    }
                }
        }
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        super.onStartCommand(
            intent,
            flags,
            startId,
        )

        commandReceived = true

        if (intent == null) {
            lifecycleScope.launch {
                environment.repository
                    .restartable()
                    .forEach {
                        environment.engine.start(it.id)
                    }
            }
            return START_STICKY
        }

        val id =
            intent.getStringExtra(
                EXTRA_DOWNLOAD_ID,
            )
        if (id == null) {
            return START_STICKY
        }

        when (intent.action) {
            ACTION_START,
            ACTION_RESUME ->
                environment.engine.start(id)
            ACTION_PAUSE ->
                lifecycleScope.launch {
                    environment.engine.pause(id)
                }
            ACTION_CANCEL ->
                lifecycleScope.launch {
                    environment.engine.cancel(id)
                }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        releaseWakeLock()
        super.onDestroy()
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val manager =
            getSystemService(
                POWER_SERVICE,
            ) as PowerManager
        wakeLock =
            manager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "YSuite::YDownload",
            ).apply {
                setReferenceCounted(false)
                acquire(10L * 60L * 60L * 1000L)
            }
    }

    private fun releaseWakeLock() {
        val lock = wakeLock ?: return
        if (lock.isHeld) {
            runCatching { lock.release() }
        }
        wakeLock = null
    }

    companion object {
        const val ACTION_START =
            "com.yagay.ysuite.ydownload.START"
        const val ACTION_RESUME =
            "com.yagay.ysuite.ydownload.RESUME"
        const val ACTION_PAUSE =
            "com.yagay.ysuite.ydownload.PAUSE"
        const val ACTION_CANCEL =
            "com.yagay.ysuite.ydownload.CANCEL"
        const val EXTRA_DOWNLOAD_ID =
            "download_id"

        fun start(
            context: Context,
            id: String,
        ) {
            send(
                context,
                ACTION_START,
                id,
            )
        }

        fun resume(
            context: Context,
            id: String,
        ) {
            send(
                context,
                ACTION_RESUME,
                id,
            )
        }

        fun pause(
            context: Context,
            id: String,
        ) {
            send(
                context,
                ACTION_PAUSE,
                id,
            )
        }

        fun cancel(
            context: Context,
            id: String,
        ) {
            send(
                context,
                ACTION_CANCEL,
                id,
            )
        }

        private fun send(
            context: Context,
            action: String,
            id: String,
        ) {
            val intent =
                Intent(
                    context,
                    YDownloadService::class.java,
                ).apply {
                    this.action = action
                    putExtra(
                        EXTRA_DOWNLOAD_ID,
                        id,
                    )
                }
            ContextCompat.startForegroundService(
                context,
                intent,
            )
        }
    }
}
