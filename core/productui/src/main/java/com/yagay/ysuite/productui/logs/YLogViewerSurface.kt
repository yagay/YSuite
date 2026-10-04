package com.yagay.ysuite.productui.logs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yagay.ysuite.productui.ProductAdaptiveBox
import com.yagay.ysuite.productui.ProductAdaptiveInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YLogViewerSurface(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    filters: @Composable () -> Unit,
    details: (@Composable (ProductAdaptiveInfo) -> Unit)? = null,
    content: @Composable (ProductAdaptiveInfo) -> Unit,
) {
    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(title) },
            navigationIcon = navigationIcon,
            actions = actions,
        )
        filters()
        ProductAdaptiveBox(
            modifier = Modifier
                .weight(1f)
                .fillMaxSize(),
        ) { adaptive ->
            Row(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f)) {
                    content(adaptive)
                }
                if (adaptive.isExpanded && details != null) {
                    Surface(
                        modifier = Modifier
                            .width(360.dp)
                            .fillMaxHeight(),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        details(adaptive)
                    }
                }
            }
        }
    }
}
