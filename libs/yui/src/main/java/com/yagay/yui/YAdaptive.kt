package com.yagay.yui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.material3.adaptive.navigationsuite.ExperimentalMaterial3AdaptiveNavigationSuiteApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Width classes used by YUI without requiring feature modules to depend on adaptive libraries. */
enum class YWindowWidthClass { COMPACT, MEDIUM, EXPANDED }

@Immutable
data class YWindowInfo(
    val widthClass: YWindowWidthClass,
    val width: Dp,
    val height: Dp,
)

internal val LocalYPageRole = staticCompositionLocalOf { YPageRole.LIST }

enum class YTopBarStyle { COMPACT, PROMINENT }

@Immutable
data class YPageTemplate(
    val maxContentWidth: Dp,
    val sectionSpacing: Dp,
    val minimumRowHeight: Dp,
    val topBarStyle: YTopBarStyle,
    val emphasizeCards: Boolean = false,
)

internal fun YPageRole.template(): YPageTemplate = when (this) {
    // A section heading and a short description no longer need a 64dp minimum row.
    // All actionable rows still use a 48dp minimum touch target in YListItem.
    YPageRole.DASHBOARD -> YPageTemplate(
        YDimens.ContentMaxWidth, 8.dp, YDimens.OptionRowHeight, YTopBarStyle.COMPACT,
        emphasizeCards = true,
    )
    YPageRole.SETTINGS -> YPageTemplate(
        YDimens.FormMaxWidth, 0.dp, YDimens.OptionRowHeight, YTopBarStyle.COMPACT,
    )
    YPageRole.MANAGER, YPageRole.BROWSER, YPageRole.TIMELINE, YPageRole.LOG -> YPageTemplate(
        YDimens.ContentMaxWidth, 0.dp, YDimens.OptionRowHeight, YTopBarStyle.COMPACT,
    )
    YPageRole.DETAIL, YPageRole.EDITOR, YPageRole.WIZARD -> YPageTemplate(
        YDimens.FormMaxWidth, 6.dp, YDimens.OptionRowHeight, YTopBarStyle.COMPACT,
    )
    YPageRole.LIST -> YPageTemplate(
        YDimens.ContentMaxWidth, 0.dp, YDimens.OptionRowHeight, YTopBarStyle.COMPACT,
    )
}

fun yPageHorizontalPadding(width: Dp): Dp = when {
    width < YDimens.MediumBreakpoint -> YDimens.ScreenHorizontal
    width < YDimens.ExpandedBreakpoint -> YDimens.ScreenHorizontalMedium
    else -> YDimens.ScreenHorizontalExpanded
}

internal fun YPageRole.maxContentWidth(): Dp = template().maxContentWidth
internal fun YPageRole.prefersCompactRows(): Boolean = template().minimumRowHeight < YDimens.OptionRowHeight
internal fun YPageRole.sectionSpacing(): Dp = template().sectionSpacing

@Composable
fun YAdaptiveBox(
    modifier: Modifier = Modifier,
    content: @Composable (YWindowInfo) -> Unit,
) {
    BoxWithConstraints(modifier) {
        val widthClass = when {
            maxWidth < YDimens.MediumBreakpoint -> YWindowWidthClass.COMPACT
            maxWidth < YDimens.ExpandedBreakpoint -> YWindowWidthClass.MEDIUM
            else -> YWindowWidthClass.EXPANDED
        }
        content(YWindowInfo(widthClass, maxWidth, maxHeight))
    }
}

@Immutable
data class YNavigationSpec(
    val key: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector = icon,
)

/**
 * Canonical YSuite application shell. Material 3 Adaptive chooses the navigation surface for
 * the current window and posture; feature modules no longer own phone/tablet breakpoints.
 */
/**
 * Navigation is resolved against the *available module viewport*, never the device's
 * full window. The suite host can already reserve a side drawer on expanded screens.
 */
@OptIn(ExperimentalMaterial3AdaptiveNavigationSuiteApi::class)
@Composable
fun YAppShell(
    selectedKey: String,
    items: List<YNavigationSpec>,
    onSelected: (YNavigationSpec) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val navigationLayout = when {
            maxWidth < YDimens.ExpandedBreakpoint -> NavigationSuiteType.NavigationBar
            else -> NavigationSuiteType.NavigationRail
        }
        NavigationSuiteScaffold(
            navigationSuiteItems = {
                items.forEach { item ->
                    val selected = item.key == selectedKey
                    item(
                        selected = selected,
                        onClick = { onSelected(item) },
                        icon = {
                            Icon(
                                if (selected) item.selectedIcon else item.icon,
                                contentDescription = item.label,
                            )
                        },
                        label = { Text(item.label) },
                    )
                }
            },
            layoutType = navigationLayout,
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
        ) {
            content()
        }
    }
}

/** Compatibility alias while old feature call sites are migrated. */
@Deprecated("Use YAppShell")
@Composable
fun YNavigationSuite(
    selectedKey: String,
    items: List<YNavigationSpec>,
    onSelected: (YNavigationSpec) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) = YAppShell(selectedKey, items, onSelected, modifier, content)

/** Standard master/detail behavior: split pane on expanded windows, single pane otherwise. */
@Composable
fun YListDetailScaffold(
    showDetail: Boolean,
    modifier: Modifier = Modifier,
    listPane: @Composable (Modifier) -> Unit,
    detailPane: @Composable (Modifier) -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        if (maxWidth >= YDimens.ExpandedBreakpoint) {
            Row(Modifier.fillMaxSize()) {
                listPane(Modifier.width(YDimens.ListDetailListWidth).fillMaxHeight())
                Box(
                    Modifier.fillMaxHeight().width(1.dp)
                        .background(androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant),
                )
                detailPane(Modifier.weight(1f).fillMaxHeight())
            }
        } else if (showDetail) {
            detailPane(Modifier.fillMaxSize())
        } else {
            listPane(Modifier.fillMaxSize())
        }
    }
}

/** Main/supporting pane pattern for dashboards, inspectors and diagnostics. */
@Composable
fun YSupportingPaneScaffold(
    showSupportingPane: Boolean = true,
    modifier: Modifier = Modifier,
    mainPane: @Composable (Modifier) -> Unit,
    supportingPane: @Composable (Modifier) -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        if (showSupportingPane && maxWidth >= YDimens.ExpandedBreakpoint) {
            Row(
                Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(YDimens.PaneGap),
            ) {
                mainPane(Modifier.weight(0.64f).fillMaxHeight())
                supportingPane(Modifier.weight(0.36f).fillMaxHeight())
            }
        } else {
            mainPane(Modifier.fillMaxSize())
        }
    }
}

@Composable
fun YResponsiveGrid(
    modifier: Modifier = Modifier,
    minCellWidth: Dp = 220.dp,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: LazyGridScope.() -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minCellWidth),
        modifier = modifier,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(YDimens.ControlGap),
        verticalArrangement = Arrangement.spacedBy(YDimens.ControlGap),
        content = content,
    )
}

@Immutable
data class YBreadcrumbSegment(
    val key: String,
    val label: String,
    val onClick: () -> Unit,
)

/** Shared hierarchical navigation for browser-style modules. */
@Composable
fun YBreadcrumbBar(
    segments: List<YBreadcrumbSegment>,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        segments.forEachIndexed { index, segment ->
            TextButton(onClick = segment.onClick) { Text(segment.label) }
            if (index < segments.lastIndex) {
                Icon(YIcons.Forward, contentDescription = null)
            }
        }
    }
}
