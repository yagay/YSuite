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
import org.json.JSONObject

data class YNotifyAppAggregate(
    val packageName: String,
    val appLabel: String,
    val count: Int,
    val latestAt: Long,
)

internal class YNotifyDatabase(
    context: Context,
) : SQLiteOpenHelper(
    context.applicationContext,
    "ynotify.db",
    null,
    DATABASE_VERSION,
) {
    private val crypto = YNotifyCrypto()

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
        createDetailsTable(db)
    }

    override fun onUpgrade(
        db: SQLiteDatabase,
        oldVersion: Int,
        newVersion: Int,
    ) {
        if (oldVersion < 2) {
            createDetailsTable(db)
        }
    }

    /**
     * Reposting a notification should update its existing row, not replace the
     * primary key and erase runtime heads-up/bubble/fullscreen markers.
     * A queued ranking update must not resurrect an already removed instance.
     */
    fun upsert(event: YNotifyEvent) {
        val db = writableDatabase
        val values = event.toValues().apply {
            put("title", crypto.encrypt(event.title))
            put("text_value", crypto.encrypt(event.text))
            put("full_text", crypto.encrypt(event.fullText))
        }
        db.beginTransaction()
        try {
            var oldPostedAt: Long? = null
            var wasRemoved = false
            db.query(
                "events",
                arrayOf("posted_at", "removed_at"),
                "event_key = ?",
                arrayOf(event.eventKey),
                null, null, null,
            ).use { cursor ->
                if (cursor.moveToFirst()) {
                    oldPostedAt = cursor.getLong(0)
                    wasRemoved = !cursor.isNull(1)
                }
            }
            if (oldPostedAt != null) {
                // The latest notification was already removed; only a truly
                // newer posting of the same key may start another generation.
                if (wasRemoved && event.postedAt <= oldPostedAt!!) return
                values.remove("id")
                if (event.postedAt == oldPostedAt) {
                    values.remove("heads_up")
                    values.remove("bubble_shown")
                    values.remove("full_screen_shown")
                }
                db.update(
                    "events",
                    values,
                    "event_key = ?",
                    arrayOf(event.eventKey),
                )
            } else {
                db.insertOrThrow("events", null, values)
            }
            db.insertWithOnConflict(
                "event_details",
                null,
                ContentValues().apply {
                    put("event_key", event.eventKey)
                    put("detail_blob", crypto.encrypt(encodeDetails(event)))
                },
                SQLiteDatabase.CONFLICT_REPLACE,
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        invalidations.tryEmit(Unit)
    }

    fun markSurface(
        notificationKey: String,
        kind: String,
        at: Long,
    ) {
        val values = ContentValues().apply {
            when (kind) {
                "heads_up" -> put("heads_up", 1)
                "bubble" -> put("bubble_shown", 1)
                "full_screen" -> put("full_screen_shown", 1)
                else -> return
            }
            put("updated_at", at)
        }
        writableDatabase.update(
            "events",
            values,
            "notification_key = ? AND removed_at IS NULL",
            arrayOf(notificationKey),
        )
        invalidations.tryEmit(Unit)
    }

    fun markRemoved(
        notificationKey: String,
        postedAt: Long,
        removedAt: Long,
    ) {
        writableDatabase.update(
            "events",
            ContentValues().apply {
                put("removed_at", removedAt)
                put("updated_at", removedAt)
            },
            "notification_key = ? AND posted_at <= ? AND removed_at IS NULL",
            arrayOf(notificationKey, postedAt.toString()),
        )
        invalidations.tryEmit(Unit)
    }

    fun query(
        limit: Int = 2_000,
    ): List<YNotifyEvent> =
        queryPage(
            limit = limit.coerceIn(1, 10_000),
            offset = 0,
        )

    private fun queryPage(
        limit: Int,
        offset: Int,
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
            offset.coerceAtLeast(0).toString() +
                "," +
                limit.coerceAtLeast(1).toString(),
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
                            crypto.decrypt(cursor.getStringOrNull(titleIndex)),
                        text =
                            crypto.decrypt(cursor.getStringOrNull(textIndex)),
                        fullText =
                            crypto.decrypt(cursor.getStringOrNull(fullTextIndex)),
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
        val details =
            queryDetails(
                result.map {
                    it.eventKey
                },
            )
        return result.map { event ->
            applyDetails(
                event,
                details[event.eventKey],
            )
        }
    }

    fun search(
        query: String,
        limit: Int = 500,
    ): List<YNotifyEvent> {
        val needle =
            query.trim()
        if (needle.isBlank()) {
            return query(
                limit.coerceIn(1, 10_000),
            )
        }

        val result =
            mutableListOf<YNotifyEvent>()
        var offset = 0
        while (result.size < limit) {
            val page =
                queryPage(
                    limit = SEARCH_PAGE_SIZE,
                    offset = offset,
                )
            if (page.isEmpty()) break
            page.asSequence()
                .filter {
                    it.matchesSearch(
                        needle,
                    )
                }
                .take(
                    limit - result.size,
                )
                .forEach(result::add)
            offset += page.size
            if (
                page.size <
                SEARCH_PAGE_SIZE
            ) {
                break
            }
        }
        return result
    }

    fun forEachEvent(
        pageSize: Int = 500,
        consumer: (YNotifyEvent) -> Unit,
    ) {
        val size =
            pageSize.coerceIn(
                50,
                2_000,
            )
        var offset = 0
        while (true) {
            val page =
                queryPage(
                    limit = size,
                    offset = offset,
                )
            if (page.isEmpty()) break
            page.forEach(consumer)
            offset += page.size
            if (page.size < size) {
                break
            }
        }
    }

    fun appAggregates():
        List<YNotifyAppAggregate> {
        val result =
            mutableListOf<
                YNotifyAppAggregate
                >()
        readableDatabase.rawQuery(
            """
            SELECT package_name,
                   MAX(app_label),
                   COUNT(*),
                   MAX(updated_at)
            FROM events
            GROUP BY package_name
            ORDER BY MAX(updated_at) DESC
            """.trimIndent(),
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val packageName =
                    cursor.getString(0)
                        .orEmpty()
                result +=
                    YNotifyAppAggregate(
                        packageName =
                            packageName,
                        appLabel =
                            cursor.getString(1)
                                ?.takeIf {
                                    it.isNotBlank()
                                }
                                ?: packageName,
                        count =
                            cursor.getInt(2),
                        latestAt =
                            cursor.getLong(3),
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

    fun prune(retentionDays: Int) {
        if (retentionDays <= 0) return
        val cutoff = System.currentTimeMillis() - retentionDays * 86_400_000L
        val removed = writableDatabase.delete(
            "events",
            "posted_at < ?",
            arrayOf(cutoff.toString()),
        )
        if (removed > 0) {
            writableDatabase.execSQL(
                "DELETE FROM event_details " +
                    "WHERE event_key NOT IN " +
                    "(SELECT event_key FROM events)",
            )
            invalidations.tryEmit(Unit)
        }
    }

    fun clear() {
        writableDatabase.beginTransaction()
        try {
            writableDatabase.delete(
                "event_details",
                null,
                null,
            )
            writableDatabase.delete(
                "events",
                null,
                null,
            )
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
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
                    it.removedAt == null &&
                    it.updatedAt <= now &&
                    now - it.updatedAt <= 8_000L &&
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

    private fun queryDetails(
        eventKeys: Collection<String>,
    ): Map<String, String> {
        if (eventKeys.isEmpty()) {
            return emptyMap()
        }
        val result =
            mutableMapOf<String, String>()
        eventKeys.distinct()
            .chunked(400)
            .forEach { keys ->
                val placeholders =
                    List(keys.size) {
                        "?"
                    }.joinToString(",")
                readableDatabase.query(
                    "event_details",
                    arrayOf(
                        "event_key",
                        "detail_blob",
                    ),
                    "event_key IN (" +
                        placeholders +
                        ")",
                    keys.toTypedArray(),
                    null,
                    null,
                    null,
                ).use { cursor ->
                    while (
                        cursor.moveToNext()
                    ) {
                        val key =
                            cursor.getString(0)
                        val value =
                            crypto.decrypt(
                                cursor
                                    .getStringOrNull(
                                        1,
                                    ),
                            )
                        if (
                            !key.isNullOrBlank() &&
                            !value.isNullOrBlank()
                        ) {
                            result[key] = value
                        }
                    }
                }
            }
        return result
    }

    private fun encodeDetails(
        event: YNotifyEvent,
    ): String =
        JSONObject().apply {
            put("subText", event.subText)
            put("summaryText", event.summaryText)
            put("rawExtras", event.rawExtras)
            put("messagesJson", event.messagesJson)
            put("actionsJson", event.actionsJson)
            put(
                "notificationId",
                event.notificationId,
            )
            put(
                "notificationTag",
                event.notificationTag,
            )
            put("channelId", event.channelId)
            put("channelName", event.channelName)
            put(
                "channelDescription",
                event.channelDescription,
            )
            put(
                "channelImportance",
                event.channelImportance,
            )
            put("groupKey", event.groupKey)
            put(
                "groupSummary",
                event.groupSummary,
            )
            put("category", event.category)
            put("template", event.template)
            put("importance", event.importance)
            put(
                "conversation",
                event.conversation,
            )
            put(
                "rankingCanBubble",
                event.rankingCanBubble,
            )
            put(
                "rankingAmbient",
                event.rankingAmbient,
            )
            put(
                "rankingSuspended",
                event.rankingSuspended,
            )
            put("flags", event.flags)
            put("clearable", event.clearable)
            put("bubble", event.bubble)
            put(
                "fullScreen",
                event.fullScreen,
            )
            put(
                "payloadSilent",
                event.payloadSilent,
            )
            put("silent", event.silent)
            put(
                "removalReason",
                event.removalReason,
            )
        }.toString()

    private fun applyDetails(
        event: YNotifyEvent,
        raw: String?,
    ): YNotifyEvent {
        if (raw.isNullOrBlank()) {
            return event
        }
        return runCatching {
            val value = JSONObject(raw)
            event.copy(
                subText =
                    value.optStringOrNull(
                        "subText",
                    ),
                summaryText =
                    value.optStringOrNull(
                        "summaryText",
                    ),
                rawExtras =
                    value.optStringOrNull(
                        "rawExtras",
                    ),
                messagesJson =
                    value.optStringOrNull(
                        "messagesJson",
                    ),
                actionsJson =
                    value.optStringOrNull(
                        "actionsJson",
                    ),
                notificationId =
                    value.optInt(
                        "notificationId",
                        0,
                    ),
                notificationTag =
                    value.optStringOrNull(
                        "notificationTag",
                    ),
                channelId =
                    value.optStringOrNull(
                        "channelId",
                    ),
                channelName =
                    value.optStringOrNull(
                        "channelName",
                    ),
                channelDescription =
                    value.optStringOrNull(
                        "channelDescription",
                    ),
                channelImportance =
                    value.optInt(
                        "channelImportance",
                        0,
                    ),
                groupKey =
                    value.optStringOrNull(
                        "groupKey",
                    ),
                groupSummary =
                    value.optBoolean(
                        "groupSummary",
                        false,
                    ),
                category =
                    value.optStringOrNull(
                        "category",
                    ),
                template =
                    value.optStringOrNull(
                        "template",
                    ),
                importance =
                    value.optInt(
                        "importance",
                        0,
                    ),
                conversation =
                    value.optBoolean(
                        "conversation",
                        false,
                    ),
                rankingCanBubble =
                    value.optBoolean(
                        "rankingCanBubble",
                        false,
                    ),
                rankingAmbient =
                    value.optBoolean(
                        "rankingAmbient",
                        false,
                    ),
                rankingSuspended =
                    value.optBoolean(
                        "rankingSuspended",
                        false,
                    ),
                flags =
                    value.optInt(
                        "flags",
                        0,
                    ),
                clearable =
                    value.optBoolean(
                        "clearable",
                        true,
                    ),
                bubble =
                    value.optBoolean(
                        "bubble",
                        false,
                    ),
                fullScreen =
                    value.optBoolean(
                        "fullScreen",
                        false,
                    ),
                payloadSilent =
                    value.optBoolean(
                        "payloadSilent",
                        false,
                    ),
                silent =
                    value.optBoolean(
                        "silent",
                        false,
                    ),
                removalReason =
                    value.optInt(
                        "removalReason",
                        0,
                    ),
            )
        }.getOrDefault(event)
    }

    private fun JSONObject.optStringOrNull(
        key: String,
    ): String? =
        if (
            has(key) &&
            !isNull(key)
        ) {
            optString(key)
                .takeIf(String::isNotBlank)
        } else {
            null
        }

    private fun YNotifyEvent.matchesSearch(
        needle: String,
    ): Boolean =
        sequenceOf(
            appLabel,
            packageName,
            title,
            text,
            fullText,
            subText,
            summaryText,
            channelName,
            channelDescription,
            messagesJson,
            actionsJson,
            rawExtras,
            className,
        ).filterNotNull()
            .any {
                it.contains(
                    needle,
                    ignoreCase = true,
                )
            }

    companion object {
        const val CLASSIFICATION_VERSION = 4
        private const val DATABASE_VERSION = 2
        private const val SEARCH_PAGE_SIZE = 500
        val invalidations = MutableSharedFlow<Unit>(
            extraBufferCapacity = 32,
        )

        private fun createDetailsTable(
            db: SQLiteDatabase,
        ) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS event_details (
                    event_key TEXT PRIMARY KEY,
                    detail_blob TEXT
                )
                """.trimIndent(),
            )
        }

        fun changes(): SharedFlow<Unit> = invalidations

        fun signalRuntimeStatusChanged() {
            invalidations.tryEmit(Unit)
        }

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
