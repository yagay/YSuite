package com.yagay.ysuite.productui

import androidx.compose.ui.unit.dp

/**
 * Product-layout geometry shared by mature workspace adaptations.
 *
 * Product types may use different pane widths, but the values live here so responsive behavior
 * remains reviewable and consistent instead of being scattered through feature/workspace code.
 */
object ProductLayoutTokens {
    val DashboardContentMaxWidth = 1280.dp
    val ToolContentMaxWidth = 880.dp
    val DetailContentMaxWidth = 900.dp

    val SettingsContentMaxWidth = 760.dp
    val SettingsCategoryPaneWidth = 264.dp

    val FileLocationPaneWidth = 280.dp
    val FileDetailPaneWidth = 360.dp

    val BrowserTabPaneWidth = 248.dp
    val LogDetailPaneWidth = 380.dp

    val TaskCategoryPaneWidth = 240.dp
    val TaskDetailPaneWidth = 360.dp

    val AutomationLibraryPaneWidth = 300.dp
    val AutomationInspectorPaneWidth = 360.dp

    val EntityNavigationPaneWidth = 248.dp
    val EntityDetailPaneWidth = 380.dp
}
