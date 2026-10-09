package com.yagay.ydownload

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yagay.yui.YTextField
import com.yagay.yui.YTabBar
import com.yagay.yui.YTabSpec

/** QDM-inspired toolbar from rebuild/product-ui-system, bound to the main-branch engine. */
@Composable
internal fun QdmHostChrome(
    filter: DownloadListFilter,
    onFilter: (DownloadListFilter) -> Unit,
    query: String,
    onQuery: (String) -> Unit,
    addExpanded: Boolean,
    onToggleAdd: () -> Unit,
    onSettings: () -> Unit,
    onBatch: () -> Unit,
) {
    var searching by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            if (searching) {
                YTextField(
                    value = query,
                    onValueChange = onQuery,
                    label = stringResource(R.string.qdm_search_downloads),
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
            } else {
                Text(
                    text = stringResource(R.string.ydownload_tab_tasks),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f).padding(start = 8.dp, top = 12.dp),
                )
            }
            IconButton(onClick = {
                searching = !searching
                if (!searching) onQuery("")
            }) {
                Icon(
                    if (searching) Icons.Default.Close else Icons.Default.Search,
                    contentDescription = stringResource(R.string.qdm_search_downloads),
                )
            }
            IconButton(onClick = onToggleAdd) {
                Icon(
                    if (addExpanded) Icons.Default.Close else Icons.Default.Add,
                    contentDescription = stringResource(R.string.new_download),
                )
            }
            IconButton(onClick = onSettings) {
                Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.ydownload_tab_settings))
            }
            androidx.compose.foundation.layout.Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.qdm_batch_actions))
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.qdm_batch_actions)) },
                        onClick = { showMenu = false; onBatch() },
                    )
                }
            }
        }
        val filters = DownloadListFilter.entries
        YTabBar(
            tabs = filters.map { candidate ->
                YTabSpec(
                    key = candidate.name,
                    label = stringResource(
                        when (candidate) {
                            DownloadListFilter.ALL -> R.string.qdm_filter_all
                            DownloadListFilter.ACTIVE -> R.string.qdm_filter_active
                            DownloadListFilter.COMPLETED -> R.string.qdm_filter_completed
                            DownloadListFilter.FAILED -> R.string.qdm_filter_failed
                        },
                    ),
                )
            },
            selectedKey = filter.name,
            onSelected = { tab ->
                filters.firstOrNull { it.name == tab.key }?.let(onFilter)
            },
        )
    }
}

internal enum class DownloadListFilter { ALL, ACTIVE, COMPLETED, FAILED }

internal fun filterDownloadItems(
    items: List<DownloadItem>,
    filter: DownloadListFilter,
    query: String,
): List<DownloadItem> = items.filter { item ->
    val selected = when (filter) {
        DownloadListFilter.ALL -> true
        DownloadListFilter.ACTIVE -> item.state in setOf(
            DownloadState.RUNNING, DownloadState.QUEUED, DownloadState.PAUSED,
        )
        DownloadListFilter.COMPLETED -> item.state == DownloadState.COMPLETED
        DownloadListFilter.FAILED -> item.state in setOf(DownloadState.FAILED, DownloadState.CANCELLED)
    }
    selected && (query.isBlank() ||
        item.fileName.contains(query.trim(), ignoreCase = true) ||
        item.url.contains(query.trim(), ignoreCase = true))
}
