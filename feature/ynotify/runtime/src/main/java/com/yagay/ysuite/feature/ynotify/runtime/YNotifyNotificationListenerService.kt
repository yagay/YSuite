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
        YNotifyDatabase.signalRuntimeStatusChanged()
        executor.execute {
            runCatching {
                val ranking =
                    currentRanking
                activeNotifications
                    ?.forEach {
                        if (
                            it.packageName !=
                            packageName
                        ) {
                            database.upsert(
                                YNotifyNotificationParser
                                    .parse(
                                        this,
                                        it,
                                        ranking,
                                    ),
                            )
                            YNotifyCaptureHealth.saved(this)
                        }
                    }
            }.onFailure {
                YNotifyCaptureHealth.failed(this, it)
            }
        }
    }

    override fun onListenerDisconnected() {
        YNotifyRuntimeState
            .notificationListenerConnected = false
        YNotifyDatabase.signalRuntimeStatusChanged()
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
            YNotifyCaptureHealth.received(this, sbn.packageName)
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
            runCatching {
                database.markRemoved(
                    key,
                    sbn.postTime,
                    System.currentTimeMillis(),
                )
            }.onFailure { YNotifyCaptureHealth.failed(this, it) }
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
        YNotifyDatabase.signalRuntimeStatusChanged()
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun scheduleSave(
        sbn: StatusBarNotification,
        rankingMap: RankingMap?,
    ) {
        if (
            sbn.packageName == packageName ||
            YNotifyCapturePolicy.isPaused(this, sbn.packageName)
        ) return
        executor.execute {
            runCatching {
                var event =
                    YNotifyNotificationParser
                        .parse(
                            this,
                            sbn,
                            rankingMap
                                ?: currentRanking,
                        )
                if (YNotifyCapturePolicy.isRedacted(this, sbn.packageName)) {
                    event = event.copy(
                        title = "•••",
                        text = null,
                        fullText = null,
                    )
                }
                database.upsert(event)
                YNotifyCaptureHealth.saved(this)
                database.prune(
                    YNotifyCapturePolicy.retentionDays(this),
                )
            }.onFailure {
                YNotifyCaptureHealth.failed(this, it)
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
        val text =
            extras.getCharSequence(
                Notification.EXTRA_TEXT,
            )
        val fullText =
            collectRichText(notification)
                .ifBlank {
                    extras.getCharSequence(
                        Notification.EXTRA_BIG_TEXT,
                    )?.toString()
                        ?: text?.toString().orEmpty()
                }
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
    private fun collectRichText(
        notification: Notification,
    ): String {
        val extras = notification.extras
        val values = linkedSetOf<String>()
        fun add(value: CharSequence?) {
            value?.toString()?.trim()
                ?.takeIf { it.isNotBlank() }
                ?.let(values::add)
        }
        add(extras.getCharSequence(Notification.EXTRA_BIG_TEXT))
        add(extras.getCharSequence(Notification.EXTRA_TEXT))
        add(extras.getCharSequence(Notification.EXTRA_SUB_TEXT))
        add(extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT))
        extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            ?.forEach(::add)
        listOf(
            Notification.EXTRA_MESSAGES,
            Notification.EXTRA_HISTORIC_MESSAGES,
        ).forEach { key ->
            extras.getParcelableArray(key)
                ?.forEach { parcelable ->
                    @Suppress("DEPRECATION")
                    val bundle = parcelable as? android.os.Bundle
                    add(bundle?.getCharSequence("text"))
                    add(bundle?.getCharSequence("sender"))
                }
        }
        return values.joinToString("\n")
    }
}
