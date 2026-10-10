package com.yagay.ysuite.productui.filemanager

import com.yagay.ysuite.productui.YSuiteProductTopBar

import com.yagay.yui.YDimens
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import com.yagay.yui.YUiCheckbox as Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import com.yagay.yui.YUiIconButton as IconButton
import com.yagay.yui.YListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.yagay.yui.YUiOutlinedTextField as TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yagay.ysuite.designsystem.component.YSuiteListItem
import com.yagay.ysuite.designsystem.component.YSuiteSection
import com.yagay.ysuite.designsystem.theme.YSuiteSpacing

enum class YFileProductItemKind {
    Folder,
    File,
    Archive,
    Link,
    Other,
}

data class YFileProductSource(
    val id: String,
    val label: String,
    val kind: YFileProductSourceKind,
)

enum class YFileProductSourceKind {
    Local,
    Root,
    Document,
    Archive,
    Remote,
}

data class FileExplorerSortOption(
    val id: String,
    val label: String,
)

private data class BreadcrumbSegment(
    val name: String,
    val path: String,
)

@Composable
fun YFileBreadcrumbBar(
    path: String,
    providerLabel: String,
    favorite: Boolean,
    onRoot: () -> Unit,
    onNavigatePath: (String) -> Unit,
    onRefresh: () -> Unit,
    onFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    val absolutePath = path.startsWith("/")
    val segments =
        if (absolutePath) {
            val parts = path.trimEnd('/').split('/').filter(String::isNotEmpty)
            buildList {
                var current = ""
                for (part in parts) {
                    current += "/" + part
                    add(BreadcrumbSegment(part, current))
                }
            }
        } else {
            emptyList()
        }

    LaunchedEffect(path) {
        scroll.animateScrollTo(scroll.maxValue)
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scroll)
                .padding(horizontal = YDimens.ControlGap, vertical = YDimens.SpacingXsmall),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Storage,
                contentDescription = providerLabel,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(20.dp)
                    .clickable(onClick = onRoot),
            )
            Text(
                text = providerLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable(onClick = onRoot)
                    .padding(horizontal = YDimens.SpacingSmall, vertical = YDimens.SpacingSmall),
            )

            if (absolutePath) {
                segments.forEachIndexed { index, segment ->
                    Text(
                        text = "›",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 2.dp),
                    )
                    val last = index == segments.lastIndex
                    Text(
                        text = segment.name,
                        style = MaterialTheme.typography.labelLarge,
                        color =
                            if (last) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .clickable(enabled = !last) {
                                onNavigatePath(segment.path)
                            }
                            .padding(horizontal = YDimens.SpacingSmall, vertical = YDimens.SpacingSmall),
                    )
                }
            } else {
                Text(
                    text = path,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }

            IconButton(onClick = onFavorite) {
                Icon(
                    imageVector =
                        if (favorite) Icons.Default.Star
                        else Icons.Default.StarBorder,
                    contentDescription = null,
                )
            }
            IconButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, contentDescription = null)
            }
        }
    }
}

@Composable
fun FileExplorerSearchBar(
    query: String,
    searchLabel: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = YDimens.ControlGap, vertical = YDimens.SpacingXsmall),
        singleLine = true,
        placeholder = { Text(searchLabel) },
        leadingIcon = {
            Icon(Icons.Default.Search, contentDescription = null)
        },
    )
}

