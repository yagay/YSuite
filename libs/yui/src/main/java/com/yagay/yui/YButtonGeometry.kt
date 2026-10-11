package com.yagay.yui

/**
 * The single YUI sizing contract for all ordinary action and icon buttons.
 *
 * visualHeightDp is the colored shape size. The measured hit target may be
 * larger, but must never silently force the visible button back to 48dp.
 * A button style (filled/outlined/text/icon) is a semantic variant, not an
 * independent source of geometry.
 */
data class YButtonGeometry(
    val visualHeightDp: Int,
    val touchHeightDp: Int,
    val iconTouchDp: Int,
    val backgroundInsetDp: Float,
    val horizontalPaddingDp: Int,
    val verticalPaddingDp: Int,
    val cornerRadiusDp: Int,
    val iconVisualSizeDp: Int,
)

fun YAppearance.buttonGeometry(): YButtonGeometry {
    val visual = buttonHeightDp.coerceAtLeast(1)
    val touch = maxOf(48, visual)
    return YButtonGeometry(
        visualHeightDp = visual,
        touchHeightDp = touch,
        iconTouchDp = maxOf(48, iconTouchTargetDp, visual),
        backgroundInsetDp = (touch - visual) / 2f,
        horizontalPaddingDp = buttonPaddingHorizontalDp,
        verticalPaddingDp = buttonVerticalPaddingDp,
        cornerRadiusDp = buttonRadiusDp,
        iconVisualSizeDp = iconVisualSizeDp,
    )
}
