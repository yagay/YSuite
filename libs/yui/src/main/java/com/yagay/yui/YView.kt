package com.yagay.yui

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.color.MaterialColors

/** Shared View-system renderer for Java/legacy YSuite modules. */
object YView {
    @JvmStatic
    fun applyComposeWindow(activity: Activity) {
        val window = activity.window
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= 28) window.navigationBarDividerColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= 29) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }
        applyBarAppearance(activity)
    }

    @JvmStatic
    fun applyViewWindow(activity: Activity) {
        applyComposeWindow(activity)
        val content = activity.findViewById<View>(android.R.id.content) ?: return
        val left = content.paddingLeft
        val top = content.paddingTop
        val right = content.paddingRight
        val bottom = content.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(content) { view, insets ->
            val types = WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            val bars = insets.getInsets(types)
            view.setPadding(left + bars.left, top + bars.top, right + bars.right, bottom + bars.bottom)
            WindowInsetsCompat.Builder(insets).setInsets(types, Insets.NONE).build()
        }
        ViewCompat.requestApplyInsets(content)
    }

    @JvmStatic fun applyRoot(view: View) {
        view.setBackgroundColor(color(view.context, com.google.android.material.R.attr.colorSurface, Color.WHITE))
    }

    @JvmStatic fun styleTitle(view: TextView) {
        view.textSize = 21f
        view.setTextColor(onSurface(view.context))
        view.setTypeface(view.typeface, android.graphics.Typeface.BOLD)
    }

    @JvmStatic fun styleBody(view: TextView) {
        view.textSize = 14f
        view.setTextColor(onSurfaceVariant(view.context))
    }

    @JvmStatic fun stylePrimaryButton(button: Button) {
        button.minHeight = dp(button.context, 44)
        button.isAllCaps = false
    }

    @JvmStatic fun styleSecondaryButton(button: Button) = stylePrimaryButton(button)

    @JvmStatic fun cardBackground(context: Context): GradientDrawable = GradientDrawable().apply {
        setColor(surfaceContainer(context))
        cornerRadius = dp(context, 18).toFloat()
    }

    @JvmStatic fun styleCard(view: ViewGroup) {
        view.background = cardBackground(view.context)
        val p = dp(view.context, 16)
        view.setPadding(p, p, p, p)
    }

    @JvmStatic fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    @JvmStatic fun color(context: Context, attr: Int, fallback: Int): Int = MaterialColors.getColor(context, attr, fallback)
    @JvmStatic fun background(context: Context): Int = color(context, com.google.android.material.R.attr.colorSurface, Color.WHITE)
    @JvmStatic fun surface(context: Context): Int = color(context, com.google.android.material.R.attr.colorSurfaceContainer, 0xFFF5F6F8.toInt())
    @JvmStatic fun surfaceContainer(context: Context): Int = surface(context)
    @JvmStatic fun onSurface(context: Context): Int = color(context, com.google.android.material.R.attr.colorOnSurface, Color.BLACK)
    @JvmStatic fun onSurfaceVariant(context: Context): Int = color(context, com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFF656A73.toInt())
    @JvmStatic fun outline(context: Context): Int = color(context, com.google.android.material.R.attr.colorOutlineVariant, 0xFFD0D5DD.toInt())
    @JvmStatic fun accent(context: Context): Int = color(context, com.google.android.material.R.attr.colorPrimary, 0xFF6750A4.toInt())

    private fun applyBarAppearance(activity: Activity) {
        val mask = activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val dark = mask == Configuration.UI_MODE_NIGHT_YES
        WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
}
