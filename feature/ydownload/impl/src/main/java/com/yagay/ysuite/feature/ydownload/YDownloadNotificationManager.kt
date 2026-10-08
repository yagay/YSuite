package com.yagay.ysuite.feature.ydownload

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.yagay.ysuite.feature.ydownload.api.YDownloadItem
import com.yagay.ysuite.feature.ydownload.api.YDownloadState

internal class YDownloadNotificationManager(
    private val context: Context,
) {
    private val manager =
        context.getSystemService(
            Context.NOTIFICATION_SERVICE,
        ) as NotificationManager
    private val terminalNotified =
        mutableSetOf<String>()

    fun createChannels() {
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_SERVICE,
                context.getString(
                    R.string.ydownload_channel_service,
                ),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_PROGRESS,
                context.getString(
                    R.string.ydownload_channel_progress,
                ),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_RESULT,
                context.getString(
                    R.string.ydownload_channel_result,
                ),
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
    }

    fun serviceNotification(
        activeCount: Int = 0,
    ): Notification =
        NotificationCompat.Builder(
            context,
            CHANNEL_SERVICE,
        )
            .setSmallIcon(
                android.R.drawable.stat_sys_download,
            )
            .setContentTitle(
                context.getString(
                    R.string.ydownload_service_title,
                ),
            )
            .setContentText(
                context.getString(
                    R.string.ydownload_service_active,
                    activeCount,
                ),
            )
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(launchIntent())
            .build()

    fun update(
        items: List<YDownloadItem>,
        enabled: Boolean,
    ) {
        if (!enabled) {
            items.forEach { item ->
                manager.cancel(item.notificationId())
                manager.cancel(
                    item.notificationId() + RESULT_OFFSET,
                )
            }
            terminalNotified.clear()
            return
        }

        val active =
            items.filter {
                it.state == YDownloadState.Downloading ||
                    it.state == YDownloadState.Connecting
            }

        manager.notify(
            SERVICE_NOTIFICATION_ID,
            serviceNotification(active.size),
        )

        items.forEach { item ->
            when (item.state) {
                YDownloadState.Downloading,
                YDownloadState.Connecting -> {
                    terminalNotified.remove(item.id)
                    manager.notify(
                        item.notificationId(),
                        progressNotification(item),
                    )
                }
                YDownloadState.Completed -> {
                    manager.cancel(item.notificationId())
                    if (terminalNotified.add(item.id)) {
                        manager.notify(
                            item.notificationId() + RESULT_OFFSET,
                            resultNotification(
                                item = item,
                                success = true,
                            ),
                        )
                    }
                }
                YDownloadState.Failed -> {
                    manager.cancel(item.notificationId())
                    if (terminalNotified.add(item.id)) {
                        manager.notify(
                            item.notificationId() + RESULT_OFFSET,
                            resultNotification(
                                item = item,
                                success = false,
                            ),
                        )
                    }
                }
                else ->
                    manager.cancel(item.notificationId())
            }
        }
    }

    private fun progressNotification(
        item: YDownloadItem,
    ): Notification {
        val progress =
            ((item.progress ?: 0f) * 100f)
                .toInt()
                .coerceIn(0, 100)
        val builder =
            NotificationCompat.Builder(
                context,
                CHANNEL_PROGRESS,
            )
                .setSmallIcon(
                    android.R.drawable.stat_sys_download,
                )
                .setContentTitle(item.fileName)
                .setContentText(
                    formatProgressText(item),
                )
                .setProgress(
                    100,
                    progress,
                    item.totalBytes <= 0L,
                )
                .setOngoing(true)
                .setSilent(true)
                .setContentIntent(launchIntent())

        if (item.backend ==
            com.yagay.ysuite.feature.ydownload.api.YDownloadBackend.Private
        ) {
            builder.addAction(
                0,
                context.getString(R.string.ydownload_pause),
                serviceAction(
                    action = YDownloadService.ACTION_PAUSE,
                    id = item.id,
                    requestCode =
                        item.notificationId() + 10,
                ),
            )
        }
        builder.addAction(
            0,
            context.getString(R.string.ydownload_cancel),
            serviceAction(
                action = YDownloadService.ACTION_CANCEL,
                id = item.id,
                requestCode =
                    item.notificationId() + 20,
            ),
        )

        return builder.build()
    }

    private fun resultNotification(
        item: YDownloadItem,
        success: Boolean,
    ): Notification =
        NotificationCompat.Builder(
            context,
            CHANNEL_RESULT,
        )
            .setSmallIcon(
                if (success) {
                    android.R.drawable.stat_sys_download_done
                } else {
                    android.R.drawable.stat_notify_error
                },
            )
            .setContentTitle(
                context.getString(
                    if (success) {
                        R.string.ydownload_completed
                    } else {
                        R.string.ydownload_failed
                    },
                ),
            )
            .setContentText(
                if (success) {
                    item.fileName
                } else {
                    item.errorMessage
                        ?: item.fileName
                },
            )
            .setAutoCancel(true)
            .setContentIntent(launchIntent())
            .build()

    private fun formatProgressText(
        item: YDownloadItem,
    ): String {
        val speed =
            formatBytes(item.speedBytesPerSecond) + "/s"
        val eta =
            if (item.etaSeconds > 0L) {
                formatDuration(item.etaSeconds)
            } else {
                "--"
            }
        return speed + " · " + eta
    }

    private fun launchIntent(): PendingIntent? {
        val launch =
            context.packageManager
                .getLaunchIntentForPackage(
                    context.packageName,
                )
                ?: return null
        return PendingIntent.getActivity(
            context,
            0,
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or
                PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun serviceAction(
        action: String,
        id: String,
        requestCode: Int,
    ): PendingIntent {
        val intent =
            Intent(
                context,
                YDownloadService::class.java,
            ).apply {
                this.action = action
                putExtra(
                    YDownloadService.EXTRA_DOWNLOAD_ID,
                    id,
                )
            }
        return PendingIntent.getService(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val SERVICE_NOTIFICATION_ID = 42001
        private const val RESULT_OFFSET = 1_000_000
        private const val CHANNEL_SERVICE =
            "ydownload_service"
        private const val CHANNEL_PROGRESS =
            "ydownload_progress"
        private const val CHANNEL_RESULT =
            "ydownload_result"
    }
}

private fun YDownloadItem.notificationId(): Int =
    id.hashCode() and 0x3fffffff

internal fun formatBytes(bytes: Long): String {
    if (bytes < 0L) return "--"
    if (bytes < 1024L) return bytes.toString() + " B"

    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var index = -1
    do {
        value /= 1024.0
        index += 1
    } while (
        value >= 1024.0 &&
        index < units.lastIndex
    )
    return String.format(
        java.util.Locale.ROOT,
        "%.1f %s",
        value,
        units[index],
    )
}

internal fun formatDuration(
    seconds: Long,
): String {
    val safe = seconds.coerceAtLeast(0L)
    val hours = safe / 3600L
    val minutes = (safe % 3600L) / 60L
    val remain = safe % 60L
    return if (hours > 0L) {
        hours.toString() + "h " +
            minutes.toString() + "m"
    } else if (minutes > 0L) {
        minutes.toString() + "m " +
            remain.toString() + "s"
    } else {
        remain.toString() + "s"
    }
}
