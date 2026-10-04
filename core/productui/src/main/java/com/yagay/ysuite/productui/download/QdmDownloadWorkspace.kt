package com.yagay.ysuite.productui.download

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.yagay.ysuite.productui.ProductAdaptiveBox
import com.yagay.ysuite.productui.ProductAdaptiveInfo

data class QdmDownloadTab(
    val id: String,
    val label: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QdmDownloadWorkspace(
    title: String,
    tabs: List<QdmDownloadTab>,
    selectedTabId: String,
    onTabSelected: (String) -> Unit,
    searchActive: Boolean,
    searchQuery: String,
    searchPlaceholder: String,
    addContentDescription: String,
    closeSearchContentDescription: String,
    onSearchQueryChange: (String) -> Unit,
    onToggleSearch: () -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (ProductAdaptiveInfo, String) -> Unit,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        if (searchActive) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = onSearchQueryChange,
                                placeholder = {
                                    Text(searchPlaceholder)
                                },
                                singleLine = true,
                            )
                        } else {
                            Text(title)
                        }
                    },
                    navigationIcon = navigationIcon,
                    actions = {
                        if (searchActive) {
                            IconButton(
                                onClick = onToggleSearch,
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription =
                                        closeSearchContentDescription,
                                )
                            }
                        } else {
                            IconButton(
                                onClick = onToggleSearch,
                            ) {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription =
                                        searchPlaceholder,
                                )
                            }
                            actions()
                        }
                    },
                )
                if (tabs.isNotEmpty()) {
                    val selectedIndex =
                        tabs.indexOfFirst {
                            it.id == selectedTabId
                        }.coerceAtLeast(0)
                    ScrollableTabRow(
                        selectedTabIndex = selectedIndex,
                    ) {
                        tabs.forEach { tab ->
                            Tab(
                                selected =
                                    tab.id == selectedTabId,
                                onClick = {
                                    onTabSelected(tab.id)
                                },
                                text = { Text(tab.label) },
                            )
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) {
                Icon(
                    Icons.Default.Add,
                    contentDescription =
                        addContentDescription,
                )
            }
        },
    ) { padding ->
        ProductAdaptiveBox(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    Modifier.padding(padding),
                ),
        ) { adaptive ->
            content(adaptive, selectedTabId)
        }
    }
}
