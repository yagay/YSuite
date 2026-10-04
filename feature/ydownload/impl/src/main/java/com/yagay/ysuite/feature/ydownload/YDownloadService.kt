package com.yagay.ysuite.feature.ydownload

import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.net.ConnectivityManager
import android.net.Network
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
    private lateinit var connectivity:
        ConnectivityManager
    private val networkCallback =
        object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                if (
                    environment.settings.settings
                        .value.wifiOnly
                ) {
                    environment.engine.pumpQueue()
                }
            }
        }

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
        connectivity =
            getSystemService(
                CONNECTIVITY_SERVICE,
            ) as ConnectivityManager
        connectivity.registerDefaultNetworkCallback(
            networkCallback,
        )

        startForeground(
            YDownloadNotificationManager
                .SERVICE_NOTIFICATION_ID,
            notifications.serviceNotification(),
        )

        lifecycleScope.launch {
            environment.repository.refresh()
            environment.engine.pumpQueue()
            environment.repository.items
                .collectLatest { items ->
                    notifications.update(
                        items = items,
                        enabled =
                            environment.settings.settings
                                .value.notificationsEnabled,
                    )
                    val hasActive =
                        items.any {
                            it.state ==
                                YDownloadState.Downloading ||
                                it.state ==
                                YDownloadState.Connecting
                        }
                    val hasQueued =
                        items.any {
                            it.state ==
                                YDownloadState.Pending &&
                                it.queued
                        }
                    if (
                        hasActive ||
                        environment.engine.hasActiveDownloads()
                    ) {
                        acquireWakeLock()
                    } else {
                        releaseWakeLock()
                    }
                    if (
                        commandReceived &&
                        !hasActive &&
                        !hasQueued &&
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

        if (intent.action == ACTION_PUMP) {
            environment.engine.pumpQueue()
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
        runCatching {
            connectivity.unregisterNetworkCallback(
                networkCallback,
            )
        }
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
        const val ACTION_PUMP =
            "com.yagay.ysuite.ydownload.PUMP"
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

        fun pump(
            context: Context,
        ) {
            val intent =
                Intent(
                    context,
                    YDownloadService::class.java,
                ).apply {
                    action = ACTION_PUMP
                }
            ContextCompat.startForegroundService(
                context,
                intent,
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
