package com.yagay.ysuite.designsystem.theme

import androidx.compose.ui.unit.dp

object YSuiteLayoutTokens {
    val CompactBreakpoint = 600.dp
    val ExpandedBreakpoint = 840.dp

    // Compatibility maxima for callers that need an inclusive threshold.
    val CompactMaxWidth = 599.dp
    val MediumMaxWidth = 839.dp

    val ContentMaxWidth = 960.dp
    val DetailMaxWidth = 760.dp
    val NavigationPaneWidth = 280.dp
}