@Composable
fun RowScope.FileExplorerTopActions(
    sortOptions: List<FileExplorerSortOption>,
    selectedSortId: String,
    descending: Boolean,
    ascendingLabel: String,
    descendingLabel: String,
    onSortSelected: (String) -> Unit,
    onDescendingChange: (Boolean) -> Unit,
    showHidden: Boolean,
    showHiddenLabel: String,
    recursive: Boolean,
    recursiveLabel: String,
    onShowHiddenChange: (Boolean) -> Unit,
    onRecursiveChange: (Boolean) -> Unit,
) {
    var sortExpanded by remember { mutableStateOf(false) }
    var optionsExpanded by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { sortExpanded = true }) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Sort,
                contentDescription = null,
            )
        }
        DropdownMenu(
            expanded = sortExpanded,
            onDismissRequest = { sortExpanded = false },
        ) {
            sortOptions.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            if (option.id == selectedSortId) {
                                "✓ " + option.label
                            } else {
                                option.label
                            },
                        )
                    },
                    onClick = {
                        onSortSelected(option.id)
                        sortExpanded = false
                    },
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = {
                    Text(
                        if (descending) descendingLabel
                        else ascendingLabel,
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector =
                            if (descending) Icons.Default.ArrowDownward
                            else Icons.Default.ArrowUpward,
                        contentDescription = null,
                    )
                },
                onClick = {
                    onDescendingChange(!descending)
                    sortExpanded = false
                },
            )
        }
    }

    Box {
        IconButton(onClick = { optionsExpanded = true }) {
            Icon(
                imageVector =
                    if (showHidden) Icons.Default.Visibility
                    else Icons.Default.VisibilityOff,
                contentDescription = null,
            )
        }
        DropdownMenu(
            expanded = optionsExpanded,
            onDismissRequest = { optionsExpanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(showHiddenLabel) },
                leadingIcon = {
                    Checkbox(
                        checked = showHidden,
                        onCheckedChange = null,
                    )
                },
                onClick = { onShowHiddenChange(!showHidden) },
            )
            DropdownMenuItem(
                text = { Text(recursiveLabel) },
                leadingIcon = {
                    Checkbox(
                        checked = recursive,
                        onCheckedChange = null,
                    )
                },
                onClick = { onRecursiveChange(!recursive) },
            )
        }
    }
}

@Composable
fun FileExplorerNewFolderFab(
    contentDescription: String?,
    onClick: () -> Unit,
) {
    FloatingActionButton(
        onClick = onClick,
    ) {
        Icon(
            imageVector = Icons.Default.CreateNewFolder,
            contentDescription = contentDescription,
        )
    }
}

