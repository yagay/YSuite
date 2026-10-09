package com.yagay.ysuite.productui.browser

import com.yagay.ysuite.productui.ProductLayoutTokens
import com.yagay.ysuite.productui.ProductPaneAdaptiveBox
import com.yagay.ysuite.productui.ProductSurfaceKind
import com.yagay.ysuite.productui.ProductSurfaceScope

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.yagay.ysuite.productui.ProductAdaptiveBox
import com.yagay.ysuite.productui.ProductAdaptiveInfo

/**
 * Browser-owned workspace aligned with the Apache-2.0 Yue-Browser product model.
 * The host never injects generic page chrome into web content.
 */
@Composable
fun YueBrowserWorkspace(
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    addressBar: @Composable () -> Unit,
    tabStrip: @Composable () -> Unit = {},
    toolbar: @Composable RowScope.() -> Unit = {},
    wideTabPane: (@Composable (ProductAdaptiveInfo) -> Unit)? = null,
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (ProductAdaptiveInfo) -> Unit,
) {
    ProductSurfaceScope(ProductSurfaceKind.Browser) {
        ProductAdaptiveBox(modifier = modifier.fillMaxSize()) { adaptive ->
            Row(modifier = Modifier.fillMaxSize()) {
            if (adaptive.isExpanded && wideTabPane != null) {
                Surface(
                    modifier = Modifier
                        .width(ProductLayoutTokens.BrowserTabPaneWidth)
                        .fillMaxHeight(),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    wideTabPane(adaptive)
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Row {
                    navigationIcon()
                    Box(modifier = Modifier.weight(1f)) {
                        addressBar()
                    }
                    toolbar()
                }
                tabStrip()
                ProductPaneAdaptiveBox(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                ) { paneAdaptive ->
                    content(paneAdaptive)
                }
                bottomBar()
            }
        }
    }
    }
}
