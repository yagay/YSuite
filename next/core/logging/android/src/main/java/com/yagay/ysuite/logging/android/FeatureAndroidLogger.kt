package com.yagay.ysuite.logging.android

import com.yagay.ysuite.logging.api.LogRecord
import com.yagay.ysuite.logging.api.YSuiteLogger

class FeatureAndroidLogger(private val feature: String) : YSuiteLogger {
    private val sink = AndroidLogSink()
    override fun log(record: LogRecord) {
        sink.write(record.copy(tag = feature + "/" + record.tag))
    }
}
