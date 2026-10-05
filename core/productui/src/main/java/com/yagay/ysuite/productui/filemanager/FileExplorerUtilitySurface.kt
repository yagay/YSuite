package com.yagay.ysuite.productui.filemanager

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.yagay.ysuite.productui.YSuiteProductPage

/**
 * FileExplorer-style secondary screen for analyzer/tools workflows.
 * Keeps file-manager content semantics while sharing the YSuite page host.
 */
@Composable
fun FileExplorerUtilitySurface(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable () -> Unit,
) {
    YSuiteProductPage(
        title = title,
        modifier = modifier,
        navigationIcon = navigationIcon,
        actions = actions,
    ) {
        content()
    }
}
