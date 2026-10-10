package com.yagay.suite.api

import android.content.Context
import android.content.SharedPreferences

/**
 * Shared preference storage primitive for YSuite features.
 *
 * Feature code should use this instead of opening SharedPreferences directly so integrated and
 * standalone builds share the same storage behaviour. Preference names and keys remain owned by
 * each feature, which keeps existing data fully compatible.
 */
class FeatureSettings private constructor(
    private val preferences: SharedPreferences,
) {
    fun boolean(key: String, defaultValue: Boolean = false): Boolean =
        preferences.getBoolean(key, defaultValue)

    fun string(key: String, defaultValue: String? = null): String? =
        preferences.getString(key, defaultValue)

    fun int(key: String, defaultValue: Int = 0): Int =
        preferences.getInt(key, defaultValue)

    fun long(key: String, defaultValue: Long = 0L): Long =
        preferences.getLong(key, defaultValue)

    fun stringSet(key: String, defaultValue: Set<String> = emptySet()): Set<String> =
        preferences.getStringSet(key, defaultValue)?.toSet() ?: defaultValue.toSet()

    fun contains(key: String): Boolean = preferences.contains(key)

    fun putBoolean(key: String, value: Boolean) = edit { putBoolean(key, value) }

    fun putString(key: String, value: String?) = edit { putString(key, value) }

    fun putInt(key: String, value: Int) = edit { putInt(key, value) }

    fun putLong(key: String, value: Long) = edit { putLong(key, value) }

    fun putStringSet(key: String, value: Set<String>) = edit { putStringSet(key, value.toSet()) }

    fun remove(key: String) = edit { remove(key) }

    fun clear() = edit { clear() }

    /** Applies related preference changes together in one SharedPreferences transaction. */
    fun edit(block: SharedPreferences.Editor.() -> Unit) {
        preferences.edit().apply {
            block()
            apply()
        }
    }

    companion object {
        @JvmStatic
        fun named(context: Context, preferenceName: String): FeatureSettings {
            require(preferenceName.isNotBlank()) { "preferenceName must not be blank" }
            val appContext = context.applicationContext ?: context
            return FeatureSettings(
                appContext.getSharedPreferences(preferenceName, Context.MODE_PRIVATE),
            )
        }
    }
}
