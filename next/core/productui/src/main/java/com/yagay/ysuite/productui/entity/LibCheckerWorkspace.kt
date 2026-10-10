package com.yagay.ysuite.productui.entity

import com.yagay.ysuite.productui.ProductSurfaceKind

import com.yagay.ysuite.productui.ProductLayoutTokens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import com.yagay.yui.YUiSurface as Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.yagay.ysuite.productui.ProductAdaptiveInfo
import com.yagay.ysuite.productui.ProductPaneAdaptiveBox
import com.yagay.ysuite.productui.YSuiteProductPage

/**
 * App/entity manager layout aligned with LibChecker (Apache-2.0):
 * compact list, expanded navigation/list/detail, feature-owned filters.
 */
@Composable
fun LibCheckerWorkspace(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    searchAndFilters: @Composable () -> Unit = {},
    navigationPane: (@Composable (ProductAdaptiveInfo) -> Unit)? = null,
    detailPane: (@Composable (ProductAdaptiveInfo) -> Unit)? = null,
    selectionBar: @Composable () -> Unit = {},
    content: @Composable (ProductAdaptiveInfo) -> Unit,
) {
    YSuiteProductPage(
        surfaceKind = ProductSurfaceKind.EntityManager,
        title = title,
        modifier = modifier,
        navigationIcon = navigationIcon,
        actions = actions,
        headerContent = searchAndFilters,
        bottomBar = selectionBar,
    ) { adaptive ->
        Row(modifier = Modifier.fillMaxSize()) {
            if (adaptive.isExpanded && navigationPane != null) {
                Surface(
                    modifier =
                        Modifier
                            .width(ProductLayoutTokens.EntityNavigationPaneWidth)
                            .fillMaxHeight(),
                    color =
                        MaterialTheme.colorScheme
                            .surfaceContainerLow,
                ) {
                    navigationPane(adaptive)
                }
            }
            ProductPaneAdaptiveBox(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(),
            ) { paneAdaptive ->
                content(paneAdaptive)
            }
            if (adaptive.isExpanded && detailPane != null) {
                Surface(
                    modifier =
                        Modifier
                            .width(ProductLayoutTokens.EntityDetailPaneWidth)
                            .fillMaxHeight(),
                    color =
                        MaterialTheme.colorScheme
                            .surfaceContainerLow,
                ) {
                    detailPane(adaptive)
                }
            }
        }
    }
}
