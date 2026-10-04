package com.yagay.ysuite.feature.yfiles

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yagay.ysuite.designsystem.component.YSuiteConfirmDialog
import com.yagay.ysuite.designsystem.component.YSuiteFormField
import com.yagay.ysuite.designsystem.component.YSuiteListItem
import com.yagay.ysuite.designsystem.component.YSuiteSearchField
import com.yagay.ysuite.designsystem.component.YSuiteSecondaryButton
import com.yagay.ysuite.designsystem.component.YSuiteSection
import com.yagay.ysuite.designsystem.component.YSuiteSectionHeader
import com.yagay.ysuite.designsystem.component.YSuiteStatusBadge
import com.yagay.ysuite.designsystem.component.YSuiteStatusTone
import com.yagay.ysuite.designsystem.component.YSuiteSwitchItem
import com.yagay.ysuite.designsystem.component.YSuiteTextEditorDialog
import com.yagay.ysuite.designsystem.component.YSuiteTextFormDialog
import com.yagay.ysuite.designsystem.component.YSuiteTextInputDialog
import com.yagay.ysuite.designsystem.theme.YSuiteSpacing
import com.yagay.ysuite.feature.yfiles.api.YFileNode
import com.yagay.ysuite.feature.yfiles.api.YFileProviderKind
import com.yagay.ysuite.feature.yfiles.api.YFileRef
import com.yagay.ysuite.feature.yfiles.api.YFileSort
import com.yagay.ysuite.feature.yfiles.api.YFileType
import com.yagay.ysuite.logging.api.YSuiteLogger
import com.yagay.ysuite.platform.api.CapabilityStatus
import com.yagay.ysuite.productui.ProductAdaptiveInfo
import com.yagay.ysuite.productui.filemanager.YFileBreadcrumbBar
import com.yagay.ysuite.productui.filemanager.YFileEntryRow
import com.yagay.ysuite.productui.filemanager.FileExplorerBackButton
import com.yagay.ysuite.productui.filemanager.FileExplorerDetailRow
import com.yagay.ysuite.productui.filemanager.FileExplorerDetailsSheet
import com.yagay.ysuite.productui.filemanager.FileExplorerSearchBar
import com.yagay.ysuite.productui.filemanager.FileExplorerSelectionTopBar
import com.yagay.ysuite.productui.filemanager.FileExplorerSortOption
import com.yagay.ysuite.productui.filemanager.FileExplorerToolAction
import com.yagay.ysuite.productui.filemanager.FileExplorerToolGroup
import com.yagay.ysuite.productui.filemanager.FileExplorerUtilitySurface
import com.yagay.ysuite.productui.filemanager.FileExplorerTopActions
import com.yagay.ysuite.productui.filemanager.FileExplorerWorkspace
import com.yagay.ysuite.productui.filemanager.YFileProductItemKind
import com.yagay.ysuite.productui.filemanager.YFileProductSource
import com.yagay.ysuite.productui.filemanager.YFileProductSourceKind
import com.yagay.ysuite.productui.filemanager.YFileSourcePane
import com.yagay.ysuite.productui.settings.ComposeSettingsGroup
import com.yagay.ysuite.productui.settings.ComposeSettingsLink
import com.yagay.ysuite.productui.settings.ComposeSettingsSurface
import java.io.File
import java.net.URLConnection
import java.text.DateFormat

@Composable
fun YFilesFeatureScreen(
    environment: YFilesEnvironment,
    logger: YSuiteLogger,
) {
    val browser: YFilesViewModel = viewModel(
        factory = BrowserFactory(environment, logger),
    )
    val tools: YFilesToolsViewModel = viewModel(
        factory = ToolsFactory(environment, logger),
    )
    val state by browser.state.collectAsStateWithLifecycle()
    val toolState by tools.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    BackHandler(enabled = browser.canHandleBack()) {
        browser.navigateBack()
    }

    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var confirmEmptyTrash by rememberSaveable { mutableStateOf(false) }

    val treeLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            val data = result.data
            val uri = data?.data
            if (uri != null) {
                browser.addDocumentTree(
                    uri = uri,
                    flags = data.flags,
                )
            }
        }

    LaunchedEffect(toolState.mutationVersion) {
        if (toolState.mutationVersion > 0) {
            browser.refresh()
        }
    }

    when (state.tab) {
        YFilesTab.Files ->
            YFilesBrowserSurface(
                state = state,
                browser = browser,
                onDelete = { confirmDelete = true },
                onEmptyTrash = { confirmEmptyTrash = true },
            )
        YFilesTab.Tools ->
            FileExplorerUtilitySurface(
                title = stringResource(R.string.yfiles_tab_tools),
                navigationIcon = {
                    FileExplorerBackButton(
                        contentDescription =
                            stringResource(R.string.yfiles_tab_files),
                        onClick = {
                            browser.setTab(YFilesTab.Files)
                        },
                    )
                },
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement =
                        Arrangement.spacedBy(YSuiteSpacing.Small),
                ) {
                    toolsContent(
                        browserState = state,
                        toolState = toolState,
                        tools = tools,
                    )
                }
            }
        YFilesTab.Settings ->
            ComposeSettingsSurface(
                title = stringResource(R.string.yfiles_tab_settings),
                navigationIcon = {
                    FileExplorerBackButton(
                        contentDescription =
                            stringResource(R.string.yfiles_tab_files),
                        onClick = {
                            browser.setTab(YFilesTab.Files)
                        },
                    )
                },
            ) { _ ->
                YFilesSettingsContent(
                    state = state,
                    environment = environment,
                    browser = browser,
                    allFilesGranted =
                        Environment.isExternalStorageManager(),
                    onOpenAllFilesSettings = {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                            Uri.parse("package:" + context.packageName),
                        )
                        context.startActivity(intent)
                    },
                    onAddSaf = {
                        val intent = Intent(
                            Intent.ACTION_OPEN_DOCUMENT_TREE,
                        ).addFlags(
                            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION,
                        )
                        treeLauncher.launch(intent)
                    },
                )
            }
    }

    namePromptDialog(
        prompt = state.namePrompt,
        browser = browser,
    )
    toolsDialogs(
        state = toolState,
        browserState = state,
        tools = tools,
    )

    if (confirmDelete) {
        YSuiteConfirmDialog(
            title = stringResource(R.string.yfiles_delete_title),
            message = stringResource(R.string.yfiles_delete_message),
            confirmText = stringResource(R.string.yfiles_confirm),
            dismissText = stringResource(R.string.yfiles_cancel),
            onConfirm = {
                confirmDelete = false
                browser.deleteSelectedPermanently()
            },
            onDismiss = { confirmDelete = false },
        )
    }

    if (confirmEmptyTrash) {
        YSuiteConfirmDialog(
            title = stringResource(R.string.yfiles_empty_trash_title),
            message = stringResource(R.string.yfiles_empty_trash_message),
            confirmText = stringResource(R.string.yfiles_confirm),
            dismissText = stringResource(R.string.yfiles_cancel),
            onConfirm = {
                confirmEmptyTrash = false
                browser.emptyTrash()
            },
            onDismiss = { confirmEmptyTrash = false },
        )
    }
}

