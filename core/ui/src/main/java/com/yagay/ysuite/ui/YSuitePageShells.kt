package com.yagay.ysuite.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yagay.ysuite.designsystem.theme.YSuiteLayoutTokens

enum class YSuitePageKind {
    Dashboard,
    Manager,
    Browser,
    Tool,
    Settings,
    Detail,
    Fullscreen,
}

@Composable
fun YSuiteSurfaceShell(
    kind: YSuitePageKind,
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    topBar: (@Composable () -> Unit)? = null,
    bottomBar: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    supportingPane: (@Composable (YSuiteAdaptiveInfo) -> Unit)? = null,
    floatingActionButton: (@Composable () -> Unit)? = null,
    content: @Composable (YSuiteAdaptiveInfo) -> Unit,
) {
    if (kind == YSuitePageKind.Fullscreen) {
        YSuiteAdaptiveLayout(
            modifier = modifier.fillMaxSize(),
            content = content,
        )
        return
    }

    YSuiteAppShell(
        title = title,
        subtitle = subtitle,
        modifier = modifier,
        topBar = topBar,
        bottomBar = bottomBar,
        actions = actions,
        floatingActionButton = floatingActionButton,
    ) { scaffoldPadding ->
        YSuiteAdaptiveLayout(
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding),
        ) { info ->
            if (
                supportingPane != null &&
                info.widthClass == YSuiteWidthClass.Expanded &&
                kind != YSuitePageKind.Browser
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    ) {
                        YSuiteContentFrame(
                            kind = kind,
                            info = info,
                            content = content,
                        )
                    }
                    Surface(
                        modifier = Modifier
                            .width(YSuiteLayoutTokens.SupportingPaneWidth)
                            .fillMaxHeight(),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        supportingPane(info)
                    }
                }
            } else {
                YSuiteContentFrame(
                    kind = kind,
                    info = info,
                    content = content,
                )
            }
        }
    }
}

@Composable
private fun YSuiteContentFrame(
    kind: YSuitePageKind,
    info: YSuiteAdaptiveInfo,
    content: @Composable (YSuiteAdaptiveInfo) -> Unit,
) {
    if (
        kind == YSuitePageKind.Browser ||
        kind == YSuitePageKind.Fullscreen
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            content(info)
        }
        return
    }

    val maxWidth = contentMaxWidth(kind)
    val pagePadding = pagePadding(kind, info.widthClass)

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            modifier = Modifier
                .then(
                    if (maxWidth != null) {
                        Modifier.widthIn(max = maxWidth)
                    } else {
                        Modifier
                    },
                )
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(pagePadding),
        ) {
            content(info)
        }
    }
}

private fun contentMaxWidth(kind: YSuitePageKind): Dp? =
    when (kind) {
        YSuitePageKind.Dashboard ->
            YSuiteLayoutTokens.DashboardContentMaxWidth
        YSuitePageKind.Manager ->
            YSuiteLayoutTokens.ManagerContentMaxWidth
        YSuitePageKind.Tool,
        YSuitePageKind.Settings,
        YSuitePageKind.Detail ->
            YSuiteLayoutTokens.ReadableContentMaxWidth
        YSuitePageKind.Browser,
        YSuitePageKind.Fullscreen ->
            null
    }

private fun pagePadding(
    kind: YSuitePageKind,
    widthClass: YSuiteWidthClass,
): PaddingValues {
    val value = when (kind) {
        YSuitePageKind.Manager ->
            when (widthClass) {
                YSuiteWidthClass.Compact -> 8.dp
                YSuiteWidthClass.Medium -> 12.dp
                YSuiteWidthClass.Expanded -> 16.dp
            }
        else ->
            when (widthClass) {
                YSuiteWidthClass.Compact -> 12.dp
                YSuiteWidthClass.Medium -> 20.dp
                YSuiteWidthClass.Expanded -> 24.dp
            }
    }
    return PaddingValues(value)
}

@Composable
fun YSuiteDashboardShell(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (YSuiteAdaptiveInfo) -> Unit,
) = YSuiteSurfaceShell(
    kind = YSuitePageKind.Dashboard,
    title = title,
    subtitle = subtitle,
    modifier = modifier,
    actions = actions,
    content = content,
)

@Composable
fun YSuiteManagerShell(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    supportingPane: (@Composable (YSuiteAdaptiveInfo) -> Unit)? = null,
    floatingActionButton: (@Composable () -> Unit)? = null,
    content: @Composable (YSuiteAdaptiveInfo) -> Unit,
) = YSuiteSurfaceShell(
    kind = YSuitePageKind.Manager,
    title = title,
    subtitle = subtitle,
    modifier = modifier,
    actions = actions,
    bottomBar = bottomBar,
    supportingPane = supportingPane,
    floatingActionButton = floatingActionButton,
    content = content,
)

@Composable
fun YSuiteBrowserShell(
    modifier: Modifier = Modifier,
    toolbar: @Composable () -> Unit,
    tabStrip: (@Composable () -> Unit)? = null,
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (YSuiteAdaptiveInfo) -> Unit,
) = YSuiteSurfaceShell(
    kind = YSuitePageKind.Browser,
    modifier = modifier,
    topBar = {
        Column {
            toolbar()
            tabStrip?.invoke()
        }
    },
    bottomBar = bottomBar,
    content = content,
)

@Composable
fun YSuiteToolShell(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    supportingPane: (@Composable (YSuiteAdaptiveInfo) -> Unit)? = null,
    content: @Composable (YSuiteAdaptiveInfo) -> Unit,
) = YSuiteSurfaceShell(
    kind = YSuitePageKind.Tool,
    title = title,
    subtitle = subtitle,
    modifier = modifier,
    actions = actions,
    supportingPane = supportingPane,
    content = content,
)

@Composable
fun YSuiteSettingsShell(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable (YSuiteAdaptiveInfo) -> Unit,
) = YSuiteSurfaceShell(
    kind = YSuitePageKind.Settings,
    title = title,
    subtitle = subtitle,
    modifier = modifier,
    content = content,
)

@Composable
fun YSuiteDetailShell(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (YSuiteAdaptiveInfo) -> Unit,
) = YSuiteSurfaceShell(
    kind = YSuitePageKind.Detail,
    title = title,
    subtitle = subtitle,
    modifier = modifier,
    actions = actions,
    content = content,
)

@Composable
fun YSuiteFullscreenShell(
    modifier: Modifier = Modifier,
    content: @Composable (YSuiteAdaptiveInfo) -> Unit,
) = YSuiteSurfaceShell(
    kind = YSuitePageKind.Fullscreen,
    modifier = modifier,
    content = content,
)
