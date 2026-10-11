package com.yagay.ysuite.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import com.yagay.yui.YDimens
import com.yagay.yui.YUiGeometry

/**
 * Compatibility aliases only: spacing scales exclusively in YUiGeometry.
 * No independent sizing algorithm exists in the rebuilt design-system layer.
 */
object YSuiteSpacing {
    val XSmall: Dp @Composable get() = YUiGeometry.spacing(YDimens.SpacingXsmall)
    val Small: Dp @Composable get() = YUiGeometry.spacing(YDimens.SpacingSmall)
    val Medium: Dp @Composable get() = YUiGeometry.spacing(YDimens.SpacingMedium)
    val Large: Dp @Composable get() = YUiGeometry.spacing(YDimens.SpacingLarge)
    val XLarge: Dp @Composable get() = YUiGeometry.spacing(YDimens.SpacingXlarge)
}