@Composable
private fun YFilesBrowserSurface(
    state: YFilesUiState,
    browser: YFilesViewModel,
    onDelete: () -> Unit,
    onEmptyTrash: () -> Unit,
) {
    val directory = state.directory
    val isFavorite =
        directory != null &&
            state.places.favorites.any { it.ref == directory }
    val focusedNode = state.focused
    val detailContent: (@Composable (ProductAdaptiveInfo) -> Unit)? =
        if (focusedNode == null) {
            null
        } else {
            { _: ProductAdaptiveInfo ->
                YFilesDetailPane(
                    node = focusedNode,
                    browser = browser,
                )
            }
        }

    FileExplorerWorkspace(
        title = stringResource(R.string.yfiles_title),
        navigationIcon = null,
        actions = {
            FileExplorerTopActions(
                sortOptions = listOf(
                    FileExplorerSortOption(
                        YFileSort.Name.name,
                        stringResource(R.string.yfiles_sort_name),
                    ),
                    FileExplorerSortOption(
                        YFileSort.Modified.name,
                        stringResource(R.string.yfiles_sort_modified),
                    ),
                    FileExplorerSortOption(
                        YFileSort.Size.name,
                        stringResource(R.string.yfiles_sort_size),
                    ),
                    FileExplorerSortOption(
                        YFileSort.Type.name,
                        stringResource(R.string.yfiles_sort_type),
                    ),
                ),
                selectedSortId = state.sort.name,
                descending = state.descending,
                ascendingLabel =
                    stringResource(R.string.yfiles_sort_ascending),
                descendingLabel =
                    stringResource(R.string.yfiles_sort_descending),
                onSortSelected = {
                    browser.setSort(YFileSort.valueOf(it))
                },
                onDescendingChange = browser::setDescending,
                showHidden = state.showHidden,
                showHiddenLabel =
                    stringResource(R.string.yfiles_show_hidden),
                onShowHiddenChange = browser::setShowHidden,
                recursive = state.recursive,
                recursiveLabel =
                    stringResource(R.string.yfiles_recursive),
                onRecursiveChange = browser::setRecursive,
            )
        },
        drawerContent = { _, closeDrawer ->
            YFilesSourcePane(
                state = state,
                browser = browser,
                onNavigate = closeDrawer,
            )
        },
        breadcrumb = {
            YFileBreadcrumbBar(
                path = directory?.path
                    ?: stringResource(R.string.yfiles_mode_directory),
                providerLabel = directory?.providerId
                    ?: state.activeProviderId.orEmpty(),
                favorite = isFavorite,
                onRoot = {
                    state.activeProviderId?.let {
                        browser.selectProvider(it)
                    }
                },
                onNavigatePath = browser::navigatePath,
                onRefresh = browser::refresh,
                onFavorite = browser::toggleFavorite,
            )
        },
        commandBar = {
            YFilesCommandBar(
                state = state,
                browser = browser,
            )
        },
        detailPane = detailContent,
        selectionTopBar =
            if (state.selected.isNotEmpty()) {
                {
                    FileExplorerSelectionTopBar(
                        countLabel = stringResource(
                            R.string.yfiles_selected_count,
                            state.selected.size,
                        ),
                        selectAllLabel =
                            stringResource(R.string.yfiles_select_all),
                        copyLabel = stringResource(R.string.yfiles_copy),
                        moveLabel = stringResource(R.string.yfiles_move),
                        trashLabel = stringResource(R.string.yfiles_move_to_bin),
                        deleteLabel = stringResource(R.string.yfiles_delete_permanently),
                        clearLabel = stringResource(R.string.yfiles_clear_selection),
                        onSelectAll = browser::selectAll,
                        onCopy = browser::prepareCopy,
                        onMove = browser::prepareMove,
                        onTrash = browser::moveSelectedToTrash,
                        onDelete = onDelete,
                        onClear = browser::clearSelection,
                    )
                }
            } else {
                null
            },
    ) { adaptive ->
        YFilesMainContent(
            state = state,
            browser = browser,
            adaptive = adaptive,
            context = LocalContext.current,
            onEmptyTrash = onEmptyTrash,
        )
    }
}