@Composable
fun YFileSourcePane(
    sources: List<YFileProductSource>,
    selectedSourceId: String?,
    browserLabel: String,
    favoritesLabel: String,
    recentLabel: String,
    trashLabel: String,
    toolsLabel: String,
    settingsLabel: String,
    activeSectionId: String,
    onSourceSelected: (String) -> Unit,
    onSectionSelected: (String) -> Unit,
    onToolsSelected: () -> Unit,
    onSettingsSelected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(YSuiteSpacing.Medium),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        sources.forEach { source ->
            SourceRow(
                label = source.label,
                icon = source.kind.icon(),
                selected =
                    activeSectionId == "browser" &&
                        source.id == selectedSourceId,
                onClick = { onSourceSelected(source.id) },
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        SourceRow(
            label = browserLabel,
            icon = Icons.Default.Home,
            selected = activeSectionId == "browser",
            onClick = { onSectionSelected("browser") },
        )
        SourceRow(
            label = favoritesLabel,
            icon = Icons.Default.Star,
            selected = activeSectionId == "favorites",
            onClick = { onSectionSelected("favorites") },
        )
        SourceRow(
            label = recentLabel,
            icon = Icons.Default.History,
            selected = activeSectionId == "recent",
            onClick = { onSectionSelected("recent") },
        )
        SourceRow(
            label = trashLabel,
            icon = Icons.Default.Delete,
            selected = activeSectionId == "trash",
            onClick = { onSectionSelected("trash") },
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        SourceRow(
            label = toolsLabel,
            icon = Icons.Default.Build,
            selected = false,
            onClick = onToolsSelected,
        )
        SourceRow(
            label = settingsLabel,
            icon = Icons.Default.Settings,
            selected = false,
            onClick = onSettingsSelected,
        )
    }
}

@Composable
private fun SourceRow(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color =
            if (selected) MaterialTheme.colorScheme.secondaryContainer
            else Color.Transparent,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = YDimens.ScreenHorizontal, vertical = YDimens.SectionGap),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(YDimens.ScreenHorizontal),
        ) {
            Icon(icon, contentDescription = null)
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight =
                    if (selected) FontWeight.SemiBold
                    else FontWeight.Normal,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun YFileEntryRow(
    title: String,
    subtitle: String?,
    kind: YFileProductItemKind,
    selected: Boolean,
    selectionMode: Boolean,
    onOpen: () -> Unit,
    onToggleSelection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color =
            if (selected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            } else {
                MaterialTheme.colorScheme.surface
            },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = {
                        if (selectionMode) onToggleSelection()
                        else onOpen()
                    },
                    onLongClick = onToggleSelection,
                )
                .heightIn(min = YDimens.OptionRowHeight)
                .padding(horizontal = YDimens.SpacingLarge, vertical = YDimens.SectionGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(YDimens.ListIconSize),
                contentAlignment = Alignment.Center,
            ) {
                if (selectionMode && selected) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp),
                    )
                } else {
                    Icon(
                        imageVector = kind.icon(),
                        contentDescription = null,
                        tint =
                            if (kind == YFileProductItemKind.Folder) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileExplorerSelectionTopBar(
    countLabel: String,
    selectAllLabel: String,
    copyLabel: String,
    moveLabel: String,
    trashLabel: String,
    deleteLabel: String,
    clearLabel: String,
    onSelectAll: () -> Unit,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onTrash: () -> Unit,
    onDelete: () -> Unit,
    onClear: () -> Unit,
) {
    YSuiteProductTopBar(
        title = { Text(countLabel) },
        navigationIcon = {
            IconButton(onClick = onClear) {
                Icon(Icons.Default.Close, contentDescription = clearLabel)
            }
        },
        actions = {
            IconButton(onClick = onSelectAll) {
                Icon(Icons.Default.SelectAll, contentDescription = selectAllLabel)
            }
            IconButton(onClick = onCopy) {
                Icon(Icons.Default.ContentCopy, contentDescription = copyLabel)
            }
            IconButton(onClick = onMove) {
                Icon(Icons.Default.ContentCut, contentDescription = moveLabel)
            }
            IconButton(onClick = onTrash) {
                Icon(Icons.Default.Delete, contentDescription = trashLabel)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Warning, contentDescription = deleteLabel)
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileExplorerDetailsSheet(
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            content = content,
        )
    }
}

@Composable
fun FileExplorerDetailRow(
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
) {
    YListItem(
        title = title,
        subtitle = subtitle,
        onClick = onClick,
    )
}

@Composable
fun FileExplorerToolGroup(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    YSuiteSection(
        title = title,
        modifier = modifier,
        content = content,
    )
}

@Composable
fun FileExplorerToolAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    YSuiteListItem(
        title = text,
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
        trailing = {
            Text(
                text = "›",
                style =
                    MaterialTheme.typography
                        .titleMedium,
                color =
                    MaterialTheme.colorScheme
                        .onSurfaceVariant,
            )
        },
    )
}

private fun YFileProductItemKind.icon(): ImageVector =
    when (this) {
        YFileProductItemKind.Folder -> Icons.Default.Folder
        YFileProductItemKind.File -> Icons.Default.Description
        YFileProductItemKind.Archive -> Icons.Default.Archive
        YFileProductItemKind.Link -> Icons.Default.Link
        YFileProductItemKind.Other -> Icons.Default.Description
    }

private fun YFileProductSourceKind.icon(): ImageVector =
    when (this) {
        YFileProductSourceKind.Local -> Icons.Default.Storage
        YFileProductSourceKind.Root -> Icons.Default.Terminal
        YFileProductSourceKind.Document -> Icons.Default.Folder
        YFileProductSourceKind.Archive -> Icons.Default.Archive
        YFileProductSourceKind.Remote -> Icons.Default.Storage
    }
