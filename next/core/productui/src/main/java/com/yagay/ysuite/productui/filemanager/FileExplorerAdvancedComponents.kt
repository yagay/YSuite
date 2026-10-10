package com.yagay.ysuite.productui.filemanager

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Splitscreen
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.Icon
import com.yagay.yui.YUiIconButton as IconButton
import androidx.compose.material3.MaterialTheme
import com.yagay.yui.YUiScrollableTabRow as ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yagay.ysuite.designsystem.theme.YSuiteSpacing

data class FileExplorerBrowserTab(
    val id: String,
    val title: String,
)

@Composable
fun FileExplorerBrowserTabs(
    tabs: List<FileExplorerBrowserTab>,
    activeTabId: String?,
    addLabel: String,
    closeLabel: String,
    onSelect: (String) -> Unit,
    onClose: (String) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (tabs.isEmpty()) {
        return
    }
    val selected =
        tabs.indexOfFirst {
            it.id == activeTabId
        }.coerceAtLeast(0)
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment =
            Alignment.CenterVertically,
    ) {
        ScrollableTabRow(
            selectedTabIndex =
                selected.coerceAtMost(
                    tabs.lastIndex,
                ),
            edgePadding = 0.dp,
            modifier = Modifier.weight(1f),
        ) {
            tabs.forEach { tab ->
                Tab(
                    selected =
                        tab.id == activeTabId,
                    onClick = {
                        onSelect(tab.id)
                    },
                    text = {
                        Row(
                            verticalAlignment =
                                Alignment
                                    .CenterVertically,
                        ) {
                            Text(
                                text = tab.title,
                                maxLines = 1,
                                overflow =
                                    TextOverflow
                                        .Ellipsis,
                            )
                            if (tabs.size > 1) {
                                IconButton(
                                    onClick = {
                                        onClose(
                                            tab.id,
                                        )
                                    },
                                    modifier =
                                        Modifier.size(
                                            32.dp,
                                        ),
                                ) {
                                    Icon(
                                        Icons.Default
                                            .Close,
                                        contentDescription =
                                            closeLabel,
                                        modifier =
                                            Modifier.size(
                                                16.dp,
                                            ),
                                    )
                                }
                            }
                        }
                    },
                )
            }
        }
        IconButton(onClick = onAdd) {
            Icon(
                Icons.Default.Add,
                contentDescription = addLabel,
            )
        }
    }
}

@Composable
fun FileExplorerViewControls(
    grid: Boolean,
    dualPane: Boolean,
    gridLabel: String,
    listLabel: String,
    dualPaneLabel: String,
    onToggleView: () -> Unit,
    onToggleDualPane: () -> Unit,
) {
    IconButton(onClick = onToggleView) {
        Icon(
            imageVector =
                if (grid) {
                    Icons.Default.ViewList
                } else {
                    Icons.Default.GridView
                },
            contentDescription =
                if (grid) {
                    listLabel
                } else {
                    gridLabel
                },
        )
    }
    IconButton(onClick = onToggleDualPane) {
        Icon(
            imageVector =
                Icons.Default.Splitscreen,
            contentDescription = dualPaneLabel,
            tint =
                if (dualPane) {
                    MaterialTheme.colorScheme
                        .primary
                } else {
                    MaterialTheme.colorScheme
                        .onSurfaceVariant
                },
        )
    }
}

data class FileExplorerGridEntry(
    val id: String,
    val title: String,
    val subtitle: String?,
    val kind: YFileProductItemKind,
    val selected: Boolean,
)

@Composable
fun FileExplorerGrid(
    entries: List<FileExplorerGridEntry>,
    selectionMode: Boolean,
    onOpen: (String) -> Unit,
    onToggleSelection: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns =
            GridCells.Adaptive(
                minSize = 112.dp,
            ),
        modifier = modifier,
        horizontalArrangement =
            Arrangement.spacedBy(
                YSuiteSpacing.Small,
            ),
        verticalArrangement =
            Arrangement.spacedBy(
                YSuiteSpacing.Small,
            ),
        content = {
            items(
                items = entries,
                key = { it.id },
            ) { entry ->
                FileExplorerGridItem(
                    entry = entry,
                    selectionMode =
                        selectionMode,
                    onOpen = {
                        onOpen(entry.id)
                    },
                    onToggleSelection = {
                        onToggleSelection(
                            entry.id,
                        )
                    },
                )
            }
        },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileExplorerGridItem(
    entry: FileExplorerGridEntry,
    selectionMode: Boolean,
    onOpen: () -> Unit,
    onToggleSelection: () -> Unit,
) {
    Surface(
        color =
            if (entry.selected) {
                MaterialTheme.colorScheme
                    .primaryContainer
            } else {
                MaterialTheme.colorScheme
                    .surfaceContainerLow
            },
        shape =
            MaterialTheme.shapes.large,
        modifier =
            Modifier.combinedClickable(
                onClick = {
                    if (selectionMode) {
                        onToggleSelection()
                    } else {
                        onOpen()
                    }
                },
                onLongClick =
                    onToggleSelection,
            ),
    ) {
        Column(
            modifier =
                Modifier.padding(
                    YSuiteSpacing.Medium,
                ),
            horizontalAlignment =
                Alignment.CenterHorizontally,
            verticalArrangement =
                Arrangement.spacedBy(
                    YSuiteSpacing.Small,
                ),
        ) {
            Box(
                modifier = Modifier.size(52.dp),
                contentAlignment =
                    Alignment.Center,
            ) {
                if (
                    selectionMode &&
                    entry.selected
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint =
                            MaterialTheme
                                .colorScheme
                                .primary,
                        modifier =
                            Modifier.size(36.dp),
                    )
                } else {
                    Text(
                        text =
                            when (entry.kind) {
                                YFileProductItemKind
                                    .Folder -> "📁"
                                YFileProductItemKind
                                    .Archive -> "🗜"
                                YFileProductItemKind
                                    .Link -> "🔗"
                                else -> "📄"
                            },
                        style =
                            MaterialTheme
                                .typography
                                .headlineMedium,
                    )
                }
            }
            Text(
                text = entry.title,
                style =
                    MaterialTheme.typography
                        .bodyMedium,
                maxLines = 2,
                overflow =
                    TextOverflow.Ellipsis,
            )
            entry.subtitle
                ?.takeIf {
                    it.isNotBlank()
                }
                ?.let {
                    Text(
                        text = it,
                        style =
                            MaterialTheme
                                .typography
                                .labelSmall,
                        color =
                            MaterialTheme
                                .colorScheme
                                .onSurfaceVariant,
                        maxLines = 1,
                        overflow =
                            TextOverflow
                                .Ellipsis,
                    )
                }
        }
    }
}
