package com.yagay.ysuite.logging

import android.util.Log

enum class LogLevel {
    Verbose,
    Debug,
    Info,
    Warning,
    Error,
}

data class LogRecord(
    val level: LogLevel,
    val tag: String,
    val message: String,
    val throwable: Throwable? = null,
)

interface YSuiteLogger {
    fun log(record: LogRecord)

    fun debug(tag: String, message: String) =
        log(LogRecord(LogLevel.Debug, tag, message))

    fun error(tag: String, message: String, throwable: Throwable? = null) =
        log(LogRecord(LogLevel.Error, tag, message, throwable))
}

class AndroidYSuiteLogger : YSuiteLogger {
    override fun log(record: LogRecord) {
        when (record.level) {
            LogLevel.Verbose -> Log.v(record.tag, record.message, record.throwable)
            LogLevel.Debug -> Log.d(record.tag, record.message, record.throwable)
            LogLevel.Info -> Log.i(record.tag, record.message, record.throwable)
            LogLevel.Warning -> Log.w(record.tag, record.message, record.throwable)
            LogLevel.Error -> Log.e(record.tag, record.message, record.throwable)
        }
    }
}