@Composable
private fun YFilesCommandBar(
    state: YFilesUiState,
    browser: YFilesViewModel,
) {
    if (state.mode == YFilesBrowserMode.Directory) {
        FileExplorerSearchBar(
            query = state.query,
            searchLabel = stringResource(R.string.yfiles_search),
            onQueryChange = browser::setQuery,
        )
    }
}

@Composable
private fun YFilesSourcePane(
    state: YFilesUiState,
    browser: YFilesViewModel,
    onNavigate: () -> Unit = {},
) {
    YFileSourcePane(
        sources =
            state.providers
                .filter { provider ->
                    provider.kind != YFileProviderKind.Root ||
                        state.rootStatus == CapabilityStatus.Available
                }
                .map { provider ->
                YFileProductSource(
                    id = provider.id,
                    label = providerLabel(provider.kind),
                    kind = provider.kind.toProductSourceKind(),
                )
            },
        selectedSourceId = state.activeProviderId,
        browserLabel = stringResource(R.string.yfiles_mode_directory),
        favoritesLabel = stringResource(R.string.yfiles_mode_favorites),
        recentLabel = stringResource(R.string.yfiles_mode_recent),
        trashLabel = stringResource(R.string.yfiles_mode_trash),
        toolsLabel = stringResource(R.string.yfiles_tab_tools),
        settingsLabel = stringResource(R.string.yfiles_tab_settings),
        activeSectionId = state.mode.productSectionId(),
        onSourceSelected = { providerId ->
            browser.selectProvider(providerId)
            onNavigate()
        },
        onSectionSelected = { section ->
            browser.setMode(
                when (section) {
                    "favorites" -> YFilesBrowserMode.Favorites
                    "recent" -> YFilesBrowserMode.Recent
                    "trash" -> YFilesBrowserMode.Trash
                    else -> YFilesBrowserMode.Directory
                },
            )
            onNavigate()
        },
        onToolsSelected = {
            browser.setTab(YFilesTab.Tools)
            onNavigate()
        },
        onSettingsSelected = {
            browser.setTab(YFilesTab.Settings)
            onNavigate()
        },
    )
}

@Composable
private fun YFilesMainContent(
    state: YFilesUiState,
    browser: YFilesViewModel,
    adaptive: ProductAdaptiveInfo,
    context: Context,
    onEmptyTrash: () -> Unit,
) {
    when (state.mode) {
        YFilesBrowserMode.Directory ->
            YFilesDirectoryList(
                state = state,
                browser = browser,
                context = context,
            )
        YFilesBrowserMode.Favorites ->
            YFilesSavedList(
                locations = state.places.favorites,
                emptyText = stringResource(R.string.yfiles_no_favorites),
                browser = browser,
            )
        YFilesBrowserMode.Recent ->
            YFilesSavedList(
                locations = state.places.recent,
                emptyText = stringResource(R.string.yfiles_no_recent),
                browser = browser,
            )
        YFilesBrowserMode.Trash ->
            YFilesTrashList(
                state = state,
                browser = browser,
                onEmptyTrash = onEmptyTrash,
            )
    }

    if (!adaptive.isExpanded) {
        state.focused?.let { node ->
            FileExplorerDetailsSheet(
                onDismiss = { browser.focus(null) },
            ) {
                YFilesDetailContent(
                    node = node,
                    browser = browser,
                )
            }
        }
    }
}

@Composable
private fun YFilesDirectoryList(
    state: YFilesUiState,
    browser: YFilesViewModel,
    context: Context,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
    ) {
        if (state.loading && state.entries.isEmpty()) {
            item {
                YSuiteListItem(
                    title = stringResource(R.string.yfiles_loading),
                )
            }
        } else if (state.entries.isEmpty()) {
            item {
                YSuiteListItem(
                    title = stringResource(R.string.yfiles_empty),
                )
            }
        }

        items(
            items = state.entries,
            key = { it.ref.providerId + "|" + it.ref.path },
        ) { node ->
            val selected = node.ref in state.selected
            YFileEntryRow(
                title = node.name,
                subtitle = nodeSubtitle(node),
                kind = node.productItemKind(),
                selected = selected,
                selectionMode = state.selected.isNotEmpty(),
                onOpen = {
                    if (
                        node.type == YFileType.Directory ||
                        (
                            node.type == YFileType.File &&
                            node.name.endsWith(".zip", ignoreCase = true)
                        )
                    ) {
                        browser.open(node)
                    } else if (!openExternalFile(context, node)) {
                        browser.focus(node)
                    }
                },
                onToggleSelection = {
                    browser.toggleSelection(node)
                },
            )
        }

        state.clipboard?.let { clipboard ->
            item {
                FileExplorerToolGroup(
                    title = stringResource(
                        if (clipboard.move) {
                            R.string.yfiles_clipboard_move
                        } else {
                            R.string.yfiles_clipboard_copy
                        },
                        clipboard.refs.size,
                    ),
                ) {
                    FileExplorerToolAction(
                        text = stringResource(R.string.yfiles_paste_here),
                        onClick = browser::pasteHere,
                    )
                }
            }
        }

        state.progress?.let { progress ->
            item {
                YSuiteStatusBadge(
                    text = stringResource(
                        R.string.yfiles_progress,
                        progress.currentName,
                        formatBytes(progress.completedBytes),
                    ),
                    tone = YSuiteStatusTone.Neutral,
                )
            }
        }

        state.error?.let { error ->
            item {
                YSuiteStatusBadge(
                    text = error,
                    tone = YSuiteStatusTone.Error,
                )
            }
        }
    }
}

@Composable
private fun YFilesSavedList(
    locations: List<YFileLocationRecord>,
    emptyText: String,
    browser: YFilesViewModel,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        if (locations.isEmpty()) {
            item {
                YSuiteListItem(title = emptyText)
            }
        }
        items(
            items = locations,
            key = { it.ref.providerId + "|" + it.ref.path },
        ) { location ->
            YFileEntryRow(
                title = location.label,
                subtitle = location.ref.path,
                kind = YFileProductItemKind.Folder,
                selected = false,
                selectionMode = false,
                onOpen = { browser.navigateSaved(location) },
                onToggleSelection = { browser.navigateSaved(location) },
            )
        }
    }
}

