package com.yagay.yfiles

import android.content.Context
import android.net.Uri

data class YFilesPatchSettings(
    val enabled: Boolean = true,
    val initialUri: String? = null,
    val localOnly: Boolean = false,
    val allowMultiple: Boolean = false,
    val defaultSort: String = SORT_SYSTEM,
) {
    companion object {
        const val PREFS = "yfiles_patch"
        const val KEY_ENABLED = "enabled"
        const val KEY_INITIAL_URI = "initial_uri"
        const val KEY_LOCAL_ONLY = "local_only"
        const val KEY_ALLOW_MULTIPLE = "allow_multiple"
        const val KEY_DEFAULT_SORT = "default_sort"

        const val SORT_SYSTEM = "system"
        const val SORT_NAME = "name"
        const val SORT_DATE = "date"
        const val SORT_SIZE = "size"
        const val SORT_TYPE = "type"

        private val VALID_SORTS = setOf(SORT_SYSTEM, SORT_NAME, SORT_DATE, SORT_SIZE, SORT_TYPE)

        fun load(context: Context): YFilesPatchSettings {
            val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            return YFilesPatchSettings(
                enabled = prefs.getBoolean(KEY_ENABLED, true),
                initialUri = prefs.getString(KEY_INITIAL_URI, null)?.takeIf { it.isNotBlank() },
                localOnly = prefs.getBoolean(KEY_LOCAL_ONLY, false),
                allowMultiple = prefs.getBoolean(KEY_ALLOW_MULTIPLE, false),
                defaultSort = prefs.getString(KEY_DEFAULT_SORT, SORT_SYSTEM)
                    ?.takeIf { it in VALID_SORTS }
                    ?: SORT_SYSTEM,
            )
        }

        fun update(context: Context, block: YFilesPatchSettings.() -> YFilesPatchSettings): YFilesPatchSettings {
            val next = load(context).block().let {
                if (it.defaultSort in VALID_SORTS) it else it.copy(defaultSort = SORT_SYSTEM)
            }
            val editor = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean(KEY_ENABLED, next.enabled)
                .putBoolean(KEY_LOCAL_ONLY, next.localOnly)
                .putBoolean(KEY_ALLOW_MULTIPLE, next.allowMultiple)
                .putString(KEY_DEFAULT_SORT, next.defaultSort)
            if (next.initialUri.isNullOrBlank()) editor.remove(KEY_INITIAL_URI)
            else editor.putString(KEY_INITIAL_URI, next.initialUri)
            editor.apply()
            return next
        }

        fun setInitialUri(context: Context, uri: Uri?): YFilesPatchSettings =
            update(context) { copy(initialUri = uri?.toString()) }
    }
}
