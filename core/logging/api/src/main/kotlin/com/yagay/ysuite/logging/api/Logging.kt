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

data class LogRecord(
    val timestampMillis: Long,
    val level: LogLevel,
    val tag: String,
    val message: String,
    val throwable: Throwable? = null,
)

fun interface LogSink {
    fun write(record: LogRecord)
}

interface YSuiteLogger {
    fun log(record: LogRecord)

    fun debug(tag: String, message: String) =
        log(
            LogRecord(
                timestampMillis = System.currentTimeMillis(),
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
                timestampMillis = System.currentTimeMillis(),
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
        sinks.forEach { sink -> sink.write(record) }
    }
}

class InMemoryLogStore(
    private val capacity: Int = 1_000,
) : LogSink {
    private val records = ArrayDeque<LogRecord>()
    private val mutableSnapshot = MutableStateFlow<List<LogRecord>>(emptyList())

    val snapshot: StateFlow<List<LogRecord>> = mutableSnapshot.asStateFlow()

    init {
        require(capacity > 0) { "capacity must be greater than zero" }
    }

    @Synchronized
    override fun write(record: LogRecord) {
        if (records.size == capacity) {
            records.removeFirst()
        }
        records.addLast(record)
        mutableSnapshot.value = records.toList()
    }

    @Synchronized
    fun clear() {
        records.clear()
        mutableSnapshot.value = emptyList()
    }
}
