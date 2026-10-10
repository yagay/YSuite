package com.yagay.yui

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate

/**
 * The single persisted app-appearance preference for YSuite and every embedded feature.
 *
 * SharedPreferences are intentionally scoped to the host package. Standalone feature APKs use
 * this same contract within their own package without relying on cross-package file access.
 */
object YAppearanceSettings {
    const val MODE_SYSTEM = 0
    const val MODE_LIGHT = 1
    const val MODE_DARK = 2

    private const val PREFERENCES = "yui_appearance"
    private const val THEME_MODE = "theme_mode"
    private const val LEGACY_YFLOAT_PREFERENCES = "yfloat_ui"

    @JvmStatic
    fun mode(context: Context): Int {
        val prefs = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        if (!prefs.contains(THEME_MODE)) {
            // One-way migration; keep a previous YFloat appearance choice after installing
            // an integrated build, then read this unified setting exclusively.
            val legacy = context.applicationContext.getSharedPreferences(
                LEGACY_YFLOAT_PREFERENCES, Context.MODE_PRIVATE
            )
            if (legacy.contains(THEME_MODE)) {
                val migrated = normalize(legacy.getInt(THEME_MODE, MODE_SYSTEM))
                prefs.edit().putInt(THEME_MODE, migrated).apply()
                return migrated
            }
        }
        return normalize(prefs.getInt(THEME_MODE, MODE_SYSTEM))
    }

    @JvmStatic
    fun setMode(context: Context, requested: Int): Boolean {
        val next = normalize(requested)
        if (next == mode(context)) return false
        context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit().putInt(THEME_MODE, next).apply()
        applySavedMode(context)
        return true
    }

    @JvmStatic
    fun isDark(context: Context): Boolean {
        val night = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return isDark(context, night == Configuration.UI_MODE_NIGHT_YES)
    }

    @JvmStatic
    fun isDark(context: Context, systemDark: Boolean): Boolean = when (mode(context)) {
        MODE_LIGHT -> false
        MODE_DARK -> true
        else -> systemDark
    }

    /** Applies the same preference to AppCompat/View screens as to YTheme Compose screens. */
    @JvmStatic
    fun applySavedMode(context: Context) {
        val delegateMode = when (mode(context)) {
            MODE_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            MODE_DARK -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        if (AppCompatDelegate.getDefaultNightMode() != delegateMode) {
            AppCompatDelegate.setDefaultNightMode(delegateMode)
        }
    }

    private fun normalize(value: Int): Int =
        if (value in MODE_SYSTEM..MODE_DARK) value else MODE_SYSTEM
}
