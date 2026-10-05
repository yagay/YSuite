package com.yagay.ysuite.productui.download

import com.yagay.ysuite.productui.YSuiteProductPage

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.yagay.ysuite.designsystem.theme.YSuiteSpacing
import com.yagay.ysuite.productui.ProductAdaptiveInfo

data class QdmDownloadTab(
    val id: String,
    val label: String,
)

data class QdmDownloadMenuLabels(
    val sortDate: String,
    val sortName: String,
    val clearCompleted: String,
    val settings: String,
)

data class QdmDownloadFabLabels(
    val add: String,
    val paste: String,
    val importFile: String,
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
    menuLabels: QdmDownloadMenuLabels? = null,
    fabLabels: QdmDownloadFabLabels? = null,
    onSettings: (() -> Unit)? = null,
    onSortDate: (() -> Unit)? = null,
    onSortName: (() -> Unit)? = null,
    onClearCompleted: (() -> Unit)? = null,
    onPasteClipboard: (() -> Unit)? = null,
    onImportFile: (() -> Unit)? = null,
    content: @Composable (ProductAdaptiveInfo, String) -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var fabExpanded by remember { mutableStateOf(false) }

    YSuiteProductPage(
        title = title,
        modifier = modifier,
        navigationIcon = navigationIcon,
        titleContent = {
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
        actions = {
            if (searchActive) {
                IconButton(onClick = onToggleSearch) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription =
                            closeSearchContentDescription,
                    )
                }
            } else {
                IconButton(onClick = onToggleSearch) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = searchPlaceholder,
                    )
                }
                if (
                    onSettings != null &&
                    menuLabels != null
                ) {
                    IconButton(onClick = onSettings) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription =
                                menuLabels.settings,
                        )
                    }
                }
                if (
                    menuLabels != null &&
                    (
                        onSortDate != null ||
                            onSortName != null ||
                            onClearCompleted != null
                    )
                ) {
                    androidx.compose.foundation.layout.Box {
                        IconButton(
                            onClick = {
                                menuExpanded = true
                            },
                        ) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = null,
                            )
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = {
                                menuExpanded = false
                            },
                        ) {
                            if (onSortDate != null) {
                                DropdownMenuItem(
                                    text = {
                                        Text(menuLabels.sortDate)
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        onSortDate()
                                    },
                                )
                            }
                            if (onSortName != null) {
                                DropdownMenuItem(
                                    text = {
                                        Text(menuLabels.sortName)
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        onSortName()
                                    },
                                )
                            }
                            if (onClearCompleted != null) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            menuLabels
                                                .clearCompleted,
                                        )
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        onClearCompleted()
                                    },
                                )
                            }
                        }
                    }
                }
                actions()
            }
        },
        headerContent = {
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
        },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement =
                    androidx.compose.foundation.layout.Arrangement
                        .spacedBy(YSuiteSpacing.Small),
            ) {
                if (
                    fabExpanded &&
                    fabLabels != null
                ) {
                    if (onImportFile != null) {
                        SmallFloatingActionButton(
                            onClick = {
                                fabExpanded = false
                                onImportFile()
                            },
                        ) {
                            Icon(
                                Icons.Default.UploadFile,
                                contentDescription =
                                    fabLabels.importFile,
                            )
                        }
                    }
                    if (onPasteClipboard != null) {
                        SmallFloatingActionButton(
                            onClick = {
                                fabExpanded = false
                                onPasteClipboard()
                            },
                        ) {
                            Icon(
                                Icons.Default.ContentPaste,
                                contentDescription =
                                    fabLabels.paste,
                            )
                        }
                    }
                    SmallFloatingActionButton(
                        onClick = {
                            fabExpanded = false
                            onAdd()
                        },
                    ) {
                        Icon(
                            Icons.Default.Link,
                            contentDescription =
                                fabLabels.add,
                        )
                    }
                }
                FloatingActionButton(
                    onClick = {
                        if (
                            fabLabels != null &&
                            (
                                onPasteClipboard != null ||
                                    onImportFile != null
                            )
                        ) {
                            fabExpanded = !fabExpanded
                        } else {
                            onAdd()
                        }
                    },
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription =
                            addContentDescription,
                    )
                }
            }
        },
    ) { adaptive ->
        content(adaptive, selectedTabId)
    }
    }
}
