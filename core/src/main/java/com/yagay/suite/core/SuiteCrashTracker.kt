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
    private var installedHandler: Thread.UncaughtExceptionHandler? = null

    fun install(context: Context) {
        if (installedHandler != null) return
        synchronized(this) {
            if (installedHandler != null) return
            val appContext = context.applicationContext
            val previous = Thread.getDefaultUncaughtExceptionHandler()
            val handler = Thread.UncaughtExceptionHandler { thread, error ->
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
            installedHandler = handler
            Thread.setDefaultUncaughtExceptionHandler(handler)
        }
    }

    /**
     * Some standalone feature runtimes install their own process-wide handler during initialization.
     * In the combined process YSuite must remain the final owner so crashes are attributed once and
     * exported through the unified log path.
     */
    fun reclaim(context: Context) {
        install(context)
        val handler = installedHandler ?: return
        if (Thread.getDefaultUncaughtExceptionHandler() !== handler) {
            Thread.setDefaultUncaughtExceptionHandler(handler)
        }
    }

    fun markActiveFeature(context: Context, featureId: String?) {
        val prefs = crashStorage(context).getSharedPreferences(
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
        crashStorage(context)
            .getSharedPreferences(SuiteContract.CRASH_CONTEXT_PREFS, Context.MODE_PRIVATE)
            .getString(SuiteContract.ACTIVE_FEATURE_KEY, null)

    /** Crash attribution must remain available during Direct Boot, before CE storage is unlocked. */
    private fun crashStorage(context: Context): Context =
        context.applicationContext.createDeviceProtectedStorageContext()
}
