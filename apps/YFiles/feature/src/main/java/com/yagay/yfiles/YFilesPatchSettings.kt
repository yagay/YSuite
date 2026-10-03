package com.yagay.yfiles

import android.content.Context
import android.net.Uri

data class YFilesPatchSettings(
    val enabled: Boolean = true,
    val initialUri: String? = null,
    val localOnly: Boolean = false,
    val allowMultiple: Boolean = false,
) {
    companion object {
        const val PREFS = "yfiles_patch"
        const val KEY_ENABLED = "enabled"
        const val KEY_INITIAL_URI = "initial_uri"
        const val KEY_LOCAL_ONLY = "local_only"
        const val KEY_ALLOW_MULTIPLE = "allow_multiple"

        fun load(context: Context): YFilesPatchSettings {
            val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            return YFilesPatchSettings(
                enabled = prefs.getBoolean(KEY_ENABLED, true),
                initialUri = prefs.getString(KEY_INITIAL_URI, null)?.takeIf { it.isNotBlank() },
                localOnly = prefs.getBoolean(KEY_LOCAL_ONLY, false),
                allowMultiple = prefs.getBoolean(KEY_ALLOW_MULTIPLE, false),
            )
        }

        fun update(context: Context, block: YFilesPatchSettings.() -> YFilesPatchSettings): YFilesPatchSettings {
            val next = load(context).block()
            val editor = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean(KEY_ENABLED, next.enabled)
                .putBoolean(KEY_LOCAL_ONLY, next.localOnly)
                .putBoolean(KEY_ALLOW_MULTIPLE, next.allowMultiple)
            if (next.initialUri.isNullOrBlank()) editor.remove(KEY_INITIAL_URI)
            else editor.putString(KEY_INITIAL_URI, next.initialUri)
            editor.apply()
            return next
        }

        fun setInitialUri(context: Context, uri: Uri?): YFilesPatchSettings =
            update(context) { copy(initialUri = uri?.toString()) }
    }
}
