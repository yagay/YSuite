package com.yagay.ysuite.productui.filemanager

import com.yagay.ysuite.productui.YSuiteProductTopBar
import com.yagay.ysuite.productui.YSuiteProductScaffold

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yagay.ysuite.productui.ProductAdaptiveBox
import com.yagay.ysuite.productui.ProductAdaptiveInfo
import kotlinx.coroutines.launch

/**
 * Product layout adapted from the MIT-licensed SysAdminDoc/FileExplorer browser:
 * compact = navigation drawer + browser scaffold,
 * expanded = persistent locations + browser + optional inspector,
 * selection replaces the normal top-bar action model.
 *
 * Visual styling comes exclusively from the active YSuite MaterialTheme.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileExplorerWorkspace(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    drawerContent: @Composable (ProductAdaptiveInfo, closeDrawer: () -> Unit) -> Unit,
    breadcrumb: @Composable () -> Unit,
    tabs: @Composable () -> Unit = {},
    statusBanner: @Composable () -> Unit = {},
    commandBar: @Composable () -> Unit = {},
    selectionTopBar: (@Composable () -> Unit)? = null,
    bottomBar: @Composable () -> Unit = {},
    floatingActionButton: (@Composable () -> Unit)? = null,
    detailPane: (@Composable (ProductAdaptiveInfo) -> Unit)? = null,
    content: @Composable (ProductAdaptiveInfo) -> Unit,
) {
    ProductAdaptiveBox(modifier = modifier.fillMaxSize()) { adaptive ->
        if (adaptive.isExpanded) {
            Row(modifier = Modifier.fillMaxSize()) {
                Surface(
                    modifier = Modifier
                        .width(280.dp)
                        .fillMaxHeight(),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    drawerContent(adaptive) {}
                }

                BrowserScaffold(
                    adaptive = adaptive,
                    title = title,
                    modifier = Modifier.weight(1f),
                    navigationIcon = { navigationIcon?.invoke() },
                    actions = actions,
                    breadcrumb = breadcrumb,
                    tabs = tabs,
                    statusBanner = statusBanner,
                    commandBar = commandBar,
                    selectionTopBar = selectionTopBar,
                    bottomBar = bottomBar,
                    floatingActionButton = floatingActionButton,
                    content = content,
                )

                if (detailPane != null) {
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
        } else {
            val drawerState = rememberDrawerState(DrawerValue.Closed)
            val scope = rememberCoroutineScope()
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    ModalDrawerSheet {
                        drawerContent(adaptive) {
                            scope.launch { drawerState.close() }
                        }
                    }
                },
            ) {
                BrowserScaffold(
                    adaptive = adaptive,
                    title = title,
                    navigationIcon = {
                        Row {
                            if (navigationIcon != null) {
                                navigationIcon()
                            }
                            IconButton(
                                onClick = {
                                    scope.launch { drawerState.open() }
                                },
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Menu,
                                    contentDescription = null,
                                )
                            }
                        }
                    },
                    actions = actions,
                    breadcrumb = breadcrumb,
                    tabs = tabs,
                    statusBanner = statusBanner,
                    commandBar = commandBar,
                    selectionTopBar = selectionTopBar,
                    bottomBar = bottomBar,
                    floatingActionButton = floatingActionButton,
                    content = content,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrowserScaffold(
    adaptive: ProductAdaptiveInfo,
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit,
    actions: @Composable RowScope.() -> Unit,
    breadcrumb: @Composable () -> Unit,
    tabs: @Composable () -> Unit,
    statusBanner: @Composable () -> Unit,
    commandBar: @Composable () -> Unit,
    selectionTopBar: (@Composable () -> Unit)?,
    bottomBar: @Composable () -> Unit,
    floatingActionButton: (@Composable () -> Unit)?,
    content: @Composable (ProductAdaptiveInfo) -> Unit,
) {
    YSuiteProductScaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            Column {
                if (selectionTopBar != null) {
                    selectionTopBar()
                } else {
                    YSuiteProductTopBar(
                        title = { Text(title) },
                        navigationIcon = navigationIcon,
                        actions = actions,
                    )
                }
                statusBanner()
                tabs()
                breadcrumb()
                commandBar()
            }
        },
        bottomBar = bottomBar,
        floatingActionButton = {
            if (floatingActionButton != null) {
                floatingActionButton()
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            content(adaptive)
        }
    }
}
