package com.yagay.yui

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
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

    /**
     * One shared YUI button binding: both normal and outlined MaterialButtons use
     * the same geometry and live appearance subscriptions. This includes RecyclerView
     * settings/action buttons, which must update without rebuilding the adapter.
     */
    private val boundButtons = WeakHashMap<Button, Boolean>()

    @JvmStatic fun stylePrimaryButton(button: Button) = bindButtonAppearance(button, outlined = false)
    @JvmStatic fun styleSecondaryButton(button: Button) = bindButtonAppearance(button, outlined = true)

    private fun bindButtonAppearance(button: Button, outlined: Boolean) {
        styleButton(button, outlined)
        synchronized(boundButtons) {
            if (boundButtons.putIfAbsent(button, true) != null) return
        }
        val store = YAppearanceStore(button.context)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key.startsWith("global.") || key.startsWith("module.")) {
                button.post {
                    if (button.isAttachedToWindow) styleButton(button, outlined)
                }
            }
        }
        button.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                store.registerPreferenceListener(listener)
                styleButton(button, outlined)
            }
            override fun onViewDetachedFromWindow(v: View) {
                store.unregisterPreferenceListener(listener)
            }
        })
        if (button.isAttachedToWindow) store.registerPreferenceListener(listener)
    }

    private fun styleButton(button: Button, outlined: Boolean) {
        val context = button.context
        val geometry = displayRules(context).button
        val colors = palette(context)
        // Treat size as visible background size, NOT only a minimum view height.
        // Keep 48dp clickable while exposing a smaller MaterialButton background.
        styleLabel(button)
        button.minHeight = dp(context, geometry.touchHeightDp)
        button.isAllCaps = false
        // MaterialButton/AppCompat themes may force a single line or ellipsis. Let
        // the TextView measure its entire label; WRAP_CONTENT rows then grow safely.
        button.isSingleLine = false
        button.maxLines = Int.MAX_VALUE
        button.ellipsize = null
        button.setHorizontallyScrolling(false)
        button.minWidth = 0
        val horizontal = buttonHorizontalPadding(context)
        button.setPadding(horizontal, dp(context, geometry.verticalPaddingDp),
            horizontal, dp(context, geometry.verticalPaddingDp))
        button.backgroundTintList = ColorStateList.valueOf(
            if (outlined) Color.TRANSPARENT else colors.primary.toArgb(),
        )
        button.setTextColor(if (outlined) colors.primary.toArgb() else colors.onPrimary.toArgb())
        if (button is MaterialButton) {
            button.cornerRadius = dp(context, geometry.cornerRadiusDp)
            val inset = (geometry.backgroundInsetDp * context.resources.displayMetrics.density)
                .toInt().coerceAtLeast(0)
            button.insetTop = inset
            button.insetBottom = inset
            button.insetLeft = 0
            button.insetRight = 0
            // Old stroke widths must not survive after a smaller appearance value is selected.
            button.strokeColor = ColorStateList.valueOf(
                if (outlined) colors.outline.toArgb() else Color.TRANSPARENT,
            )
            button.strokeWidth = if (outlined) cardOutlineWidth(context) else 0
        }
    }

    @JvmStatic fun cardBackground(context: Context): GradientDrawable = GradientDrawable().apply {
        setColor(cardSurface(context))
        cornerRadius = dp(context, displayRules(context).cardRadiusDp).toFloat()
    }

    @JvmStatic fun fieldBackground(context: Context): GradientDrawable = GradientDrawable().apply {
        setColor(surfaceContainer(context))
        cornerRadius = dp(context, displayRules(context).fieldRadiusDp).toFloat()
        setStroke(cardOutlineWidth(context), outline(context))
    }

    @JvmStatic fun styleCard(view: ViewGroup) {
        view.background = cardBackground(view.context)
        val p = cardPadding(view.context)
        view.setPadding(p, p, p, p)
    }

    @JvmStatic fun dimen(context: Context, @DimenRes resource: Int): Int =
        context.resources.getDimensionPixelSize(resource)

    /** Render-time safety only: saved values remain fully adjustable. */
    @JvmStatic fun fitHorizontalInset(context: Context, requestedPx: Int, minContentDp: Int = 128): Int {
        val windowPx = context.resources.displayMetrics.widthPixels
        if (windowPx <= 0) return requestedPx.coerceAtLeast(0)
        return YDisplayMath.boundedHorizontalInset(
            requestedPx, windowPx, dp(context, minContentDp),
        )
    }

    /** Single final display contract; same width/bounds as YTheme on this window. */
    @JvmStatic fun displayRules(context: Context): YDisplayRules {
        val density = context.resources.displayMetrics.density.coerceAtLeast(0.01f)
        val configurationWidth = context.resources.configuration.screenWidthDp
        val windowWidth = if (configurationWidth > 0) configurationWidth
            else (context.resources.displayMetrics.widthPixels / density).toInt().coerceAtLeast(1)
        return appearance(context).displayRules(windowWidth)
    }

    @JvmStatic fun screenHorizontal(context: Context): Int =
        dp(context, displayRules(context).pageHorizontalPaddingDp)
    @JvmStatic fun screenVertical(context: Context): Int =
        dp(context, displayRules(context).pageVerticalPaddingDp)
    @JvmStatic fun sectionGap(context: Context): Int =
        dp(context, displayRules(context).sectionGapDp)
    @JvmStatic fun controlGap(context: Context): Int =
        dp(context, displayRules(context).controlGapDp)
    @JvmStatic fun cardRadius(context: Context): Int =
        dp(context, displayRules(context).cardRadiusDp)
    @JvmStatic fun cardOutlineWidth(context: Context): Int =
        dp(context, displayRules(context).cardOutlineWidthDp)
    @JvmStatic fun cardPadding(context: Context): Int =
        dp(context, displayRules(context).cardPaddingDp)
    @JvmStatic fun touchTarget(context: Context): Int =
        dp(context, displayRules(context).touchTargetDp)
    @JvmStatic fun inputHeight(context: Context): Int =
        dp(context, displayRules(context).inputHeightDp)
    @JvmStatic fun buttonRadiusDp(context: Context): Int =
        displayRules(context).button.cornerRadiusDp
    @JvmStatic fun rowHeight(context: Context): Int =
        dp(context, displayRules(context).rowHeightDp)
    @JvmStatic fun switchSlotWidth(context: Context): Int =
        dp(context, displayRules(context).appearance.switchSlotWidthDp)
    @JvmStatic fun rowHorizontalPadding(context: Context): Int =
        dp(context, displayRules(context).rowHorizontalPaddingDp)
    @JvmStatic fun buttonHorizontalPadding(context: Context): Int =
        dp(context, displayRules(context).button.horizontalPaddingDp)
    @JvmStatic fun navigationAccessoryWidth(context: Context): Int =
        minOf(touchTarget(context), maxOf(dp(context, 48), context.resources.displayMetrics.widthPixels / 5))
    @JvmStatic fun rowVerticalPadding(context: Context): Int =
        dp(context, displayRules(context).rowVerticalPaddingDp)
    @JvmStatic fun dialogRadius(context: Context): Int =
        dp(context, displayRules(context).dialogRadiusDp)
    @JvmStatic fun fontPercent(context: Context): Int =
        displayRules(context).appearance.fontPercent
    @JvmStatic fun toolbarHeight(context: Context): Int =
        dp(context, displayRules(context).toolbarHeightDp)
    @JvmStatic fun iconSize(context: Context): Int =
        dp(context, displayRules(context).iconSizeDp)
    @JvmStatic fun listIconSize(context: Context): Int =
        dp(context, displayRules(context).listIconSizeDp)
    /** Synchronise inflated row icons with the central YUI appearance settings. */
    @JvmStatic fun styleListIcon(view: android.widget.ImageView) {
        val size = listIconSize(view.context)
        val layout = view.layoutParams ?: return
        if (layout.width != size || layout.height != size) {
            layout.width = size
            layout.height = size
            view.layoutParams = layout
        }
    }
    @JvmStatic fun buttonHeight(context: Context): Int =
        dp(context, displayRules(context).button.visualHeightDp)

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
    /** Match the Compose YCard surface role without changing input fields or overlays. */
    @JvmStatic fun cardSurface(context: Context): Int = palette(context).surfaceContainerLow.toArgb()
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

    /** Single source for system bar colors in both Compose and legacy View screens. */
    @JvmStatic fun applyBarAppearance(activity: Activity, dark: Boolean) {
        WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
        if (Build.VERSION.SDK_INT >= 29) {
            activity.window.isStatusBarContrastEnforced = false
            activity.window.isNavigationBarContrastEnforced = false
        }
    }

    private fun applyBarAppearance(activity: Activity) =
        applyBarAppearance(activity, isDark(activity))
}
