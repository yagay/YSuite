package com.yagay.ysuite.feature.ynotify.runtime

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.yagay.ysuite.feature.ynotify.api.YNotifyEvent
import com.yagay.ysuite.feature.ynotify.api.YNotifyEventType
import com.yagay.ysuite.feature.ynotify.api.YNotifyNotificationKind
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

internal class YNotifyDatabase(
    context: Context,
) : SQLiteOpenHelper(
    context.applicationContext,
    "ynotify.db",
    null,
    DATABASE_VERSION,
) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE events (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                event_key TEXT NOT NULL UNIQUE,
                event_type TEXT NOT NULL,
                source TEXT NOT NULL,
                package_name TEXT NOT NULL,
                app_label TEXT NOT NULL,
                title TEXT,
                text_value TEXT,
                full_text TEXT,
                posted_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                removed_at INTEGER,
                notification_key TEXT,
                notification_kind TEXT NOT NULL,
                heads_up INTEGER NOT NULL DEFAULT 0,
                bubble_shown INTEGER NOT NULL DEFAULT 0,
                full_screen_shown INTEGER NOT NULL DEFAULT 0,
                ongoing INTEGER NOT NULL DEFAULT 0,
                foreground_service INTEGER NOT NULL DEFAULT 0,
                progress INTEGER NOT NULL DEFAULT 0,
                progress_max INTEGER NOT NULL DEFAULT 0,
                progress_indeterminate INTEGER NOT NULL DEFAULT 0,
                class_name TEXT,
                classification_version INTEGER NOT NULL DEFAULT 1
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX idx_ynotify_package ON events(package_name)",
        )
        db.execSQL(
            "CREATE INDEX idx_ynotify_type ON events(event_type)",
        )
        db.execSQL(
            "CREATE INDEX idx_ynotify_posted ON events(posted_at DESC)",
        )
        db.execSQL(
            "CREATE INDEX idx_ynotify_notification_key ON events(notification_key)",
        )
    }

    override fun onUpgrade(
        db: SQLiteDatabase,
        oldVersion: Int,
        newVersion: Int,
    ) {
        db.execSQL("DROP TABLE IF EXISTS events")
        onCreate(db)
    }

    fun upsert(event: YNotifyEvent) {
        writableDatabase.insertWithOnConflict(
            "events",
            null,
            event.toValues(),
            SQLiteDatabase.CONFLICT_REPLACE,
        )
        invalidations.tryEmit(Unit)
    }

    fun markRemoved(
        notificationKey: String,
        removedAt: Long,
    ) {
        writableDatabase.update(
            "events",
            ContentValues().apply {
                put("removed_at", removedAt)
                put("updated_at", removedAt)
            },
            "notification_key = ?",
            arrayOf(notificationKey),
        )
        invalidations.tryEmit(Unit)
    }

    fun query(
        limit: Int = 2_000,
    ): List<YNotifyEvent> {
        val result = mutableListOf<YNotifyEvent>()
        readableDatabase.query(
            "events",
            null,
            null,
            null,
            null,
            null,
            "posted_at DESC, id DESC",
            limit.coerceIn(1, 10_000).toString(),
        ).use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow("id")
            val eventKeyIndex = cursor.getColumnIndexOrThrow("event_key")
            val eventTypeIndex = cursor.getColumnIndexOrThrow("event_type")
            val sourceIndex = cursor.getColumnIndexOrThrow("source")
            val packageIndex = cursor.getColumnIndexOrThrow("package_name")
            val labelIndex = cursor.getColumnIndexOrThrow("app_label")
            val titleIndex = cursor.getColumnIndexOrThrow("title")
            val textIndex = cursor.getColumnIndexOrThrow("text_value")
            val fullTextIndex = cursor.getColumnIndexOrThrow("full_text")
            val postedIndex = cursor.getColumnIndexOrThrow("posted_at")
            val updatedIndex = cursor.getColumnIndexOrThrow("updated_at")
            val removedIndex = cursor.getColumnIndexOrThrow("removed_at")
            val notificationKeyIndex = cursor.getColumnIndexOrThrow("notification_key")
            val kindIndex = cursor.getColumnIndexOrThrow("notification_kind")
            val headsUpIndex = cursor.getColumnIndexOrThrow("heads_up")
            val bubbleIndex = cursor.getColumnIndexOrThrow("bubble_shown")
            val fullScreenIndex = cursor.getColumnIndexOrThrow("full_screen_shown")
            val ongoingIndex = cursor.getColumnIndexOrThrow("ongoing")
            val fgIndex = cursor.getColumnIndexOrThrow("foreground_service")
            val progressIndex = cursor.getColumnIndexOrThrow("progress")
            val progressMaxIndex = cursor.getColumnIndexOrThrow("progress_max")
            val progressIndeterminateIndex =
                cursor.getColumnIndexOrThrow("progress_indeterminate")
            val classIndex = cursor.getColumnIndexOrThrow("class_name")
            val versionIndex = cursor.getColumnIndexOrThrow("classification_version")
            while (cursor.moveToNext()) {
                result +=
                    YNotifyEvent(
                        id = cursor.getLong(idIndex),
                        eventKey = cursor.getString(eventKeyIndex),
                        eventType =
                            enumValueOrDefault(
                                cursor.getString(eventTypeIndex),
                                YNotifyEventType.OtherUi,
                            ),
                        source = cursor.getString(sourceIndex),
                        packageName = cursor.getString(packageIndex),
                        appLabel = cursor.getString(labelIndex),
                        title =
                            cursor.getStringOrNull(titleIndex),
                        text =
                            cursor.getStringOrNull(textIndex),
                        fullText =
                            cursor.getStringOrNull(fullTextIndex),
                        postedAt = cursor.getLong(postedIndex),
                        updatedAt = cursor.getLong(updatedIndex),
                        removedAt =
                            cursor.getLongOrNull(removedIndex),
                        notificationKey =
                            cursor.getStringOrNull(
                                notificationKeyIndex,
                            ),
                        notificationKind =
                            enumValueOrDefault(
                                cursor.getString(kindIndex),
                                YNotifyNotificationKind.Unknown,
                            ),
                        headsUp =
                            cursor.getInt(headsUpIndex) != 0,
                        bubbleShown =
                            cursor.getInt(bubbleIndex) != 0,
                        fullScreenShown =
                            cursor.getInt(fullScreenIndex) != 0,
                        ongoing =
                            cursor.getInt(ongoingIndex) != 0,
                        foregroundService =
                            cursor.getInt(fgIndex) != 0,
                        progress =
                            cursor.getInt(progressIndex),
                        progressMax =
                            cursor.getInt(progressMaxIndex),
                        progressIndeterminate =
                            cursor.getInt(
                                progressIndeterminateIndex,
                            ) != 0,
                        className =
                            cursor.getStringOrNull(classIndex),
                        classificationVersion =
                            cursor.getInt(versionIndex),
                    )
            }
        }
        return result
    }

    fun count(): Int =
        readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM events",
            null,
        ).use {
            if (it.moveToFirst()) it.getInt(0) else 0
        }

    fun clear() {
        writableDatabase.delete("events", null, null)
        invalidations.tryEmit(Unit)
    }

    fun reclassify() {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.rawQuery(
                "SELECT id,event_type,source,package_name,class_name,notification_key,notification_kind,ongoing,foreground_service,progress,progress_max,progress_indeterminate FROM events",
                null,
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(0)
                    val currentType =
                        enumValueOrDefault(
                            cursor.getString(1),
                            YNotifyEventType.OtherUi,
                        )
                    val source = cursor.getString(2).orEmpty()
                    val packageName = cursor.getString(3).orEmpty()
                    val className = cursor.getString(4).orEmpty()
                    val notificationKey = cursor.getString(5)
                    val currentKind =
                        enumValueOrDefault(
                            cursor.getString(6),
                            YNotifyNotificationKind.Unknown,
                        )
                    val ongoing = cursor.getInt(7) != 0
                    val foreground = cursor.getInt(8) != 0
                    val progress = cursor.getInt(9)
                    val progressMax = cursor.getInt(10)
                    val progressIndeterminate = cursor.getInt(11) != 0

                    val type =
                        classifyUiType(
                            current = currentType,
                            source = source,
                            packageName = packageName,
                            className = className,
                            notificationKey = notificationKey,
                        )
                    val kind =
                        if (type == YNotifyEventType.Notification) {
                            classifyNotificationFallback(
                                currentKind,
                                ongoing,
                                foreground,
                                progress,
                                progressMax,
                                progressIndeterminate,
                            )
                        } else {
                            YNotifyNotificationKind.Unknown
                        }
                    db.update(
                        "events",
                        ContentValues().apply {
                            put("event_type", type.name)
                            put("notification_kind", kind.name)
                            put("classification_version", CLASSIFICATION_VERSION)
                        },
                        "id = ?",
                        arrayOf(id.toString()),
                    )
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        invalidations.tryEmit(Unit)
    }

    fun markPresentation(
        packageName: String?,
        text: String,
        type: YNotifyEventType,
        now: Long,
    ) {
        val normalized = normalize(text)
        if (normalized.length < 2) return
        val candidates = query(80)
            .asSequence()
            .filter {
                it.eventType == YNotifyEventType.Notification &&
                    kotlin.math.abs(now - it.updatedAt) <= 8_000L &&
                    (
                        packageName.isNullOrBlank() ||
                            packageName == "com.android.systemui" ||
                            it.packageName == packageName
                    )
            }
            .map {
                it to similarity(
                    normalized,
                    normalize(
                        listOfNotNull(
                            it.title,
                            it.fullText,
                            it.text,
                        ).joinToString(" "),
                    ),
                )
            }
            .filter { it.second >= 0.72 }
            .maxByOrNull { it.second }
            ?.first
            ?: return

        val values = ContentValues()
        if (type == YNotifyEventType.SystemUi) {
            values.put("heads_up", 1)
        }
        if (
            type == YNotifyEventType.Popup ||
            type == YNotifyEventType.Dialog
        ) {
            values.put("full_screen_shown", 1)
        }
        if (
            text.contains("bubble", ignoreCase = true)
        ) {
            values.put("bubble_shown", 1)
        }
        if (values.size() > 0) {
            writableDatabase.update(
                "events",
                values,
                "id = ?",
                arrayOf(candidates.id.toString()),
            )
            invalidations.tryEmit(Unit)
        }
    }

    companion object {
        const val CLASSIFICATION_VERSION = 4
        private const val DATABASE_VERSION = 1
        val invalidations = MutableSharedFlow<Unit>(
            extraBufferCapacity = 32,
        )

        fun changes(): SharedFlow<Unit> = invalidations

        fun classifyUiType(
            current: YNotifyEventType,
            source: String,
            packageName: String,
            className: String,
            notificationKey: String?,
        ): YNotifyEventType {
            if (
                source.contains(
                    "notification_listener",
                    ignoreCase = true,
                ) ||
                !notificationKey.isNullOrBlank()
            ) {
                return YNotifyEventType.Notification
            }
            val lowered = className.lowercase()
            return when {
                "snackbar" in lowered ->
                    YNotifyEventType.Snackbar
                "transientnotification" in lowered ||
                    "toast" in lowered ->
                    YNotifyEventType.Toast
                "popup" in lowered ->
                    YNotifyEventType.Popup
                "dialog" in lowered ->
                    YNotifyEventType.Dialog
                packageName == "com.android.systemui" ->
                    YNotifyEventType.SystemUi
                current == YNotifyEventType.Notification ->
                    YNotifyEventType.OtherUi
                else -> current
            }
        }

        fun classifyNotificationFallback(
            current: YNotifyNotificationKind,
            ongoing: Boolean,
            foreground: Boolean,
            progress: Int,
            progressMax: Int,
            progressIndeterminate: Boolean,
        ): YNotifyNotificationKind =
            when {
                current != YNotifyNotificationKind.Unknown ->
                    current
                progressMax > 0 ||
                    progress > 0 ||
                    progressIndeterminate ->
                    YNotifyNotificationKind.Progress
                foreground ->
                    YNotifyNotificationKind.ForegroundService
                ongoing ->
                    YNotifyNotificationKind.Ongoing
                else ->
                    YNotifyNotificationKind.Standard
            }

        private fun YNotifyEvent.toValues(): ContentValues =
            ContentValues().apply {
                if (id > 0L) put("id", id)
                put("event_key", eventKey)
                put("event_type", eventType.name)
                put("source", source)
                put("package_name", packageName)
                put("app_label", appLabel)
                put("title", title)
                put("text_value", text)
                put("full_text", fullText)
                put("posted_at", postedAt)
                put("updated_at", updatedAt)
                put("removed_at", removedAt)
                put("notification_key", notificationKey)
                put("notification_kind", notificationKind.name)
                put("heads_up", if (headsUp) 1 else 0)
                put("bubble_shown", if (bubbleShown) 1 else 0)
                put("full_screen_shown", if (fullScreenShown) 1 else 0)
                put("ongoing", if (ongoing) 1 else 0)
                put(
                    "foreground_service",
                    if (foregroundService) 1 else 0,
                )
                put("progress", progress)
                put("progress_max", progressMax)
                put(
                    "progress_indeterminate",
                    if (progressIndeterminate) 1 else 0,
                )
                put("class_name", className)
                put(
                    "classification_version",
                    classificationVersion,
                )
            }

        private fun android.database.Cursor.getStringOrNull(
            index: Int,
        ): String? =
            if (isNull(index)) null else getString(index)

        private fun android.database.Cursor.getLongOrNull(
            index: Int,
        ): Long? =
            if (isNull(index)) null else getLong(index)

        private inline fun <reified T : Enum<T>>
            enumValueOrDefault(
                raw: String?,
                fallback: T,
            ): T =
            runCatching {
                enumValueOf<T>(raw.orEmpty())
            }.getOrDefault(fallback)

        private fun normalize(value: String): String =
            value.lowercase()
                .replace(
                    Regex("[^\\p{L}\\p{N}]+"),
                    "",
                )

        private fun similarity(
            a: String,
            b: String,
        ): Double {
            if (a.isBlank() || b.isBlank()) return 0.0
            if (a == b || a.contains(b) || b.contains(a)) {
                return 1.0
            }
            if (a.length < 2 || b.length < 2) return 0.0
            val aa =
                (0 until a.length - 1)
                    .mapTo(linkedSetOf()) {
                        a.substring(it, it + 2)
                    }
            val bb =
                (0 until b.length - 1)
                    .mapTo(linkedSetOf()) {
                        b.substring(it, it + 2)
                    }
            if (aa.isEmpty() || bb.isEmpty()) return 0.0
            val intersection = aa.count { it in bb }
            return 2.0 * intersection /
                (aa.size + bb.size).toDouble()
        }
    }
}
