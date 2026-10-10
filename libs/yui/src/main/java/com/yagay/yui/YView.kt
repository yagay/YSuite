package com.yagay.yui

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.content.res.ColorStateList
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
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.toArgb
import java.util.WeakHashMap

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
        view.setBackgroundColor(background(view.context))
    }

    @JvmStatic fun styleTitle(view: TextView) = stylePageTitle(view)

    @JvmStatic fun stylePageTitle(view: TextView) {
        TextViewCompat.setTextAppearance(view, R.style.TextAppearance_YUI_PageTitle)
        applyFontScale(view)
        view.setTextColor(onSurface(view.context))
    }

    @JvmStatic fun styleSectionTitle(view: TextView) {
        TextViewCompat.setTextAppearance(view, R.style.TextAppearance_YUI_SectionTitle)
        applyFontScale(view)
        view.setTextColor(onSurface(view.context))
    }

    @JvmStatic fun styleItemTitle(view: TextView) {
        TextViewCompat.setTextAppearance(view, R.style.TextAppearance_YUI_ItemTitle)
        applyFontScale(view)
        view.setTextColor(onSurface(view.context))
    }

    @JvmStatic fun styleBody(view: TextView) {
        TextViewCompat.setTextAppearance(view, R.style.TextAppearance_YUI_Body)
        applyFontScale(view)
        view.setTextColor(onSurfaceVariant(view.context))
    }

    @JvmStatic fun styleStrongBody(view: TextView) {
        TextViewCompat.setTextAppearance(view, R.style.TextAppearance_YUI_Body_Strong)
        applyFontScale(view)
        view.setTextColor(onSurface(view.context))
    }

    @JvmStatic fun styleCaption(view: TextView) {
        TextViewCompat.setTextAppearance(view, R.style.TextAppearance_YUI_Caption)
        applyFontScale(view)
        view.setTextColor(onSurfaceVariant(view.context))
    }

    @JvmStatic fun styleLabel(view: TextView) {
        TextViewCompat.setTextAppearance(view, R.style.TextAppearance_YUI_Label)
        applyFontScale(view)
        view.setTextColor(onSurface(view.context))
    }

    /** Both View button variants consume the same contrast-safe accent and geometry as Compose. */
    @JvmStatic fun stylePrimaryButton(button: Button) = styleButton(button, outlined = false)
    @JvmStatic fun styleSecondaryButton(button: Button) = styleButton(button, outlined = true)

    private fun styleButton(button: Button, outlined: Boolean) {
        val context = button.context
        val appearance = appearance(context)
        val colors = palette(context)
        button.minHeight = dp(context, appearance.buttonHeightDp)
        button.isAllCaps = false
        button.minWidth = 0
        button.setPadding(dp(context, appearance.buttonPaddingHorizontalDp), dp(context, appearance.buttonVerticalPaddingDp),
            dp(context, appearance.buttonPaddingHorizontalDp), dp(context, appearance.buttonVerticalPaddingDp))
        button.backgroundTintList = ColorStateList.valueOf(
            if (outlined) Color.TRANSPARENT else colors.primary.toArgb(),
        )
        button.setTextColor(if (outlined) colors.primary.toArgb() else colors.onPrimary.toArgb())
        if (button is MaterialButton) {
            button.cornerRadius = dp(context, appearance.buttonRadiusDp)
            button.insetTop = dimen(context, R.dimen.yui_button_inset_vertical)
            button.insetBottom = dimen(context, R.dimen.yui_button_inset_vertical)
            if (outlined) {
                button.strokeColor = ColorStateList.valueOf(colors.outline.toArgb())
                button.strokeWidth = maxOf(button.strokeWidth, dp(context, 1))
            }
        }
    }

    @JvmStatic fun cardBackground(context: Context): GradientDrawable = GradientDrawable().apply {
        setColor(surfaceContainer(context))
        cornerRadius = dp(context, YAppearanceStore(context).appearance(YAppearanceStore.moduleIdFor(context)).cardRadiusDp).toFloat()
    }

    @JvmStatic fun fieldBackground(context: Context): GradientDrawable = GradientDrawable().apply {
        setColor(surfaceContainer(context))
        cornerRadius = dp(context, appearance(context).fieldRadiusDp).toFloat()
        setStroke(dp(context, 1), outline(context))
    }

    @JvmStatic fun styleCard(view: ViewGroup) {
        view.background = cardBackground(view.context)
        val p = dp(view.context, YAppearanceStore(view.context).appearance(YAppearanceStore.moduleIdFor(view.context)).cardPaddingDp)
        view.setPadding(p, p, p, p)
    }

    @JvmStatic fun dimen(context: Context, @DimenRes resource: Int): Int =
        context.resources.getDimensionPixelSize(resource)

    @JvmStatic fun screenHorizontal(context: Context): Int =
        dp(context, YAppearanceStore(context).appearance(YAppearanceStore.moduleIdFor(context)).screenPaddingDp)
    @JvmStatic fun screenVertical(context: Context): Int = dimen(context, R.dimen.yui_screen_vertical)
    @JvmStatic fun sectionGap(context: Context): Int =
        dp(context, YAppearanceStore(context).appearance(YAppearanceStore.moduleIdFor(context)).sectionSpacingDp)
    @JvmStatic fun controlGap(context: Context): Int =
        dp(context, YAppearanceStore(context).appearance(YAppearanceStore.moduleIdFor(context)).effectiveGapDp)
    @JvmStatic fun cardRadius(context: Context): Int =
        dp(context, YAppearanceStore(context).appearance(YAppearanceStore.moduleIdFor(context)).cardRadiusDp)
    @JvmStatic fun cardPadding(context: Context): Int =
        dp(context, YAppearanceStore(context).appearance(YAppearanceStore.moduleIdFor(context)).cardPaddingDp)
    @JvmStatic fun touchTarget(context: Context): Int = dp(context, appearance(context).iconTouchTargetDp)
    @JvmStatic fun rowHeight(context: Context): Int = dp(context, appearance(context).rowHeightDp)
    @JvmStatic fun rowHorizontalPadding(context: Context): Int = dp(context, appearance(context).rowHorizontalPaddingDp)
    @JvmStatic fun rowVerticalPadding(context: Context): Int = dp(context, appearance(context).rowVerticalPaddingDp)
    @JvmStatic fun dialogRadius(context: Context): Int = dp(context, appearance(context).dialogRadiusDp)
    @JvmStatic fun fontPercent(context: Context): Int = appearance(context).fontPercent
    @JvmStatic fun buttonHeight(context: Context): Int =
        dp(context, YAppearanceStore(context).appearance(YAppearanceStore.moduleIdFor(context)).buttonHeightDp)

    /** Compatibility helper for task-specific overlays; normal-screen geometry must use generated dimen resources. */
    @JvmStatic fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    private fun appearance(context: Context): YAppearance =
        YAppearanceStore(context).appearance(YAppearanceStore.moduleIdFor(context))

    private fun applyFontScale(view: TextView) {
        val percent = fontPercent(view.context)
        if (percent != 100) view.setTextSize(
            android.util.TypedValue.COMPLEX_UNIT_PX, view.textSize * (percent / 100f),
        )
    }

    private data class PaletteEntry(
        val settings: YAppearance,
        val dark: Boolean,
        val scheme: ColorScheme,
    )
    // Color schemes are immutable; keep one per active Context/appearance to avoid repeatedly
    // resolving Android dynamic colors during RecyclerView binding and overlay drawing.
    private val paletteCache = WeakHashMap<Context, PaletteEntry>()

    /** The same scheme, accents, and module overrides used by the Compose renderer. */
    private fun palette(context: Context): ColorScheme {
        val setting = appearance(context)
        val dark = when (setting.theme) {
            "dark" -> true
            "light" -> false
            else -> (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        }
        synchronized(paletteCache) {
            paletteCache[context]?.takeIf { it.settings == setting && it.dark == dark }
                ?.let { return it.scheme }
        }
        val base = when {
            setting.dynamicColor && Build.VERSION.SDK_INT >= 31 && dark -> dynamicDarkColorScheme(context)
            setting.dynamicColor && Build.VERSION.SDK_INT >= 31 -> dynamicLightColorScheme(context)
            dark -> YDarkColors
            else -> YLightColors
        }
        val scheme = yAccentColorScheme(base, setting.accent, dark)
        synchronized(paletteCache) { paletteCache[context] = PaletteEntry(setting, dark, scheme) }
        return scheme
    }

    @JvmStatic fun color(context: Context, attr: Int, fallback: Int): Int {
        val p = palette(context)
        return when (attr) {
            com.google.android.material.R.attr.colorSurface -> p.surface.toArgb()
            com.google.android.material.R.attr.colorSurfaceContainer -> p.surfaceContainer.toArgb()
            com.google.android.material.R.attr.colorSurfaceContainerHigh -> p.surfaceContainerHigh.toArgb()
            com.google.android.material.R.attr.colorOnSurface -> p.onSurface.toArgb()
            com.google.android.material.R.attr.colorOnSurfaceVariant -> p.onSurfaceVariant.toArgb()
            com.google.android.material.R.attr.colorOutlineVariant -> p.outlineVariant.toArgb()
            com.google.android.material.R.attr.colorOutline -> p.outline.toArgb()
            androidx.appcompat.R.attr.colorPrimary -> p.primary.toArgb()
            android.R.attr.colorError -> p.error.toArgb()
            com.google.android.material.R.attr.colorTertiary -> p.tertiary.toArgb()
            else -> MaterialColors.getColor(context, attr, fallback)
        }
    }
    @JvmStatic fun background(context: Context): Int = palette(context).surface.toArgb()
    @JvmStatic fun surface(context: Context): Int = palette(context).surfaceContainer.toArgb()
    @JvmStatic fun surfaceContainer(context: Context): Int = surface(context)
    @JvmStatic fun onSurface(context: Context): Int = palette(context).onSurface.toArgb()
    @JvmStatic fun onSurfaceVariant(context: Context): Int = palette(context).onSurfaceVariant.toArgb()
    @JvmStatic fun outline(context: Context): Int = palette(context).outlineVariant.toArgb()
    @JvmStatic fun accent(context: Context): Int = palette(context).primary.toArgb()
    @JvmStatic fun success(context: Context): Int =
        (if (isDark(context)) YUiPalette.DarkSuccess else YUiPalette.LightSuccess).toArgb()
    @JvmStatic fun successContainer(context: Context): Int =
        (if (isDark(context)) YUiPalette.DarkSuccessContainer else YUiPalette.LightSuccessContainer).toArgb()
    @JvmStatic fun warning(context: Context): Int =
        (if (isDark(context)) YUiPalette.DarkWarning else YUiPalette.LightWarning).toArgb()
    @JvmStatic fun warningContainer(context: Context): Int =
        (if (isDark(context)) YUiPalette.DarkWarningContainer else YUiPalette.LightWarningContainer).toArgb()
    @JvmStatic fun info(context: Context): Int =
        (if (isDark(context)) YUiPalette.DarkInfo else YUiPalette.LightInfo).toArgb()

    @JvmStatic fun isDark(context: Context): Boolean = when (appearance(context).theme) {
        "dark" -> true
        "light" -> false
        else -> {
            val mask = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            mask == Configuration.UI_MODE_NIGHT_YES
        }
    }

    private fun applyBarAppearance(activity: Activity) {
        val dark = isDark(activity)
        WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
}
