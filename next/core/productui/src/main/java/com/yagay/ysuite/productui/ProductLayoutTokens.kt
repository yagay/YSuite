package com.yagay.ysuite.productui

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yagay.yui.YUiGeometry
import com.yagay.ysuite.designsystem.theme.YSuiteLayoutTokens

/**
 * Shared pane geometry for mature workspace adapters.
 * Role-specific base widths remain structural; actual sizes are computed solely from
 * YAppearanceStore, so the central settings affect every product layout.
 */
object ProductLayoutTokens {
    @Composable
    private fun content(base: Dp): Dp = YUiGeometry.contentWidth(base)

    @Composable
    private fun pane(base: Dp): Dp = YUiGeometry.paneWidth(base)

    val DashboardContentMaxWidth: Dp @Composable get() = content(YUiGeometry.dashboardContentBase)
    val ToolContentMaxWidth: Dp @Composable get() = content(YUiGeometry.toolContentBase)
    val DetailContentMaxWidth: Dp @Composable get() = content(YUiGeometry.detailContentBase)
    val SettingsContentMaxWidth: Dp @Composable get() =
        content(YSuiteLayoutTokens.DetailMaxWidth)

    val NavigationPaneWidth: Dp @Composable get() =
        pane(YSuiteLayoutTokens.NavigationPaneWidth)
    val CompactSidePaneWidth: Dp @Composable get() = pane(YUiGeometry.compactPaneBase)
    val DetailPaneWidth: Dp @Composable get() = pane(YUiGeometry.detailPaneBase)

    val SettingsCategoryPaneWidth: Dp @Composable get() = NavigationPaneWidth
    val FileLocationPaneWidth: Dp @Composable get() = NavigationPaneWidth
    val BrowserTabPaneWidth: Dp @Composable get() = CompactSidePaneWidth
    val TaskCategoryPaneWidth: Dp @Composable get() = CompactSidePaneWidth
    val AutomationLibraryPaneWidth: Dp @Composable get() = NavigationPaneWidth
    val EntityNavigationPaneWidth: Dp @Composable get() = NavigationPaneWidth

    val FileDetailPaneWidth: Dp @Composable get() = DetailPaneWidth
    val LogDetailPaneWidth: Dp @Composable get() = DetailPaneWidth
    val TaskDetailPaneWidth: Dp @Composable get() = DetailPaneWidth
    val AutomationInspectorPaneWidth: Dp @Composable get() = DetailPaneWidth
    val EntityDetailPaneWidth: Dp @Composable get() = DetailPaneWidth
}
