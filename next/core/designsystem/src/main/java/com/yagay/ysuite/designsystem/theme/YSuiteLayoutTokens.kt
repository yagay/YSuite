package com.yagay.ysuite.designsystem.theme

import androidx.compose.ui.unit.dp
import com.yagay.yui.YDimens

/** Breakpoints and shared maxima are owned by YUI; product-specific pane widths remain local. */
object YSuiteLayoutTokens {
    val CompactBreakpoint = YDimens.MediumBreakpoint
    val ExpandedBreakpoint = YDimens.ExpandedBreakpoint
    val CompactMaxWidth = YDimens.MediumBreakpoint - 1.dp
    val MediumMaxWidth = YDimens.ExpandedBreakpoint - 1.dp
    val ContentMaxWidth = YDimens.ContentMaxWidth
    val DetailMaxWidth = YDimens.FormMaxWidth
    val NavigationPaneWidth = 280.dp
}
