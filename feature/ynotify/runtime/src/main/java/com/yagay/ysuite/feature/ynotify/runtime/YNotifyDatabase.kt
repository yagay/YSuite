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
import java.security.MessageDigest

data class YNotifyRevision(
    val sequence: Int,
    val capturedAt: Long,
    val title: String?,
    val text: String?,
    val fullText: String?,
    val progress: Int,
    val progressMax: Int,
)

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
                classification_version INTEGER NOT NULL DEFAULT 1,
                classification_locked INTEGER NOT NULL DEFAULT 0,
                original_event_type TEXT,
                classification_source TEXT,
                merged_into_id INTEGER,
                linked_notification_id INTEGER
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
        createRevisionsTable(db)
        createMergeIndex(db)
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_ynotify_linked ON events(linked_notification_id)")
        createUpdatedIndex(db)
    }

    override fun onUpgrade(
        db: SQLiteDatabase,
        oldVersion: Int,
        newVersion: Int,
    ) {
        if (oldVersion < 2) {
            createDetailsTable(db)
        }
        if (oldVersion < 3) {
            createRevisionsTable(db)
        }
        if (oldVersion < 4) {
            db.execSQL("ALTER TABLE events ADD COLUMN classification_locked INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE events ADD COLUMN original_event_type TEXT")
            db.execSQL("ALTER TABLE events ADD COLUMN classification_source TEXT")
        }
        if (oldVersion < 5) {
            db.execSQL("ALTER TABLE events ADD COLUMN merged_into_id INTEGER")
            createMergeIndex(db)
        }
        if (oldVersion < 6) {
            db.execSQL("ALTER TABLE events ADD COLUMN linked_notification_id INTEGER")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_ynotify_linked ON events(linked_notification_id)")
        }
        if (oldVersion < 7) {
            createUpdatedIndex(db)
        }
    }

    /**
     * Reposting a notification should update its existing row, not replace the
     * primary key and erase runtime heads-up/bubble/fullscreen markers.
     * A queued ranking update must not resurrect an already removed instance.
     */
    fun upsert(event: YNotifyEvent) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            var current: YNotifyEvent = event
            if (event.eventType == YNotifyEventType.Notification &&
                !event.notificationKey.isNullOrBlank()
            ) {
                // The original app distinguished notification instances and their
                // updates. A reused Android notification key must not erase a
                // previously dismissed instance.
                var lastKey: String? = null
                var lastPostedAt = 0L
                var active = false
                db.query(
                    "events",
                    arrayOf("event_key", "posted_at", "removed_at"),
                    "notification_key = ? AND merged_into_id IS NULL",
                    arrayOf(event.notificationKey),
                    null, null,
                    "posted_at DESC, id DESC",
                    "1",
                ).use { cursor ->
                    if (cursor.moveToFirst()) {
                        lastKey = cursor.getString(0)
                        lastPostedAt = cursor.getLong(1)
                        active = cursor.isNull(2)
                    }
                }
                if (lastKey != null && event.postedAt < lastPostedAt) {
                    // Delayed ranking updates for an older notification instance.
                    return
                }
                if (lastKey != null && !active &&
                    event.postedAt <= lastPostedAt
                ) return
                current = event.copy(
                    eventKey = if (active && lastKey != null) lastKey!!
                        else "notification:" + event.notificationKey + "@" + event.postedAt,
                    postedAt = if (active) lastPostedAt else event.postedAt,
                )
            }
            val values = current.toValues().apply {
                put("title", crypto.encrypt(current.title))
                put("text_value", crypto.encrypt(current.text))
                put("full_text", crypto.encrypt(current.fullText))
            }
            var existing = false
            db.query(
                "events", arrayOf("id"), "event_key = ?",
                arrayOf(current.eventKey), null, null, null,
            ).use { existing = it.moveToFirst() }
            if (existing) {
                values.remove("id")
                val locked = db.query(
                    "events", arrayOf("classification_locked"),
                    "event_key = ?", arrayOf(current.eventKey),
                    null, null, null,
                ).use { cursor ->
                    cursor.moveToFirst() && cursor.getInt(0) != 0
                }
                if (locked) {
                    values.remove("event_type")
                    values.remove("notification_kind")
                    values.remove("classification_version")
                }
                // A later ranking callback must not clear a surface marker.
                values.remove("heads_up")
                values.remove("bubble_shown")
                values.remove("full_screen_shown")
                db.update(
                    "events", values, "event_key = ?",
                    arrayOf(current.eventKey),
                )
            } else {
                db.insertOrThrow("events", null, values)
            }
            db.insertWithOnConflict(
                "event_details",
                null,
                ContentValues().apply {
                    put("event_key", current.eventKey)
                    put("detail_blob", crypto.encrypt(encodeDetails(current)))
                },
                SQLiteDatabase.CONFLICT_REPLACE,
            )
            if (current.eventType == YNotifyEventType.Notification) {
                recordRevision(db, current)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        invalidations.tryEmit(Unit)
    }

    private fun recordRevision(db: SQLiteDatabase, event: YNotifyEvent) {
        val payload = JSONObject().apply {
            put("title", event.title)
            put("text", event.text)
            put("fullText", event.fullText)
            put("messages", event.messagesJson)
            put("progress", event.progress)
            put("progressMax", event.progressMax)
            put("importance", event.importance)
        }.toString()
        val digest = MessageDigest.getInstance("SHA-256").digest(
            payload.toByteArray(Charsets.UTF_8),
        ).joinToString("") { "%02x".format(it) }
        var sequence = 1
        var unchanged = false
        db.query(
            "notification_revisions",
            arrayOf("sequence", "content_hash"),
            "event_key = ?",
            arrayOf(event.eventKey),
            null, null, "sequence DESC", "1",
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                sequence = cursor.getInt(0) + 1
                unchanged = cursor.getString(1) == digest
            }
        }
        if (unchanged) return
        db.insertOrThrow(
            "notification_revisions", null,
            ContentValues().apply {
                put("event_key", event.eventKey)
                put("sequence", sequence)
                put("captured_at", event.updatedAt)
                put("content_hash", digest)
                put("title", crypto.encrypt(event.title))
                put("text_value", crypto.encrypt(event.text))
                put("full_text", crypto.encrypt(event.fullText))
                put("progress", event.progress)
                put("progress_max", event.progressMax)
            },
        )
    }

    fun revisions(eventKey: String): List<YNotifyRevision> {
        val result = mutableListOf<YNotifyRevision>()
        readableDatabase.query(
            "notification_revisions", null,
            "event_key IN (SELECT event_key FROM events WHERE event_key = ? OR merged_into_id = (SELECT id FROM events WHERE event_key = ?))",
            arrayOf(eventKey, eventKey),
            null, null, "captured_at DESC, sequence DESC", "100",
        ).use { cursor ->
            val number = cursor.getColumnIndexOrThrow("sequence")
            val at = cursor.getColumnIndexOrThrow("captured_at")
            val title = cursor.getColumnIndexOrThrow("title")
            val text = cursor.getColumnIndexOrThrow("text_value")
            val full = cursor.getColumnIndexOrThrow("full_text")
            val progress = cursor.getColumnIndexOrThrow("progress")
            val maximum = cursor.getColumnIndexOrThrow("progress_max")
            while (cursor.moveToNext()) {
                result += YNotifyRevision(
                    sequence = cursor.getInt(number),
                    capturedAt = cursor.getLong(at),
                    title = crypto.decrypt(cursor.getString(title)),
                    text = crypto.decrypt(cursor.getString(text)),
                    fullText = crypto.decrypt(cursor.getString(full)),
                    progress = cursor.getInt(progress),
                    progressMax = cursor.getInt(maximum),
                )
            }
        }
        return result
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
            "notification_key = ? AND removed_at IS NULL AND classification_locked = 0",
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
        includeMerged: Boolean = false,
    ): List<YNotifyEvent> {
        val result = mutableListOf<YNotifyEvent>()
        readableDatabase.query(
            "events",
            null,
            if (includeMerged) null else "merged_into_id IS NULL",
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
            val lockedIndex = cursor.getColumnIndexOrThrow("classification_locked")
            val originalTypeIndex = cursor.getColumnIndexOrThrow("original_event_type")
            val manualSourceIndex = cursor.getColumnIndexOrThrow("classification_source")
            val mergedIndex = cursor.getColumnIndexOrThrow("merged_into_id")
            val linkedIndex = cursor.getColumnIndexOrThrow("linked_notification_id")
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
                        classificationLocked = cursor.getInt(lockedIndex) != 0,
                        originalEventType = cursor.getStringOrNull(originalTypeIndex)?.let {
                            enumValueOrDefault(it, YNotifyEventType.OtherUi)
                        },
                        classificationSource = cursor.getStringOrNull(manualSourceIndex),
                        mergedIntoId = cursor.getLongOrNull(mergedIndex),
                        linkedNotificationId = cursor.getLongOrNull(linkedIndex),
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
                    includeMerged = true,
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
            WHERE merged_into_id IS NULL
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
            "SELECT COUNT(*) FROM events WHERE merged_into_id IS NULL",
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
                "UPDATE events SET merged_into_id = NULL " +
                    "WHERE merged_into_id IS NOT NULL AND merged_into_id NOT IN " +
                    "(SELECT id FROM events)",
            )
            writableDatabase.execSQL(
                "UPDATE events SET linked_notification_id = NULL " +
                    "WHERE linked_notification_id IS NOT NULL " +
                    "AND linked_notification_id NOT IN (SELECT id FROM events)",
            )
            writableDatabase.execSQL(
                "DELETE FROM event_details " +
                    "WHERE event_key NOT IN " +
                    "(SELECT event_key FROM events)",
            )
            writableDatabase.execSQL(
                "DELETE FROM notification_revisions " +
                    "WHERE event_key NOT IN (SELECT event_key FROM events)",
            )
            invalidations.tryEmit(Unit)
        }
    }

    fun deletePackage(packageName: String) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete(
                "notification_revisions",
                "event_key IN (SELECT event_key FROM events WHERE package_name = ?)",
                arrayOf(packageName),
            )
            db.delete(
                "event_details",
                "event_key IN (SELECT event_key FROM events WHERE package_name = ?)",
                arrayOf(packageName),
            )
            db.delete("events", "package_name = ?", arrayOf(packageName))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        invalidations.tryEmit(Unit)
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
                "notification_revisions",
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


    fun setManualClassification(
        id: Long,
        type: YNotifyEventType,
        headsUp: Boolean,
        bubble: Boolean,
    ): Boolean {
        val db = writableDatabase
        var changed = false
        db.beginTransaction()
        try {
            val previous = db.query(
                "events", arrayOf("event_type", "original_event_type"),
                "id = ?", arrayOf(id.toString()),
                null, null, null, "1",
            ).use { cursor ->
                if (!cursor.moveToFirst()) null
                else if (cursor.isNull(1)) cursor.getString(0) else cursor.getString(1)
            }
            if (previous != null) {
                changed = db.update(
                    "events",
                    ContentValues().apply {
                        put("original_event_type", previous)
                        put("event_type", type.name)
                        put("classification_locked", 1)
                        put("classification_source", "manual")
                        put("classification_version", CLASSIFICATION_VERSION)
                        put("heads_up", if (type == YNotifyEventType.Notification && headsUp) 1 else 0)
                        put("bubble_shown", if (type == YNotifyEventType.Notification && bubble) 1 else 0)
                    },
                    "id = ?", arrayOf(id.toString()),
                ) > 0
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        if (changed) invalidations.tryEmit(Unit)
        return changed
    }

    fun clearManualClassification(id: Long): Boolean {
        val db = writableDatabase
        var changed = false
        db.beginTransaction()
        try {
            val originalType = db.query(
                "events", arrayOf("original_event_type"),
                "id = ? AND classification_locked = 1",
                arrayOf(id.toString()), null, null, null, "1",
            ).use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0)
                else null
            }
            changed = db.update(
                "events",
                ContentValues().apply {
                    put("classification_locked", 0)
                    put("classification_source", "manual_reset")
                    if (originalType != null) put("event_type", originalType)
                    put("classification_version", 0)
                },
                "id = ? AND classification_locked = 1",
                arrayOf(id.toString()),
            ) > 0
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        if (changed) reclassify()
        return changed
    }

    fun reclassify(): Pair<Int, Int> {
        val db = writableDatabase
        var mergedCount = 0
        var linkedCount = 0
        db.beginTransaction()
        try {
            db.rawQuery(
                "SELECT id,event_type,source,package_name,class_name,notification_key,notification_kind,ongoing,foreground_service,progress,progress_max,progress_indeterminate FROM events WHERE classification_locked = 0",
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
            mergedCount = mergeHistoricalNotificationUpdates(db)
            linkedCount = correlateHistoricalBanners(db)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        invalidations.tryEmit(Unit)
        return mergedCount to linkedCount
    }

    /**
     * Correlate accessibility banners with a real notification only when text
     * and timing both agree. A SystemUI banner can legitimately belong to a
     * different package, but uses a higher matching threshold.
     */
    fun markPresentation(
        packageName: String?,
        text: String,
        type: YNotifyEventType,
        now: Long,
        eventKey: String? = null,
    ) {
        val database = writableDatabase
        val targetId = findBannerNotification(database, packageName, text, now)
            ?: return
        database.beginTransaction()
        var updated = 0
        try {
            updated += applyBannerMatch(database, targetId, type, text, eventKey)
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
        if (updated > 0) invalidations.tryEmit(Unit)
    }

    /**
     * Both live capture and repair query the same indexed 5-second window.
     * Matching against only the last N notifications misses busy devices.
     * SystemUI can present another package's notification, but a cross-app
     * match requires stronger confidence and an unambiguous best candidate.
     */
    private fun findBannerNotification(
        db: SQLiteDatabase,
        packageName: String?,
        text: String,
        at: Long,
    ): Long? {
        val normalized = normalize(text)
        if (normalized.length < 2) return null
        val systemUi = packageName.isNullOrBlank() ||
            packageName == "com.android.systemui"
        val earliest = (at - 5_000L).coerceAtLeast(0L).toString()
        val latest = (at + 5_000L).toString()
        val matches = mutableListOf<Pair<Long, Double>>()
        db.rawQuery(
            """
            SELECT id,package_name,posted_at,updated_at,title,full_text,text_value,removed_at
            FROM events
            WHERE event_type = 'Notification'
              AND classification_locked = 0 AND merged_into_id IS NULL
              AND (posted_at BETWEEN ? AND ? OR updated_at BETWEEN ? AND ?)
            ORDER BY updated_at DESC LIMIT 200
            """.trimIndent(),
            arrayOf(earliest, latest, earliest, latest),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val otherPackage = cursor.getString(1).orEmpty()
                if (!systemUi && otherPackage != packageName) continue
                if (!cursor.isNull(7) && cursor.getLong(7) < at) continue
                val dt = minOf(
                    kotlin.math.abs(at - cursor.getLong(2)),
                    kotlin.math.abs(at - cursor.getLong(3)),
                )
                if (dt > 5_000L) continue
                val candidateText = normalize(listOfNotNull(
                    crypto.decrypt(cursor.getStringOrNull(4)),
                    crypto.decrypt(cursor.getStringOrNull(5)),
                    crypto.decrypt(cursor.getStringOrNull(6)),
                ).joinToString(" "))
                val similarity = similarity(normalized, candidateText)
                val confident =
                    if (systemUi && otherPackage != packageName) {
                        similarity >= 0.995 ||
                            (similarity >= 0.92 && dt <= 2_500L)
                    } else {
                        similarity >= 0.995 ||
                            (similarity >= 0.82 && dt <= 3_000L)
                    }
                if (confident) {
                    matches += cursor.getLong(0) to
                        (similarity - dt / 5_000.0 * 0.04)
                }
            }
        }
        val ranked = matches.sortedByDescending { it.second }
        val best = ranked.firstOrNull() ?: return null
        if (ranked.size > 1 && best.second - ranked[1].second < 0.05) {
            return null
        }
        return best.first
    }

    private fun applyBannerMatch(
        db: SQLiteDatabase,
        notificationId: Long,
        type: YNotifyEventType,
        text: String,
        eventKey: String?,
    ): Int {
        val values = ContentValues().apply {
            if (type == YNotifyEventType.SystemUi) put("heads_up", 1)
            if (type == YNotifyEventType.Popup ||
                type == YNotifyEventType.Dialog
            ) put("full_screen_shown", 1)
            if (text.contains("bubble", ignoreCase = true)) {
                put("bubble_shown", 1)
            }
        }
        var changed = 0
        if (values.size() > 0) {
            changed += db.update(
                "events", values,
                "id = ? AND classification_locked = 0",
                arrayOf(notificationId.toString()),
            )
        }
        if (!eventKey.isNullOrBlank()) {
            changed += db.update(
                "events",
                ContentValues().apply {
                    put("linked_notification_id", notificationId)
                },
                "event_key = ? AND classification_locked = 0 AND event_type != ?",
                arrayOf(eventKey, YNotifyEventType.Notification.name),
            )
        }
        return changed
    }

    /**
     * Recover relationships for older accessibility banners. Iterate in fixed
     * batches by stable row ID so updates do not change pagination positions.
     * No historical row is deleted and manually locked records are skipped.
     */
    private fun correlateHistoricalBanners(db: SQLiteDatabase): Int {
        data class Banner(
            val id: Long,
            val eventKey: String,
            val pkg: String,
            val text: String,
            val at: Long,
            val type: YNotifyEventType,
        )
        var afterId = 0L
        var linked = 0
        while (true) {
            val batch = mutableListOf<Banner>()
            db.rawQuery(
                """
                SELECT id,event_key,package_name,posted_at,event_type,
                       full_text,text_value
                FROM events
                WHERE id > ? AND source LIKE '%accessibility%'
                  AND event_type != 'Notification' AND classification_locked = 0
                  AND merged_into_id IS NULL AND linked_notification_id IS NULL
                ORDER BY id LIMIT 200
                """.trimIndent(),
                arrayOf(afterId.toString()),
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(0)
                    afterId = id
                    val text = crypto.decrypt(cursor.getStringOrNull(5))
                        ?.takeIf(String::isNotBlank)
                        ?: crypto.decrypt(cursor.getStringOrNull(6)).orEmpty()
                    if (text.isBlank()) continue
                    batch += Banner(
                        id = id,
                        eventKey = cursor.getString(1),
                        pkg = cursor.getString(2).orEmpty(),
                        at = cursor.getLong(3),
                        type = enumValueOrDefault(
                            cursor.getString(4), YNotifyEventType.OtherUi,
                        ),
                        text = text,
                    )
                }
            }
            if (batch.isEmpty()) break
            for (banner in batch) {
                val match = findBannerNotification(
                    db, banner.pkg, banner.text, banner.at,
                ) ?: continue
                // Only count successful relationships; presentation indicators
                // are updated by the same shared path as real-time capture.
                val changed = applyBannerMatch(
                    db, match, banner.type, banner.text, banner.eventKey,
                )
                if (changed > 0) linked++
            }
            if (batch.size < 200) break
        }
        return linked
    }

    /**
     * Port of the original YNotify notification lifecycle grouping.
     * Nothing is deleted: secondary rows retain their own revisions and detail
     * blobs, while timeline, search and aggregates show only canonical rows.
     * A manual classification always prevents either side from being merged.
     */
    private fun mergeHistoricalNotificationUpdates(db: SQLiteDatabase): Int {
        data class Entry(
            val id: Long,
            val key: String,
            val pkg: String,
            val posted: Long,
            val updated: Long,
            val removed: Long?,
            val longLived: Boolean,
            val locked: Boolean,
        )
        val merges = mutableListOf<Pair<Long, Long>>()
        var previous: Entry? = null
        db.rawQuery(
            """
            SELECT id, notification_key, package_name, posted_at, updated_at,
                   removed_at, ongoing, foreground_service, progress,
                   progress_max, progress_indeterminate, classification_locked
            FROM events
            WHERE merged_into_id IS NULL AND event_type = 'Notification'
              AND notification_key IS NOT NULL AND notification_key != ''
            ORDER BY notification_key, package_name, posted_at, id
            """.trimIndent(),
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val next = Entry(
                    id = cursor.getLong(0),
                    key = cursor.getString(1),
                    pkg = cursor.getString(2),
                    posted = cursor.getLong(3),
                    updated = cursor.getLong(4),
                    removed = if (cursor.isNull(5)) null else cursor.getLong(5),
                    longLived = cursor.getInt(6) != 0 || cursor.getInt(7) != 0 ||
                        cursor.getInt(8) > 0 || cursor.getInt(9) > 0 ||
                        cursor.getInt(10) != 0,
                    locked = cursor.getInt(11) != 0,
                )
                val canonical = previous
                val sameGroup = canonical != null &&
                    canonical.key == next.key && canonical.pkg == next.pkg
                val gap = if (canonical != null)
                    (next.posted - maxOf(canonical.posted, canonical.updated)).coerceAtLeast(0L)
                else Long.MAX_VALUE
                val window = if (canonical != null && (canonical.longLived || next.longLived))
                    86_400_000L else 600_000L
                val sameLifecycle = sameGroup && !next.locked &&
                    canonical?.locked == false && gap <= window &&
                    (canonical.removed == null || next.posted <= canonical.removed + 2_000L)
                if (sameLifecycle && canonical != null) {
                    merges += next.id to canonical.id
                    previous = canonical.copy(
                        updated = maxOf(canonical.updated, next.updated),
                        removed = next.removed ?: canonical.removed,
                        longLived = canonical.longLived || next.longLived,
                    )
                } else {
                    previous = next
                }
            }
        }
        for ((secondary, canonical) in merges) {
            db.execSQL(
                """
                UPDATE events SET
                    updated_at = MAX(updated_at, (SELECT updated_at FROM events WHERE id = ?)),
                    removed_at = COALESCE((SELECT removed_at FROM events WHERE id = ?), removed_at),
                    heads_up = MAX(heads_up, (SELECT heads_up FROM events WHERE id = ?)),
                    bubble_shown = MAX(bubble_shown, (SELECT bubble_shown FROM events WHERE id = ?)),
                    full_screen_shown = MAX(full_screen_shown, (SELECT full_screen_shown FROM events WHERE id = ?)),
                    title = COALESCE((SELECT title FROM events WHERE id = ?), title),
                    text_value = COALESCE((SELECT text_value FROM events WHERE id = ?), text_value),
                    full_text = COALESCE((SELECT full_text FROM events WHERE id = ?), full_text)
                WHERE id = ? AND classification_locked = 0
                """.trimIndent(),
                arrayOf(secondary, secondary, secondary, secondary, secondary,
                    secondary, secondary, secondary, canonical),
            )
            // Preserve the most recent encrypted detail payload on the visible
            // event without deleting either event's original detail or revisions.
            db.execSQL(
                """
                UPDATE event_details SET detail_blob =
                    (SELECT detail_blob FROM event_details
                     WHERE event_key = (SELECT event_key FROM events WHERE id = ?))
                WHERE event_key = (SELECT event_key FROM events WHERE id = ?)
                  AND EXISTS (
                      SELECT 1 FROM event_details
                      WHERE event_key = (SELECT event_key FROM events WHERE id = ?)
                  )
                """.trimIndent(),
                arrayOf(secondary, canonical, secondary),
            )
            db.execSQL(
                """
                UPDATE events SET merged_into_id = ?,
                    classification_version = ?,
                    classification_source = 'repair:merged-notification-update'
                WHERE id = ? AND merged_into_id IS NULL AND classification_locked = 0
                """.trimIndent(),
                arrayOf(canonical, CLASSIFICATION_VERSION, secondary),
            )
        }
        return merges.size
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
        private const val DATABASE_VERSION = 7
        private const val SEARCH_PAGE_SIZE = 500
        val invalidations = MutableSharedFlow<Unit>(
            extraBufferCapacity = 32,
        )

        private fun createUpdatedIndex(db: SQLiteDatabase) {
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS idx_ynotify_updated ON events(updated_at)",
            )
        }

        private fun createMergeIndex(db: SQLiteDatabase) {
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS idx_ynotify_merged ON events(merged_into_id)",
            )
        }

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

        private fun createRevisionsTable(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS notification_revisions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    event_key TEXT NOT NULL,
                    sequence INTEGER NOT NULL,
                    captured_at INTEGER NOT NULL,
                    content_hash TEXT NOT NULL,
                    title TEXT,
                    text_value TEXT,
                    full_text TEXT,
                    progress INTEGER NOT NULL DEFAULT 0,
                    progress_max INTEGER NOT NULL DEFAULT 0,
                    UNIQUE(event_key, sequence)
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS idx_ynotify_revisions_key " +
                    "ON notification_revisions(event_key)",
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
