package com.yagay.suite.core

import android.content.Context

/**
 * Attributes same-process crashes to the feature that YSuite most recently opened.
 *
 * This intentionally stays host-level and lightweight. It does not replace Android/LSPosed
 * diagnostics; it guarantees that click-to-crash failures leave a useful breadcrumb in the
 * next YSuite log export.
 */
object SuiteCrashTracker {
    @Volatile
    private var installed = false

    fun install(context: Context) {
        if (installed) return
        synchronized(this) {
            if (installed) return
            val appContext = context.applicationContext
            val previous = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, error ->
                val activeFeature = activeFeature(appContext)
                runCatching {
                    SuiteLog.e(
                        appContext,
                        SuiteContract.CRASH_MODULE_ID,
                        "uncaught exception; activeFeature=${activeFeature ?: "none"}; thread=${thread.name}",
                        error,
                    )
                    if (!activeFeature.isNullOrBlank()) {
                        SuiteLog.e(
                            appContext,
                            activeFeature,
                            "uncaught exception while feature active; thread=${thread.name}",
                            error,
                        )
                    }
                }
                previous?.uncaughtException(thread, error)
            }
            installed = true
        }
    }

    fun markActiveFeature(context: Context, featureId: String?) {
        val prefs = context.applicationContext.getSharedPreferences(
            SuiteContract.CRASH_CONTEXT_PREFS,
            Context.MODE_PRIVATE,
        )
        if (featureId.isNullOrBlank()) {
            prefs.edit().remove(SuiteContract.ACTIVE_FEATURE_KEY).apply()
        } else {
            prefs.edit().putString(SuiteContract.ACTIVE_FEATURE_KEY, featureId).apply()
        }
    }

    fun activeFeature(context: Context): String? =
        context.applicationContext
            .getSharedPreferences(SuiteContract.CRASH_CONTEXT_PREFS, Context.MODE_PRIVATE)
            .getString(SuiteContract.ACTIVE_FEATURE_KEY, null)
}
