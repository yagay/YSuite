package com.yagay.YNFC

import android.content.Context
import com.yagay.suite.api.FeatureServices
import com.yagay.suite.api.FeatureLogBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppLogger {
    private val lines = FeatureLogBuffer(1000)
    private val format = SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT)
    private val services = FeatureServices.of("ynfc", "YNFC")

    /** Kept for compatibility; host discovery and log routing are centralized. */
    fun attach(context: Context) = Unit

    fun detach() = Unit

    @Synchronized
    fun i(message: String) {
        val line = "[${format.format(Date())}] APP: $message"
        lines.append(line)
        services.info(message)
    }

    @Synchronized
    fun e(message: String, error: Throwable? = null) {
        val detail = if (error == null) message else "$message ${error.javaClass.simpleName}: ${error.message}"
        val line = "[${format.format(Date())}] APP: $detail"
        lines.addLast(line)
        while (lines.size > 1000) lines.removeFirst()
        services.error(message, error)
    }

    @Synchronized
    fun readAll(): String = lines.readAll()

    @Synchronized
    fun clear() {
        lines.clear()
    }
}
