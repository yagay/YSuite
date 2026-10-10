package com.yagay.ysuite.productui.nfc

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
 * Operational NFC workspace aligned with NFCGate (Apache-2.0).
 *
 * The status/mode/work-area hierarchy is adapted only as product layout. YNFC keeps its own
 * access-card model, HCE routing, Root/Hook integration and recovery behaviour.
 */
@Composable
fun NfcGateWorkspace(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    status: @Composable () -> Unit = {},
    modes: (@Composable (ProductAdaptiveInfo) -> Unit)? = null,
    detailPane: (@Composable (ProductAdaptiveInfo) -> Unit)? = null,
    content: @Composable (ProductAdaptiveInfo) -> Unit,
) {
    YSuiteProductPage(
        surfaceKind = ProductSurfaceKind.Tool,
        title = title,
        modifier = modifier,
        navigationIcon = navigationIcon,
        actions = actions,
        headerContent = status,
    ) { adaptive ->
        Row(modifier = Modifier.fillMaxSize()) {
            if (adaptive.isExpanded && modes != null) {
                Surface(
                    modifier = Modifier
                        .width(ProductLayoutTokens.CompactSidePaneWidth)
                        .fillMaxHeight(),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    modes(adaptive)
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
                        .width(ProductLayoutTokens.DetailPaneWidth)
                        .fillMaxHeight(),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    detailPane(adaptive)
                }
            }
        }
    }
}
