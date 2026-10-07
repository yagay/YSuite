package com.yagay.ysuite.feature.yminiguard.api

data class YMiniGuardApp(
    val packageName: String,
    val label: String,
    val system: Boolean,
    val alwaysForeground: Boolean,
    val backgroundPlayback: Boolean,
    val forceFlexibleSupport: Boolean,
)

data class YMiniGuardSettings(
    val masterEnabled: Boolean = true,
    val autoReload: Boolean = true,
    val importanceTop: Boolean = true,
    val hasResumed: Boolean = true,
    val blockRemoveKill: Boolean = true,
    val diagnostics: Boolean = false,
)

data class YMiniGuardEngineStatus(
    val versionCode: Long = -1L,
    val bootstrapVersionCode: Long = -1L,
    val hotReload: Boolean = false,
    val generation: Long = 0L,
    val reloadMessage: String = "",
    val activeSessions: Int = -1,
    val startedAt: Long = 0L,
    val pid: Int = -1,
    val hookCount: Int = -1,
)
