package com.yagay.ysuite.productui.download

import com.yagay.ysuite.productui.ProductSurfaceKind
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
import com.yagay.yui.YUiDropdownMenuItem as DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import com.yagay.yui.YUiFloatingActionButton as FloatingActionButton
import androidx.compose.material3.Icon
import com.yagay.yui.YUiIconButton as IconButton
import com.yagay.yui.YUiOutlinedTextField as OutlinedTextField
import com.yagay.yui.YUiSmallFloatingActionButton as SmallFloatingActionButton
import androidx.compose.material3.Text
import com.yagay.yui.YTabBar
import com.yagay.yui.YTabSpec
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
    val pauseAll: String? = null,
    val resumeAll: String? = null,
    val retryFailed: String? = null,
    val clearFinished: String? = null,
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
    onPauseAll: (() -> Unit)? = null,
    onResumeAll: (() -> Unit)? = null,
    onRetryFailed: (() -> Unit)? = null,
    onClearFinished: (() -> Unit)? = null,
    onPasteClipboard: (() -> Unit)? = null,
    onImportFile: (() -> Unit)? = null,
    content: @Composable (ProductAdaptiveInfo, String) -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var fabExpanded by remember { mutableStateOf(false) }
    // Visual selection and filtered content must never disagree for stale saved tab IDs.
    val effectiveTabId =
        tabs.firstOrNull { it.id == selectedTabId }?.id
            ?: tabs.firstOrNull()?.id
            ?: selectedTabId

    YSuiteProductPage(
        surfaceKind = ProductSurfaceKind.DownloadManager,
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
                            onClearCompleted != null ||
                            onPauseAll != null ||
                            onResumeAll != null ||
                            onRetryFailed != null ||
                            onClearFinished != null
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
                            if (
                                onPauseAll != null &&
                                menuLabels.pauseAll != null
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Text(menuLabels.pauseAll)
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        onPauseAll()
                                    },
                                )
                            }
                            if (
                                onResumeAll != null &&
                                menuLabels.resumeAll != null
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Text(menuLabels.resumeAll)
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        onResumeAll()
                                    },
                                )
                            }
                            if (
                                onRetryFailed != null &&
                                menuLabels.retryFailed != null
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Text(menuLabels.retryFailed)
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        onRetryFailed()
                                    },
                                )
                            }
                            if (
                                onClearFinished != null &&
                                menuLabels.clearFinished != null
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Text(menuLabels.clearFinished)
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        onClearFinished()
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
                YTabBar(
                    tabs = tabs.map { YTabSpec(key = it.id, label = it.label) },
                    selectedKey = effectiveTabId,
                    onSelected = { onTabSelected(it.key) },
                )
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
        content(adaptive, effectiveTabId)
    }
 }
