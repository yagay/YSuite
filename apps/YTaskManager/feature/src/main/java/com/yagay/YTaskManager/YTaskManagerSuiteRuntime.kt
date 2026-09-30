package com.yagay.YTaskManager

import android.content.Context

/** Thin runtime entry used by YSuite while keeping standalone behavior identical. */
object YTaskManagerSuiteRuntime {
    @JvmStatic
    fun get(context: Context): Any {
        val appContext = context.applicationContext
        AppLogger.attach(appContext)
        AppLogger.i("YSuite runtime attached")
        // Return the real libxposed listener so YSuite's shared broker can capture it and
        // immediately reclaim process-wide listener ownership.
        return YTaskManagerRuntime.get(appContext)
    }
}
