package com.yagay.yui

import android.app.Activity
import android.app.Application
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.startup.Initializer
import java.util.WeakHashMap

class YUiInitializer : Initializer<Unit> {
    override fun create(context: Context) {
        val application = context.applicationContext as? Application ?: return
        YUiRuntime.install(application)
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}

object YUiRuntime {
    private data class BasePadding(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
    )

    private val originalPadding = WeakHashMap<View, BasePadding>()
    private var installed = false

    @JvmStatic
    fun install(application: Application) {
        if (installed) return
        installed = true
        // Do not auto-apply system dynamic colors to only the View side of a mixed UI.
        // The shared Material 3 scheme is applied by theme resources and YTheme.
        application.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = schedule(activity)
                override fun onActivityResumed(activity: Activity) = schedule(activity)
                override fun onActivityStarted(activity: Activity) = Unit
                override fun onActivityPaused(activity: Activity) = Unit
                override fun onActivityStopped(activity: Activity) = Unit
                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
                override fun onActivityDestroyed(activity: Activity) = Unit
            },
        )
    }

    private fun excluded(activity: Activity): Boolean = activity is YUiWindowOptOut

    private fun schedule(activity: Activity) {
        if (excluded(activity)) return
        activity.window.decorView.post { apply(activity) }
    }

    private fun apply(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed || excluded(activity)) return

        val window = activity.window
        val decor = window.decorView
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= 28) window.navigationBarDividerColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= 29) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }

        val dark = YView.isDark(activity)
        WindowCompat.getInsetsController(window, decor).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }

        val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        YView.applyRoot(content)
        // Never mutate component sizes/backgrounds after inflation. Material 3 owns widgets.

        // Compose owns its insets through YScaffold/Scaffold. Traditional View Activities receive
        // exactly one system-bar padding layer here so every standalone app and YSuite behave alike.
        if (containsComposeView(content)) {
            ViewCompat.setOnApplyWindowInsetsListener(content, null)
            return
        }

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
            WindowInsetsCompat.Builder(insets).setInsets(types, Insets.NONE).build()
        }
        ViewCompat.requestApplyInsets(content)
    }

    private fun containsComposeView(view: View): Boolean {
        if (view is ComposeView) return true
        if (view !is ViewGroup) return false
        for (i in 0 until view.childCount) {
            if (containsComposeView(view.getChildAt(i))) return true
        }
        return false
    }

}
