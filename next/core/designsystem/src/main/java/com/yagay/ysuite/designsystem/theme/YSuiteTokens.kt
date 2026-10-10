package com.yagay.ysuite.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yagay.yui.LocalYAppearance

/**
 * One spacing source for every rebuilt product workspace.
 * The public compatibility names stay stable, but values are resolved from the YUI
 * appearance on every composition rather than a second static design-system palette.
 */
object YSuiteSpacing {
    @Composable
    private fun gap(): Float = LocalYAppearance.current.effectiveGapDp.toFloat()

    val XSmall: Dp @Composable get() = (gap() / 3f).dp
    val Small: Dp @Composable get() = (gap() * 2f / 3f).dp
    val Medium: Dp @Composable get() = (gap() * 4f / 3f).dp
    val Large: Dp @Composable get() = (gap() * 2f).dp
    val XLarge: Dp @Composable get() = (gap() * 8f / 3f).dp
}