@Composable
private fun YFilesTrashList(
    state: YFilesUiState,
    browser: YFilesViewModel,
    onEmptyTrash: () -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        if (state.trashRecords.isEmpty()) {
            item {
                YSuiteListItem(
                    title = stringResource(R.string.yfiles_no_trash),
                )
            }
        }
        items(
            items = state.trashRecords,
            key = { it.id },
        ) { record ->
            YFileEntryRow(
                title = record.originalName,
                subtitle = record.originalParent.path,
                kind = YFileProductItemKind.File,
                selected = false,
                selectionMode = false,
                onOpen = { browser.restoreTrash(record.id) },
                onToggleSelection = { browser.restoreTrash(record.id) },
            )
        }
        if (state.trashRecords.isNotEmpty()) {
            item {
                FileExplorerToolAction(
                    text = stringResource(R.string.yfiles_empty_trash),
                    onClick = onEmptyTrash,
                )
            }
        }
    }
}

@Composable
private fun YFilesDetailPane(
    node: YFileNode,
    browser: YFilesViewModel,
) {
    Column(
        modifier = Modifier.padding(YSuiteSpacing.Medium),
    ) {
        YFilesDetailContent(
            node = node,
            browser = browser,
        )
    }
}

@Composable
private fun YFilesDetailContent(
    node: YFileNode,
    browser: YFilesViewModel,
) {
    FileExplorerToolGroup(
        title = stringResource(R.string.yfiles_details),
    ) {
        FileExplorerDetailRow(
            title = node.name,
            subtitle = nodeTypeLabel(node.type),
        )
        FileExplorerDetailRow(
            title = stringResource(R.string.yfiles_provider),
            subtitle = node.ref.providerId,
        )
        node.sizeBytes?.let {
            FileExplorerDetailRow(
                title = stringResource(R.string.yfiles_size),
                subtitle = formatBytes(it),
            )
        }
        node.modifiedAtMillis?.let {
            FileExplorerDetailRow(
                title = stringResource(R.string.yfiles_modified),
                subtitle = formatDate(it),
            )
        }
        FileExplorerToolAction(
            text = stringResource(R.string.yfiles_rename),
            onClick = { browser.beginRename(node) },
        )
        FileExplorerToolAction(
            text = stringResource(R.string.yfiles_close),
            onClick = { browser.focus(null) },
        )
    }
}

@Composable
private fun YFilesSettingsContent(
    state: YFilesUiState,
    environment: YFilesEnvironment,
    browser: YFilesViewModel,
    allFilesGranted: Boolean,
    onOpenAllFilesSettings: () -> Unit,
    onAddSaf: () -> Unit,
) {
    ComposeSettingsGroup(
        title = stringResource(R.string.yfiles_settings_access),
    ) {
        ComposeSettingsLink(
            title = stringResource(R.string.yfiles_all_files_access),
            subtitle = stringResource(
                if (allFilesGranted) {
                    R.string.yfiles_granted
                } else {
                    R.string.yfiles_not_granted
                },
            ),
            enabled = !allFilesGranted,
            onClick = onOpenAllFilesSettings,
        )
        ComposeSettingsLink(
            title = stringResource(R.string.yfiles_root_access),
            subtitle = rootStatusText(state.rootStatus),
            onClick = browser::refreshRootStatus,
        )
    }

    ComposeSettingsGroup(
        title = stringResource(R.string.yfiles_saf),
    ) {
        ComposeSettingsLink(
            title = stringResource(R.string.yfiles_add_saf),
            onClick = onAddSaf,
        )
        val trees = environment.documentTrees.trees()
        if (trees.isEmpty()) {
            ComposeSettingsLink(
                title = stringResource(R.string.yfiles_no_saf),
                enabled = false,
                onClick = {},
            )
        } else {
            trees.forEach { tree ->
                ComposeSettingsLink(
                    title = tree.toString(),
                    subtitle = stringResource(R.string.yfiles_remove_saf),
                    onClick = {
                        browser.removeDocumentTree(tree)
                    },
                )
            }
        }
    }

}

private fun YFilesBrowserMode.productSectionId(): String =
    when (this) {
        YFilesBrowserMode.Directory -> "browser"
        YFilesBrowserMode.Favorites -> "favorites"
        YFilesBrowserMode.Recent -> "recent"
        YFilesBrowserMode.Trash -> "trash"
    }

private fun YFileProviderKind.toProductSourceKind(): YFileProductSourceKind =
    when (this) {
        YFileProviderKind.Local -> YFileProductSourceKind.Local
        YFileProviderKind.Document -> YFileProductSourceKind.Document
        YFileProviderKind.Root -> YFileProductSourceKind.Root
        YFileProviderKind.Archive -> YFileProductSourceKind.Archive
        YFileProviderKind.Remote -> YFileProductSourceKind.Remote
    }

private fun YFileNode.productItemKind(): YFileProductItemKind =
    when {
        type == YFileType.Directory ->
            YFileProductItemKind.Folder
        type == YFileType.SymbolicLink ->
            YFileProductItemKind.Link
        name.endsWith(".zip", ignoreCase = true) ->
            YFileProductItemKind.Archive
        type == YFileType.File ->
            YFileProductItemKind.File
        else ->
            YFileProductItemKind.Other
    }

