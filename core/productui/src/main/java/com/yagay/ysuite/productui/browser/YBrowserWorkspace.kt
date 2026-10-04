package com.yagay.ysuite.productui.browser

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
import androidx.compose.ui.unit.dp
import com.yagay.ysuite.productui.ProductAdaptiveBox
import com.yagay.ysuite.productui.ProductAdaptiveInfo

@Composable
fun YBrowserWorkspace(
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    addressBar: @Composable () -> Unit,
    tabStrip: @Composable () -> Unit = {},
    toolbar: @Composable RowScope.() -> Unit = {},
    tabSidebar: @Composable (ProductAdaptiveInfo) -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (ProductAdaptiveInfo) -> Unit,
) {
    ProductAdaptiveBox(modifier = modifier.fillMaxSize()) { adaptive ->
        Row(modifier = Modifier.fillMaxSize()) {
            if (adaptive.isExpanded) {
                Surface(
                    modifier = Modifier
                        .width(240.dp)
                        .fillMaxHeight(),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    tabSidebar(adaptive)
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
                Box(modifier = Modifier.weight(1f)) {
                    content(adaptive)
                }
                bottomBar()
            }
        }
    }
}
