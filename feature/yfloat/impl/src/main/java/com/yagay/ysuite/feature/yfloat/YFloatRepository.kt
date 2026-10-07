package com.yagay.ysuite.feature.yfloat

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.yagay.YFloat.AccessibilityState
import com.yagay.YFloat.FloatServiceState
import com.yagay.YFloat.FloatSettings
import com.yagay.YFloat.LsposedStatusManager
import com.yagay.YFloat.PrivilegeManager
import com.yagay.YFloat.YFloatRuntimeBootstrap
import com.yagay.ysuite.common.Outcome
import com.yagay.ysuite.platform.api.CapabilityStatus
import com.yagay.ysuite.platform.api.HookGateway

data class YFloatSnapshot(
    val serviceEnabled: Boolean,
    val overlayPermission: Boolean,
    val accessibilityEnabled: Boolean,
    val accessibilityConnected: Boolean,
    val hookStatus: CapabilityStatus,
    val rootGranted: Boolean,
    val alphaPercent: Int,
    val sizeDp: Int,
    val showPercent: Int,
    val bothSide: Boolean,
    val snap: Boolean,
    val showOnLock: Boolean,
    val hideFullscreen: Boolean,
    val imeAvoid: Boolean,
    val quickMove: Boolean,
    val vibrate: Boolean,
    val track: Boolean,
    val longPressDrag: Boolean,
    val clickAction: String,
    val doubleAction: String,
    val longAction: String,
    val keepStatusBar: Boolean,
    val keepNavigationBar: Boolean,
    val accessibilityScreenshot: Boolean,
    val circleEngine: Int,
    val circleBorder: Boolean,
    val circleBorderWidthDp: Int,
    val fullOcrEngine: Int,
    val correctionEngine: Int,
    val enhancedMode: Boolean,
    val rootEnabled: Boolean,
    val lsposedEnabled: Boolean,
    val secureScreenshot: Boolean,
    val diagnosticLogging: Boolean,
)

internal class YFloatRepository(
    private val context: Context,
    private val hooks: HookGateway,
) {
    private val prefs =
        context.getSharedPreferences(
            FloatSettings.PREF,
            Context.MODE_PRIVATE,
        )

    init {
        YFloatRuntimeBootstrap.initialize(context)
    }

    suspend fun snapshot(): YFloatSnapshot {
        val fs = FloatSettings(context)
        val accessibility =
            AccessibilityState.snapshot(context)
        return YFloatSnapshot(
            serviceEnabled =
                FloatServiceState.isEnabled(context),
            overlayPermission =
                Settings.canDrawOverlays(context),
            accessibilityEnabled =
                accessibility.hostEnabled,
            accessibilityConnected =
                accessibility.connected,
            hookStatus =
                runCatching { hooks.status() }
                    .getOrDefault(
                        CapabilityStatus.Error,
                    ),
            rootGranted =
                fs.rootLastGranted(),
            alphaPercent =
                (fs.alpha() * 100f).toInt(),
            sizeDp = fs.sizeDp(),
            showPercent = fs.showPercentage(),
            bothSide = fs.bothSide(),
            snap = fs.snap(),
            showOnLock = fs.showOnLock(),
            hideFullscreen =
                fs.hideWhenFullscreen(),
            imeAvoid = fs.imeAvoid(),
            quickMove = fs.quickMoveEnabled(),
            vibrate = fs.vibrate(),
            track = fs.track(),
            longPressDrag =
                fs.longPressDragEnabled(),
            clickAction =
                fs.action(
                    FloatSettings.K_ACTION_CLICK,
                    "none",
                ),
            doubleAction =
                fs.action(
                    FloatSettings.K_ACTION_DOUBLE,
                    "none",
                ),
            longAction =
                fs.action(
                    FloatSettings.K_ACTION_LONG,
                    "none",
                ),
            keepStatusBar =
                fs.keepStatusBarInScreenshot(),
            keepNavigationBar =
                fs.keepNavigationBarInScreenshot(),
            accessibilityScreenshot =
                fs.accessibilityScreenshot(),
            circleEngine = fs.circleEngine(),
            circleBorder =
                fs.circleBorderEnabled(),
            circleBorderWidthDp =
                fs.circleBorderWidthDp(),
            fullOcrEngine =
                fs.circleFullOcrEngine(),
            correctionEngine =
                fs.circleCorrectionEngine(),
            enhancedMode = fs.enhancedMode(),
            rootEnabled = fs.rootEnabled(),
            lsposedEnabled =
                fs.lsposedEnabled(),
            secureScreenshot =
                fs.lsposedSecureScreenshot(),
            diagnosticLogging =
                fs.diagnosticLogging(),
        )
    }

    fun setService(enabled: Boolean) {
        if (enabled) {
            FloatServiceState.start(context)
        } else {
            FloatServiceState.stop(context)
        }
    }

    fun putBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    fun putInt(key: String, value: Int) {
        prefs.edit().putInt(key, value).apply()
    }

    fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    suspend fun syncHooks(): Outcome<Unit> {
        val fs = FloatSettings(context)
        val updatedAt = System.currentTimeMillis()
        val writes =
            listOf(
                FloatSettings.K_ENHANCED_MODE to
                    fs.enhancedMode().toString(),
                FloatSettings.K_LSPOSED_ENABLED to
                    fs.lsposedEnabled().toString(),
                FloatSettings.K_LSPOSED_SECURE_SCREENSHOT to
                    fs.lsposedSecureScreenshot().toString(),
                FloatSettings.K_DIAGNOSTIC to
                    fs.diagnosticLogging().toString(),
                "updated_at" to
                    updatedAt.toString(),
            )
        for ((key, value) in writes) {
            val result =
                hooks.writeConfig(
                    "yfloat_runtime",
                    key,
                    value,
                )
            if (result is Outcome.Failure) {
                return result
            }
        }
        return hooks.reload(
            setOf(
                "system",
                "com.android.systemui",
                "com.google.android.googlequicksearchbox",
            ),
        )
    }

    fun openOverlayPermission() {
        context.startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse(
                    "package:" +
                        context.packageName,
                ),
            ).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK,
            ),
        )
    }

    fun openAccessibility() {
        context.startActivity(
            Intent(
                Settings.ACTION_ACCESSIBILITY_SETTINGS,
            ).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK,
            ),
        )
    }

    fun checkRoot(
        callback: (Boolean) -> Unit,
    ) {
        PrivilegeManager.checkRootAsync(context) {
            callback(it.granted)
        }
    }

    fun lsposedSummary(): String {
        val snapshot =
            LsposedStatusManager.snapshot()
        return buildString {
            append(
                if (snapshot.serviceConnected) {
                    snapshot.frameworkName +
                        " " +
                        snapshot.frameworkVersion
                } else {
                    "Disconnected"
                },
            )
            if (snapshot.apiVersion > 0) {
                append(" · API ")
                append(snapshot.apiVersion)
            }
            if (snapshot.runningProcesses.isNotEmpty()) {
                append(" · ")
                append(snapshot.runningProcesses.size)
                append(" targets")
            }
        }
    }
}
