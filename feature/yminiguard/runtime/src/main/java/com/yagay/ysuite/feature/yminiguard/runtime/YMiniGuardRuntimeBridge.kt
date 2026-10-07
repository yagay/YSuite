package com.yagay.ysuite.feature.yminiguard.runtime

import android.content.Context
import com.yagay.ysuite.feature.yminiguard.api.YMiniGuardEngineStatus

object YMiniGuardRuntimeBridge {
    const val GROUP = "guard_config"
    const val MASTER_ENABLED = "master_enabled"
    const val ENGINE_AUTO_RELOAD = "engine_auto_reload"
    const val ENGINE_RELOAD_SEQ = "engine_reload_seq"
    const val FOREGROUND_PACKAGES = "foreground_packages"
    const val BACKGROUND_PLAYBACK_PACKAGES = "background_playback_packages"
    const val FORCE_SUPPORT_PACKAGES = "force_support_packages"
    const val SYSTEM_IMPORTANCE_TOP = "system_importance_top"
    const val SYSTEM_HAS_RESUMED = "system_has_resumed"
    const val SYSTEM_BLOCK_REMOVE_KILL = "system_block_remove_kill"
    const val DIAGNOSTICS_ACTIVE = "diagnostics_active"
    const val DIAGNOSTICS_STARTED_AT = "diagnostics_started_at"
    const val HOST_PACKAGE = "host_package"
    const val MODULE_VERSION = "module_version"

    @JvmStatic
    fun readStatus(context: Context): YMiniGuardEngineStatus {
        val prefs =
            context.createDeviceProtectedStorageContext()
                .getSharedPreferences(
                    "engine_status",
                    Context.MODE_PRIVATE,
                )
        return YMiniGuardEngineStatus(
            versionCode = prefs.getLong("version", -1L),
            bootstrapVersionCode = prefs.getLong("bootstrap_version", -1L),
            hotReload = prefs.getBoolean("hot_reload", false),
            generation = prefs.getLong("generation", 0L),
            reloadMessage = prefs.getString("reload_message", "").orEmpty(),
            activeSessions = prefs.getInt("active_sessions", -1),
            startedAt = prefs.getLong("started_at", 0L),
            pid = prefs.getInt("pid", -1),
            hookCount = prefs.getInt("hooks", -1),
        )
    }
}