private fun LazyListScope.toolsContent(
    browserState: YFilesUiState,
    toolState: YFilesToolsUiState,
    tools: YFilesToolsViewModel,
) {
    val directory =
        browserState.directory
    val selectedNodes =
        browserState.entries.filter {
            it.ref in browserState.selected
        }
    val single =
        selectedNodes.singleOrNull()
    val singleFile =
        single?.takeIf {
            it.type == YFileType.File
        }

    item {
        FileExplorerToolGroup(
            title = stringResource(
                R.string
                    .yfiles_tools_current,
            ),
        ) {
            if (directory != null) {
                FileExplorerToolAction(
                    text = stringResource(
                        R.string.yfiles_analyze,
                    ),
                    onClick = {
                        tools.analyze(
                            directory,
                        )
                    },
                )
                FileExplorerToolAction(
                    text = stringResource(
                        R.string
                            .yfiles_duplicates,
                    ),
                    onClick = {
                        tools.duplicates(
                            directory,
                        )
                    },
                )
                FileExplorerToolAction(
                    text = stringResource(
                        R.string
                            .yfiles_cleanup_scan,
                    ),
                    onClick = {
                        tools.cleanup(
                            directory,
                        )
                    },
                )
                FileExplorerToolAction(
                    text = stringResource(
                        R.string
                            .yfiles_symlink,
                    ),
                    onClick =
                        tools::beginSymlink,
                )
            }
        }
    }

    item {
        FileExplorerToolGroup(
            title = stringResource(
                R.string
                    .yfiles_tools_selected,
            ),
        ) {
            YSuiteListItem(
                title = stringResource(
                    R.string
                        .yfiles_selected_count,
                    selectedNodes.size,
                ),
            )
            if (selectedNodes.isNotEmpty()) {
                FileExplorerToolAction(
                    text = stringResource(
                        R.string
                            .yfiles_create_zip,
                    ),
                    onClick =
                        tools::beginZip,
                )
                FileExplorerToolAction(
                    text = stringResource(
                        R.string
                            .yfiles_bulk_rename,
                    ),
                    onClick =
                        tools::beginRename,
                )
            }
            if (singleFile != null) {
                FileExplorerToolAction(
                    text = stringResource(
                        R.string.yfiles_sha256,
                    ),
                    onClick = {
                        tools.hash(
                            singleFile.ref,
                        )
                    },
                )
                FileExplorerToolAction(
                    text = stringResource(
                        R.string
                            .yfiles_text_editor,
                    ),
                    onClick = {
                        tools.openText(
                            singleFile.ref,
                        )
                    },
                )
                FileExplorerToolAction(
                    text = stringResource(
                        R.string.yfiles_hex,
                    ),
                    onClick = {
                        tools.hex(
                            singleFile.ref,
                        )
                    },
                )
                FileExplorerToolAction(
                    text = stringResource(
                        R.string.yfiles_split,
                    ),
                    onClick =
                        tools::beginSplit,
                )
                FileExplorerToolAction(
                    text = stringResource(
                        R.string.yfiles_chmod,
                    ),
                    onClick = {
                        tools.beginChmod(
                            singleFile,
                        )
                    },
                )
                if (
                    singleFile.name.endsWith(
                        ".zip",
                        ignoreCase = true,
                    ) &&
                    directory != null
                ) {
                    FileExplorerToolAction(
                        text = stringResource(
                            R.string
                                .yfiles_extract_zip,
                        ),
                        onClick = {
                            tools.extractZip(
                                singleFile.ref,
                                directory,
                            )
                        },
                    )
                }
                if (
                    singleFile.name.endsWith(
                        ".part001",
                    )
                ) {
                    FileExplorerToolAction(
                        text = stringResource(
                            R.string.yfiles_join,
                        ),
                        onClick = {
                            tools.join(
                                singleFile.ref,
                            )
                        },
                    )
                }
            }
            if (selectedNodes.size == 2) {
                FileExplorerToolAction(
                    text = stringResource(
                        R.string
                            .yfiles_compare,
                    ),
                    onClick = {
                        tools.compare(
                            selectedNodes.map {
                                it.ref
                            },
                        )
                    },
                )
            }
        }
    }

    if (toolState.busy) {
        item {
            YSuiteStatusBadge(
                text = stringResource(
                    R.string
                        .yfiles_tool_running,
                ),
                tone =
                    YSuiteStatusTone.Neutral,
            )
        }
    }
    toolState.error?.let {
        item {
            YSuiteStatusBadge(
                text = it,
                tone = YSuiteStatusTone.Error,
            )
        }
    }
    toolState.batchResult?.let {
        item {
            YSuiteStatusBadge(
                text = stringResource(
                    R.string
                        .yfiles_operation_result,
                    it.succeeded,
                    it.skipped,
                    it.failed,
                ),
                tone =
                    if (it.failed == 0) {
                        YSuiteStatusTone.Positive
                    } else {
                        YSuiteStatusTone.Warning
                    },
            )
        }
    }

    toolResults(
        state = toolState,
        tools = tools,
    )
}

