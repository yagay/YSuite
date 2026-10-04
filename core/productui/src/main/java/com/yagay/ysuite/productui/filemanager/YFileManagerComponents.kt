package com.yagay.ysuite.productui.filemanager

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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

@Composable
fun YFileBreadcrumbBar(
    path: String,
    providerLabel: String,
    favorite: Boolean,
    onUp: () -> Unit,
    onRefresh: () -> Unit,
    onFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = YSuiteSpacing.Small,
                    vertical = YSuiteSpacing.XSmall,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(YSuiteSpacing.XSmall),
        ) {
            IconButton(onClick = onUp) {
                Icon(Icons.Default.ArrowBack, contentDescription = null)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = path,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = providerLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
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
            .padding(
                horizontal = YSuiteSpacing.Small,
                vertical = YSuiteSpacing.XSmall,
            ),
        singleLine = true,
        placeholder = { Text(searchLabel) },
        leadingIcon = {
            Icon(Icons.Default.Search, contentDescription = null)
        },
        shape = MaterialTheme.shapes.extraLarge,
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    )
}

@Composable
fun RowScope.FileExplorerTopActions(
    sortOptions: List<FileExplorerSortOption>,
    selectedSortId: String,
    onSortSelected: (String) -> Unit,
    showHidden: Boolean,
    showHiddenLabel: String,
    onShowHiddenChange: (Boolean) -> Unit,
    recursive: Boolean,
    recursiveLabel: String,
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
                            text =
                                if (option.id == selectedSortId) {
                                    "✓ " + option.label
                                } else {
                                    option.label
                                },
                        )
                    },
                    onClick = {
                        sortExpanded = false
                        onSortSelected(option.id)
                    },
                )
            }
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
                onClick = {
                    onShowHiddenChange(!showHidden)
                },
            )
            DropdownMenuItem(
                text = { Text(recursiveLabel) },
                leadingIcon = {
                    Checkbox(
                        checked = recursive,
                        onCheckedChange = null,
                    )
                },
                onClick = {
                    onRecursiveChange(!recursive)
                },
            )
        }
    }
}

@Composable
fun FileExplorerBackButton(
    contentDescription: String?,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
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
        verticalArrangement = Arrangement.spacedBy(YSuiteSpacing.XSmall),
    ) {
        sources.forEach { source ->
            YFileSourceRow(
                label = source.label,
                icon = source.kind.icon(),
                selected =
                    activeSectionId == "browser" &&
                        source.id == selectedSourceId,
                onClick = { onSourceSelected(source.id) },
            )
        }

        YFileSourceRow(
            label = browserLabel,
            icon = Icons.Default.Home,
            selected = activeSectionId == "browser",
            onClick = { onSectionSelected("browser") },
        )
        YFileSourceRow(
            label = favoritesLabel,
            icon = Icons.Default.Star,
            selected = activeSectionId == "favorites",
            onClick = { onSectionSelected("favorites") },
        )
        YFileSourceRow(
            label = recentLabel,
            icon = Icons.Default.History,
            selected = activeSectionId == "recent",
            onClick = { onSectionSelected("recent") },
        )
        YFileSourceRow(
            label = trashLabel,
            icon = Icons.Default.Delete,
            selected = activeSectionId == "trash",
            onClick = { onSectionSelected("trash") },
        )

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = YSuiteSpacing.XSmall),
            color = MaterialTheme.colorScheme.outlineVariant,
        ) {
            Box(modifier = Modifier.size(width = 1.dp, height = 1.dp))
        }

        YFileSourceRow(
            label = toolsLabel,
            icon = Icons.Default.Build,
            selected = false,
            onClick = onToolsSelected,
        )
        YFileSourceRow(
            label = settingsLabel,
            icon = Icons.Default.Settings,
            selected = false,
            onClick = onSettingsSelected,
        )
    }
}

@Composable
private fun YFileSourceRow(
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
            modifier = Modifier.padding(
                horizontal = YSuiteSpacing.Small,
                vertical = YSuiteSpacing.Small,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(YSuiteSpacing.Small),
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

@Composable
fun YFileEntryRow(
    title: String,
    subtitle: String?,
    kind: YFileProductItemKind,
    selected: Boolean,
    onOpen: () -> Unit,
    onToggleSelection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        color =
            if (selected) MaterialTheme.colorScheme.primaryContainer
            else Color.Transparent,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = YSuiteSpacing.Medium,
                    vertical = YSuiteSpacing.Small,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(YSuiteSpacing.Medium),
        ) {
            Surface(
                modifier = Modifier.size(42.dp),
                color =
                    if (kind == YFileProductItemKind.Folder) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                shape = MaterialTheme.shapes.medium,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = kind.icon(),
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
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
            IconButton(onClick = onToggleSelection) {
                Icon(
                    imageVector =
                        if (selected) Icons.Default.Star
                        else Icons.Default.MoreVert,
                    contentDescription = null,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileExplorerSelectionTopBar(
    countLabel: String,
    copyLabel: String,
    moveLabel: String,
    trashLabel: String,
    deleteLabel: String,
    clearLabel: String,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onTrash: () -> Unit,
    onDelete: () -> Unit,
    onClear: () -> Unit,
) {
    TopAppBar(
        title = { Text(countLabel) },
        navigationIcon = {
            IconButton(onClick = onClear) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = clearLabel,
                )
            }
        },
        actions = {
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

private fun YFileProductItemKind.icon(): ImageVector =
    when (this) {
        YFileProductItemKind.Folder -> Icons.Default.Folder
        YFileProductItemKind.File -> Icons.Default.Description
        YFileProductItemKind.Archive -> Icons.Default.Archive
        YFileProductItemKind.Link -> Icons.Default.Link
        YFileProductItemKind.Other -> Icons.Default.MoreVert
    }

private fun YFileProductSourceKind.icon(): ImageVector =
    when (this) {
        YFileProductSourceKind.Local -> Icons.Default.Storage
        YFileProductSourceKind.Root -> Icons.Default.Terminal
        YFileProductSourceKind.Document -> Icons.Default.Folder
        YFileProductSourceKind.Archive -> Icons.Default.Archive
        YFileProductSourceKind.Remote -> Icons.Default.Storage
    }
