package com.yagay.ysuite.feature.ydownload

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
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
                val values = ContentValues().apply {
                    put(COL_ID, id)
                    put(COL_URL, request.url)
                    put(COL_FILE_NAME, request.fileName)
                    putNull(COL_OUTPUT_URI)
                    put(COL_MIME_TYPE, request.mimeType)
                    put(COL_TOTAL_BYTES, request.totalBytes)
                    put(COL_DOWNLOADED_BYTES, 0L)
                    put(COL_STATE, YDownloadState.Pending.name)
                    put(COL_SPEED, 0L)
                    put(COL_ETA, 0L)
                    put(COL_ADDED_AT, System.currentTimeMillis())
                    putNull(COL_COMPLETED_AT)
                    putNull(COL_ERROR)
                    put(
                        COL_SUPPORTS_RANGES,
                        if (request.supportsRanges) 1 else 0,
                    )
                    put(COL_QUEUED, if (queued) 1 else 0)
                    put(COL_REFERER, request.referer)
                    put(COL_USER_AGENT, request.userAgent)
                    put(COL_COOKIES, request.cookies)
                    put(COL_USERNAME, request.username)
                    put(COL_PASSWORD, request.password)
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
                        "$COL_STATE IN (?, ?, ?)",
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
                    $COL_PASSWORD TEXT
                )
                """.trimIndent(),
            )
        }

        override fun onUpgrade(
            db: SQLiteDatabase,
            oldVersion: Int,
            newVersion: Int,
        ) = Unit
    }

    private companion object {
        const val DB_NAME = "ydownload.db"
        const val DB_VERSION = 1
        const val TABLE = "downloads"
        const val COL_ID = "id"
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

        val ALL_COLUMNS = arrayOf(
            COL_ID,
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
        )
    }
}
