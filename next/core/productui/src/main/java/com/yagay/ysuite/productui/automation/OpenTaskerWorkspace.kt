package com.yagay.ysuite.productui.automation

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
 * Profiles/tasks/editor split aligned with SysAdminDoc/OpenTasker (MIT).
 */
@Composable
fun OpenTaskerWorkspace(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    library: @Composable (ProductAdaptiveInfo) -> Unit,
    editor: @Composable (ProductAdaptiveInfo) -> Unit,
    inspector: (@Composable (ProductAdaptiveInfo) -> Unit)? = null,
) {
    YSuiteProductPage(
        surfaceKind = ProductSurfaceKind.AutomationStudio,
        title = title,
        modifier = modifier,
        navigationIcon = navigationIcon,
        actions = actions,
    ) { adaptive ->
        if (adaptive.isExpanded) {
            Row(modifier = Modifier.fillMaxSize()) {
                Surface(
                    modifier =
                        Modifier
                            .width(ProductLayoutTokens.AutomationLibraryPaneWidth)
                            .fillMaxHeight(),
                    color =
                        MaterialTheme.colorScheme
                            .surfaceContainerLow,
                ) {
                    library(adaptive)
                }
                ProductPaneAdaptiveBox(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                ) { paneAdaptive ->
                    editor(paneAdaptive)
                }
                if (inspector != null) {
                    Surface(
                        modifier =
                            Modifier
                                .width(ProductLayoutTokens.AutomationInspectorPaneWidth)
                                .fillMaxHeight(),
                        color =
                            MaterialTheme.colorScheme
                                .surfaceContainerLow,
                    ) {
                        inspector(adaptive)
                    }
                }
            }
        } else {
            ProductPaneAdaptiveBox(
                modifier = Modifier.fillMaxSize(),
            ) { paneAdaptive ->
                editor(paneAdaptive)
            }
        }
    }
}
