package com.yagay.ysuite.productui.manager

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
 * Product surface for app/component/notification/rule style managers.
 *
 * Expanded layouts expose collection navigation, the entity list and an optional
 * inspector simultaneously. Compact layouts keep the collection/list workflow
 * primary and let the feature navigate to detail when required.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YEntityManagerSurface(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    collectionPane: @Composable (ProductAdaptiveInfo) -> Unit = {},
    filters: @Composable () -> Unit = {},
    inspector: (@Composable (ProductAdaptiveInfo) -> Unit)? = null,
    selectionBar: @Composable () -> Unit = {},
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
                            .width(256.dp)
                            .fillMaxHeight(),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        collectionPane(adaptive)
                    }
                }
                Box(modifier = Modifier.weight(1f)) {
                    content(adaptive)
                }
                if (adaptive.isExpanded && inspector != null) {
                    Surface(
                        modifier = Modifier
                            .width(360.dp)
                            .fillMaxHeight(),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        inspector(adaptive)
                    }
                }
            }
        }
        selectionBar()
    }
}