private fun LazyListScope.toolResults(
    state: YFilesToolsUiState,
    tools: YFilesToolsViewModel,
) {
    state.hash?.let { hash ->
        item {
            FileExplorerToolGroup(
                title = stringResource(
                    R.string.yfiles_sha256,
                ),
            ) {
                YSuiteListItem(
                    title = hash.algorithm,
                    subtitle = hash.hex,
                )
            }
        }
    }

    state.analysis?.let { analysis ->
        item {
            FileExplorerToolGroup(
                title = stringResource(
                    R.string
                        .yfiles_analysis,
                ),
            ) {
                YSuiteListItem(
                    title = stringResource(
                        R.string
                            .yfiles_analysis_items,
                        analysis.fileCount,
                        analysis
                            .directoryCount,
                    ),
                )
                YSuiteListItem(
                    title = stringResource(
                        R.string
                            .yfiles_analysis_size,
                        formatBytes(
                            analysis
                                .totalBytes,
                        ),
                    ),
                )
                if (analysis.truncated) {
                    YSuiteStatusBadge(
                        text = stringResource(
                            R.string
                                .yfiles_analysis_truncated,
                        ),
                        tone =
                            YSuiteStatusTone
                                .Warning,
                    )
                }
                analysis.largestFiles
                    .take(10)
                    .forEach {
                        YSuiteListItem(
                            title = it.name,
                            subtitle =
                                formatBytes(
                                    it.sizeBytes
                                        ?: 0L,
                                ),
                        )
                    }
            }
        }
    }

    if (state.duplicates.isNotEmpty()) {
        item {
            YSuiteSectionHeader(
                title = stringResource(
                    R.string
                        .yfiles_duplicates,
                ),
            )
        }
        items(state.duplicates) { group ->
            YSuiteListItem(
                title = stringResource(
                    R.string
                        .yfiles_duplicate_group,
                    group.nodes.size,
                    formatBytes(
                        group.sizeBytes,
                    ),
                ),
                subtitle =
                    group.nodes
                        .joinToString("\n") {
                            it.ref.path
                        },
            )
        }
    }

    state.hexPreview?.let { hex ->
        item {
            FileExplorerToolGroup(
                title = stringResource(
                    R.string.yfiles_hex,
                ),
            ) {
                YSuiteListItem(
                    title =
                        hex.byteCount
                            .toString() +
                            " B",
                    subtitle = hex.text,
                )
                if (hex.truncated) {
                    YSuiteStatusBadge(
                        text = stringResource(
                            R.string
                                .yfiles_preview_truncated,
                        ),
                        tone =
                            YSuiteStatusTone
                                .Warning,
                    )
                }
                FileExplorerToolAction(
                    text = stringResource(
                        R.string.yfiles_close,
                    ),
                    onClick =
                        tools::dismissHex,
                )
            }
        }
    }

    state.compare?.let { compare ->
        item {
            FileExplorerToolGroup(
                title = stringResource(
                    R.string.yfiles_compare,
                ),
            ) {
                YSuiteStatusBadge(
                    text = stringResource(
                        if (
                            compare.identical
                        ) {
                            R.string
                                .yfiles_compare_identical
                        } else {
                            R.string
                                .yfiles_compare_different
                        },
                    ),
                    tone =
                        if (
                            compare.identical
                        ) {
                            YSuiteStatusTone
                                .Positive
                        } else {
                            YSuiteStatusTone
                                .Warning
                        },
                )
                YSuiteListItem(
                    title = stringResource(R.string.yfiles_compare_left_hash),
                    subtitle =
                        compare.leftHash,
                )
                YSuiteListItem(
                    title = stringResource(R.string.yfiles_compare_right_hash),
                    subtitle =
                        compare.rightHash,
                )
            }
        }
    }

    state.cleanup?.let { cleanup ->
        item {
            FileExplorerToolGroup(
                title = stringResource(
                    R.string
                        .yfiles_cleanup_scan,
                ),
            ) {
                YSuiteListItem(
                    title = stringResource(
                        R.string
                            .yfiles_cleanup_summary,
                        cleanup.scanned,
                        formatBytes(
                            cleanup
                                .totalBytes,
                        ),
                    ),
                )
                YSuiteListItem(
                    title = stringResource(
                        R.string
                            .yfiles_cleanup_large,
                        cleanup.largeFiles
                            .size,
                    ),
                )
                YSuiteListItem(
                    title = stringResource(
                        R.string
                            .yfiles_cleanup_old,
                        cleanup.oldFiles.size,
                    ),
                )
                YSuiteListItem(
                    title = stringResource(
                        R.string
                            .yfiles_cleanup_hidden,
                        cleanup.hiddenFiles
                            .size,
                    ),
                )
                YSuiteListItem(
                    title = stringResource(
                        R.string
                            .yfiles_cleanup_empty,
                        cleanup
                            .emptyDirectories
                            .size,
                    ),
                )
                YSuiteListItem(
                    title = stringResource(
                        R.string
                            .yfiles_cleanup_apk,
                        cleanup.apkFiles.size,
                    ),
                )
                YSuiteListItem(
                    title = stringResource(
                        R.string
                            .yfiles_cleanup_downloads,
                        cleanup.oldDownloads
                            .size,
                    ),
                )
                YSuiteListItem(
                    title = stringResource(
                        R.string
                            .yfiles_cleanup_screenshots,
                        cleanup.screenshots
                            .size,
                    ),
                )
                YSuiteListItem(
                    title = stringResource(
                        R.string
                            .yfiles_cleanup_recordings,
                        cleanup.recordings
                            .size,
                    ),
                )
            }
        }
    }

    if (
        state.hash != null ||
        state.analysis != null ||
        state.duplicates.isNotEmpty() ||
        state.compare != null ||
        state.cleanup != null
    ) {
        item {
            FileExplorerToolAction(
                text = stringResource(
                    R.string
                        .yfiles_clear_results,
                ),
                onClick =
                    tools::clearResults,
            )
        }
    }
}

