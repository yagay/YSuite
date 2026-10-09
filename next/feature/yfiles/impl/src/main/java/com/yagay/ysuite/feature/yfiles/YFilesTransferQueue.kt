package com.yagay.ysuite.feature.yfiles

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.yagay.ysuite.feature.yfiles.api.YFileConflictStrategy
import com.yagay.ysuite.feature.yfiles.api.YFileOperationProgress
import com.yagay.ysuite.feature.yfiles.api.YFileRef
import com.yagay.ysuite.feature.yfiles.engine.DefaultYFilesEngine
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

enum class YFilesTransferState {
    Pending,
    Running,
    Paused,
    Completed,
    Failed,
    Cancelled,
}

enum class YFilesTransferOperation {
    Copy,
    Move,
}

data class YFilesTransferTask(
    val id: String,
    val sources: List<YFileRef>,
    val destination: YFileRef,
    val operation: YFilesTransferOperation,
    val conflictStrategy: YFileConflictStrategy,
    val state: YFilesTransferState,
    val order: Int,
    val speedLimitBytesPerSecond: Long,
    val completedItems: Int,
    val totalItems: Int,
    val completedBytes: Long,
    val totalBytes: Long?,
    val currentName: String?,
    val currentSourceIndex: Int,
    val error: String?,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
) {
    val progress: Float?
        get() =
            totalBytes
                ?.takeIf { it > 0L }
                ?.let {
                    completedBytes
                        .toFloat()
                        .div(it.toFloat())
                        .coerceIn(0f, 1f)
                }
}

