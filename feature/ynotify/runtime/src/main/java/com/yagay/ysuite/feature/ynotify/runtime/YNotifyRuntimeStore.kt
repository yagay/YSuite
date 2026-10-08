package com.yagay.ysuite.feature.ynotify.runtime

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import com.yagay.ysuite.feature.ynotify.api.YNotifyEvent
import com.yagay.ysuite.feature.ynotify.api.YNotifyRuntimeStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext
import org.json.JSONObject

class YNotifyRuntimeStore(
    context: Context,
) {
    private val applicationContext =
        context.applicationContext

    init {
        YNotifyXposedRuntime.ensure(applicationContext)
    }
    private val database =
        YNotifyDatabase(applicationContext)

    // The history is paged to avoid decrypting thousands of events on each
    // invalidation. Changing the page limit also refreshes the database query.
    fun observeEvents(pageLimit: Flow<Int>): Flow<List<YNotifyEvent>> =
        combine(
            YNotifyDatabase.changes().onStart { emit(Unit) },
            pageLimit.distinctUntilChanged(),
        ) { _, limit -> limit.coerceIn(1, 10_000) }
            .map { limit ->
                withContext(Dispatchers.IO) {
                    database.query(limit)
                }
            }
            .distinctUntilChanged()

    fun status(): YNotifyRuntimeStatus =
        YNotifyRuntimeStatus(
            notificationListenerConnected =
                YNotifyRuntimeState
                    .notificationListenerConnected,
            accessibilityConnected =
                YNotifyRuntimeState
                    .accessibilityConnected,
            storedEventCount = database.count(),
            lastReceivedAt = YNotifyCaptureHealth.lastReceived(applicationContext),
            lastSavedAt = YNotifyCaptureHealth.lastSaved(applicationContext),
            lastPackage = YNotifyCaptureHealth.lastPackage(applicationContext),
            lastError = YNotifyCaptureHealth.lastError(applicationContext),
        )

    fun search(
        query: String,
        limit: Int = 500,
    ): List<YNotifyEvent> =
        database.search(
            query = query,
            limit = limit,
        )

    fun revisions(eventKey: String): List<YNotifyRevision> =
        database.revisions(eventKey)

    fun appAggregates():
        List<YNotifyAppAggregate> =
        database.appAggregates()

    fun isPaused(packageName: String): Boolean =
        YNotifyCapturePolicy.isPaused(
            applicationContext,
            packageName,
        )

    fun setPaused(
        packageName: String,
        value: Boolean,
    ) {
        YNotifyCapturePolicy.setPaused(
            applicationContext,
            packageName,
            value,
        )
    }

    fun isRedacted(packageName: String): Boolean =
        YNotifyCapturePolicy.isRedacted(
            applicationContext,
            packageName,
        )

    fun setRedacted(
        packageName: String,
        value: Boolean,
    ) {
        YNotifyCapturePolicy.setRedacted(
            applicationContext,
            packageName,
            value,
        )
    }

    fun retentionDays(): Int =
        YNotifyCapturePolicy.retentionDays(
            applicationContext,
        )

    fun setRetentionDays(days: Int) {
        YNotifyCapturePolicy.setRetentionDays(
            applicationContext,
            days,
        )
        database.prune(days)
    }

    fun clear() {
        database.clear()
    }

    fun clearPackage(packageName: String) {
        database.deletePackage(packageName)
    }

    fun reclassify() {
        database.reclassify()
    }

    fun setManualClassification(
        id: Long,
        type: com.yagay.ysuite.feature.ynotify.api.YNotifyEventType,
        headsUp: Boolean,
        bubble: Boolean,
    ): Boolean = database.setManualClassification(id, type, headsUp, bubble)

    fun clearManualClassification(id: Long): Boolean =
        database.clearManualClassification(id)

    fun export(): String {
        val fileName =
            "YNotify-" +
                System.currentTimeMillis() +
                ".jsonl"
        val resolver =
            applicationContext.contentResolver
        val values =
            ContentValues().apply {
                put(
                    MediaStore.MediaColumns.DISPLAY_NAME,
                    fileName,
                )
                put(
                    MediaStore.MediaColumns.MIME_TYPE,
                    "application/x-ndjson",
                )
                put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    Environment.DIRECTORY_DOWNLOADS +
                        "/YSuite",
                )
            }
        val uri =
            checkNotNull(
                resolver.insert(
                    MediaStore.Downloads
                        .EXTERNAL_CONTENT_URI,
                    values,
                ),
            )
        resolver.openOutputStream(uri)
            ?.bufferedWriter()
            ?.use { writer ->
                database.forEachEvent { event ->
                    writer.appendLine(
                        JSONObject().apply {
                            put("id", event.id)
                            put(
                                "eventType",
                                event.eventType.name,
                            )
                            put(
                                "packageName",
                                event.packageName,
                            )
                            put(
                                "appLabel",
                                event.appLabel,
                            )
                            put("title", event.title)
                            put("text", event.text)
                            put(
                                "fullText",
                                event.fullText,
                            )
                            put(
                                "postedAt",
                                event.postedAt,
                            )
                            put(
                                "updatedAt",
                                event.updatedAt,
                            )
                            put(
                                "notificationKind",
                                event.notificationKind
                                    .name,
                            )
                            put(
                                "headsUp",
                                event.headsUp,
                            )
                            put(
                                "bubbleShown",
                                event.bubbleShown,
                            )
                            put(
                                "fullScreenShown",
                                event.fullScreenShown,
                            )
                            put(
                                "source",
                                event.source,
                            )
                            put("eventKey", event.eventKey)
                            put("notificationKey", event.notificationKey)
                            put("notificationId", event.notificationId)
                            put("notificationTag", event.notificationTag)
                            put("subText", event.subText)
                            put("summaryText", event.summaryText)
                            put("rawExtras", event.rawExtras)
                            put("messagesJson", event.messagesJson)
                            put("actionsJson", event.actionsJson)
                            put("channelId", event.channelId)
                            put("channelName", event.channelName)
                            put("channelDescription", event.channelDescription)
                            put("channelImportance", event.channelImportance)
                            put("groupKey", event.groupKey)
                            put("groupSummary", event.groupSummary)
                            put("category", event.category)
                            put("template", event.template)
                            put("importance", event.importance)
                            put("conversation", event.conversation)
                            put("flags", event.flags)
                            put("clearable", event.clearable)
                            put("silent", event.silent)
                            put("ongoing", event.ongoing)
                            put("foregroundService", event.foregroundService)
                            put("progress", event.progress)
                            put("progressMax", event.progressMax)
                            put("progressIndeterminate", event.progressIndeterminate)
                            put("removedAt", event.removedAt)
                            put("removalReason", event.removalReason)
                            put("className", event.className)
                            put("classificationVersion", event.classificationVersion)
                            put("classificationLocked", event.classificationLocked)
                            put("originalEventType", event.originalEventType?.name)
                            put("classificationSource", event.classificationSource)
                        }.toString(),
                    )
                }
            }
            ?: error("Unable to open export file")
        return uri.toString()
    }
}

internal object YNotifyCaptureHealth {
    private const val NAME = "ysuite_ynotify_capture_health"
    fun received(context: Context, packageName: String) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putLong("received_at", System.currentTimeMillis())
            .putString("package", packageName)
            .apply()
    }

    fun saved(context: Context) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putLong("saved_at", System.currentTimeMillis())
            .remove("last_error")
            .apply()
        YNotifyDatabase.signalRuntimeStatusChanged()
    }

    fun failed(context: Context, error: Throwable) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putString(
                "last_error",
                error.javaClass.simpleName + ": " +
                    error.message.orEmpty().take(160),
            ).apply()
        YNotifyDatabase.signalRuntimeStatusChanged()
    }

    fun lastReceived(context: Context): Long =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getLong("received_at", 0L)

    fun lastSaved(context: Context): Long =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getLong("saved_at", 0L)

    fun lastPackage(context: Context): String? =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getString("package", null)

    fun lastError(context: Context): String? =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getString("last_error", null)
}

internal object YNotifyRuntimeState {
    @Volatile
    var notificationListenerConnected:
        Boolean = false

    @Volatile
    var accessibilityConnected:
        Boolean = false
}
