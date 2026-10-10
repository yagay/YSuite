package com.yagay.ysuite.productui.logs

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
 * Search/filter/log-stream workflow aligned with darshanparajuli/LogcatReader (MIT).
 */
@Composable
fun LogcatReaderWorkspace(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    search: @Composable () -> Unit,
    filters: @Composable () -> Unit,
    details: (@Composable (ProductAdaptiveInfo) -> Unit)? = null,
    content: @Composable (ProductAdaptiveInfo) -> Unit,
) {
    YSuiteProductPage(
        surfaceKind = ProductSurfaceKind.LogViewer,
        title = title,
        modifier = modifier,
        navigationIcon = navigationIcon,
        actions = actions,
        headerContent = {
            search()
            filters()
        },
    ) { adaptive ->
        Row(modifier = Modifier.fillMaxSize()) {
            ProductPaneAdaptiveBox(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(),
            ) { paneAdaptive ->
                content(paneAdaptive)
            }
            if (adaptive.isExpanded && details != null) {
                Surface(
                    modifier =
                        Modifier
                            .width(ProductLayoutTokens.LogDetailPaneWidth)
                            .fillMaxHeight(),
                    color =
                        MaterialTheme.colorScheme
                            .surfaceContainerLow,
                ) {
                    details(adaptive)
                }
            }
        }
    }
}
