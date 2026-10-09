package com.yagay.ysuite.logging.android

import android.util.Log
import com.yagay.ysuite.logging.api.LogLevel
import com.yagay.ysuite.logging.api.LogRecord
import com.yagay.ysuite.logging.api.LogSink

class AndroidLogSink : LogSink {
    override fun write(record: LogRecord) {
        when (record.level) {
            LogLevel.Verbose -> Log.v(record.tag, record.message, record.throwable)
            LogLevel.Debug -> Log.d(record.tag, record.message, record.throwable)
            LogLevel.Info -> Log.i(record.tag, record.message, record.throwable)
            LogLevel.Warning -> Log.w(record.tag, record.message, record.throwable)
            LogLevel.Error -> Log.e(record.tag, record.message, record.throwable)
        }
    }
}
