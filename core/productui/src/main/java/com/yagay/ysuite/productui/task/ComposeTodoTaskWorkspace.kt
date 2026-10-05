package com.yagay.ysuite.productui.task

import com.yagay.ysuite.productui.ProductSurfaceKind

import com.yagay.ysuite.productui.ProductLayoutTokens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.yagay.ysuite.productui.ProductAdaptiveInfo
import com.yagay.ysuite.productui.ProductPaneAdaptiveBox
import com.yagay.ysuite.productui.YSuiteProductPage

/**
 * Task collection/detail workflow aligned with the Apache-2.0 Compose-ToDo app.
 */
@Composable
fun ComposeTodoTaskWorkspace(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    filters: @Composable () -> Unit = {},
    categories: (@Composable (ProductAdaptiveInfo) -> Unit)? = null,
    detailPane: (@Composable (ProductAdaptiveInfo) -> Unit)? = null,
    content: @Composable (ProductAdaptiveInfo) -> Unit,
) {
    YSuiteProductPage(
        surfaceKind = ProductSurfaceKind.TaskManager,
        title = title,
        modifier = modifier,
        navigationIcon = navigationIcon,
        actions = actions,
        headerContent = filters,
    ) { adaptive ->
        Row(modifier = Modifier.fillMaxSize()) {
            if (adaptive.isExpanded && categories != null) {
                Surface(
                    modifier =
                        Modifier
                            .width(ProductLayoutTokens.TaskCategoryPaneWidth)
                            .fillMaxHeight(),
                    color =
                        MaterialTheme.colorScheme
                            .surfaceContainerLow,
                ) {
                    categories(adaptive)
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
                            .width(ProductLayoutTokens.TaskDetailPaneWidth)
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
