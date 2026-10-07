package com.yagay.ysuite.feature.ynotify.runtime

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import com.yagay.ysuite.feature.ynotify.api.YNotifyEvent
import com.yagay.ysuite.feature.ynotify.api.YNotifyRuntimeStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
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
    private val database =
        YNotifyDatabase(applicationContext)

    fun observeEvents(): Flow<List<YNotifyEvent>> =
        YNotifyDatabase
            .changes()
            .onStart { emit(Unit) }
            .map {
                withContext(Dispatchers.IO) {
                    database.query()
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
        )

    fun clear() {
        database.clear()
    }

    fun reclassify() {
        database.reclassify()
    }

    fun export(events: List<YNotifyEvent>): String {
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
                events.forEach { event ->
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
                        }.toString(),
                    )
                }
            }
            ?: error("Unable to open export file")
        return uri.toString()
    }
}

internal object YNotifyRuntimeState {
    @Volatile
    var notificationListenerConnected:
        Boolean = false

    @Volatile
    var accessibilityConnected:
        Boolean = false
}
