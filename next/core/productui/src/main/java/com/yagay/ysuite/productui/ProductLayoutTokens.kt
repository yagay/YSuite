package com.yagay.ysuite.productui

import androidx.compose.ui.unit.dp
import com.yagay.ysuite.designsystem.theme.YSuiteLayoutTokens

/**
 * Product-layout geometry shared by mature workspace adaptations.
 *
 * Product types can still differ structurally, but side panes use a small, reviewable set of
 * canonical widths so moving between products does not cause visible pane-width jumps.
 */
object ProductLayoutTokens {
    val DashboardContentMaxWidth = 1280.dp
    val ToolContentMaxWidth = 880.dp
    val DetailContentMaxWidth = 900.dp
    val SettingsContentMaxWidth = YSuiteLayoutTokens.DetailMaxWidth

    val NavigationPaneWidth = YSuiteLayoutTokens.NavigationPaneWidth
    val CompactSidePaneWidth = 240.dp
    val DetailPaneWidth = 360.dp

    val SettingsCategoryPaneWidth = NavigationPaneWidth
    val FileLocationPaneWidth = NavigationPaneWidth
    val BrowserTabPaneWidth = CompactSidePaneWidth
    val TaskCategoryPaneWidth = CompactSidePaneWidth
    val AutomationLibraryPaneWidth = NavigationPaneWidth
    val EntityNavigationPaneWidth = NavigationPaneWidth

    val FileDetailPaneWidth = DetailPaneWidth
    val LogDetailPaneWidth = DetailPaneWidth
    val TaskDetailPaneWidth = DetailPaneWidth
    val AutomationInspectorPaneWidth = DetailPaneWidth
    val EntityDetailPaneWidth = DetailPaneWidth
}
