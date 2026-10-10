package com.yagay.ysuite.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import com.yagay.yui.LocalYAppearance
import com.yagay.yui.YDimens

/**
 * One spacing source for every rebuilt product workspace.
 * The public compatibility names stay stable, but values are resolved from the YUI
 * appearance on every composition rather than a second static design-system palette.
 */
object YSuiteSpacing {
    @Composable
    private fun scale(): Float = LocalYAppearance.current.effectiveGapDp / YDimens.ControlGap.value

    val XSmall: Dp @Composable get() = YDimens.SpacingXsmall * scale()
    val Small: Dp @Composable get() = YDimens.SpacingSmall * scale()
    val Medium: Dp @Composable get() = YDimens.SpacingMedium * scale()
    val Large: Dp @Composable get() = YDimens.SpacingLarge * scale()
    val XLarge: Dp @Composable get() = YDimens.SpacingXlarge * scale()
}
