package com.yagay.suite.core

import android.content.Context
import android.content.SharedPreferences
import java.util.Locale

/**
 * Shared host logging and validated global language/home controls.
 *
 * Do not add per-feature business options here; they belong to feature stores. All readers use
 * the same preference file and a module override falls back to its global value.
 */
enum class SuiteCommonSetting(val key: String, val defaultValue: String, val globalOnly: Boolean = false) {
    LOG_LEVEL("log_level", "debug"),
    LOG_MAX_FILE_MB("log_max_file_mb", "1"),
    LOG_KEEP_PREVIOUS("log_keep_previous", "true"),
    LANGUAGE("language", "system", globalOnly = true),
    HOME_HIDE_DISABLED("home_hide_disabled", "false", globalOnly = true),
    HOME_SHOW_SEARCH("home_show_search", "true", globalOnly = true),
    HOME_SHOW_DIAGNOSTICS("home_show_diagnostics", "true", globalOnly = true);

    fun validated(value: String): String {
        val valid = when (this) {
            LOG_LEVEL -> value in setOf("debug", "info", "warning", "error")
            LOG_MAX_FILE_MB -> (value.toIntOrNull() ?: 0) in 1..10
            LOG_KEEP_PREVIOUS, HOME_HIDE_DISABLED, HOME_SHOW_SEARCH, HOME_SHOW_DIAGNOSTICS ->
                value == "true" || value == "false"
            LANGUAGE -> value in setOf("system", "en", "zh-CN")
        }
        require(valid) { "Invalid shared setting: $key" }
        return value
    }
}

data class SuiteLoggingOptions(
    val level: String = "debug",
    val maxFileBytes: Long = 1024L * 1024L,
    val keepPrevious: Boolean = true,
) {
    /** Uncaught exceptions and other errors are always preserved. */
    fun accepts(code: String): Boolean {
        val severity = when (code.uppercase(Locale.ROOT)) {
            "E", "ERROR" -> 3
            "W", "WARN", "WARNING" -> 2
            "I", "INFO" -> 1
            else -> 0
        }
        val threshold = when (level) {
            "error" -> 3
            "warning" -> 2
            "info" -> 1
            else -> 0
        }
        return severity >= threshold
    }
}

class SuiteCommonSettings(context: Context) {
    private val preferences: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun value(setting: SuiteCommonSetting, moduleId: String? = null): String {
        val moduleOverride = moduleId?.takeUnless { setting.globalOnly }?.let {
            preferences.getString(prefix(it) + setting.key, null)
        }
        val global = preferences.getString("global." + setting.key, null)
        return runCatching { setting.validated(moduleOverride ?: global ?: setting.defaultValue) }
            .getOrDefault(setting.defaultValue)
    }

    fun set(setting: SuiteCommonSetting, value: String, moduleId: String? = null) {
        require(!setting.globalOnly || moduleId == null) { "Global setting cannot be overridden per module" }
        preferences.edit().putString(prefix(moduleId) + setting.key, setting.validated(value)).apply()
    }

    fun isOverridden(setting: SuiteCommonSetting, moduleId: String): Boolean =
        !setting.globalOnly && preferences.contains(prefix(moduleId) + setting.key)

    fun inherit(setting: SuiteCommonSetting, moduleId: String) {
        require(!setting.globalOnly) { "Global setting cannot be inherited per module" }
        preferences.edit().remove(prefix(moduleId) + setting.key).apply()
    }

    /** Reset only logging settings; language, home, feature states and data stay untouched. */
    fun reset(moduleId: String? = null) {
        val edit = preferences.edit()
        SuiteCommonSetting.entries.filterNot { it.globalOnly }.forEach {
            edit.remove(prefix(moduleId) + it.key)
        }
        edit.apply()
    }

    fun logging(moduleId: String? = null): SuiteLoggingOptions = SuiteLoggingOptions(
        level = value(SuiteCommonSetting.LOG_LEVEL, moduleId),
        maxFileBytes = value(SuiteCommonSetting.LOG_MAX_FILE_MB, moduleId).toLong() * 1024L * 1024L,
        keepPrevious = value(SuiteCommonSetting.LOG_KEEP_PREVIOUS, moduleId) == "true",
    )

    fun observe(onChanged: () -> Unit): () -> Unit {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> onChanged() }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        return { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    private fun prefix(moduleId: String?): String =
        if (moduleId == null) "global." else {
            require(MODULE_ID.matches(moduleId)) { "Invalid module ID" }
            "module.$moduleId."
        }

    companion object {
        private const val FILE = "ysuite_common_settings_v1"
        private val MODULE_ID = Regex("[a-z][a-z0-9_-]{0,63}")
    }
}
