package com.yagay.ysuite.feature.ynotify.runtime

import android.app.Notification
import android.app.NotificationManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.yagay.ysuite.feature.ynotify.api.YNotifyEvent
import com.yagay.ysuite.feature.ynotify.api.YNotifyEventType
import com.yagay.ysuite.feature.ynotify.api.YNotifyNotificationKind
import java.util.concurrent.Executors

class YNotifyNotificationListenerService :
    NotificationListenerService() {
    private val executor =
        Executors.newSingleThreadExecutor()
    private lateinit var database:
        YNotifyDatabase

    override fun onCreate() {
        super.onCreate()
        database = YNotifyDatabase(this)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        YNotifyRuntimeState
            .notificationListenerConnected = true
        runCatching {
            activeNotifications
                ?.forEach {
                    scheduleSave(
                        it,
                        currentRanking,
                    )
                }
        }
    }

    override fun onListenerDisconnected() {
        YNotifyRuntimeState
            .notificationListenerConnected = false
        super.onListenerDisconnected()
        runCatching {
            requestRebind(
                android.content.ComponentName(
                    this,
                    YNotifyNotificationListenerService::class.java,
                ),
            )
        }
    }

    override fun onNotificationPosted(
        sbn: StatusBarNotification?,
        rankingMap: RankingMap?,
    ) {
        if (sbn != null) {
            scheduleSave(sbn, rankingMap)
        }
    }

    override fun onNotificationRemoved(
        sbn: StatusBarNotification?,
        rankingMap: RankingMap?,
        reason: Int,
    ) {
        val key = sbn?.key ?: return
        executor.execute {
            database.markRemoved(
                key,
                System.currentTimeMillis(),
            )
        }
    }

    override fun onNotificationRankingUpdate(
        rankingMap: RankingMap?,
    ) {
        runCatching {
            activeNotifications
                ?.forEach {
                    scheduleSave(
                        it,
                        rankingMap,
                    )
                }
        }
    }

    override fun onDestroy() {
        YNotifyRuntimeState
            .notificationListenerConnected = false
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun scheduleSave(
        sbn: StatusBarNotification,
        rankingMap: RankingMap?,
    ) {
        if (sbn.packageName == packageName) return
        executor.execute {
            runCatching {
                database.upsert(
                    parse(
                        sbn,
                        rankingMap,
                    ),
                )
            }
        }
    }

    private fun parse(
        sbn: StatusBarNotification,
        rankingMap: RankingMap?,
    ): YNotifyEvent {
        val notification = sbn.notification
        val extras = notification.extras
        val title =
            extras.getCharSequence(
                Notification.EXTRA_TITLE_BIG,
            )
                ?: extras.getCharSequence(
                    Notification.EXTRA_TITLE,
                )
        val fullText =
            extras.getCharSequence(
                Notification.EXTRA_BIG_TEXT,
            )
                ?: extras.getCharSequence(
                    Notification.EXTRA_TEXT,
                )
        val text =
            extras.getCharSequence(
                Notification.EXTRA_TEXT,
            )
        val progress =
            extras.getInt(
                Notification.EXTRA_PROGRESS,
                0,
            )
        val progressMax =
            extras.getInt(
                Notification.EXTRA_PROGRESS_MAX,
                0,
            )
        val progressIndeterminate =
            extras.getBoolean(
                Notification.EXTRA_PROGRESS_INDETERMINATE,
                false,
            )

        val ranking = Ranking()
        val hasRanking =
            (rankingMap ?: currentRanking)
                ?.getRanking(
                    sbn.key,
                    ranking,
                ) == true
        val importance =
            if (hasRanking) {
                ranking.importance
            } else {
                NotificationManager
                    .IMPORTANCE_DEFAULT
            }
        val payloadSilent =
            notification.sound == null &&
                notification.vibrate == null
        val silent =
            payloadSilent ||
                importance <=
                    NotificationManager
                        .IMPORTANCE_LOW ||
                (
                    hasRanking &&
                        ranking.isAmbient
                )

        val bubble =
            notification.bubbleMetadata != null
        val fullScreen =
            notification.fullScreenIntent != null
        val foreground =
            notification.flags and
                Notification.FLAG_FOREGROUND_SERVICE !=
                0
        val groupSummary =
            notification.flags and
                Notification.FLAG_GROUP_SUMMARY !=
                0
        val conversation =
            hasRanking &&
                ranking.isConversation

        val kind =
            when {
                fullScreen ->
                    YNotifyNotificationKind
                        .FullScreen
                bubble ->
                    YNotifyNotificationKind
                        .Bubble
                notification.category ==
                    Notification.CATEGORY_CALL ->
                    YNotifyNotificationKind.Call
                notification.category ==
                    Notification.CATEGORY_ALARM ->
                    YNotifyNotificationKind.Alarm
                notification.category ==
                    Notification.CATEGORY_TRANSPORT ||
                    extras.containsKey(
                        Notification
                            .EXTRA_MEDIA_SESSION,
                    ) ->
                    YNotifyNotificationKind.Media
                progressMax > 0 ||
                    progress > 0 ||
                    progressIndeterminate ->
                    YNotifyNotificationKind.Progress
                foreground ->
                    YNotifyNotificationKind
                        .ForegroundService
                notification.category ==
                    Notification.CATEGORY_MESSAGE ||
                    conversation ->
                    YNotifyNotificationKind.Message
                notification.category ==
                    Notification.CATEGORY_SYSTEM ||
                    notification.category ==
                    Notification.CATEGORY_STATUS ||
                    notification.category ==
                    Notification.CATEGORY_SERVICE ->
                    YNotifyNotificationKind.System
                sbn.isOngoing ->
                    YNotifyNotificationKind.Ongoing
                silent ->
                    YNotifyNotificationKind.Silent
                else ->
                    YNotifyNotificationKind.Standard
            }
        val label =
            runCatching {
                val info =
                    packageManager.getApplicationInfo(
                        sbn.packageName,
                        0,
                    )
                packageManager
                    .getApplicationLabel(info)
                    .toString()
            }.getOrDefault(sbn.packageName)
        val now = System.currentTimeMillis()
        return YNotifyEvent(
            id = 0L,
            eventKey =
                "notification:" +
                    sbn.key,
            eventType =
                YNotifyEventType.Notification,
            source = "notification_listener",
            packageName = sbn.packageName,
            appLabel = label,
            title = title?.toString(),
            text = text?.toString(),
            fullText = fullText?.toString(),
            postedAt = sbn.postTime,
            updatedAt = now,
            removedAt = null,
            notificationKey = sbn.key,
            notificationKind = kind,
            headsUp = false,
            bubbleShown = false,
            fullScreenShown = false,
            ongoing = sbn.isOngoing,
            foregroundService = foreground,
            progress = progress,
            progressMax = progressMax,
            progressIndeterminate =
                progressIndeterminate,
            className =
                extras.getString(
                    Notification.EXTRA_TEMPLATE,
                ),
            classificationVersion =
                YNotifyDatabase
                    .CLASSIFICATION_VERSION,
        )
    }
}
