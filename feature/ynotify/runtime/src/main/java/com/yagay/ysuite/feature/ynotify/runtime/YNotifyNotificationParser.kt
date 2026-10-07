package com.yagay.ysuite.feature.ynotify.runtime

import android.app.Notification
import android.app.Person
import android.content.Context
import android.os.Bundle
import android.os.Parcelable
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.RemoteViews
import android.widget.TextView
import com.yagay.ysuite.feature.ynotify.api.YNotifyEvent
import com.yagay.ysuite.feature.ynotify.api.YNotifyEventType
import com.yagay.ysuite.feature.ynotify.api.YNotifyNotificationKind
import java.lang.reflect.Array
import org.json.JSONArray
import org.json.JSONObject

internal object YNotifyNotificationParser {
    fun parse(
        context: Context,
        sbn: StatusBarNotification,
        rankingMap:
            NotificationListenerService.RankingMap?,
    ): YNotifyEvent {
        val notification = sbn.notification
        val extras =
            notification.extras ?: Bundle.EMPTY
        val ranking =
            NotificationListenerService.Ranking()
        val hasRanking =
            rankingMap?.getRanking(
                sbn.key,
                ranking,
            ) == true

        val title =
            firstNonBlank(
                extras.getCharSequence(
                    Notification.EXTRA_TITLE_BIG,
                )?.toString(),
                extras.getCharSequence(
                    Notification.EXTRA_TITLE,
                )?.toString(),
            )
        val text =
            extras.getCharSequence(
                Notification.EXTRA_TEXT,
            )?.toString()
        val subText =
            firstNonBlank(
                extras.getCharSequence(
                    Notification.EXTRA_SUB_TEXT,
                )?.toString(),
                extras.getCharSequence(
                    Notification.EXTRA_INFO_TEXT,
                )?.toString(),
            )
        val summaryText =
            extras.getCharSequence(
                Notification.EXTRA_SUMMARY_TEXT,
            )?.toString()
        val messagesJson =
            messagesToJson(extras)
        val messagesText =
            messagesToText(extras)
        val textLines =
            extras.getCharSequenceArray(
                Notification.EXTRA_TEXT_LINES,
            )
                ?.mapNotNull {
                    it?.toString()
                        ?.trim()
                        ?.takeIf(String::isNotBlank)
                }
                ?.joinToString("\n")
        val bigText =
            extras.getCharSequence(
                Notification.EXTRA_BIG_TEXT,
            )?.toString()
        val template =
            extras.getString(
                Notification.EXTRA_TEMPLATE,
            )
        val remoteText =
            if (
                template == null ||
                (
                    bigText.isNullOrBlank() &&
                    messagesText.isNullOrBlank() &&
                    textLines.isNullOrBlank()
                )
            ) {
                extractRemoteViewsText(
                    context,
                    sbn.packageName,
                    notification.contentView,
                    notification.bigContentView,
                    notification.headsUpContentView,
                )
            } else {
                null
            }
        val fullText =
            mergeUseful(
                bigText,
                messagesText,
                textLines,
                remoteText,
                text,
            )

        val importance =
            if (hasRanking) {
                ranking.importance
            } else {
                0
            }
        val payloadSilent =
            notification.sound == null &&
                notification.vibrate == null &&
                (
                    notification.defaults and
                        (
                            Notification.DEFAULT_SOUND or
                                Notification.DEFAULT_VIBRATE
                            )
                    ) == 0
        val silent =
            payloadSilent ||
                (
                    hasRanking &&
                        ranking.isAmbient
                    ) ||
                importance in
                    1..android.app.NotificationManager
                        .IMPORTANCE_LOW

        val bubble =
            notification.bubbleMetadata != null ||
                notification.flags and
                    Notification.FLAG_BUBBLE !=
                0
        val fullScreen =
            notification.fullScreenIntent != null
        val foreground =
            notification.flags and
                Notification.FLAG_FOREGROUND_SERVICE !=
                0
        val conversation =
            hasRanking &&
                ranking.isConversation
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
        val kind =
            classifyKind(
                notification = notification,
                bubble = bubble,
                fullScreen = fullScreen,
                foreground = foreground,
                conversation = conversation,
                silent = silent,
                progress = progress,
                progressMax = progressMax,
                progressIndeterminate =
                    progressIndeterminate,
            )
        val label =
            runCatching {
                val info =
                    context.packageManager
                        .getApplicationInfo(
                            sbn.packageName,
                            0,
                        )
                context.packageManager
                    .getApplicationLabel(info)
                    .toString()
            }.getOrDefault(
                sbn.packageName,
            )
        val channel =
            if (hasRanking) {
                ranking.channel
            } else {
                null
            }
        val now =
            System.currentTimeMillis()

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
            title = title,
            text = text,
            fullText = fullText,
            subText = subText,
            summaryText = summaryText,
            rawExtras =
                bundleToJson(extras)
                    .toString(),
            messagesJson = messagesJson,
            actionsJson =
                actionsToJson(notification)
                    .toString(),
            postedAt = sbn.postTime,
            updatedAt = now,
            removedAt = null,
            notificationKey = sbn.key,
            notificationId = sbn.id,
            notificationTag = sbn.tag,
            channelId =
                notification.channelId,
            channelName =
                channel?.name
                    ?.toString(),
            channelDescription =
                channel?.description,
            channelImportance =
                channel?.importance ?: 0,
            groupKey = sbn.groupKey,
            groupSummary =
                notification.flags and
                    Notification.FLAG_GROUP_SUMMARY !=
                    0,
            category =
                notification.category,
            template = template,
            importance = importance,
            conversation = conversation,
            rankingCanBubble =
                hasRanking &&
                    ranking.canBubble(),
            rankingAmbient =
                hasRanking &&
                    ranking.isAmbient,
            rankingSuspended =
                hasRanking &&
                    ranking.isSuspended,
            flags = notification.flags,
            clearable = sbn.isClearable,
            bubble = bubble,
            fullScreen = fullScreen,
            payloadSilent =
                payloadSilent,
            silent = silent,
            removalReason = 0,
            notificationKind = kind,
            headsUp = false,
            bubbleShown = false,
            fullScreenShown = false,
            ongoing = sbn.isOngoing,
            foregroundService =
                foreground,
            progress = progress,
            progressMax = progressMax,
            progressIndeterminate =
                progressIndeterminate,
            className = template,
            classificationVersion =
                YNotifyDatabase
                    .CLASSIFICATION_VERSION,
        )
    }

    private fun classifyKind(
        notification: Notification,
        bubble: Boolean,
        fullScreen: Boolean,
        foreground: Boolean,
        conversation: Boolean,
        silent: Boolean,
        progress: Int,
        progressMax: Int,
        progressIndeterminate: Boolean,
    ): YNotifyNotificationKind =
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
                notification.extras
                    ?.containsKey(
                        Notification
                            .EXTRA_MEDIA_SESSION,
                    ) == true ->
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
            notification.flags and
                Notification.FLAG_ONGOING_EVENT !=
                0 ->
                YNotifyNotificationKind.Ongoing
            silent ->
                YNotifyNotificationKind.Silent
            else ->
                YNotifyNotificationKind.Standard
        }

    private fun messagesToText(
        extras: Bundle,
    ): String? {
        val out = mutableListOf<String>()
        appendMessagesText(
            extras,
            Notification.EXTRA_MESSAGES,
            out,
        )
        appendMessagesText(
            extras,
            Notification.EXTRA_HISTORIC_MESSAGES,
            out,
        )
        return out
            .takeIf(List<String>::isNotEmpty)
            ?.joinToString("\n")
    }

    private fun appendMessagesText(
        extras: Bundle,
        key: String,
        out: MutableList<String>,
    ) {
        runCatching {
            extras.getParcelableArray(key)
                ?.forEach { value ->
                    val bundle =
                        value as? Bundle
                            ?: return@forEach
                    val message =
                        bundle.getCharSequence(
                            "text",
                        )?.toString()
                            ?: return@forEach
                    val person =
                        bundle.getParcelable(
                            "sender_person",
                            Person::class.java,
                        )
                    val sender =
                        person?.name
                            ?.toString()
                            ?: bundle
                                .getCharSequence(
                                    "sender",
                                )?.toString()
                    out +=
                        if (
                            sender.isNullOrBlank()
                        ) {
                            message
                        } else {
                            "$sender: $message"
                        }
                }
        }
    }

    private fun messagesToJson(
        extras: Bundle,
    ): String {
        val out = JSONArray()
        appendMessagesJson(
            extras,
            Notification.EXTRA_MESSAGES,
            false,
            out,
        )
        appendMessagesJson(
            extras,
            Notification.EXTRA_HISTORIC_MESSAGES,
            true,
            out,
        )
        return out.toString()
    }

    private fun appendMessagesJson(
        extras: Bundle,
        key: String,
        historic: Boolean,
        out: JSONArray,
    ) {
        runCatching {
            extras.getParcelableArray(key)
                ?.forEach { value ->
                    val bundle =
                        value as? Bundle
                            ?: return@forEach
                    val person =
                        bundle.getParcelable(
                            "sender_person",
                            Person::class.java,
                        )
                    out.put(
                        JSONObject()
                            .put(
                                "text",
                                bundle.getCharSequence(
                                    "text",
                                )?.toString(),
                            )
                            .put(
                                "sender",
                                bundle.getCharSequence(
                                    "sender",
                                )?.toString(),
                            )
                            .put(
                                "person",
                                person?.name
                                    ?.toString(),
                            )
                            .put(
                                "time",
                                bundle.getLong(
                                    "time",
                                    0L,
                                ),
                            )
                            .put(
                                "historic",
                                historic,
                            ),
                    )
                }
        }
    }

    private fun actionsToJson(
        notification: Notification,
    ): JSONArray {
        val out = JSONArray()
        notification.actions
            ?.forEach { action ->
                runCatching {
                    out.put(
                        JSONObject()
                            .put(
                                "title",
                                action.title
                                    ?.toString(),
                            )
                            .put(
                                "semanticAction",
                                action.semanticAction,
                            )
                            .put(
                                "remoteInputCount",
                                action.remoteInputs
                                    ?.size ?: 0,
                            )
                            .put(
                                "contextual",
                                action.isContextual,
                            )
                            .put(
                                "authenticationRequired",
                                action
                                    .isAuthenticationRequired,
                            ),
                    )
                }
            }
        return out
    }

    private fun extractRemoteViewsText(
        context: Context,
        packageName: String,
        vararg views: RemoteViews?,
    ): String? {
        val values =
            linkedSetOf<String>()
        runCatching {
            val packageContext =
                context.createPackageContext(
                    packageName,
                    Context.CONTEXT_RESTRICTED,
                )
            views.filterNotNull()
                .forEach { remoteViews ->
                    runCatching {
                        val root =
                            remoteViews.apply(
                                packageContext,
                                FrameLayout(
                                    packageContext,
                                ),
                            )
                        collectText(
                            root,
                            values,
                            0,
                        )
                    }
                }
        }
        return values
            .takeIf(
                Set<String>::isNotEmpty,
            )
            ?.joinToString("\n")
    }

    private fun collectText(
        view: View?,
        values: MutableSet<String>,
        depth: Int,
    ) {
        if (
            view == null ||
            depth > 16
        ) return
        if (view is TextView) {
            view.text
                ?.toString()
                ?.trim()
                ?.takeIf(String::isNotBlank)
                ?.let(values::add)
        }
        if (view is ViewGroup) {
            repeat(
                view.childCount
                    .coerceAtMost(100),
            ) { index ->
                collectText(
                    view.getChildAt(index),
                    values,
                    depth + 1,
                )
            }
        }
    }

    private fun mergeUseful(
        vararg values: String?,
    ): String? {
        val parts =
            linkedSetOf<String>()
        values.forEach { value ->
            val text =
                value?.trim()
                    ?.takeIf(String::isNotBlank)
                    ?: return@forEach
            var covered = false
            parts.toList()
                .forEach { existing ->
                    when {
                        existing.contains(
                            text,
                        ) ->
                            covered = true
                        text.contains(
                            existing,
                        ) ->
                            parts.remove(
                                existing,
                            )
                    }
                }
            if (!covered) {
                parts += text
            }
        }
        return parts
            .takeIf(
                Set<String>::isNotEmpty,
            )
            ?.joinToString("\n")
    }

    private fun firstNonBlank(
        vararg values: String?,
    ): String? =
        values.firstOrNull {
            !it.isNullOrBlank()
        }

    private fun bundleToJson(
        bundle: Bundle?,
        depth: Int = 0,
    ): JSONObject {
        val out = JSONObject()
        if (
            bundle == null ||
            depth > 5
        ) return out
        runCatching {
            bundle.keySet()
                .forEach { key ->
                    out.put(
                        key,
                        runCatching {
                            jsonValue(
                                bundle.get(key),
                                depth + 1,
                            )
                        }.getOrElse {
                            "<unreadable:" +
                                it.javaClass.simpleName +
                                ">"
                        },
                    )
                }
        }
        return out
    }

    private fun jsonValue(
        value: Any?,
        depth: Int,
    ): Any =
        when {
            value == null ->
                JSONObject.NULL
            value is Bundle ->
                bundleToJson(
                    value,
                    depth,
                )
            value is CharSequence ||
                value is Number ||
                value is Boolean ->
                value.toString()
            value.javaClass.isArray -> {
                val result = JSONArray()
                repeat(
                    Array.getLength(value)
                        .coerceAtMost(100),
                ) { index ->
                    result.put(
                        jsonValue(
                            Array.get(
                                value,
                                index,
                            ),
                            depth + 1,
                        ),
                    )
                }
                result
            }
            value is Iterable<*> -> {
                val result = JSONArray()
                value.take(100)
                    .forEach {
                        result.put(
                            jsonValue(
                                it,
                                depth + 1,
                            ),
                        )
                    }
                result
            }
            else ->
                value.toString()
        }
}
