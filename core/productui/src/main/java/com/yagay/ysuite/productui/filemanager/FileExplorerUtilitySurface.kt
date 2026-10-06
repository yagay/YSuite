package com.yagay.ysuite.productui.filemanager

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.yagay.ysuite.productui.tool.NiaToolSurface

/**
 * FileExplorer-style secondary screen for analyzer/tools workflows.
 *
 * Utility pages keep their file-manager content while using the same bounded Tool geometry as
 * every other YSuite tool page.
 */
@Composable
fun FileExplorerUtilitySurface(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable () -> Unit,
) {
    NiaToolSurface(
        title = title,
        modifier = modifier,
        navigationIcon = navigationIcon,
        actions = actions,
    ) {
        content()
    }
}
