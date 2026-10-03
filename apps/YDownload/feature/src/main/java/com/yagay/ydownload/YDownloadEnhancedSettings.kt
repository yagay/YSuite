package com.yagay.ydownload

import android.content.Context

/**
 * Settings that belong only to YDownload's own enhanced engine.
 *
 * Android DownloadManager remains the compatibility-first system path. These options intentionally
 * do not mutate system DownloadProvider behaviour.
 */
data class YDownloadEnhancedSettings(
    val defaultBackend: DownloadBackend = DownloadBackend.SYSTEM,
    val maxConcurrent: Int = 2,
    val autoRetry: Boolean = true,
    val maxRetries: Int = 2,
) {
    fun normalized(): YDownloadEnhancedSettings = copy(
        maxConcurrent = maxConcurrent.coerceIn(1, 4),
        maxRetries = maxRetries.coerceIn(0, 5),
    )

    companion object {
        private const val PREFS = "ydownload_enhanced"
        private const val KEY_DEFAULT_BACKEND = "default_backend"
        private const val KEY_MAX_CONCURRENT = "max_concurrent"
        private const val KEY_AUTO_RETRY = "auto_retry"
        private const val KEY_MAX_RETRIES = "max_retries"

        fun load(context: Context): YDownloadEnhancedSettings {
            val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val backend = runCatching {
                DownloadBackend.valueOf(
                    prefs.getString(KEY_DEFAULT_BACKEND, DownloadBackend.SYSTEM.name)
                        ?: DownloadBackend.SYSTEM.name,
                )
            }.getOrDefault(DownloadBackend.SYSTEM)
            return YDownloadEnhancedSettings(
                defaultBackend = backend,
                maxConcurrent = prefs.getInt(KEY_MAX_CONCURRENT, 2),
                autoRetry = prefs.getBoolean(KEY_AUTO_RETRY, true),
                maxRetries = prefs.getInt(KEY_MAX_RETRIES, 2),
            ).normalized()
        }

        fun update(
            context: Context,
            block: YDownloadEnhancedSettings.() -> YDownloadEnhancedSettings,
        ): YDownloadEnhancedSettings {
            val next = load(context).block().normalized()
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_DEFAULT_BACKEND, next.defaultBackend.name)
                .putInt(KEY_MAX_CONCURRENT, next.maxConcurrent)
                .putBoolean(KEY_AUTO_RETRY, next.autoRetry)
                .putInt(KEY_MAX_RETRIES, next.maxRetries)
                .apply()
            return next
        }
    }
}
