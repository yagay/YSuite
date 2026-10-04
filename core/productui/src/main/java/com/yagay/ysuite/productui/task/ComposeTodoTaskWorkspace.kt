package com.yagay.ysuite.productui.task

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

/**
 * Task collection/detail workflow aligned with the Apache-2.0 Compose-ToDo app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposeTodoTaskWorkspace(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    filters: @Composable () -> Unit = {},
    categories: @Composable (ProductAdaptiveInfo) -> Unit = {},
    detailPane: (@Composable (ProductAdaptiveInfo) -> Unit)? = null,
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
                if (adaptive.isExpanded) {
                    Surface(
                        modifier = Modifier
                            .width(240.dp)
                            .fillMaxHeight(),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        categories(adaptive)
                    }
                }
                Box(modifier = Modifier.weight(1f)) {
                    content(adaptive)
                }
                if (adaptive.isExpanded && detailPane != null) {
                    Surface(
                        modifier = Modifier
                            .width(360.dp)
                            .fillMaxHeight(),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        detailPane(adaptive)
                    }
                }
            }
        }
    }
}
