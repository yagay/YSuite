package com.yagay.YSuite

import android.app.Activity
import android.app.Application
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import java.util.WeakHashMap

/**
 * One host-level window shell for every regular Activity embedded in YSuite.
 *
 * Feature APKs remain independently buildable and keep their standalone window behavior. When
 * embedded in YSuite, system bars and system-bar insets are owned here so a feature cannot add the
 * status/navigation insets a second time. IME insets are deliberately preserved for child views.
 */
object SuiteUiCoordinator {
    private data class BasePadding(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
    )

    private val originalPadding = WeakHashMap<View, BasePadding>()

    private const val HOST_ACTIVITY = "com.yagay.YSuite.MainActivity"

    /** Activities whose transparent/overlay window semantics must not be changed by the host. */
    private val excludedActivities = setOf(
        "com.yagay.YFloat.ResultActivity",
        "com.yagay.YFloat.ShadeDismissActivity",
        "com.yagay.YFloat.SecureCaptureProbeActivity",
    )

    fun install(application: Application) {
        application.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                    scheduleApply(activity)
                }

                override fun onActivityResumed(activity: Activity) {
                    scheduleApply(activity)
                }

                override fun onActivityStarted(activity: Activity) = Unit
                override fun onActivityPaused(activity: Activity) = Unit
                override fun onActivityStopped(activity: Activity) = Unit
                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
                override fun onActivityDestroyed(activity: Activity) = Unit
            },
        )
    }

    private fun scheduleApply(activity: Activity) {
        if (activity.javaClass.name in excludedActivities) return
        activity.window.decorView.post { apply(activity) }
    }

    private fun apply(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed) return
        if (activity.javaClass.name in excludedActivities) return

        val window = activity.window
        val decor = window.decorView

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        window.navigationBarDividerColor = Color.TRANSPARENT
        window.isStatusBarContrastEnforced = false
        window.isNavigationBarContrastEnforced = false

        val nightMask = activity.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        val dark = nightMask == android.content.res.Configuration.UI_MODE_NIGHT_YES
        WindowCompat.getInsetsController(window, decor).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }

        val content = activity.findViewById<View>(android.R.id.content) ?: return
        resolveHostBackground(activity)?.let(content::setBackgroundColor)

        // The suite home already uses YUI's Compose shell, which owns status/navigation insets.
        if (activity.javaClass.name == HOST_ACTIVITY) return

        val base = synchronized(originalPadding) {
            originalPadding.getOrPut(content) {
                BasePadding(content.paddingLeft, content.paddingTop, content.paddingRight, content.paddingBottom)
            }
        }

        ViewCompat.setOnApplyWindowInsetsListener(content) { view, insets ->
            val types = WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            val bars = insets.getInsets(types)
            view.setPadding(
                base.left + bars.left,
                base.top + bars.top,
                base.right + bars.right,
                base.bottom + bars.bottom,
            )

            // Children receive IME/gesture/etc. insets, but system bars are now centrally handled.
            WindowInsetsCompat.Builder(insets)
                .setInsets(types, Insets.NONE)
                .build()
        }
        ViewCompat.requestApplyInsets(content)
    }

    private fun resolveHostBackground(activity: Activity): Int? {
        val host = ContextThemeWrapper(activity, R.style.Theme_YSuite)
        val value = TypedValue()
        if (!host.theme.resolveAttribute(android.R.attr.colorBackground, value, true)) return null
        return if (value.resourceId != 0) {
            runCatching { host.getColor(value.resourceId) }.getOrNull()
        } else {
            value.data
        }
    }
}
