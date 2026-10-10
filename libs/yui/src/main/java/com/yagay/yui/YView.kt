package com.yagay.yui

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.annotation.DimenRes
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.TextViewCompat
import com.google.android.material.color.MaterialColors
import com.google.android.material.button.MaterialButton

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

    @JvmStatic fun styleTitle(view: TextView) = stylePageTitle(view)

    @JvmStatic fun stylePageTitle(view: TextView) {
        TextViewCompat.setTextAppearance(view, R.style.TextAppearance_YUI_PageTitle)
        view.setTextColor(onSurface(view.context))
    }

    @JvmStatic fun styleSectionTitle(view: TextView) {
        TextViewCompat.setTextAppearance(view, R.style.TextAppearance_YUI_SectionTitle)
        view.setTextColor(onSurface(view.context))
    }

    @JvmStatic fun styleItemTitle(view: TextView) {
        TextViewCompat.setTextAppearance(view, R.style.TextAppearance_YUI_ItemTitle)
        view.setTextColor(onSurface(view.context))
    }

    @JvmStatic fun styleBody(view: TextView) {
        TextViewCompat.setTextAppearance(view, R.style.TextAppearance_YUI_Body)
        view.setTextColor(onSurfaceVariant(view.context))
    }

    @JvmStatic fun styleStrongBody(view: TextView) {
        TextViewCompat.setTextAppearance(view, R.style.TextAppearance_YUI_Body_Strong)
        view.setTextColor(onSurface(view.context))
    }

    @JvmStatic fun styleCaption(view: TextView) {
        TextViewCompat.setTextAppearance(view, R.style.TextAppearance_YUI_Caption)
        view.setTextColor(onSurfaceVariant(view.context))
    }

    @JvmStatic fun styleLabel(view: TextView) {
        TextViewCompat.setTextAppearance(view, R.style.TextAppearance_YUI_Label)
        view.setTextColor(onSurface(view.context))
    }

    /** Keep a 48dp clickable View but draw its Material background with tighter insets. */
    @JvmStatic fun stylePrimaryButton(button: Button) {
        val context = button.context
        button.minHeight = dimen(context, R.dimen.yui_button_height)
        button.isAllCaps = false
        button.minWidth = 0
        button.setPadding(dimen(context, R.dimen.yui_button_padding_horizontal), 0, dimen(context, R.dimen.yui_button_padding_horizontal), 0)
        if (button is MaterialButton) {
            button.insetTop = dimen(context, R.dimen.yui_button_inset_vertical)
            button.insetBottom = dimen(context, R.dimen.yui_button_inset_vertical)
            button.cornerRadius = dimen(context, R.dimen.yui_button_radius)
        }
    }

    @JvmStatic fun styleSecondaryButton(button: Button) = stylePrimaryButton(button)

    @JvmStatic fun cardBackground(context: Context): GradientDrawable = GradientDrawable().apply {
        setColor(surfaceContainer(context))
        cornerRadius = dimen(context, R.dimen.yui_card_radius).toFloat()
    }

    @JvmStatic fun fieldBackground(context: Context): GradientDrawable = GradientDrawable().apply {
        setColor(surfaceContainer(context))
        cornerRadius = dimen(context, R.dimen.yui_field_radius).toFloat()
        setStroke(dp(context, 1), outline(context))
    }

    @JvmStatic fun styleCard(view: ViewGroup) {
        view.background = cardBackground(view.context)
        val p = dimen(view.context, R.dimen.yui_card_padding)
        view.setPadding(p, p, p, p)
    }

    @JvmStatic fun dimen(context: Context, @DimenRes resource: Int): Int =
        context.resources.getDimensionPixelSize(resource)

    @JvmStatic fun screenHorizontal(context: Context): Int = dimen(context, R.dimen.yui_screen_horizontal)
    @JvmStatic fun screenVertical(context: Context): Int = dimen(context, R.dimen.yui_screen_vertical)
    @JvmStatic fun sectionGap(context: Context): Int = dimen(context, R.dimen.yui_section_gap)
    @JvmStatic fun controlGap(context: Context): Int = dimen(context, R.dimen.yui_control_gap)
    @JvmStatic fun cardRadius(context: Context): Int = dimen(context, R.dimen.yui_card_radius)
    @JvmStatic fun cardPadding(context: Context): Int = dimen(context, R.dimen.yui_card_padding)
    @JvmStatic fun touchTarget(context: Context): Int = dimen(context, R.dimen.yui_touch_target)
    @JvmStatic fun buttonHeight(context: Context): Int = dimen(context, R.dimen.yui_button_height)

    /** Compatibility helper for task-specific overlays; normal-screen geometry must use generated dimen resources. */
    @JvmStatic fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    @JvmStatic fun color(context: Context, attr: Int, fallback: Int): Int = MaterialColors.getColor(context, attr, fallback)
    @JvmStatic fun background(context: Context): Int = color(context, com.google.android.material.R.attr.colorSurface, Color.WHITE)
    @JvmStatic fun surface(context: Context): Int = color(context, com.google.android.material.R.attr.colorSurfaceContainer, 0xFFF5F6F8.toInt())
    @JvmStatic fun surfaceContainer(context: Context): Int = surface(context)
    @JvmStatic fun onSurface(context: Context): Int = color(context, com.google.android.material.R.attr.colorOnSurface, Color.BLACK)
    @JvmStatic fun onSurfaceVariant(context: Context): Int = color(context, com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFF656A73.toInt())
    @JvmStatic fun outline(context: Context): Int = color(context, com.google.android.material.R.attr.colorOutlineVariant, 0xFFD0D5DD.toInt())
    @JvmStatic fun accent(context: Context): Int = color(context, androidx.appcompat.R.attr.colorPrimary, context.getColor(R.color.yui_palette_primary))

    @JvmStatic fun success(context: Context): Int = if (isDark(context)) 0xFF9BDAA8.toInt() else 0xFF146C2E.toInt()
    @JvmStatic fun successContainer(context: Context): Int = if (isDark(context)) 0xFF005321.toInt() else 0xFFB7F2C4.toInt()
    @JvmStatic fun warning(context: Context): Int = if (isDark(context)) 0xFFFFB95F.toInt() else 0xFF8A4D00.toInt()
    @JvmStatic fun warningContainer(context: Context): Int = if (isDark(context)) 0xFF693900.toInt() else 0xFFFFDDB8.toInt()
    @JvmStatic fun info(context: Context): Int = if (isDark(context)) 0xFFA7C8FF.toInt() else 0xFF285F9E.toInt()

    @JvmStatic fun isDark(context: Context): Boolean = YAppearanceSettings.isDark(context)

    private fun applyBarAppearance(activity: Activity) {
        val dark = isDark(activity)
        WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
}
