package com.yagay.ysuite.productui.filemanager

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowUpward
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
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
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
                Icon(Icons.Default.ArrowUpward, contentDescription = null)
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
fun YFileSearchCommandBar(
    query: String,
    searchLabel: String,
    showHiddenLabel: String,
    recursiveLabel: String,
    showHidden: Boolean,
    recursive: Boolean,
    onQueryChange: (String) -> Unit,
    onShowHiddenChange: (Boolean) -> Unit,
    onRecursiveChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = YSuiteSpacing.Small,
                vertical = YSuiteSpacing.XSmall,
            ),
        verticalArrangement = Arrangement.spacedBy(YSuiteSpacing.XSmall),
    ) {
        TextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(YSuiteSpacing.Small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            YFileToggleChip(
                label = showHiddenLabel,
                checked = showHidden,
                onCheckedChange = onShowHiddenChange,
            )
            YFileToggleChip(
                label = recursiveLabel,
                checked = recursive,
                onCheckedChange = onRecursiveChange,
            )
        }
    }
}

@Composable
private fun YFileToggleChip(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Surface(
        modifier = Modifier.clickable {
            onCheckedChange(!checked)
        },
        color =
            if (checked) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = YSuiteSpacing.Small,
                vertical = YSuiteSpacing.XSmall,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(YSuiteSpacing.XSmall),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
            )
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
            )
        }
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
    activeSectionId: String,
    onSourceSelected: (String) -> Unit,
    onSectionSelected: (String) -> Unit,
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

@Composable
fun YFileSelectionBar(
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
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 4.dp,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(YSuiteSpacing.Small),
            horizontalArrangement = Arrangement.spacedBy(YSuiteSpacing.Small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = countLabel,
                style = MaterialTheme.typography.labelLarge,
            )
            YFileAction(copyLabel, Icons.Default.ContentCopy, onCopy)
            YFileAction(moveLabel, Icons.Default.ContentCut, onMove)
            YFileAction(trashLabel, Icons.Default.Delete, onTrash)
            YFileAction(deleteLabel, Icons.Default.Warning, onDelete)
            YFileAction(clearLabel, Icons.Default.MoreVert, onClear)
        }
    }
}

@Composable
private fun YFileAction(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    FilledTonalButton(onClick = onClick) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Text(label)
    }
}

@Composable
fun YFileSectionSwitcher(
    filesLabel: String,
    toolsLabel: String,
    settingsLabel: String,
    selectedId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(YSuiteSpacing.XSmall),
            horizontalArrangement = Arrangement.spacedBy(YSuiteSpacing.XSmall),
        ) {
            listOf(
                "files" to filesLabel,
                "tools" to toolsLabel,
                "settings" to settingsLabel,
            ).forEach { (id, label) ->
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onSelect(id) },
                    color =
                        if (selectedId == id) MaterialTheme.colorScheme.primaryContainer
                        else Color.Transparent,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Text(
                        text = label,
                        modifier = Modifier.padding(YSuiteSpacing.Small),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
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