class YFilesTransferQueue(
    context: Context,
    private val engine: DefaultYFilesEngine,
) {
    private val helper =
        Database(
            context.applicationContext,
        )
    private val scope =
        CoroutineScope(
            SupervisorJob() +
                Dispatchers.IO,
        )
    private val mutex = Mutex()
    private val activeJobs =
        ConcurrentHashMap<String, Job>()
    private val pauseRequested =
        ConcurrentHashMap.newKeySet<String>()
    private val cancelRequested =
        ConcurrentHashMap.newKeySet<String>()
    private val mutableTasks =
        MutableStateFlow<List<YFilesTransferTask>>(
            emptyList(),
        )

    val tasks: StateFlow<
        List<YFilesTransferTask>,
        > = mutableTasks.asStateFlow()

    init {
        scope.launch {
            helper.writableDatabase.execSQL(
                "UPDATE $TABLE SET $COL_STATE=? " +
                    "WHERE $COL_STATE=?",
                arrayOf(
                    YFilesTransferState.Pending.name,
                    YFilesTransferState.Running.name,
                ),
            )
            refresh()
            pump()
        }
    }

    suspend fun enqueue(
        sources: List<YFileRef>,
        destination: YFileRef,
        move: Boolean,
        strategy:
            YFileConflictStrategy =
            YFileConflictStrategy.Rename,
        speedLimitBytesPerSecond: Long = 0L,
    ): String {
        require(sources.isNotEmpty())
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val totalBytes =
            estimateTotalBytes(sources)
        mutex.withLock {
            val order =
                queryTasks()
                    .maxOfOrNull { it.order }
                    ?.plus(1)
                    ?: 0
            helper.writableDatabase
                .insertOrThrow(
                    TABLE,
                    null,
                    ContentValues().apply {
                        put(COL_ID, id)
                        put(
                            COL_SOURCES,
                            encodeRefs(sources),
                        )
                        put(
                            COL_DESTINATION,
                            encodeRef(destination),
                        )
                        put(
                            COL_OPERATION,
                            if (move) {
                                YFilesTransferOperation
                                    .Move.name
                            } else {
                                YFilesTransferOperation
                                    .Copy.name
                            },
                        )
                        put(
                            COL_CONFLICT,
                            strategy.name,
                        )
                        put(
                            COL_STATE,
                            YFilesTransferState
                                .Pending.name,
                        )
                        put(COL_ORDER, order)
                        put(
                            COL_SPEED_LIMIT,
                            speedLimitBytesPerSecond
                                .coerceAtLeast(0L),
                        )
                        put(COL_COMPLETED_ITEMS, 0)
                        put(
                            COL_TOTAL_ITEMS,
                            sources.size,
                        )
                        put(COL_COMPLETED_BYTES, 0L)
                        if (totalBytes != null) {
                            put(
                                COL_TOTAL_BYTES,
                                totalBytes,
                            )
                        } else {
                            putNull(COL_TOTAL_BYTES)
                        }
                        putNull(COL_CURRENT_NAME)
                        put(COL_CURRENT_INDEX, 0)
                        putNull(COL_ERROR)
                        put(COL_CREATED_AT, now)
                        put(COL_UPDATED_AT, now)
                    },
                )
            refreshLocked()
        }
        pump()
        return id
    }

    suspend fun pause(id: String) {
        pauseRequested += id
        activeJobs.remove(id)?.cancel()
        updateState(
            id,
            YFilesTransferState.Paused,
            null,
        )
    }

    suspend fun resume(id: String) {
        pauseRequested -= id
        cancelRequested -= id
        updateState(
            id,
            YFilesTransferState.Pending,
            null,
        )
        pump()
    }

    suspend fun retry(id: String) {
        pauseRequested -= id
        cancelRequested -= id
        updateColumns(
            id,
            ContentValues().apply {
                put(
                    COL_STATE,
                    YFilesTransferState.Pending.name,
                )
                putNull(COL_ERROR)
                put(
                    COL_UPDATED_AT,
                    System.currentTimeMillis(),
                )
            },
        )
        pump()
    }

    suspend fun cancel(id: String) {
        cancelRequested += id
        pauseRequested -= id
        activeJobs.remove(id)?.cancel()
        updateState(
            id,
            YFilesTransferState.Cancelled,
            null,
        )
    }

    suspend fun remove(id: String) {
        activeJobs.remove(id)?.cancel()
        pauseRequested -= id
        cancelRequested -= id
        mutex.withLock {
            helper.writableDatabase.delete(
                TABLE,
                "$COL_ID=?",
                arrayOf(id),
            )
            normalizeOrderLocked()
            refreshLocked()
        }
    }

    suspend fun moveUp(id: String) =
        reorder(id, -1)

    suspend fun moveDown(id: String) =
        reorder(id, 1)

    suspend fun setSpeedLimit(
        id: String,
        bytesPerSecond: Long,
    ) {
        updateColumns(
            id,
            ContentValues().apply {
                put(
                    COL_SPEED_LIMIT,
                    bytesPerSecond
                        .coerceAtLeast(0L),
                )
            },
        )
    }

    suspend fun refresh() {
        mutex.withLock {
            refreshLocked()
        }
    }

    private suspend fun pump() {
        mutex.withLock {
            val next =
                queryTasks()
                    .firstOrNull {
                        it.state ==
                            YFilesTransferState
                                .Pending &&
                            !activeJobs
                                .containsKey(it.id)
                    }
                    ?: return
            val job =
                scope.launch {
                    runTask(next.id)
                }
            activeJobs[next.id] = job
        }
    }

    private suspend fun runTask(id: String) {
        try {
            val initial =
                task(id) ?: return
            updateState(
                id,
                YFilesTransferState.Running,
                null,
            )
            var completedItems =
                initial.completedItems
            var completedBytes =
                initial.completedBytes
            var index =
                initial.currentSourceIndex
            val startTime =
                System.currentTimeMillis()
            val startBytes = completedBytes

            while (
                index < initial.sources.size
            ) {
                if (
                    cancelRequested.contains(id)
                ) {
                    throw CancellationException(
                        "Cancelled",
                    )
                }
                if (
                    pauseRequested.contains(id)
                ) {
                    updateState(
                        id,
                        YFilesTransferState.Paused,
                        null,
                    )
                    return
                }

                val current =
                    task(id) ?: return
                val source =
                    initial.sources[index]
                val sourceNode =
                    engine.stat(source)
                        .let {
                            result ->
                            when (result) {
                                is com.yagay.ysuite
                                    .common.Outcome
                                    .Success ->
                                    result.value
                                is com.yagay.ysuite
                                    .common.Outcome
                                    .Failure ->
                                    throw IllegalStateException(
                                        result.message,
                                    )
                            }
                        }
                updateCheckpoint(
                    id = id,
                    index = index,
                    name = sourceNode.name,
                    completedItems =
                        completedItems,
                    completedBytes =
                        completedBytes,
                )

                var sourceProgress = 0L
                var throttleStarted =
                    System.nanoTime()
                val listener:
                    (YFileOperationProgress) -> Unit =
                    { progress ->
                        sourceProgress =
                            progress.completedBytes
                        val aggregate =
                            completedBytes +
                                sourceProgress
                        val now =
                            System.nanoTime()
                        val elapsed =
                            (now - throttleStarted)
                                .coerceAtLeast(1L)
                        val limit =
                            current
                                .speedLimitBytesPerSecond
                        if (limit > 0L) {
                            val required =
                                sourceProgress *
                                    1_000_000_000L /
                                    limit
                            if (required > elapsed) {
                                val sleepNanos =
                                    required -
                                        elapsed
                                val millis =
                                    sleepNanos /
                                        1_000_000L
                                val nanos =
                                    (
                                        sleepNanos %
                                            1_000_000L
                                        ).toInt()
                                if (
                                    millis > 0L ||
                                    nanos > 0
                                ) {
                                    Thread.sleep(
                                        millis,
                                        nanos,
                                    )
                                }
                            }
                        }
                        scope.launch {
                            updateProgress(
                                id = id,
                                bytes = aggregate,
                                name =
                                    progress.currentName,
                            )
                        }
                    }

                val strategy =
                    if (
                        current.currentSourceIndex ==
                            index &&
                        current.currentName != null &&
                        current.completedItems <= index
                    ) {
                        YFileConflictStrategy.Replace
                    } else {
                        current.conflictStrategy
                    }

                val result =
                    if (
                        initial.operation ==
                            YFilesTransferOperation
                                .Move
                    ) {
                        engine.move(
                            source = source,
                            destinationDirectory =
                                initial.destination,
                            strategy = strategy,
                            onProgress = listener,
                        )
                    } else {
                        engine.copy(
                            source = source,
                            destinationDirectory =
                                initial.destination,
                            strategy = strategy,
                            onProgress = listener,
                        )
                    }

                when (result) {
                    is com.yagay.ysuite
                        .common.Outcome.Success -> {
                        completedItems += 1
                        completedBytes +=
                            sourceNode.sizeBytes
                                ?: sourceProgress
                        index += 1
                        updateCheckpoint(
                            id = id,
                            index = index,
                            name = null,
                            completedItems =
                                completedItems,
                            completedBytes =
                                completedBytes,
                        )
                    }
                    is com.yagay.ysuite
                        .common.Outcome.Failure -> {
                        throw IllegalStateException(
                            result.message,
                        )
                    }
                }
            }

            val elapsedMillis =
                (
                    System.currentTimeMillis() -
                        startTime
                    ).coerceAtLeast(1L)
            val transferred =
                (
                    completedBytes -
                        startBytes
                    ).coerceAtLeast(0L)
            updateColumns(
                id,
                ContentValues().apply {
                    put(
                        COL_STATE,
                        YFilesTransferState
                            .Completed.name,
                    )
                    put(
                        COL_COMPLETED_ITEMS,
                        completedItems,
                    )
                    put(
                        COL_COMPLETED_BYTES,
                        completedBytes,
                    )
                    put(COL_CURRENT_INDEX, index)
                    putNull(COL_CURRENT_NAME)
                    putNull(COL_ERROR)
                    put(
                        COL_LAST_SPEED,
                        transferred *
                            1000L /
                            elapsedMillis,
                    )
                },
            )
        } catch (
            cancelled: CancellationException,
        ) {
            val task = task(id)
            if (
                task?.state !=
                    YFilesTransferState.Cancelled &&
                task?.state !=
                    YFilesTransferState.Paused
            ) {
                updateState(
                    id,
                    YFilesTransferState.Paused,
                    null,
                )
            }
        } catch (error: Throwable) {
            updateState(
                id,
                YFilesTransferState.Failed,
                error.message
                    ?: "Transfer failed",
            )
        } finally {
            activeJobs.remove(id)
            delay(50L)
            refresh()
            pump()
        }
    }

    private suspend fun estimateTotalBytes(
        sources: List<YFileRef>,
    ): Long? {
        var total = 0L
        for (source in sources) {
            when (val result = engine.stat(source)) {
                is com.yagay.ysuite.common.Outcome.Success -> {
                    val size =
                        result.value.sizeBytes
                            ?: return null
                    total =
                        runCatching {
                            Math.addExact(
                                total,
                                size,
                            )
                        }.getOrElse {
                            return null
                        }
                }
                is com.yagay.ysuite.common.Outcome.Failure ->
                    return null
            }
        }
        return total
    }

    private suspend fun reorder(
        id: String,
        delta: Int,
    ) {
        mutex.withLock {
            val list =
                queryTasks()
                    .filter {
                        it.state ==
                            YFilesTransferState
                                .Pending ||
                            it.state ==
                                YFilesTransferState
                                    .Paused
                    }
                    .sortedBy { it.order }
                    .toMutableList()
            val index =
                list.indexOfFirst {
                    it.id == id
                }
            if (index < 0) return
            val target =
                (index + delta)
                    .coerceIn(
                        0,
                        list.lastIndex,
                    )
            if (target == index) return
            val item = list.removeAt(index)
            list.add(target, item)
            list.forEachIndexed {
                order,
                task,
                ->
                helper.writableDatabase
                    .update(
                        TABLE,
                        ContentValues().apply {
                            put(
                                COL_ORDER,
                                order,
                            )
                        },
                        "$COL_ID=?",
                        arrayOf(task.id),
                    )
            }
            refreshLocked()
        }
    }

    private suspend fun updateState(
        id: String,
        state: YFilesTransferState,
        error: String?,
    ) {
        updateColumns(
            id,
            ContentValues().apply {
                put(COL_STATE, state.name)
                if (error == null) {
                    putNull(COL_ERROR)
                } else {
                    put(COL_ERROR, error)
                }
                put(
                    COL_UPDATED_AT,
                    System.currentTimeMillis(),
                )
            },
        )
    }

    private suspend fun updateProgress(
        id: String,
        bytes: Long,
        name: String?,
    ) {
        updateColumns(
            id,
            ContentValues().apply {
                put(COL_COMPLETED_BYTES, bytes)
                put(COL_CURRENT_NAME, name)
                put(
                    COL_UPDATED_AT,
                    System.currentTimeMillis(),
                )
            },
            refresh = false,
        )
    }

    private suspend fun updateCheckpoint(
        id: String,
        index: Int,
        name: String?,
        completedItems: Int,
        completedBytes: Long,
    ) {
        updateColumns(
            id,
            ContentValues().apply {
                put(COL_CURRENT_INDEX, index)
                put(
                    COL_COMPLETED_ITEMS,
                    completedItems,
                )
                put(
                    COL_COMPLETED_BYTES,
                    completedBytes,
                )
                if (name == null) {
                    putNull(COL_CURRENT_NAME)
                } else {
                    put(COL_CURRENT_NAME, name)
                }
                put(
                    COL_UPDATED_AT,
                    System.currentTimeMillis(),
                )
            },
        )
    }

    private suspend fun updateColumns(
        id: String,
        values: ContentValues,
        refresh: Boolean = true,
    ) {
        mutex.withLock {
            helper.writableDatabase.update(
                TABLE,
                values,
                "$COL_ID=?",
                arrayOf(id),
            )
            if (refresh) {
                refreshLocked()
            } else {
                mutableTasks.value =
                    queryTasks()
            }
        }
    }

    private fun task(
        id: String,
    ): YFilesTransferTask? =
        queryTasks()
            .firstOrNull { it.id == id }

    private fun queryTasks():
        List<YFilesTransferTask> =
        helper.readableDatabase.query(
            TABLE,
            ALL_COLUMNS,
            null,
            null,
            null,
            null,
            "$COL_ORDER ASC, $COL_CREATED_AT ASC",
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        YFilesTransferTask(
                            id =
                                cursor.getString(
                                    cursor.getColumnIndexOrThrow(
                                        COL_ID,
                                    ),
                                ),
                            sources =
                                decodeRefs(
                                    cursor.getString(
                                        cursor.getColumnIndexOrThrow(
                                            COL_SOURCES,
                                        ),
                                    ),
                                ),
                            destination =
                                decodeRef(
                                    cursor.getString(
                                        cursor.getColumnIndexOrThrow(
                                            COL_DESTINATION,
                                        ),
                                    ),
                                ),
                            operation =
                                enumValueOf(
                                    cursor.getString(
                                        cursor.getColumnIndexOrThrow(
                                            COL_OPERATION,
                                        ),
                                    ),
                                ),
                            conflictStrategy =
                                enumValueOf(
                                    cursor.getString(
                                        cursor.getColumnIndexOrThrow(
                                            COL_CONFLICT,
                                        ),
                                    ),
                                ),
                            state =
                                enumValueOf(
                                    cursor.getString(
                                        cursor.getColumnIndexOrThrow(
                                            COL_STATE,
                                        ),
                                    ),
                                ),
                            order =
                                cursor.getInt(
                                    cursor.getColumnIndexOrThrow(
                                        COL_ORDER,
                                    ),
                                ),
                            speedLimitBytesPerSecond =
                                cursor.getLong(
                                    cursor.getColumnIndexOrThrow(
                                        COL_SPEED_LIMIT,
                                    ),
                                ),
                            completedItems =
                                cursor.getInt(
                                    cursor.getColumnIndexOrThrow(
                                        COL_COMPLETED_ITEMS,
                                    ),
                                ),
                            totalItems =
                                cursor.getInt(
                                    cursor.getColumnIndexOrThrow(
                                        COL_TOTAL_ITEMS,
                                    ),
                                ),
                            completedBytes =
                                cursor.getLong(
                                    cursor.getColumnIndexOrThrow(
                                        COL_COMPLETED_BYTES,
                                    ),
                                ),
                            totalBytes =
                                cursor.getColumnIndexOrThrow(
                                    COL_TOTAL_BYTES,
                                ).let {
                                    if (cursor.isNull(it)) {
                                        null
                                    } else {
                                        cursor.getLong(it)
                                    }
                                },
                            currentName =
                                cursor.getColumnIndexOrThrow(
                                    COL_CURRENT_NAME,
                                ).let {
                                    if (cursor.isNull(it)) {
                                        null
                                    } else {
                                        cursor.getString(it)
                                    }
                                },
                            currentSourceIndex =
                                cursor.getInt(
                                    cursor.getColumnIndexOrThrow(
                                        COL_CURRENT_INDEX,
                                    ),
                                ),
                            error =
                                cursor.getColumnIndexOrThrow(
                                    COL_ERROR,
                                ).let {
                                    if (cursor.isNull(it)) {
                                        null
                                    } else {
                                        cursor.getString(it)
                                    }
                                },
                            createdAtMillis =
                                cursor.getLong(
                                    cursor.getColumnIndexOrThrow(
                                        COL_CREATED_AT,
                                    ),
                                ),
                            updatedAtMillis =
                                cursor.getLong(
                                    cursor.getColumnIndexOrThrow(
                                        COL_UPDATED_AT,
                                    ),
                                ),
                        ),
                    )
                }
            }
        }

    private fun refreshLocked() {
        mutableTasks.value = queryTasks()
    }

    private fun normalizeOrderLocked() {
        queryTasks()
            .forEachIndexed { index, task ->
                helper.writableDatabase.update(
                    TABLE,
                    ContentValues().apply {
                        put(COL_ORDER, index)
                    },
                    "$COL_ID=?",
                    arrayOf(task.id),
                )
            }
    }

    private class Database(
        context: Context,
    ) : SQLiteOpenHelper(
        context,
        DB_NAME,
        null,
        DB_VERSION,
    ) {
        override fun onCreate(
            db: SQLiteDatabase,
        ) {
            db.execSQL(
                """
                CREATE TABLE $TABLE (
                    $COL_ID TEXT PRIMARY KEY NOT NULL,
                    $COL_SOURCES TEXT NOT NULL,
                    $COL_DESTINATION TEXT NOT NULL,
                    $COL_OPERATION TEXT NOT NULL,
                    $COL_CONFLICT TEXT NOT NULL,
                    $COL_STATE TEXT NOT NULL,
                    $COL_ORDER INTEGER NOT NULL,
                    $COL_SPEED_LIMIT INTEGER NOT NULL DEFAULT 0,
                    $COL_COMPLETED_ITEMS INTEGER NOT NULL DEFAULT 0,
                    $COL_TOTAL_ITEMS INTEGER NOT NULL DEFAULT 0,
                    $COL_COMPLETED_BYTES INTEGER NOT NULL DEFAULT 0,
                    $COL_TOTAL_BYTES INTEGER,
                    $COL_CURRENT_NAME TEXT,
                    $COL_CURRENT_INDEX INTEGER NOT NULL DEFAULT 0,
                    $COL_ERROR TEXT,
                    $COL_LAST_SPEED INTEGER NOT NULL DEFAULT 0,
                    $COL_CREATED_AT INTEGER NOT NULL,
                    $COL_UPDATED_AT INTEGER NOT NULL
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

    companion object {
        private const val DB_NAME =
            "yfiles_transfers.db"
        private const val DB_VERSION = 1
        private const val TABLE = "transfers"
        private const val COL_ID = "id"
        private const val COL_SOURCES = "sources"
        private const val COL_DESTINATION =
            "destination"
        private const val COL_OPERATION =
            "operation"
        private const val COL_CONFLICT =
            "conflict_strategy"
        private const val COL_STATE = "state"
        private const val COL_ORDER = "sort_order"
        private const val COL_SPEED_LIMIT =
            "speed_limit"
        private const val COL_COMPLETED_ITEMS =
            "completed_items"
        private const val COL_TOTAL_ITEMS =
            "total_items"
        private const val COL_COMPLETED_BYTES =
            "completed_bytes"
        private const val COL_TOTAL_BYTES =
            "total_bytes"
        private const val COL_CURRENT_NAME =
            "current_name"
        private const val COL_CURRENT_INDEX =
            "current_index"
        private const val COL_ERROR = "error"
        private const val COL_LAST_SPEED =
            "last_speed"
        private const val COL_CREATED_AT =
            "created_at"
        private const val COL_UPDATED_AT =
            "updated_at"

        private val ALL_COLUMNS =
            arrayOf(
                COL_ID,
                COL_SOURCES,
                COL_DESTINATION,
                COL_OPERATION,
                COL_CONFLICT,
                COL_STATE,
                COL_ORDER,
                COL_SPEED_LIMIT,
                COL_COMPLETED_ITEMS,
                COL_TOTAL_ITEMS,
                COL_COMPLETED_BYTES,
                COL_TOTAL_BYTES,
                COL_CURRENT_NAME,
                COL_CURRENT_INDEX,
                COL_ERROR,
                COL_LAST_SPEED,
                COL_CREATED_AT,
                COL_UPDATED_AT,
            )

        private fun encodeRef(
            ref: YFileRef,
        ): String =
            JSONObject()
                .put("provider", ref.providerId)
                .put("path", ref.path)
                .toString()

        private fun decodeRef(
            value: String,
        ): YFileRef {
            val json = JSONObject(value)
            return YFileRef(
                providerId =
                    json.getString("provider"),
                path = json.getString("path"),
            )
        }

        private fun encodeRefs(
            refs: List<YFileRef>,
        ): String =
            JSONArray().apply {
                refs.forEach {
                    put(
                        JSONObject()
                            .put(
                                "provider",
                                it.providerId,
                            )
                            .put("path", it.path),
                    )
                }
            }.toString()

        private fun decodeRefs(
            value: String,
        ): List<YFileRef> {
            val array = JSONArray(value)
            return buildList {
                for (
                    index in 0 until
                        array.length()
                ) {
                    val item =
                        array.getJSONObject(
                            index,
                        )
                    add(
                        YFileRef(
                            providerId =
                                item.getString(
                                    "provider",
                                ),
                            path =
                                item.getString(
                                    "path",
                                ),
                        ),
                    )
                }
            }
        }
    }
}
