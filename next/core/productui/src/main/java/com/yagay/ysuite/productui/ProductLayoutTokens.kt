package com.yagay.ysuite.productui

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yagay.yui.LocalYAppearance
import com.yagay.ysuite.designsystem.theme.YSuiteLayoutTokens

/**
 * Shared pane geometry for mature workspace adapters.
 * Role-specific base widths remain structural; actual sizes are computed solely from
 * YAppearanceStore, so the central settings affect every product layout.
 */
object ProductLayoutTokens {
    @Composable
    private fun content(base: Dp): Dp =
        base * (LocalYAppearance.current.contentWidthPercent / 100f)

    @Composable
    private fun pane(base: Dp): Dp =
        base * (LocalYAppearance.current.paneWidthPercent / 100f)

    val DashboardContentMaxWidth: Dp @Composable get() = content(1280.dp)
    val ToolContentMaxWidth: Dp @Composable get() = content(880.dp)
    val DetailContentMaxWidth: Dp @Composable get() = content(900.dp)
    val SettingsContentMaxWidth: Dp @Composable get() =
        content(YSuiteLayoutTokens.DetailMaxWidth)

    val NavigationPaneWidth: Dp @Composable get() =
        pane(YSuiteLayoutTokens.NavigationPaneWidth)
    val CompactSidePaneWidth: Dp @Composable get() = pane(240.dp)
    val DetailPaneWidth: Dp @Composable get() = pane(360.dp)

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
