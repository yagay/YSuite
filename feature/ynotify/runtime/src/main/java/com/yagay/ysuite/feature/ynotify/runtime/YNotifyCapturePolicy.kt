package com.yagay.ysuite.feature.ynotify.runtime

import android.content.Context

internal object YNotifyCapturePolicy {
    private const val PREFS = "ynotify_capture_policy"
    private const val PAUSED = "paused_packages"
    private const val REDACTED = "redacted_packages"
    private const val RETENTION = "retention_days"

    fun isPaused(context: Context, packageName: String): Boolean =
        prefs(context).getStringSet(PAUSED, emptySet()).orEmpty().contains(packageName)

    fun isRedacted(context: Context, packageName: String): Boolean =
        prefs(context).getStringSet(REDACTED, emptySet()).orEmpty().contains(packageName)

    fun setPaused(context: Context, packageName: String, value: Boolean) =
        mutate(context, PAUSED, packageName, value)

    fun setRedacted(context: Context, packageName: String, value: Boolean) =
        mutate(context, REDACTED, packageName, value)

    fun retentionDays(context: Context): Int =
        prefs(context).getInt(RETENTION, 90).let { if (it in setOf(0, 7, 30, 90)) it else 90 }

    fun setRetentionDays(context: Context, days: Int) {
        require(days in setOf(0, 7, 30, 90))
        prefs(context).edit().putInt(RETENTION, days).apply()
    }

    private fun mutate(context: Context, key: String, packageName: String, value: Boolean) {
        val preferences = prefs(context)
        val next = preferences.getStringSet(key, emptySet()).orEmpty().toMutableSet()
        if (value) next += packageName else next -= packageName
        preferences.edit().putStringSet(key, next).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
