package com.yagay.yui

import android.content.Context

/**
 * Shared high-contrast tokens for transient overlay/result surfaces.
 *
 * Normal app screens must use YTheme/YViewLayout. This object exists only for overlays whose
 * contrast must remain independent from page cards, while still sharing one implementation.
 */
object YOverlayTokens {
    @JvmStatic fun dark(context: Context): Boolean = YView.isDark(context)

    @JvmStatic fun background(context: Context): Int = YView.background(context)
    @JvmStatic fun surface(context: Context): Int = YView.surfaceContainer(context)
    @JvmStatic fun surfaceAlt(context: Context): Int =
        YView.color(context, com.google.android.material.R.attr.colorSurfaceContainerHigh, surface(context))
    @JvmStatic fun textPrimary(context: Context): Int = YView.onSurface(context)
    @JvmStatic fun textSecondary(context: Context): Int = YView.onSurfaceVariant(context)
    @JvmStatic fun outline(context: Context): Int = YView.outline(context)
    @JvmStatic fun success(context: Context): Int = YView.success(context)
    @JvmStatic fun warning(context: Context): Int = YView.warning(context)
    @JvmStatic fun successSurface(context: Context): Int = YView.successContainer(context)
    @JvmStatic fun warningSurface(context: Context): Int = YView.warningContainer(context)
    @JvmStatic fun accent(context: Context): Int = YView.accent(context)
    @JvmStatic fun ripple(context: Context): Int =
        YView.color(context, android.R.attr.colorControlHighlight, if (dark(context)) 0x33FFFFFF else 0x22000000)

    @JvmStatic fun menuSurface(context: Context): Int =
        if (dark(context)) 0xFF2B2B2B.toInt() else 0xFFF8F8F8.toInt()
    @JvmStatic fun menuPrimaryText(context: Context): Int =
        if (dark(context)) 0xFFF5F5F5.toInt() else 0xFF202124.toInt()
    @JvmStatic fun menuSecondaryText(context: Context): Int =
        if (dark(context)) 0xFFB8B8B8.toInt() else 0xFF5F6368.toInt()
    @JvmStatic fun menuRipple(context: Context): Int =
        if (dark(context)) 0x33FFFFFF else 0x22000000
    @JvmStatic fun resultSurface(context: Context): Int =
        if (dark(context)) 0xF0202124.toInt() else 0xF8FFFFFF.toInt()

    @JvmStatic fun dp(context: Context, value: Float): Int =
        Math.round(value * context.resources.displayMetrics.density)
}
