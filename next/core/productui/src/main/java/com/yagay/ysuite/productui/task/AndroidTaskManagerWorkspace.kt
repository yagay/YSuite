package com.yagay.ysuite.productui.task

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
import com.yagay.ysuite.productui.ProductLayoutTokens
import com.yagay.ysuite.productui.ProductPaneAdaptiveBox
import com.yagay.ysuite.productui.ProductSurfaceKind
import com.yagay.ysuite.productui.YSuiteProductPage

/**
 * Android task/process manager layout aligned with RohitKushvaha01/TaskManager (Apache-2.0).
 *
 * YSuite keeps the upstream Resources/Processes hierarchy and process-focused search/filter flow,
 * while business data and privileged operations remain YSuite-owned.
 */
@Composable
fun AndroidTaskManagerWorkspace(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    summary: @Composable () -> Unit = {},
    filters: @Composable () -> Unit = {},
    resourcesPane: (@Composable (ProductAdaptiveInfo) -> Unit)? = null,
    detailPane: (@Composable (ProductAdaptiveInfo) -> Unit)? = null,
    content: @Composable (ProductAdaptiveInfo) -> Unit,
) {
    YSuiteProductPage(
        surfaceKind = ProductSurfaceKind.TaskManager,
        title = title,
        modifier = modifier,
        navigationIcon = navigationIcon,
        actions = actions,
        headerContent = {
            summary()
            filters()
        },
    ) { adaptive ->
        Row(modifier = Modifier.fillMaxSize()) {
            if (adaptive.isExpanded && resourcesPane != null) {
                Surface(
                    modifier = Modifier
                        .width(ProductLayoutTokens.TaskCategoryPaneWidth)
                        .fillMaxHeight(),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    resourcesPane(adaptive)
                }
            }
            ProductPaneAdaptiveBox(
                modifier = Modifier.weight(1f).fillMaxHeight(),
            ) { paneAdaptive ->
                content(paneAdaptive)
            }
            if (adaptive.isExpanded && detailPane != null) {
                Surface(
                    modifier = Modifier
                        .width(ProductLayoutTokens.TaskDetailPaneWidth)
                        .fillMaxHeight(),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    detailPane(adaptive)
                }
            }
        }
    }
}