@Composable
private fun namePromptDialog(
    prompt: YFilesNamePrompt?,
    browser: YFilesViewModel,
) {
    if (prompt == null) {
        return
    }

    val title = when (prompt) {
        is YFilesNamePrompt
            .CreateDirectory ->
            stringResource(
                R.string.yfiles_new_folder,
            )
        is YFilesNamePrompt
            .CreateFile ->
            stringResource(
                R.string.yfiles_new_file,
            )
        is YFilesNamePrompt
            .Rename ->
            stringResource(
                R.string.yfiles_rename,
            )
    }
    val label = when (prompt) {
        is YFilesNamePrompt
            .CreateDirectory ->
            stringResource(
                R.string.yfiles_folder_name,
            )
        is YFilesNamePrompt
            .CreateFile ->
            stringResource(
                R.string.yfiles_file_name,
            )
        is YFilesNamePrompt
            .Rename ->
            stringResource(
                R.string.yfiles_new_name,
            )
    }

    YSuiteTextInputDialog(
        title = title,
        label = label,
        value = prompt.value,
        confirmText = stringResource(
            R.string.yfiles_confirm,
        ),
        dismissText = stringResource(
            R.string.yfiles_cancel,
        ),
        onValueChange =
            browser::updatePromptValue,
        onConfirm =
            browser::confirmPrompt,
        onDismiss =
            browser::dismissPrompt,
    )
}

@Composable
private fun toolsDialogs(
    state: YFilesToolsUiState,
    browserState: YFilesUiState,
    tools: YFilesToolsViewModel,
) {
    val selected =
        browserState.entries
            .filter {
                it.ref in
                    browserState.selected
            }
    val directory =
        browserState.directory

    state.textDocument?.let {
        YSuiteTextEditorDialog(
            title = stringResource(
                R.string.yfiles_text_editor,
            ),
            value = it.text,
            confirmText = stringResource(
                R.string.yfiles_save,
            ),
            dismissText = stringResource(
                R.string.yfiles_cancel,
            ),
            onValueChange =
                tools::updateText,
            onConfirm =
                tools::saveText,
            onDismiss =
                tools::dismissText,
        )
    }

    state.zipName?.let { name ->
        YSuiteTextInputDialog(
            title = stringResource(
                R.string.yfiles_create_zip,
            ),
            label = stringResource(
                R.string.yfiles_archive_name,
            ),
            value = name,
            confirmText = stringResource(
                R.string.yfiles_confirm,
            ),
            dismissText = stringResource(
                R.string.yfiles_cancel,
            ),
            onValueChange =
                tools::updateZipName,
            onConfirm = {
                if (directory != null) {
                    tools.createZip(
                        refs =
                            selected.map {
                                it.ref
                            },
                        destination =
                            directory,
                    )
                }
            },
            onDismiss =
                tools::cancelZip,
        )
    }

    state.renameRule?.let { rule ->
        if (
            state.renamePreview.isEmpty()
        ) {
            YSuiteTextFormDialog(
                title = stringResource(
                    R.string
                        .yfiles_bulk_rename,
                ),
                fields = listOf(
                    YSuiteFormField(
                        id = "prefix",
                        label =
                            stringResource(
                                R.string
                                    .yfiles_rename_prefix,
                            ),
                        value =
                            rule.prefix,
                    ),
                    YSuiteFormField(
                        id = "suffix",
                        label =
                            stringResource(
                                R.string
                                    .yfiles_rename_suffix,
                            ),
                        value =
                            rule.suffix,
                    ),
                    YSuiteFormField(
                        id = "find",
                        label =
                            stringResource(
                                R.string
                                    .yfiles_rename_find,
                            ),
                        value = rule.find,
                    ),
                    YSuiteFormField(
                        id = "replace",
                        label =
                            stringResource(
                                R.string
                                    .yfiles_rename_replace,
                            ),
                        value =
                            rule.replace,
                    ),
                ),
                confirmText =
                    stringResource(
                        R.string.yfiles_preview,
                    ),
                dismissText =
                    stringResource(
                        R.string.yfiles_cancel,
                    ),
                onValueChange =
                    tools::updateRenameRule,
                onConfirm = {
                    tools.previewRename(
                        selected.map {
                            it.ref
                        },
                    )
                },
                onDismiss =
                    tools::cancelRename,
                extraContent = {
                    YSuiteSwitchItem(
                        title =
                            stringResource(
                                R.string
                                    .yfiles_rename_regex,
                            ),
                        checked =
                            rule.regex,
                        onCheckedChange =
                            tools::toggleRegex,
                    )
                },
            )
        } else {
            YSuiteConfirmDialog(
                title = stringResource(
                    R.string
                        .yfiles_bulk_rename,
                ),
                message = stringResource(
                    R.string
                        .yfiles_rename_preview,
                    state.renamePreview.size,
                ),
                confirmText =
                    stringResource(
                        R.string.yfiles_apply,
                    ),
                dismissText =
                    stringResource(
                        R.string.yfiles_cancel,
                    ),
                onConfirm =
                    tools::applyRename,
                onDismiss =
                    tools::cancelRename,
            )
        }
    }

    state.splitSizeMiB?.let { size ->
        YSuiteTextInputDialog(
            title = stringResource(
                R.string.yfiles_split,
            ),
            label = stringResource(
                R.string.yfiles_part_size,
            ),
            value = size,
            confirmText = stringResource(
                R.string.yfiles_confirm,
            ),
            dismissText = stringResource(
                R.string.yfiles_cancel,
            ),
            onValueChange =
                tools::updateSplitSize,
            onConfirm = {
                selected.singleOrNull()
                    ?.let {
                        tools.split(it.ref)
                    }
            },
            onDismiss =
                tools::cancelSplit,
        )
    }

    state.chmodMode?.let { mode ->
        YSuiteTextInputDialog(
            title = stringResource(
                R.string.yfiles_chmod,
            ),
            label = stringResource(
                R.string.yfiles_chmod_mode,
            ),
            value = mode,
            confirmText = stringResource(
                R.string.yfiles_confirm,
            ),
            dismissText = stringResource(
                R.string.yfiles_cancel,
            ),
            onValueChange =
                tools::updateChmod,
            onConfirm = {
                selected.singleOrNull()
                    ?.let {
                        tools.chmod(it.ref)
                    }
            },
            onDismiss =
                tools::cancelChmod,
        )
    }

    state.linkDraft?.let { draft ->
        YSuiteTextFormDialog(
            title = stringResource(
                R.string.yfiles_symlink,
            ),
            fields = listOf(
                YSuiteFormField(
                    id = "name",
                    label = stringResource(
                        R.string
                            .yfiles_symlink_name,
                    ),
                    value = draft.name,
                ),
                YSuiteFormField(
                    id = "target",
                    label = stringResource(
                        R.string
                            .yfiles_symlink_target,
                    ),
                    value = draft.target,
                ),
            ),
            confirmText = stringResource(
                R.string.yfiles_confirm,
            ),
            dismissText = stringResource(
                R.string.yfiles_cancel,
            ),
            onValueChange =
                tools::updateLink,
            onConfirm = {
                if (directory != null) {
                    tools.createSymlink(
                        directory,
                    )
                }
            },
            onDismiss =
                tools::cancelSymlink,
        )
    }
}

