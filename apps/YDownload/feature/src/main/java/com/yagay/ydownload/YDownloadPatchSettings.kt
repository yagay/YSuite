package com.yagay.ydownload

import android.content.Context

/**
 * Settings consumed by both the YDownload UI and LSPosed DownloadManager patch.
 * Defaults are deliberately non-invasive: enabling the patch alone does not loosen or tighten
 * the caller's existing DownloadManager policy until the user changes a setting.
 */
data class YDownloadPatchSettings(
    val enabled: Boolean = true,
    val allowMetered: Boolean = true,
    val allowRoaming: Boolean = true,
    val requireCharging: Boolean = false,
    val requireDeviceIdle: Boolean = false,
    val forceCompletionNotification: Boolean = false,
) {
    companion object {
        const val PREFS = "ydownload_patch"
        const val KEY_ENABLED = "enabled"
        const val KEY_ALLOW_METERED = "allow_metered"
        const val KEY_ALLOW_ROAMING = "allow_roaming"
        const val KEY_REQUIRE_CHARGING = "require_charging"
        const val KEY_REQUIRE_IDLE = "require_device_idle"
        const val KEY_FORCE_COMPLETION_NOTIFICATION = "force_completion_notification"

        fun load(context: Context): YDownloadPatchSettings {
            val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            return YDownloadPatchSettings(
                enabled = prefs.getBoolean(KEY_ENABLED, true),
                allowMetered = prefs.getBoolean(KEY_ALLOW_METERED, true),
                allowRoaming = prefs.getBoolean(KEY_ALLOW_ROAMING, true),
                requireCharging = prefs.getBoolean(KEY_REQUIRE_CHARGING, false),
                requireDeviceIdle = prefs.getBoolean(KEY_REQUIRE_IDLE, false),
                forceCompletionNotification = prefs.getBoolean(KEY_FORCE_COMPLETION_NOTIFICATION, false),
            )
        }

        fun update(context: Context, block: YDownloadPatchSettings.() -> YDownloadPatchSettings): YDownloadPatchSettings {
            val next = load(context).block()
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean(KEY_ENABLED, next.enabled)
                .putBoolean(KEY_ALLOW_METERED, next.allowMetered)
                .putBoolean(KEY_ALLOW_ROAMING, next.allowRoaming)
                .putBoolean(KEY_REQUIRE_CHARGING, next.requireCharging)
                .putBoolean(KEY_REQUIRE_IDLE, next.requireDeviceIdle)
                .putBoolean(KEY_FORCE_COMPLETION_NOTIFICATION, next.forceCompletionNotification)
                .apply()
            return next
        }
    }
}
