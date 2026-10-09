package com.yagay.ysuite.logging.api

/** Rebindable logger shared by background download services and their UI hosts. */
class BindableYSuiteLogger : YSuiteLogger {
    @Volatile private var delegate: YSuiteLogger? = null

    fun bind(logger: YSuiteLogger) {
        delegate = logger
    }

    override fun log(record: LogRecord) {
        delegate?.log(record)
    }
}
