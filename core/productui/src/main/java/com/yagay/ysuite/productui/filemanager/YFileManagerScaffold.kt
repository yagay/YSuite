package com.yagay.ysuite.productui.filemanager

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
fun YFileManagerScaffold(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    breadcrumb: @Composable () -> Unit,
    commandBar: @Composable () -> Unit = {},
    compactSourceBar: @Composable (ProductAdaptiveInfo) -> Unit = {},
    sourcePane: @Composable (ProductAdaptiveInfo) -> Unit = {},
    detailPane: (@Composable (ProductAdaptiveInfo) -> Unit)? = null,
    selectionBar: (@Composable () -> Unit)? = null,
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (ProductAdaptiveInfo) -> Unit,
) {
    ProductAdaptiveBox(modifier = modifier.fillMaxSize()) { adaptive ->
        if (adaptive.isExpanded) {
            Row(modifier = Modifier.fillMaxSize()) {
                Surface(
                    modifier = Modifier
                        .width(272.dp)
                        .fillMaxHeight(),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    sourcePane(adaptive)
                }

                Column(modifier = Modifier.weight(1f)) {
                    TopAppBar(
                        title = { Text(title) },
                        navigationIcon = navigationIcon,
                        actions = actions,
                    )
                    breadcrumb()
                    commandBar()
                    Box(modifier = Modifier.weight(1f)) {
                        content(adaptive)
                    }
                    selectionBar?.invoke() ?: bottomBar()
                }

                if (detailPane != null) {
                    Surface(
                        modifier = Modifier
                            .width(340.dp)
                            .fillMaxHeight(),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        detailPane(adaptive)
                    }
                }
            }
        } else {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                contentWindowInsets = WindowInsets.safeDrawing,
                topBar = {
                    Column {
                        TopAppBar(
                            title = { Text(title) },
                            navigationIcon = navigationIcon,
                            actions = actions,
                        )
                        compactSourceBar(adaptive)
                        breadcrumb()
                        commandBar()
                    }
                },
                bottomBar = {
                    selectionBar?.invoke() ?: bottomBar()
                },
            ) { padding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            Modifier.padding(padding),
                        ),
                ) {
                    content(adaptive)
                }
            }
        }
    }
}
