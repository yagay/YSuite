package com.yagay.ysuite.feature.yfloat

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.yagay.ysuite.common.Outcome
import com.yagay.ysuite.feature.yfloat.runtime.YFloatRuntimeBridge
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
            YFloatRuntimeBridge.PREF,
            Context.MODE_PRIVATE,
        )

    init {
        YFloatRuntimeBridge.initialize(context)
    }

    suspend fun snapshot(): YFloatSnapshot {
        val fs = YFloatRuntimeBridge.snapshot(context)
        return YFloatSnapshot(
            serviceEnabled =
                fs.serviceEnabled,
            overlayPermission =
                Settings.canDrawOverlays(context),
            accessibilityEnabled =
                fs.accessibilityEnabled,
            accessibilityConnected =
                fs.accessibilityConnected,
            hookStatus =
                runCatching { hooks.status() }
                    .getOrDefault(
                        CapabilityStatus.Error,
                    ),
            rootGranted =
                fs.rootGranted,
            alphaPercent =
                fs.alphaPercent,
            sizeDp = fs.sizeDp,
            showPercent = fs.showPercent,
            bothSide = fs.bothSide,
            snap = fs.snap,
            showOnLock = fs.showOnLock,
            hideFullscreen =
                fs.hideFullscreen,
            imeAvoid = fs.imeAvoid,
            quickMove = fs.quickMove,
            vibrate = fs.vibrate,
            track = fs.track,
            longPressDrag =
                fs.longPressDrag,
            clickAction =
                fs.clickAction,
            doubleAction =
                fs.doubleAction,
            longAction =
                fs.longAction,
            keepStatusBar =
                fs.keepStatusBar,
            keepNavigationBar =
                fs.keepNavigationBar,
            accessibilityScreenshot =
                fs.accessibilityScreenshot,
            circleEngine = fs.circleEngine,
            circleBorder =
                fs.circleBorder,
            circleBorderWidthDp =
                fs.circleBorderWidthDp,
            fullOcrEngine =
                fs.fullOcrEngine,
            correctionEngine =
                fs.correctionEngine,
            enhancedMode = fs.enhancedMode,
            rootEnabled = fs.rootEnabled,
            lsposedEnabled =
                fs.lsposedEnabled,
            secureScreenshot =
                fs.secureScreenshot,
            diagnosticLogging =
                fs.diagnosticLogging,
        )
    }

    fun setService(enabled: Boolean) {
        YFloatRuntimeBridge.setService(context, enabled)
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
        val fs = YFloatRuntimeBridge.snapshot(context)
        val updatedAt = System.currentTimeMillis()
        val writes =
            listOf(
                YFloatRuntimeBridge.K_ENHANCED_MODE to
                    fs.enhancedMode.toString(),
                YFloatRuntimeBridge.K_LSPOSED_ENABLED to
                    fs.lsposedEnabled.toString(),
                YFloatRuntimeBridge.K_LSPOSED_SECURE_SCREENSHOT to
                    fs.secureScreenshot.toString(),
                YFloatRuntimeBridge.K_DIAGNOSTIC to
                    fs.diagnosticLogging.toString(),
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
        YFloatRuntimeBridge.checkRoot(context, callback)
    }

    fun lsposedSummary(): String {
        return YFloatRuntimeBridge.lsposedSummary()
    }
}
