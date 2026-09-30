package com.yagay.YTaskManager

import android.content.Context

/** Lightweight logger that works standalone and mirrors into YSuite when embedded. */
object AppLogger {
    @Volatile private var appContext: Context? = null

    fun attach(context: Context) {
        appContext = context.applicationContext
    }

    fun i(message: String) {
        android.util.Log.i("YTaskManager", message)
        mirrorToYSuite("I", message, null)
    }

    fun e(message: String, error: Throwable? = null) {
        android.util.Log.e("YTaskManager", message, error)
        mirrorToYSuite("E", message, error)
    }

    private fun mirrorToYSuite(level: String, message: String, error: Throwable?) {
        val context = appContext ?: return
        runCatching {
            val clazz = Class.forName("com.yagay.suite.core.SuiteLog")
            val instance = clazz.getField("INSTANCE").get(null)
            if (level == "E") {
                clazz.getMethod(
                    "e",
                    Context::class.java,
                    String::class.java,
                    String::class.java,
                    Throwable::class.java,
                ).invoke(instance, context, "ytaskmanager", message, error)
            } else {
                clazz.getMethod(
                    "i",
                    Context::class.java,
                    String::class.java,
                    String::class.java,
                ).invoke(instance, context, "ytaskmanager", message)
            }
        }
    }
}
