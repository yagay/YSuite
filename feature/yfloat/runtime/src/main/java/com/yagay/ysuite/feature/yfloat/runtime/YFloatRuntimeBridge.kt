package com.yagay.ysuite.feature.yfloat.runtime

import android.content.Context
import com.yagay.YFloat.AccessibilityState
import com.yagay.YFloat.ActionId
import com.yagay.YFloat.FloatServiceState
import com.yagay.YFloat.FloatSettings
import com.yagay.YFloat.LsposedStatusManager
import com.yagay.YFloat.PrivilegeManager
import com.yagay.YFloat.YFloatRuntimeBootstrap
import com.yagay.YFloat.compat.YFloatRuntimeHost
import com.yagay.ysuite.logging.api.YSuiteLogger
import com.yagay.ysuite.platform.api.RootGateway

data class YFloatRuntimeSnapshot(
    val serviceEnabled: Boolean,
    val accessibilityEnabled: Boolean,
    val accessibilityConnected: Boolean,
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

object YFloatRuntimeBridge {
    const val PREF = FloatSettings.PREF

    const val K_ALPHA = FloatSettings.K_ALPHA
    const val K_SIZE = FloatSettings.K_SIZE
    const val K_SHOW_PERCENT = FloatSettings.K_SHOW_PERCENT
    const val K_BOTH_SIDE = FloatSettings.K_BOTH_SIDE
    const val K_SNAP = FloatSettings.K_SNAP
    const val K_SHOW_ON_LOCK = FloatSettings.K_SHOW_ON_LOCK
    const val K_HIDE_FULLSCREEN = FloatSettings.K_HIDE_FULLSCREEN
    const val K_IME_AVOID = FloatSettings.K_IME_AVOID
    const val K_QUICK_MOVE = FloatSettings.K_QUICK_MOVE
    const val K_VIBRATE = FloatSettings.K_VIBRATE
    const val K_TRACK = FloatSettings.K_TRACK
    const val K_LONG_PRESS_DRAG = FloatSettings.K_LONG_PRESS_DRAG
    const val K_ACTION_CLICK = FloatSettings.K_ACTION_CLICK
    const val K_ACTION_DOUBLE = FloatSettings.K_ACTION_DOUBLE
    const val K_ACTION_LONG = FloatSettings.K_ACTION_LONG
    const val K_KEEP_STATUS_BAR = FloatSettings.K_KEEP_STATUS_BAR
    const val K_KEEP_NAVIGATION_BAR = FloatSettings.K_KEEP_NAVIGATION_BAR
    const val K_ACCESSIBILITY_SCREENSHOT = FloatSettings.K_ACCESSIBILITY_SCREENSHOT
    const val K_CIRCLE_BORDER_ENABLED = FloatSettings.K_CIRCLE_BORDER_ENABLED
    const val K_CIRCLE_BORDER_WIDTH_DP = FloatSettings.K_CIRCLE_BORDER_WIDTH_DP
    const val K_CIRCLE_ENGINE = FloatSettings.K_CIRCLE_ENGINE
    const val K_CIRCLE_FULL_OCR_ENGINE = FloatSettings.K_CIRCLE_FULL_OCR_ENGINE
    const val K_CIRCLE_CORRECTION_ENGINE = FloatSettings.K_CIRCLE_CORRECTION_ENGINE
    const val K_ENHANCED_MODE = FloatSettings.K_ENHANCED_MODE
    const val K_ROOT_ENABLED = FloatSettings.K_ROOT_ENABLED
    const val K_LSPOSED_ENABLED = FloatSettings.K_LSPOSED_ENABLED
    const val K_LSPOSED_SECURE_SCREENSHOT = FloatSettings.K_LSPOSED_SECURE_SCREENSHOT
    const val K_DIAGNOSTIC = FloatSettings.K_DIAGNOSTIC

    const val ACTION_NONE = ActionId.NONE
    const val ACTION_BACK = ActionId.BACK
    const val ACTION_HOME = ActionId.HOME
    const val ACTION_RECENTS = ActionId.RECENTS
    const val ACTION_SCREENSHOT = ActionId.SCREENSHOT
    const val ACTION_REGION_SCREENSHOT = ActionId.REGION_SCREENSHOT
    const val ACTION_OCR = ActionId.OCR
    const val ACTION_NOTIFICATIONS = ActionId.NOTIFICATIONS
    const val ACTION_HIDE = ActionId.HIDE
    const val ACTION_MOVE_ICON = ActionId.MOVE_ICON
    const val ACTION_AI_SCREEN = ActionId.AI_SCREEN

    @JvmStatic
    fun attachHost(
        rootGateway: RootGateway,
        logger: YSuiteLogger,
    ) {
        YFloatRuntimeHost.install(rootGateway, logger)
    }

    @JvmStatic
    fun initialize(context: Context) {
        YFloatRuntimeBootstrap.initialize(context)
    }

    @JvmStatic
    fun snapshot(context: Context): YFloatRuntimeSnapshot {
        val fs = FloatSettings(context)
        val accessibility = AccessibilityState.snapshot(context)
        return YFloatRuntimeSnapshot(
            serviceEnabled = FloatServiceState.isEnabled(context),
            accessibilityEnabled = accessibility.hostEnabled,
            accessibilityConnected = accessibility.connected,
            rootGranted = fs.rootLastGranted(),
            alphaPercent = (fs.alpha() * 100f).toInt(),
            sizeDp = fs.sizeDp(),
            showPercent = fs.showPercentage(),
            bothSide = fs.bothSide(),
            snap = fs.snap(),
            showOnLock = fs.showOnLock(),
            hideFullscreen = fs.hideWhenFullscreen(),
            imeAvoid = fs.imeAvoid(),
            quickMove = fs.quickMoveEnabled(),
            vibrate = fs.vibrate(),
            track = fs.track(),
            longPressDrag = fs.longPressDragEnabled(),
            clickAction = fs.action(FloatSettings.K_ACTION_CLICK, "none"),
            doubleAction = fs.action(FloatSettings.K_ACTION_DOUBLE, "none"),
            longAction = fs.action(FloatSettings.K_ACTION_LONG, "none"),
            keepStatusBar = fs.keepStatusBarInScreenshot(),
            keepNavigationBar = fs.keepNavigationBarInScreenshot(),
            accessibilityScreenshot = fs.accessibilityScreenshot(),
            circleEngine = fs.circleEngine(),
            circleBorder = fs.circleBorderEnabled(),
            circleBorderWidthDp = fs.circleBorderWidthDp(),
            fullOcrEngine = fs.circleFullOcrEngine(),
            correctionEngine = fs.circleCorrectionEngine(),
            enhancedMode = fs.enhancedMode(),
            rootEnabled = fs.rootEnabled(),
            lsposedEnabled = fs.lsposedEnabled(),
            secureScreenshot = fs.lsposedSecureScreenshot(),
            diagnosticLogging = fs.diagnosticLogging(),
        )
    }

    @JvmStatic
    fun setService(context: Context, enabled: Boolean) {
        if (enabled) {
            FloatServiceState.start(context)
        } else {
            FloatServiceState.stop(context)
        }
    }

    @JvmStatic
    fun checkRoot(
        context: Context,
        callback: (Boolean) -> Unit,
    ) {
        PrivilegeManager.checkRootAsync(context) {
            callback(it.granted)
        }
    }

    @JvmStatic
    fun lsposedSummary(): String {
        val snapshot = LsposedStatusManager.snapshot()
        return buildString {
            append(
                if (snapshot.serviceConnected) {
                    snapshot.frameworkName + " " + snapshot.frameworkVersion
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