private fun openExternalFile(
    context: Context,
    node: YFileNode,
): Boolean {
    val uri =
        when (node.ref.providerId) {
            "document" ->
                runCatching {
                    Uri.parse(node.ref.path)
                }.getOrNull()
            "local" ->
                runCatching {
                    FileProvider.getUriForFile(
                        context,
                        context.packageName + ".yfiles.fileprovider",
                        File(node.ref.path),
                    )
                }.getOrNull()
            else ->
                null
        } ?: return false

    val mime =
        node.mimeType
            ?.takeIf { it.isNotBlank() }
            ?: URLConnection.guessContentTypeFromName(node.name)
            ?: "*/*"

    val intent =
        Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

    return runCatching {
        context.startActivity(intent)
        true
    }.getOrDefault(false)
}

@Composable
private fun providerLabel(
    kind: YFileProviderKind,
): String =
    when (kind) {
        YFileProviderKind.Local ->
            stringResource(
                R.string
                    .yfiles_provider_local,
            )
        YFileProviderKind.Document ->
            stringResource(
                R.string
                    .yfiles_provider_document,
            )
        YFileProviderKind.Root ->
            stringResource(
                R.string
                    .yfiles_provider_root,
            )
        YFileProviderKind.Archive ->
            stringResource(
                R.string
                    .yfiles_provider_archive,
            )
        YFileProviderKind.Remote ->
            stringResource(
                R.string
                    .yfiles_provider_remote,
            )
    }

@Composable
private fun nodeSubtitle(
    node: YFileNode,
): String =
    when (node.type) {
        YFileType.Directory ->
            stringResource(
                R.string.yfiles_type_folder,
            )
        YFileType.File ->
            node.sizeBytes
                ?.let(::formatBytes)
                ?: stringResource(
                    R.string
                        .yfiles_type_file,
                )
        YFileType.SymbolicLink ->
            stringResource(
                R.string.yfiles_type_link,
            )
        YFileType.Other ->
            stringResource(
                R.string.yfiles_type_other,
            )
    }

@Composable
private fun nodeTypeLabel(
    type: YFileType,
): String =
    when (type) {
        YFileType.Directory ->
            stringResource(
                R.string.yfiles_type_folder,
            )
        YFileType.File ->
            stringResource(
                R.string.yfiles_type_file,
            )
        YFileType.SymbolicLink ->
            stringResource(
                R.string.yfiles_type_link,
            )
        YFileType.Other ->
            stringResource(
                R.string.yfiles_type_other,
            )
    }

@Composable
private fun yesNo(
    value: Boolean,
): String =
    stringResource(
        if (value) {
            R.string.yfiles_yes
        } else {
            R.string.yfiles_no
        },
    )

@Composable
private fun rootStatusText(
    status: CapabilityStatus,
): String =
    when (status) {
        CapabilityStatus.Available ->
            stringResource(
                R.string.yfiles_available,
            )
        CapabilityStatus.Unavailable ->
            stringResource(
                R.string.yfiles_unavailable,
            )
        CapabilityStatus.PermissionRequired ->
            stringResource(
                R.string
                    .yfiles_permission_required,
            )
        CapabilityStatus.Error ->
            stringResource(
                R.string.yfiles_status_error,
            )
    }

private fun rootStatusTone(
    status: CapabilityStatus,
): YSuiteStatusTone =
    when (status) {
        CapabilityStatus.Available ->
            YSuiteStatusTone.Positive
        CapabilityStatus.PermissionRequired ->
            YSuiteStatusTone.Warning
        CapabilityStatus.Unavailable ->
            YSuiteStatusTone.Neutral
        CapabilityStatus.Error ->
            YSuiteStatusTone.Error
    }

private fun formatDate(
    millis: Long,
): String =
    DateFormat.getDateTimeInstance(
        DateFormat.SHORT,
        DateFormat.SHORT,
    ).format(millis)

private fun formatBytes(
    value: Long,
): String {
    if (value < 1024L) {
        return value.toString() + " B"
    }
    val units = arrayOf(
        "KiB",
        "MiB",
        "GiB",
        "TiB",
    )
    var amount = value.toDouble()
    var index = -1
    do {
        amount /= 1024.0
        index += 1
    } while (
        amount >= 1024.0 &&
        index < units.lastIndex
    )
    return String.format(
        "%.1f %s",
        amount,
        units[index],
    )
}

private class BrowserFactory(
    private val environment:
        YFilesEnvironment,
    private val logger: YSuiteLogger,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(
        modelClass: Class<T>,
    ): T =
        YFilesViewModel(
            environment,
            logger,
        ) as T
}

private class ToolsFactory(
    private val environment:
        YFilesEnvironment,
    private val logger: YSuiteLogger,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(
        modelClass: Class<T>,
    ): T =
        YFilesToolsViewModel(
            environment,
            logger,
        ) as T
}
