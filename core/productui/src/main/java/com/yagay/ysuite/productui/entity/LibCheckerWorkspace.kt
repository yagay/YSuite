package com.yagay.ysuite.productui.entity

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
 * App/entity manager layout aligned with LibChecker (Apache-2.0):
 * compact list, expanded navigation/list/detail, feature-owned filters.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(title) },
            navigationIcon = navigationIcon,
            actions = actions,
        )
        searchAndFilters()
        ProductAdaptiveBox(
            modifier = Modifier
                .weight(1f)
                .fillMaxSize(),
        ) { adaptive ->
            Row(modifier = Modifier.fillMaxSize()) {
                if (adaptive.isExpanded && navigationPane != null) {
                    Surface(
                        modifier = Modifier
                            .width(248.dp)
                            .fillMaxHeight(),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        navigationPane(adaptive)
                    }
                }
                Box(modifier = Modifier.weight(1f)) {
                    content(adaptive)
                }
                if (adaptive.isExpanded && detailPane != null) {
                    Surface(
                        modifier = Modifier
                            .width(380.dp)
                            .fillMaxHeight(),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        detailPane(adaptive)
                    }
                }
            }
        }
        selectionBar()
    }
}
