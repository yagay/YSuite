package com.yagay.ysuite.feature.yminiguard

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.yagay.ysuite.common.Outcome
import com.yagay.ysuite.feature.yminiguard.api.YMiniGuardApp
import com.yagay.ysuite.feature.yminiguard.api.YMiniGuardEngineStatus
import com.yagay.ysuite.feature.yminiguard.api.YMiniGuardSettings
import com.yagay.ysuite.feature.yminiguard.runtime.YMiniGuardRuntimeBridge
import com.yagay.ysuite.platform.api.CapabilityStatus
import com.yagay.ysuite.platform.api.HookGateway
import com.yagay.ysuite.platform.api.RootGateway
import com.yagay.ysuite.platform.api.RootRequest

internal class YMiniGuardRepository(
    private val context: Context,
    private val root: RootGateway,
    private val hooks: HookGateway,
) {
    private val prefs =
        context.getSharedPreferences(
            "ysuite_yminiguard",
            Context.MODE_PRIVATE,
        )
    private val pm = context.packageManager

    fun settings(): YMiniGuardSettings =
        YMiniGuardSettings(
            masterEnabled = prefs.getBoolean("master_enabled", true),
            autoReload = prefs.getBoolean("engine_auto_reload", true),
            importanceTop = prefs.getBoolean("system_importance_top", true),
            hasResumed = prefs.getBoolean("system_has_resumed", true),
            blockRemoveKill = prefs.getBoolean("system_block_remove_kill", true),
            diagnostics = prefs.getBoolean("diagnostics_active", false),
        )

    fun setSettings(value: YMiniGuardSettings) {
        prefs.edit()
            .putBoolean("master_enabled", value.masterEnabled)
            .putBoolean("engine_auto_reload", value.autoReload)
            .putBoolean("system_importance_top", value.importanceTop)
            .putBoolean("system_has_resumed", value.hasResumed)
            .putBoolean("system_block_remove_kill", value.blockRemoveKill)
            .putBoolean("diagnostics_active", value.diagnostics)
            .apply()
    }

    private fun packageSet(key: String): Set<String> =
        prefs.getStringSet(key, emptySet())?.toSet().orEmpty()

    fun apps(): List<YMiniGuardApp> {
        val foreground = packageSet("foreground_packages")
        val playback = packageSet("background_playback_packages")
        val force = packageSet("force_support_packages")
        return pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .asSequence()
            .filter { it.packageName != context.packageName }
            .map { info ->
                YMiniGuardApp(
                    packageName = info.packageName,
                    label = runCatching {
                        pm.getApplicationLabel(info).toString()
                    }.getOrDefault(info.packageName),
                    system = info.flags and ApplicationInfo.FLAG_SYSTEM != 0,
                    alwaysForeground = info.packageName in foreground,
                    backgroundPlayback = info.packageName in playback,
                    forceFlexibleSupport = info.packageName in force,
                )
            }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    fun updateApp(
        packageName: String,
        foreground: Boolean? = null,
        playback: Boolean? = null,
        forceSupport: Boolean? = null,
    ) {
        fun update(key: String, enabled: Boolean?) {
            if (enabled == null) return
            val values = packageSet(key).toMutableSet()
            if (enabled) values += packageName else values -= packageName
            prefs.edit().putStringSet(key, values).apply()
        }
        update("foreground_packages", foreground)
        update("background_playback_packages", playback)
        update("force_support_packages", forceSupport)
    }

    suspend fun hookStatus(): CapabilityStatus = hooks.status()
    suspend fun rootStatus(): CapabilityStatus = root.status()
    fun engineStatus(): YMiniGuardEngineStatus =
        YMiniGuardRuntimeBridge.readStatus(context)

    suspend fun sync(requestReload: Boolean): Outcome<Unit> {
        val value = settings()
        val reloadSeq =
            if (requestReload) {
                prefs.getInt("engine_reload_seq", 0) + 1
            } else {
                prefs.getInt("engine_reload_seq", 0)
            }
        if (requestReload) {
            prefs.edit().putInt("engine_reload_seq", reloadSeq).apply()
        }
        val version =
            runCatching {
                pm.getPackageInfo(context.packageName, 0).longVersionCode
            }.getOrDefault(1L)
        val values =
            linkedMapOf(
                YMiniGuardRuntimeBridge.HOST_PACKAGE to context.packageName,
                YMiniGuardRuntimeBridge.MODULE_VERSION to version.toString(),
                YMiniGuardRuntimeBridge.MASTER_ENABLED to value.masterEnabled.toString(),
                YMiniGuardRuntimeBridge.ENGINE_AUTO_RELOAD to value.autoReload.toString(),
                YMiniGuardRuntimeBridge.ENGINE_RELOAD_SEQ to reloadSeq.toString(),
                YMiniGuardRuntimeBridge.FOREGROUND_PACKAGES to
                    packageSet("foreground_packages").sorted().joinToString(","),
                YMiniGuardRuntimeBridge.BACKGROUND_PLAYBACK_PACKAGES to
                    packageSet("background_playback_packages").sorted().joinToString(","),
                YMiniGuardRuntimeBridge.FORCE_SUPPORT_PACKAGES to
                    packageSet("force_support_packages").sorted().joinToString(","),
                YMiniGuardRuntimeBridge.SYSTEM_IMPORTANCE_TOP to value.importanceTop.toString(),
                YMiniGuardRuntimeBridge.SYSTEM_HAS_RESUMED to value.hasResumed.toString(),
                YMiniGuardRuntimeBridge.SYSTEM_BLOCK_REMOVE_KILL to value.blockRemoveKill.toString(),
                YMiniGuardRuntimeBridge.DIAGNOSTICS_ACTIVE to value.diagnostics.toString(),
                YMiniGuardRuntimeBridge.DIAGNOSTICS_STARTED_AT to
                    if (value.diagnostics) System.currentTimeMillis().toString() else "",
            )
        for ((key, item) in values) {
            val result =
                hooks.writeConfig(
                    YMiniGuardRuntimeBridge.GROUP,
                    key,
                    item,
                )
            if (result is Outcome.Failure) return result
        }
        return hooks.reload(setOf("android", "com.android.systemui"))
    }

    suspend fun diagnostics(): String {
        val engine = engineStatus()
        val rootStatus = root.status()
        val hookStatus = hooks.status()
        val body =
            if (rootStatus == CapabilityStatus.Available) {
                when (
                    val result =
                        root.execute(
                            RootRequest(
                                command =
                                    "echo '--- SYSTEM ---'; pidof system_server; " +
                                        "echo '--- ACTIVITIES ---'; dumpsys activity activities 2>/dev/null | head -n 240; " +
                                        "echo '--- LOGS ---'; logcat -d -v threadtime -t 700 2>/dev/null | " +
                                        "grep -E 'YMiniGuard|OPLUS_|BACKGROUND_PROTECTED' | tail -n 500",
                                timeoutMillis = 15_000L,
                            ),
                        )
                ) {
                    is Outcome.Success -> result.value.stdout
                    is Outcome.Failure -> result.error.code + ": " + result.error.message
                }
            } else {
                "root_unavailable"
            }
        return "Root=" + rootStatus + " Hook=" + hookStatus + "\n" +
            "engineVersion=" + engine.versionCode +
            " bootstrap=" + engine.bootstrapVersionCode +
            " pid=" + engine.pid +
            " hooks=" + engine.hookCount +
            " sessions=" + engine.activeSessions + "\n" +
            "generation=" + engine.generation +
            " hotReload=" + engine.hotReload +
            " message=" + engine.reloadMessage + "\n\n" +
            body
    }
}
