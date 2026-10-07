package com.yagay.ysuite.feature.ydownload

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.yagay.ysuite.feature.ydownload.api.YDownloadBackend
import com.yagay.ysuite.feature.ydownload.api.YDownloadChunk
import com.yagay.ysuite.feature.ydownload.api.YDownloadItem
import com.yagay.ysuite.feature.ydownload.api.YDownloadRequest
import com.yagay.ysuite.feature.ydownload.api.YDownloadState
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class YDownloadRepository(
    context: Context,
) {
    private val helper =
        DownloadDbHelper(context.applicationContext)
    private val mutex = Mutex()
    private val mutableItems =
        MutableStateFlow<List<YDownloadItem>>(emptyList())

    val items: StateFlow<List<YDownloadItem>> =
        mutableItems.asStateFlow()

    suspend fun refresh() {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                mutableItems.value = queryAll()
            }
        }
    }

    suspend fun add(
        request: YDownloadRequest,
        queued: Boolean,
    ): String =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val id = UUID.randomUUID().toString()
                val scheduledAt =
                    request.scheduledAtMillis
                        ?.takeIf {
                            it > System.currentTimeMillis()
                        }
                val backend =
                    if (
                        request.backend ==
                            YDownloadBackend.System &&
                        (
                            request.destinationTreeUri != null ||
                            request.speedLimitBytesPerSecond > 0L
                        )
                    ) {
                        YDownloadBackend.Private
                    } else {
                        request.backend
                    }
                val values = ContentValues().apply {
                    put(COL_ID, id)
                    put(
                        COL_BACKEND,
                        backend.name,
                    )
                    putNull(COL_SYSTEM_ID)
                    put(COL_URL, request.url)
                    put(COL_FILE_NAME, request.fileName)
                    putNull(COL_OUTPUT_URI)
                    put(COL_MIME_TYPE, request.mimeType)
                    put(COL_TOTAL_BYTES, request.totalBytes)
                    put(COL_DOWNLOADED_BYTES, 0L)
                    put(
                        COL_STATE,
                        if (scheduledAt != null) {
                            YDownloadState.Scheduled.name
                        } else {
                            YDownloadState.Pending.name
                        },
                    )
                    put(COL_SPEED, 0L)
                    put(COL_ETA, 0L)
                    put(COL_ADDED_AT, System.currentTimeMillis())
                    putNull(COL_COMPLETED_AT)
                    putNull(COL_ERROR)
                    put(
                        COL_SUPPORTS_RANGES,
                        if (request.supportsRanges) 1 else 0,
                    )
                    put(
                        COL_QUEUED,
                        if (scheduledAt == null && queued) 1 else 0,
                    )
                    put(COL_REFERER, request.referer)
                    put(COL_USER_AGENT, request.userAgent)
                    put(COL_COOKIES, request.cookies)
                    put(COL_USERNAME, request.username)
                    put(COL_PASSWORD, request.password)
                    put(
                        COL_DESTINATION_TREE_URI,
                        request.destinationTreeUri,
                    )
                    put(
                        COL_THREAD_COUNT,
                        request.threadCount.coerceIn(1, 16),
                    )
                    put(
                        COL_TASK_SPEED_LIMIT,
                        request.speedLimitBytesPerSecond
                            .coerceAtLeast(0L),
                    )
                    put(
                        COL_CUSTOM_HEADERS,
                        encodeHeaders(request.customHeaders),
                    )
                    put(COL_SCHEDULED_AT, scheduledAt)
                    put(COL_CHUNKS, "")
                }
                helper.writableDatabase.insertOrThrow(
                    TABLE,
                    null,
                    values,
                )
                mutableItems.value = queryAll()
                id
            }
        }

    suspend fun find(id: String): YDownloadItem? =
        withContext(Dispatchers.IO) {
            mutex.withLock { queryOne(id) }
        }

    suspend fun setBackend(
        id: String,
        backend: YDownloadBackend,
    ) {
        updateColumns(
            id,
            ContentValues().apply {
                put(
                    COL_BACKEND,
                    backend.name,
                )
                if (
                    backend !=
                    YDownloadBackend.System
                ) {
                    putNull(COL_SYSTEM_ID)
                }
            },
        )
    }

    suspend fun bindSystemDownload(
        id: String,
        systemId: Long,
    ) {
        updateColumns(
            id,
            ContentValues().apply {
                put(
                    COL_BACKEND,
                    YDownloadBackend.System.name,
                )
                put(COL_SYSTEM_ID, systemId)
                put(
                    COL_STATE,
                    YDownloadState.Connecting.name,
                )
                put(COL_QUEUED, 0)
                putNull(COL_ERROR)
            },
        )
    }

    suspend fun updateSystemSnapshot(
        id: String,
        state: YDownloadState,
        downloadedBytes: Long,
        totalBytes: Long,
        speedBytesPerSecond: Long,
        etaSeconds: Long,
        outputUri: String?,
        error: String?,
    ) {
        updateColumns(
            id,
            ContentValues().apply {
                put(COL_STATE, state.name)
                put(
                    COL_DOWNLOADED_BYTES,
                    downloadedBytes.coerceAtLeast(0L),
                )
                put(COL_TOTAL_BYTES, totalBytes)
                put(
                    COL_SPEED,
                    speedBytesPerSecond
                        .coerceAtLeast(0L),
                )
                put(COL_ETA, etaSeconds)
                put(COL_OUTPUT_URI, outputUri)
                put(COL_ERROR, error)
                put(COL_QUEUED, 0)
                if (
                    state ==
                    YDownloadState.Completed
                ) {
                    put(
                        COL_COMPLETED_AT,
                        System.currentTimeMillis(),
                    )
                }
            },
        )
    }

    suspend fun clearSystemDownload(
        id: String,
    ) {
        updateColumns(
            id,
            ContentValues().apply {
                putNull(COL_SYSTEM_ID)
            },
        )
    }

    suspend fun setOutputUri(
        id: String,
        uri: String,
    ) = updateColumns(
        id,
        ContentValues().apply {
            put(COL_OUTPUT_URI, uri)
        },
    )

    suspend fun updateState(
        id: String,
        state: YDownloadState,
        error: String? = null,
        queued: Boolean? = null,
    ) {
        updateColumns(
            id,
            ContentValues().apply {
                put(COL_STATE, state.name)
                put(COL_ERROR, error)
                if (queued != null) {
                    put(COL_QUEUED, if (queued) 1 else 0)
                }
                if (
                    state != YDownloadState.Downloading &&
                    state != YDownloadState.Connecting
                ) {
                    put(COL_SPEED, 0L)
                    put(COL_ETA, 0L)
                }
            },
        )
    }

    suspend fun updateProgress(
        id: String,
        downloadedBytes: Long,
        totalBytes: Long,
        speed: Long,
        etaSeconds: Long,
        supportsRanges: Boolean,
    ) {
        updateColumns(
            id,
            ContentValues().apply {
                put(COL_STATE, YDownloadState.Downloading.name)
                put(COL_DOWNLOADED_BYTES, downloadedBytes)
                put(COL_TOTAL_BYTES, totalBytes)
                put(COL_SPEED, speed)
                put(COL_ETA, etaSeconds)
                put(
                    COL_SUPPORTS_RANGES,
                    if (supportsRanges) 1 else 0,
                )
                put(COL_QUEUED, 0)
                putNull(COL_ERROR)
            },
        )
    }

    suspend fun markCompleted(
        id: String,
        totalBytes: Long,
    ) {
        updateColumns(
            id,
            ContentValues().apply {
                put(COL_STATE, YDownloadState.Completed.name)
                put(COL_DOWNLOADED_BYTES, totalBytes)
                put(COL_TOTAL_BYTES, totalBytes)
                put(COL_SPEED, 0L)
                put(COL_ETA, 0L)
                put(COL_COMPLETED_AT, System.currentTimeMillis())
                put(COL_QUEUED, 0)
                putNull(COL_ERROR)
            },
        )
    }

    suspend fun updateChunks(
        id: String,
        chunks: List<YDownloadChunk>,
        downloadedBytes: Long,
        totalBytes: Long,
        speed: Long,
        etaSeconds: Long,
    ) {
        updateColumns(
            id,
            ContentValues().apply {
                put(COL_STATE, YDownloadState.Downloading.name)
                put(COL_CHUNKS, encodeChunks(chunks))
                put(COL_DOWNLOADED_BYTES, downloadedBytes)
                put(COL_TOTAL_BYTES, totalBytes)
                put(COL_SPEED, speed)
                put(COL_ETA, etaSeconds)
                put(COL_QUEUED, 0)
                putNull(COL_ERROR)
            },
        )
    }

    suspend fun setChunks(
        id: String,
        chunks: List<YDownloadChunk>,
    ) {
        updateColumns(
            id,
            ContentValues().apply {
                put(COL_CHUNKS, encodeChunks(chunks))
            },
        )
    }

    suspend fun clearChunks(id: String) {
        updateColumns(
            id,
            ContentValues().apply {
                put(COL_CHUNKS, "")
            },
        )
    }

    suspend fun resetProgress(
        id: String,
    ) {
        updateColumns(
            id,
            ContentValues().apply {
                put(COL_DOWNLOADED_BYTES, 0L)
                put(COL_SPEED, 0L)
                put(COL_ETA, 0L)
            },
        )
    }

    suspend fun remove(id: String) {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                helper.writableDatabase.delete(
                    TABLE,
                    "$COL_ID = ?",
                    arrayOf(id),
                )
                mutableItems.value = queryAll()
            }
        }
    }

    suspend fun nextQueued(): YDownloadItem? =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                query(
                    selection =
                        "$COL_STATE = ? AND $COL_QUEUED = 1",
                    args = arrayOf(YDownloadState.Pending.name),
                    order = "$COL_ADDED_AT ASC",
                    limit = "1",
                ).firstOrNull()
            }
        }

    suspend fun restartable(): List<YDownloadItem> =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                query(
                    selection =
                        "($COL_STATE IN (?, ?)) OR " +
                            "($COL_STATE = ? AND $COL_QUEUED = 0)",
                    args = arrayOf(
                        YDownloadState.Downloading.name,
                        YDownloadState.Connecting.name,
                        YDownloadState.Pending.name,
                    ),
                    order = "$COL_ADDED_AT ASC",
                )
            }
        }

    private suspend fun updateColumns(
        id: String,
        values: ContentValues,
    ) {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                helper.writableDatabase.update(
                    TABLE,
                    values,
                    "$COL_ID = ?",
                    arrayOf(id),
                )
                mutableItems.value = queryAll()
            }
        }
    }

    private fun queryAll(): List<YDownloadItem> =
        query(
            selection = null,
            args = null,
            order = "$COL_ADDED_AT DESC",
        )

    private fun queryOne(
        id: String,
    ): YDownloadItem? =
        query(
            selection = "$COL_ID = ?",
            args = arrayOf(id),
            order = "$COL_ADDED_AT DESC",
            limit = "1",
        ).firstOrNull()

    private fun query(
        selection: String?,
        args: Array<String>?,
        order: String,
        limit: String? = null,
    ): List<YDownloadItem> {
        val rows = mutableListOf<YDownloadItem>()
        helper.readableDatabase.query(
            TABLE,
            ALL_COLUMNS,
            selection,
            args,
            null,
            null,
            order,
            limit,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                rows += cursor.toItem()
            }
        }
        return rows
    }

    private fun Cursor.toItem(): YDownloadItem =
        YDownloadItem(
            id = string(COL_ID),
            backend =
                runCatching {
                    YDownloadBackend.valueOf(
                        string(COL_BACKEND),
                    )
                }.getOrDefault(
                    YDownloadBackend.Private,
                ),
            systemId =
                nullableLong(COL_SYSTEM_ID),
            url = string(COL_URL),
            fileName = string(COL_FILE_NAME),
            outputUri = nullableString(COL_OUTPUT_URI),
            mimeType = string(COL_MIME_TYPE),
            totalBytes = long(COL_TOTAL_BYTES),
            downloadedBytes = long(COL_DOWNLOADED_BYTES),
            state = runCatching {
                YDownloadState.valueOf(string(COL_STATE))
            }.getOrDefault(YDownloadState.Pending),
            speedBytesPerSecond = long(COL_SPEED),
            etaSeconds = long(COL_ETA),
            addedAtMillis = long(COL_ADDED_AT),
            completedAtMillis =
                nullableLong(COL_COMPLETED_AT),
            errorMessage = nullableString(COL_ERROR),
            supportsRanges =
                int(COL_SUPPORTS_RANGES) != 0,
            queued = int(COL_QUEUED) != 0,
            referer = nullableString(COL_REFERER),
            userAgent = nullableString(COL_USER_AGENT),
            cookies = nullableString(COL_COOKIES),
            username = nullableString(COL_USERNAME),
            password = nullableString(COL_PASSWORD),
            destinationTreeUri =
                nullableString(COL_DESTINATION_TREE_URI),
            threadCount =
                int(COL_THREAD_COUNT).coerceIn(1, 16),
            speedLimitBytesPerSecond =
                long(COL_TASK_SPEED_LIMIT)
                    .coerceAtLeast(0L),
            customHeaders =
                decodeHeaders(
                    nullableString(COL_CUSTOM_HEADERS),
                ),
            scheduledAtMillis =
                nullableLong(COL_SCHEDULED_AT),
            chunks =
                decodeChunks(
                    nullableString(COL_CHUNKS),
                ),
        )

    private fun Cursor.string(name: String): String =
        getString(getColumnIndexOrThrow(name))

    private fun Cursor.nullableString(name: String): String? {
        val index = getColumnIndexOrThrow(name)
        return if (isNull(index)) null else getString(index)
    }

    private fun Cursor.long(name: String): Long =
        getLong(getColumnIndexOrThrow(name))

    private fun Cursor.nullableLong(name: String): Long? {
        val index = getColumnIndexOrThrow(name)
        return if (isNull(index)) null else getLong(index)
    }

    private fun Cursor.int(name: String): Int =
        getInt(getColumnIndexOrThrow(name))

    private class DownloadDbHelper(
        context: Context,
    ) : SQLiteOpenHelper(
        context,
        DB_NAME,
        null,
        DB_VERSION,
    ) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE $TABLE (
                    $COL_ID TEXT PRIMARY KEY,
                    $COL_BACKEND TEXT NOT NULL DEFAULT 'Private',
                    $COL_SYSTEM_ID INTEGER,
                    $COL_URL TEXT NOT NULL,
                    $COL_FILE_NAME TEXT NOT NULL,
                    $COL_OUTPUT_URI TEXT,
                    $COL_MIME_TYPE TEXT NOT NULL,
                    $COL_TOTAL_BYTES INTEGER NOT NULL,
                    $COL_DOWNLOADED_BYTES INTEGER NOT NULL,
                    $COL_STATE TEXT NOT NULL,
                    $COL_SPEED INTEGER NOT NULL,
                    $COL_ETA INTEGER NOT NULL,
                    $COL_ADDED_AT INTEGER NOT NULL,
                    $COL_COMPLETED_AT INTEGER,
                    $COL_ERROR TEXT,
                    $COL_SUPPORTS_RANGES INTEGER NOT NULL,
                    $COL_QUEUED INTEGER NOT NULL,
                    $COL_REFERER TEXT,
                    $COL_USER_AGENT TEXT,
                    $COL_COOKIES TEXT,
                    $COL_USERNAME TEXT,
                    $COL_PASSWORD TEXT,
                    $COL_DESTINATION_TREE_URI TEXT,
                    $COL_THREAD_COUNT INTEGER NOT NULL DEFAULT 1,
                    $COL_TASK_SPEED_LIMIT INTEGER NOT NULL DEFAULT 0,
                    $COL_CUSTOM_HEADERS TEXT,
                    $COL_SCHEDULED_AT INTEGER,
                    $COL_CHUNKS TEXT
                )
                """.trimIndent(),
            )
        }

        override fun onUpgrade(
            db: SQLiteDatabase,
            oldVersion: Int,
            newVersion: Int,
        ) {
            if (oldVersion < 2) {
                db.execSQL(
                    "ALTER TABLE $TABLE ADD COLUMN " +
                        "$COL_DESTINATION_TREE_URI TEXT",
                )
            }
            if (oldVersion < 3) {
                db.execSQL(
                    "ALTER TABLE $TABLE ADD COLUMN " +
                        "$COL_THREAD_COUNT INTEGER NOT NULL DEFAULT 1",
                )
                db.execSQL(
                    "ALTER TABLE $TABLE ADD COLUMN " +
                        "$COL_TASK_SPEED_LIMIT INTEGER NOT NULL DEFAULT 0",
                )
                db.execSQL(
                    "ALTER TABLE $TABLE ADD COLUMN " +
                        "$COL_CUSTOM_HEADERS TEXT",
                )
                db.execSQL(
                    "ALTER TABLE $TABLE ADD COLUMN " +
                        "$COL_SCHEDULED_AT INTEGER",
                )
                db.execSQL(
                    "ALTER TABLE $TABLE ADD COLUMN " +
                        "$COL_CHUNKS TEXT",
                )
            }
            if (oldVersion < 4) {
                db.execSQL(
                    "ALTER TABLE $TABLE ADD COLUMN " +
                        "$COL_BACKEND TEXT NOT NULL DEFAULT 'Private'",
                )
                db.execSQL(
                    "ALTER TABLE $TABLE ADD COLUMN " +
                        "$COL_SYSTEM_ID INTEGER",
                )
            }
        }
    }

    private companion object {
        const val DB_NAME = "ydownload.db"
        const val DB_VERSION = 4
        const val TABLE = "downloads"
        const val COL_ID = "id"
        const val COL_BACKEND = "backend"
        const val COL_SYSTEM_ID = "system_id"
        const val COL_URL = "url"
        const val COL_FILE_NAME = "file_name"
        const val COL_OUTPUT_URI = "output_uri"
        const val COL_MIME_TYPE = "mime_type"
        const val COL_TOTAL_BYTES = "total_bytes"
        const val COL_DOWNLOADED_BYTES = "downloaded_bytes"
        const val COL_STATE = "state"
        const val COL_SPEED = "speed"
        const val COL_ETA = "eta"
        const val COL_ADDED_AT = "added_at"
        const val COL_COMPLETED_AT = "completed_at"
        const val COL_ERROR = "error"
        const val COL_SUPPORTS_RANGES = "supports_ranges"
        const val COL_QUEUED = "queued"
        const val COL_REFERER = "referer"
        const val COL_USER_AGENT = "user_agent"
        const val COL_COOKIES = "cookies"
        const val COL_USERNAME = "username"
        const val COL_PASSWORD = "password"
        const val COL_DESTINATION_TREE_URI =
            "destination_tree_uri"
        const val COL_THREAD_COUNT = "thread_count"
        const val COL_TASK_SPEED_LIMIT = "task_speed_limit"
        const val COL_CUSTOM_HEADERS = "custom_headers"
        const val COL_SCHEDULED_AT = "scheduled_at"
        const val COL_CHUNKS = "chunks"

        fun encodeHeaders(
            headers: Map<String, String>,
        ): String =
            headers.entries.joinToString("\n") {
                it.key.trim() + "\t" + it.value.trim()
            }

        fun decodeHeaders(value: String?): Map<String, String> =
            value.orEmpty()
                .lineSequence()
                .mapNotNull { line ->
                    val index = line.indexOf('\t')
                    if (index <= 0) return@mapNotNull null
                    val key = line.substring(0, index).trim()
                    val headerValue =
                        line.substring(index + 1).trim()
                    if (key.isBlank()) null else key to headerValue
                }
                .toMap()

        fun encodeChunks(
            chunks: List<YDownloadChunk>,
        ): String =
            chunks.joinToString(";") {
                listOf(
                    it.startByte,
                    it.endByte,
                    it.downloadedBytes,
                ).joinToString(",")
            }

        fun decodeChunks(
            value: String?,
        ): List<YDownloadChunk> =
            value.orEmpty()
                .split(';')
                .mapNotNull { encoded ->
                    val parts = encoded.split(',')
                    if (parts.size != 3) {
                        return@mapNotNull null
                    }
                    val start =
                        parts[0].toLongOrNull()
                            ?: return@mapNotNull null
                    val end =
                        parts[1].toLongOrNull()
                            ?: return@mapNotNull null
                    val downloaded =
                        parts[2].toLongOrNull()
                            ?: return@mapNotNull null
                    YDownloadChunk(
                        startByte = start,
                        endByte = end,
                        downloadedBytes =
                            downloaded.coerceAtLeast(0L),
                    )
                }

        val ALL_COLUMNS = arrayOf(
            COL_ID,
            COL_BACKEND,
            COL_SYSTEM_ID,
            COL_URL,
            COL_FILE_NAME,
            COL_OUTPUT_URI,
            COL_MIME_TYPE,
            COL_TOTAL_BYTES,
            COL_DOWNLOADED_BYTES,
            COL_STATE,
            COL_SPEED,
            COL_ETA,
            COL_ADDED_AT,
            COL_COMPLETED_AT,
            COL_ERROR,
            COL_SUPPORTS_RANGES,
            COL_QUEUED,
            COL_REFERER,
            COL_USER_AGENT,
            COL_COOKIES,
            COL_USERNAME,
            COL_PASSWORD,
            COL_DESTINATION_TREE_URI,
            COL_THREAD_COUNT,
            COL_TASK_SPEED_LIMIT,
            COL_CUSTOM_HEADERS,
            COL_SCHEDULED_AT,
            COL_CHUNKS,
        )
    }
}
