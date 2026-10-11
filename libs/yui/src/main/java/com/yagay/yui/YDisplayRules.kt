package com.yagay.yui

/**
 * The single resolved display contract for Compose, Android Views and product
 * workspaces. User values remain intact in YAppearanceStore; only the effective
 * values for the current window are bounded to protect readable content.
 *
 * Never repeat these fitting calculations in a product module or renderer.
 */
/** Fitting math is defined once and reused by both window and native helpers. */
object YDisplayMath {
    @JvmStatic
    fun boundedHorizontalInset(requested: Int, availableWidth: Int, minContent: Int): Int =
        requested.coerceIn(0, ((availableWidth - minContent).coerceAtLeast(0) / 2))
}

data class YDisplayRules(
    val windowWidthDp: Int,
    val appearance: YAppearance,
    val button: YButtonGeometry,
    val switch: YSwitchGeometry,
    val touchTargetDp: Int,
    val pageHorizontalPaddingDp: Int,
    val pageVerticalPaddingDp: Int,
    val sectionGapDp: Int,
    val controlGapDp: Int,
    val cardPaddingDp: Int,
    val cardRadiusDp: Int,
    val cardOutlineWidthDp: Int,
    val rowHeightDp: Int,
    val rowHorizontalPaddingDp: Int,
    val rowVerticalPaddingDp: Int,
    val inputHeightDp: Int,
    val fieldRadiusDp: Int,
    val dialogRadiusDp: Int,
    val navigationRadiusDp: Int,
    val toolbarHeightDp: Int,
    val iconSizeDp: Int,
    val listIconSizeDp: Int,
    val fontScale: Float,
) {
    fun spacing(baseDp: Float, defaultGapDp: Float): Float =
        if (defaultGapDp > 0f) baseDp * (controlGapDp / defaultGapDp) else baseDp
}

/**
 * Same arithmetic for both UI toolkits. windowWidthDp must be the available
 * display width, not a product-specific pane width; nested panels may apply
 * their own structural constraints after the global visual rules are resolved.
 */
fun YAppearance.displayRules(windowWidthDp: Int): YDisplayRules {
    val width = windowWidthDp.coerceAtLeast(1)
    val screen = YDisplayMath.boundedHorizontalInset(screenPaddingDp, width, 240)
    val card = YDisplayMath.boundedHorizontalInset(cardPaddingDp, width - 2 * screen, 192)
    val row = YDisplayMath.boundedHorizontalInset(rowHorizontalPaddingDp,
        width - 2 * screen - 2 * card, 128)
    val buttonInset = YDisplayMath.boundedHorizontalInset(buttonPaddingHorizontalDp, width, 104)
    val switchSlot = switchSlotWidthDp.coerceAtMost(
        (width - 2 * screen - 4 * card - 88).coerceAtLeast(56),
    )
    val touch = iconTouchTargetDp.coerceAtMost((width / 4).coerceAtLeast(48))
    val listIcon = listIconSizeDp.coerceAtMost((width / 4).coerceAtLeast(48))
    val effective = copy(
        screenPaddingDp = screen,
        cardPaddingDp = card,
        rowHorizontalPaddingDp = row,
        buttonPaddingHorizontalDp = buttonInset,
        switchSlotWidthDp = switchSlot,
        iconTouchTargetDp = touch,
        listIconSizeDp = listIcon,
    )
    return YDisplayRules(
        windowWidthDp = width,
        appearance = effective,
        button = effective.buttonGeometry(),
        switch = effective.switchGeometry(),
        touchTargetDp = maxOf(48, touch),
        pageHorizontalPaddingDp = screen,
        pageVerticalPaddingDp = effective.pageVerticalPaddingDp,
        sectionGapDp = effective.sectionSpacingDp,
        controlGapDp = effective.effectiveGapDp,
        cardPaddingDp = card,
        cardRadiusDp = effective.cardRadiusDp,
        cardOutlineWidthDp = effective.cardOutlineWidthDp,
        rowHeightDp = effective.rowHeightDp,
        rowHorizontalPaddingDp = row,
        rowVerticalPaddingDp = effective.rowVerticalPaddingDp,
        inputHeightDp = effective.inputHeightDp,
        fieldRadiusDp = effective.fieldRadiusDp,
        dialogRadiusDp = effective.dialogRadiusDp,
        navigationRadiusDp = effective.navRadiusDp,
        toolbarHeightDp = effective.toolbarHeightDp,
        iconSizeDp = effective.iconVisualSizeDp,
        listIconSizeDp = listIcon,
        fontScale = effective.fontPercent / 100f,
    )
}
