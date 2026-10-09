package com.yagay.ysuite.logging.api

import java.util.ArrayDeque
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class LogLevel {
    Verbose,
    Debug,
    Info,
    Warning,
    Error,
}

enum class LogSource {
    App,
    Logcat,
}

data class LogRecord(
    val timestampMillis: Long,
    val level: LogLevel,
    val tag: String,
    val message: String,
    val throwable: Throwable? = null,
    val source: LogSource = LogSource.App,
)

data class LogQuery(
    val text: String = "",
    val levels: Set<LogLevel> = emptySet(),
    val tags: Set<String> = emptySet(),
    val sources: Set<LogSource> = emptySet(),
    val limit: Int = 1_000,
)

fun List<LogRecord>.filtered(
    query: LogQuery,
): List<LogRecord> {
    val needle = query.text.trim()
    return asSequence()
        .filter {
            query.levels.isEmpty() ||
                it.level in query.levels
        }
        .filter {
            query.tags.isEmpty() ||
                it.tag in query.tags
        }
        .filter {
            query.sources.isEmpty() ||
                it.source in query.sources
        }
        .filter {
            needle.isEmpty() ||
                it.tag.contains(
                    needle,
                    ignoreCase = true,
                ) ||
                it.message.contains(
                    needle,
                    ignoreCase = true,
                )
        }
        .toList()
        .takeLast(
            query.limit.coerceAtLeast(1),
        )
}

fun interface LogSink {
    fun write(record: LogRecord)
}

interface LogStore : LogSink {
    val records: StateFlow<List<LogRecord>>

    fun clear()

    fun query(
        query: LogQuery,
    ): List<LogRecord> =
        records.value.filtered(query)
}

fun interface LogCollector {
    suspend fun collect(
        maxLines: Int,
    ): List<LogRecord>
}

interface YSuiteLogger {
    fun log(record: LogRecord)

    fun debug(tag: String, message: String) =
        log(
            LogRecord(
                timestampMillis =
                    System.currentTimeMillis(),
                level = LogLevel.Debug,
                tag = tag,
                message = message,
            ),
        )

    fun error(
        tag: String,
        message: String,
        throwable: Throwable? = null,
    ) =
        log(
            LogRecord(
                timestampMillis =
                    System.currentTimeMillis(),
                level = LogLevel.Error,
                tag = tag,
                message = message,
                throwable = throwable,
            ),
        )
}

class CompositeYSuiteLogger(
    private val sinks: List<LogSink>,
) : YSuiteLogger {
    override fun log(record: LogRecord) {
        sinks.forEach { sink ->
            sink.write(record)
        }
    }
}

class InMemoryLogStore(
    private val capacity: Int = 1_000,
) : LogStore {
    private val buffer =
        ArrayDeque<LogRecord>()
    private val mutableRecords =
        MutableStateFlow<List<LogRecord>>(
            emptyList(),
        )

    override val records:
        StateFlow<List<LogRecord>> =
        mutableRecords.asStateFlow()

    val snapshot: StateFlow<List<LogRecord>>
        get() = records

    init {
        require(capacity > 0) {
            "capacity must be greater than zero"
        }
    }

    @Synchronized
    override fun write(record: LogRecord) {
        if (buffer.size == capacity) {
            buffer.removeFirst()
        }
        buffer.addLast(record)
        mutableRecords.value =
            buffer.toList()
    }

    @Synchronized
    override fun clear() {
        buffer.clear()
        mutableRecords.value = emptyList()
    }
}
