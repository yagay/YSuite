package com.yagay.yfiles

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddBox
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** FileExplorer-style command bar adapted from the rebuild branch to main's repository. */
@Composable
internal fun FileExplorerHostChrome(
    path: String,
    query: String,
    onQuery: (String) -> Unit,
    onNavigate: (String) -> Unit,
    onParent: () -> Unit,
    canParent: Boolean,
    onRefresh: () -> Unit,
    onNewFolder: () -> Unit,
    onNewFile: () -> Unit,
    sortMode: FileSortMode,
    onSortMode: (FileSortMode) -> Unit,
    descending: Boolean,
    onDescending: () -> Unit,
    showHidden: Boolean,
    onShowHidden: () -> Unit,
    recursiveSearch: Boolean,
    onRecursiveSearch: () -> Unit,
    rootMode: Boolean,
    rootAllowed: Boolean,
    onRootMode: () -> Unit,
    busy: Boolean,
) {
    var searching by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp)) {
            IconButton(onClick = onParent, enabled = canParent && !busy) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.parent))
            }
            if (searching) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQuery,
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.search_files)) },
                    modifier = Modifier.weight(1f),
                )
            } else {
                Text(
                    text = path.substringAfterLast('/').ifBlank { "/" },
                    modifier = Modifier.weight(1f).padding(top = 12.dp, start = 4.dp),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = {
                searching = !searching
                if (!searching) onQuery("")
            }) {
                Icon(
                    if (searching) Icons.Default.Close else Icons.Default.Search,
                    contentDescription = stringResource(R.string.search_files),
                )
            }
            IconButton(onClick = onRefresh, enabled = !busy) {
                Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh))
            }
            IconButton(onClick = onNewFolder, enabled = !busy) {
                Icon(Icons.Default.CreateNewFolder, contentDescription = stringResource(R.string.new_folder))
            }
            androidx.compose.foundation.layout.Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.yfiles_browser_options))
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.new_file)) },
                        leadingIcon = { Icon(Icons.Default.AddBox, contentDescription = null) },
                        onClick = { menuExpanded = false; onNewFile() },
                        enabled = !busy,
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.show_hidden)) },
                        trailingIcon = { Text(if (showHidden) "✓" else "") },
                        onClick = { menuExpanded = false; onShowHidden() },
                        enabled = !busy,
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.recursive_search)) },
                        trailingIcon = { Text(if (recursiveSearch) "✓" else "") },
                        onClick = { menuExpanded = false; onRecursiveSearch() },
                        enabled = !rootMode && !busy,
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.root_mode)) },
                        trailingIcon = { Text(if (rootMode) "✓" else "") },
                        onClick = { menuExpanded = false; onRootMode() },
                        enabled = rootAllowed && !busy,
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.descending)) },
                        trailingIcon = { Text(if (descending) "✓" else "") },
                        onClick = { menuExpanded = false; onDescending() },
                        enabled = !rootMode && !busy,
                    )
                    val modeTitles = listOf(
                        R.string.sort_name, R.string.sort_modified,
                        R.string.sort_size, R.string.sort_type,
                    )
                    FileSortMode.entries.forEachIndexed { index, mode ->
                        DropdownMenuItem(
                            text = { Text(stringResource(modeTitles[index])) },
                            trailingIcon = { Text(if (sortMode == mode) "✓" else "") },
                            onClick = { menuExpanded = false; onSortMode(mode) },
                            enabled = !rootMode && !busy,
                        )
                    }
                }
            }
        }
        // FileExplorer's compact location bar replaces oversized path and action cards.
        Row(
            modifier = Modifier.fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            TextButton(onClick = { onNavigate("/") }) { Text("/") }
            var accumulated = ""
            path.split('/').filter { it.isNotBlank() }.forEach { segment ->
                accumulated += "/$segment"
                val destination = accumulated
                TextButton(onClick = { onNavigate(destination) }) {
                    Text(segment, maxLines = 1)
                }
            }
        }
    }
}
