package com.yagay.ysuite.productui.filemanager

import com.yagay.ysuite.productui.YSuiteProductTopBar
import com.yagay.ysuite.productui.YSuiteProductScaffold

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * FileExplorer-style secondary screen for analyzer/tools workflows.
 * Keeps the file-manager navigation model instead of falling back to a generic tool page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileExplorerUtilitySurface(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable () -> Unit,
) {
    YSuiteProductScaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            YSuiteProductTopBar(
                title = { Text(title) },
                navigationIcon = navigationIcon,
                actions = actions,
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            content()
        }
    }
}
