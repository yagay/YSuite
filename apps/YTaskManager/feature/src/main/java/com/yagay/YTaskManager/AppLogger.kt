package com.yagay.YTaskManager

import android.content.Context
import com.yagay.suite.api.FeatureServices

/** Lightweight logger shared by standalone and YSuite through the common feature services facade. */
object AppLogger {
    private val services = FeatureServices.of("ytaskmanager", "YTaskManager")

    /** Kept for source compatibility; host discovery is now centralized in FeatureHostRegistry. */
    fun attach(context: Context) = Unit

    fun i(message: String) {
        services.info(message)
    }

    fun e(message: String, error: Throwable? = null) {
        services.error(message, error)
    }
}
