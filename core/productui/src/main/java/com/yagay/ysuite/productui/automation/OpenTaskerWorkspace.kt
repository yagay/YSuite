package com.yagay.ysuite.productui.automation

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
 * Profiles/tasks/editor split aligned with SysAdminDoc/OpenTasker (MIT).
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(title) },
            navigationIcon = navigationIcon,
            actions = actions,
        )
        ProductAdaptiveBox(
            modifier = Modifier
                .weight(1f)
                .fillMaxSize(),
        ) { adaptive ->
            if (adaptive.isExpanded) {
                Row(modifier = Modifier.fillMaxSize()) {
                    Surface(
                        modifier = Modifier
                            .width(300.dp)
                            .fillMaxHeight(),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        library(adaptive)
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        editor(adaptive)
                    }
                    if (inspector != null) {
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
            } else {
                editor(adaptive)
            }
        }
    }
}
