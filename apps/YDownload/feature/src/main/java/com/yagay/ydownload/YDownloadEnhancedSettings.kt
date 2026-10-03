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
    val userAgent: String = DEFAULT_USER_AGENT,
    /** Global enhanced-engine bandwidth cap in KiB/s. 0 means unlimited. */
    val speedLimitKib: Int = 0,
    val calculateSha256: Boolean = false,
) {
    fun normalized(): YDownloadEnhancedSettings = copy(
        maxConcurrent = maxConcurrent.coerceIn(1, 4),
        maxRetries = maxRetries.coerceIn(0, 5),
        userAgent = userAgent.trim().take(256).ifBlank { DEFAULT_USER_AGENT },
        speedLimitKib = speedLimitKib.coerceIn(0, MAX_SPEED_LIMIT_KIB),
    )

    companion object {
        const val DEFAULT_USER_AGENT = "YDownload/0.4"
        private const val MAX_SPEED_LIMIT_KIB = 1024 * 100
        private const val PREFS = "ydownload_enhanced"
        private const val KEY_DEFAULT_BACKEND = "default_backend"
        private const val KEY_MAX_CONCURRENT = "max_concurrent"
        private const val KEY_AUTO_RETRY = "auto_retry"
        private const val KEY_MAX_RETRIES = "max_retries"
        private const val KEY_USER_AGENT = "user_agent"
        private const val KEY_SPEED_LIMIT_KIB = "speed_limit_kib"
        private const val KEY_CALCULATE_SHA256 = "calculate_sha256"

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
                userAgent = prefs.getString(KEY_USER_AGENT, DEFAULT_USER_AGENT) ?: DEFAULT_USER_AGENT,
                speedLimitKib = prefs.getInt(KEY_SPEED_LIMIT_KIB, 0),
                calculateSha256 = prefs.getBoolean(KEY_CALCULATE_SHA256, false),
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
                .putString(KEY_USER_AGENT, next.userAgent)
                .putInt(KEY_SPEED_LIMIT_KIB, next.speedLimitKib)
                .putBoolean(KEY_CALCULATE_SHA256, next.calculateSha256)
                .apply()
            return next
        }
    }
}
