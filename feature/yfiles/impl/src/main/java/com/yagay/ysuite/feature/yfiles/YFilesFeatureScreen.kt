package com.yagay.ysuite.feature.yfiles

import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yagay.ysuite.designsystem.component.YSuiteConfirmDialog
import com.yagay.ysuite.designsystem.component.YSuiteSegmentedControl
import com.yagay.ysuite.designsystem.component.YSuiteSegmentOption
import com.yagay.ysuite.designsystem.component.YSuiteFormField
import com.yagay.ysuite.designsystem.component.YSuiteDataRow
import com.yagay.ysuite.designsystem.component.YSuiteSearchBar
import com.yagay.ysuite.designsystem.component.YSuiteActionButton
import com.yagay.ysuite.designsystem.component.YSuitePanel
import com.yagay.ysuite.designsystem.component.YSuitePanelLabel
import com.yagay.ysuite.designsystem.component.YSuiteStatusPill
import com.yagay.ysuite.designsystem.component.YSuiteStatusTone
import com.yagay.ysuite.designsystem.component.YSuiteToggleRow
import com.yagay.ysuite.designsystem.component.YSuiteTextEditorDialog
import com.yagay.ysuite.designsystem.component.YSuiteTextFormDialog
import com.yagay.ysuite.designsystem.component.YSuiteTextInputDialog
import com.yagay.ysuite.feature.yfiles.api.YFileNode
import com.yagay.ysuite.feature.yfiles.api.YFileProviderKind
import com.yagay.ysuite.feature.yfiles.api.YFileRef
import com.yagay.ysuite.feature.yfiles.api.YFileSort
import com.yagay.ysuite.feature.yfiles.api.YFileType
import com.yagay.ysuite.logging.api.YSuiteLogger
import com.yagay.ysuite.platform.api.CapabilityStatus
import com.yagay.ysuite.ui.YSuiteManagerListScreen
import com.yagay.ysuite.ui.YSuitePageState
import java.text.DateFormat

@Composable
fun YFilesFeatureScreen(
    environment: YFilesEnvironment,
    logger: YSuiteLogger,
) {
    val browser: YFilesViewModel = viewModel(
        factory = BrowserFactory(
            environment,
            logger,
        ),
    )
    val tools: YFilesToolsViewModel = viewModel(
        factory = ToolsFactory(
            environment,
            logger,
        ),
    )
    val state by browser.state
        .collectAsStateWithLifecycle()
    val toolState by tools.state
        .collectAsStateWithLifecycle()
    val context = LocalContext.current

    var confirmDelete by rememberSaveable {
        mutableStateOf(false)
    }
    var confirmEmptyTrash by rememberSaveable {
        mutableStateOf(false)
    }

    val treeLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts
                .StartActivityForResult(),
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

    LaunchedEffect(
        toolState.mutationVersion,
    ) {
        if (toolState.mutationVersion > 0) {
            browser.refresh()
        }
    }

    val pageState =
        if (
            state.tab == YFilesTab.Files &&
            state.mode ==
                YFilesBrowserMode.Directory &&
            state.loading &&
            state.entries.isEmpty()
        ) {
            YSuitePageState.Loading(
                stringResource(
                    R.string.yfiles_loading,
                ),
            )
        } else {
            YSuitePageState.Content
        }

    YSuiteManagerListScreen(
        title = stringResource(
            R.string.yfiles_title,
        ),
        subtitle = stringResource(
            R.string.yfiles_summary,
        ),
        state = pageState,
        onRetry = browser::refresh,
        header = {
            item {
                YSuiteSegmentedControl(
                    options = listOf(
                        YSuiteSegmentOption(
                            YFilesTab.Files.name,
                            stringResource(
                                R.string
                                    .yfiles_tab_files,
                            ),
                        ),
                        YSuiteSegmentOption(
                            YFilesTab.Tools.name,
                            stringResource(
                                R.string
                                    .yfiles_tab_tools,
                            ),
                        ),
                        YSuiteSegmentOption(
                            YFilesTab.Settings.name,
                            stringResource(
                                R.string
                                    .yfiles_tab_settings,
                            ),
                        ),
                    ),
                    selectedId =
                        state.tab.name,
                    onSelected = {
                        browser.setTab(
                            YFilesTab.valueOf(it),
                        )
                    },
                )
            }
        },
    ) {
        when (state.tab) {
            YFilesTab.Files ->
                filesContent(
                    state = state,
                    browser = browser,
                    onDelete = {
                        confirmDelete = true
                    },
                    onEmptyTrash = {
                        confirmEmptyTrash = true
                    },
                )
            YFilesTab.Tools ->
                toolsContent(
                    browserState = state,
                    toolState = toolState,
                    tools = tools,
                )
            YFilesTab.Settings ->
                settingsContent(
                    state = state,
                    environment = environment,
                    browser = browser,
                    allFilesGranted =
                        Environment
                            .isExternalStorageManager(),
                    onOpenAllFilesSettings = {
                        val intent = Intent(
                            Settings
                                .ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                            Uri.parse(
                                "package:" +
                                    context.packageName,
                            ),
                        )
                        context.startActivity(
                            intent,
                        )
                    },
                    onAddSaf = {
                        val intent = Intent(
                            Intent
                                .ACTION_OPEN_DOCUMENT_TREE,
                        ).addFlags(
                            Intent
                                .FLAG_GRANT_READ_URI_PERMISSION or
                                Intent
                                    .FLAG_GRANT_WRITE_URI_PERMISSION or
                                Intent
                                    .FLAG_GRANT_PERSISTABLE_URI_PERMISSION,
                        )
                        treeLauncher.launch(
                            intent,
                        )
                    },
                )
        }

        if (state.error != null) {
            item {
                YSuiteStatusPill(
                    text = state.error.orEmpty(),
                    tone =
                        YSuiteStatusTone.Error,
                )
            }
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
            title = stringResource(
                R.string.yfiles_delete_title,
            ),
            message = stringResource(
                R.string.yfiles_delete_message,
            ),
            confirmText = stringResource(
                R.string.yfiles_confirm,
            ),
            dismissText = stringResource(
                R.string.yfiles_cancel,
            ),
            onConfirm = {
                confirmDelete = false
                browser
                    .deleteSelectedPermanently()
            },
            onDismiss = {
                confirmDelete = false
            },
        )
    }

    if (confirmEmptyTrash) {
        YSuiteConfirmDialog(
            title = stringResource(
                R.string
                    .yfiles_empty_trash_title,
            ),
            message = stringResource(
                R.string
                    .yfiles_empty_trash_message,
            ),
            confirmText = stringResource(
                R.string.yfiles_confirm,
            ),
            dismissText = stringResource(
                R.string.yfiles_cancel,
            ),
            onConfirm = {
                confirmEmptyTrash = false
                browser.emptyTrash()
            },
            onDismiss = {
                confirmEmptyTrash = false
            },
        )
    }
}

private fun LazyListScope.filesContent(
    state: YFilesUiState,
    browser: YFilesViewModel,
    onDelete: () -> Unit,
    onEmptyTrash: () -> Unit,
) {
    item {
        YSuiteSegmentedControl(
            options = listOf(
                YSuiteSegmentOption(
                    YFilesBrowserMode
                        .Directory.name,
                    stringResource(
                        R.string
                            .yfiles_mode_directory,
                    ),
                ),
                YSuiteSegmentOption(
                    YFilesBrowserMode
                        .Favorites.name,
                    stringResource(
                        R.string
                            .yfiles_mode_favorites,
                    ),
                ),
                YSuiteSegmentOption(
                    YFilesBrowserMode
                        .Recent.name,
                    stringResource(
                        R.string
                            .yfiles_mode_recent,
                    ),
                ),
                YSuiteSegmentOption(
                    YFilesBrowserMode
                        .Trash.name,
                    stringResource(
                        R.string
                            .yfiles_mode_trash,
                    ),
                ),
            ),
            selectedId = state.mode.name,
            onSelected = {
                browser.setMode(
                    YFilesBrowserMode
                        .valueOf(it),
                )
            },
        )
    }

    when (state.mode) {
        YFilesBrowserMode.Directory ->
            directoryContent(
                state,
                browser,
                onDelete,
            )
        YFilesBrowserMode.Favorites ->
            savedLocations(
                titleRes =
                    R.string
                        .yfiles_mode_favorites,
                emptyRes =
                    R.string
                        .yfiles_no_favorites,
                locations =
                    state.places.favorites,
                browser = browser,
            )
        YFilesBrowserMode.Recent ->
            savedLocations(
                titleRes =
                    R.string.yfiles_mode_recent,
                emptyRes =
                    R.string.yfiles_no_recent,
                locations =
                    state.places.recent,
                browser = browser,
            )
        YFilesBrowserMode.Trash ->
            trashContent(
                state,
                browser,
                onEmptyTrash,
            )
    }
}

private fun LazyListScope.directoryContent(
    state: YFilesUiState,
    browser: YFilesViewModel,
    onDelete: () -> Unit,
) {
    item {
        YSuitePanel(
            title = stringResource(
                R.string.yfiles_sources,
            ),
        ) {
            YSuiteSegmentedControl(
                options =
                    state.providers.map {
                        YSuiteSegmentOption(
                            id = it.id,
                            label =
                                providerLabel(
                                    it.kind,
                                ),
                        )
                    },
                selectedId =
                    state.activeProviderId,
                onSelected =
                    browser::selectProvider,
            )
        }
    }

    state.directory?.let { directory ->
        item {
            val isFavorite =
                state.places.favorites
                    .any {
                        it.ref == directory
                    }
            YSuitePanel(
                title = stringResource(
                    R.string.yfiles_location,
                ),
            ) {
                YSuiteDataRow(
                    title = directory.path,
                    subtitle =
                        directory.providerId,
                )
                YSuiteActionButton(
                    text = stringResource(
                        R.string.yfiles_parent,
                    ),
                    onClick = browser::parent,
                )
                YSuiteActionButton(
                    text = stringResource(
                        R.string.yfiles_refresh,
                    ),
                    onClick = browser::refresh,
                )
                YSuiteActionButton(
                    text = stringResource(
                        if (isFavorite) {
                            R.string
                                .yfiles_remove_favorite
                        } else {
                            R.string
                                .yfiles_add_favorite
                        },
                    ),
                    onClick =
                        browser::toggleFavorite,
                )
                YSuiteActionButton(
                    text = stringResource(
                        R.string
                            .yfiles_new_folder,
                    ),
                    onClick =
                        browser
                            ::beginCreateDirectory,
                )
                YSuiteActionButton(
                    text = stringResource(
                        R.string.yfiles_new_file,
                    ),
                    onClick =
                        browser::beginCreateFile,
                )
            }
        }

        item {
            YSuitePanel(
                title = stringResource(
                    R.string.yfiles_search,
                ),
            ) {
                YSuiteSearchBar(
                    value = state.query,
                    onValueChange =
                        browser::setQuery,
                    label = stringResource(
                        R.string.yfiles_search,
                    ),
                )
                YSuiteToggleRow(
                    title = stringResource(
                        R.string
                            .yfiles_recursive,
                    ),
                    checked = state.recursive,
                    onCheckedChange =
                        browser::setRecursive,
                )
                YSuiteToggleRow(
                    title = stringResource(
                        R.string
                            .yfiles_show_hidden,
                    ),
                    checked =
                        state.showHidden,
                    onCheckedChange =
                        browser::setShowHidden,
                )
                YSuiteSegmentedControl(
                    options = listOf(
                        YSuiteSegmentOption(
                            YFileSort.Name.name,
                            stringResource(
                                R.string
                                    .yfiles_sort_name,
                            ),
                        ),
                        YSuiteSegmentOption(
                            YFileSort
                                .Modified.name,
                            stringResource(
                                R.string
                                    .yfiles_sort_modified,
                            ),
                        ),
                        YSuiteSegmentOption(
                            YFileSort.Size.name,
                            stringResource(
                                R.string
                                    .yfiles_sort_size,
                            ),
                        ),
                        YSuiteSegmentOption(
                            YFileSort.Type.name,
                            stringResource(
                                R.string
                                    .yfiles_sort_type,
                            ),
                        ),
                    ),
                    selectedId =
                        state.sort.name,
                    onSelected = {
                        browser.setSort(
                            YFileSort.valueOf(it),
                        )
                    },
                )
                YSuiteSegmentedControl(
                    options = listOf(
                        YSuiteSegmentOption(
                            "asc",
                            stringResource(
                                R.string
                                    .yfiles_ascending,
                            ),
                        ),
                        YSuiteSegmentOption(
                            "desc",
                            stringResource(
                                R.string
                                    .yfiles_descending,
                            ),
                        ),
                    ),
                    selectedId =
                        if (
                            state.descending
                        ) {
                            "desc"
                        } else {
                            "asc"
                        },
                    onSelected = {
                        browser.setDescending(
                            it == "desc",
                        )
                    },
                )
            }
        }
    }

    if (state.selected.isNotEmpty()) {
        item {
            YSuitePanel(
                title = stringResource(
                    R.string
                        .yfiles_selected_count,
                    state.selected.size,
                ),
            ) {
                YSuiteActionButton(
                    text = stringResource(
                        R.string.yfiles_copy,
                    ),
                    onClick =
                        browser::prepareCopy,
                )
                YSuiteActionButton(
                    text = stringResource(
                        R.string.yfiles_move,
                    ),
                    onClick =
                        browser::prepareMove,
                )
                YSuiteActionButton(
                    text = stringResource(
                        R.string
                            .yfiles_move_to_bin,
                    ),
                    onClick =
                        browser
                            ::moveSelectedToTrash,
                )
                YSuiteActionButton(
                    text = stringResource(
                        R.string
                            .yfiles_delete_permanently,
                    ),
                    onClick = onDelete,
                )
                YSuiteActionButton(
                    text = stringResource(
                        R.string
                            .yfiles_clear_selection,
                    ),
                    onClick =
                        browser::clearSelection,
                )
            }
        }
    }

    state.clipboard?.let { clipboard ->
        item {
            YSuitePanel(
                title = stringResource(
                    if (clipboard.move) {
                        R.string
                            .yfiles_clipboard_move
                    } else {
                        R.string
                            .yfiles_clipboard_copy
                    },
                    clipboard.refs.size,
                ),
            ) {
                YSuiteActionButton(
                    text = stringResource(
                        R.string
                            .yfiles_paste_here,
                    ),
                    onClick =
                        browser::pasteHere,
                )
            }
        }
    }

    state.progress?.let { progress ->
        item {
            YSuiteStatusPill(
                text = stringResource(
                    R.string.yfiles_progress,
                    progress.currentName,
                    formatBytes(
                        progress.completedBytes,
                    ),
                ),
                tone =
                    YSuiteStatusTone.Neutral,
            )
        }
    }

    state.operationResult?.let { result ->
        item {
            YSuiteStatusPill(
                text = stringResource(
                    R.string
                        .yfiles_operation_result,
                    result.succeeded,
                    result.skipped,
                    result.failed,
                ),
                tone =
                    if (result.failed == 0) {
                        YSuiteStatusTone.Positive
                    } else {
                        YSuiteStatusTone.Warning
                    },
            )
        }
    }

    item {
        YSuitePanelLabel(
            title = stringResource(
                R.string.yfiles_files,
            ),
        )
    }

    if (
        !state.loading &&
        state.entries.isEmpty()
    ) {
        item {
            YSuiteDataRow(
                title = stringResource(
                    R.string.yfiles_empty,
                ),
            )
        }
    }

    items(
        items = state.entries,
        key = {
            it.ref.providerId +
                "|" +
                it.ref.path
        },
    ) { node ->
        val selected =
            node.ref in state.selected
        YSuiteDataRow(
            title = node.name,
            subtitle =
                nodeSubtitle(node),
            kind = nodeItemKind(node.type),
            selected = selected,
            modifier = Modifier.clickable {
                browser.open(node)
            },
            trailing = {
                YSuiteActionButton(
                    text = stringResource(
                        if (selected) {
                            R.string
                                .yfiles_unselect
                        } else {
                            R.string
                                .yfiles_select
                        },
                    ),
                    onClick = {
                        browser
                            .toggleSelection(
                                node,
                            )
                    },
                )
            },
        )
    }

    state.focused?.let { node ->
        item {
            YSuitePanel(
                title = stringResource(
                    R.string.yfiles_details,
                ),
            ) {
                YSuiteDataRow(
                    title = node.name,
                    subtitle =
                        nodeTypeLabel(
                            node.type,
                        ),
                )
                YSuiteDataRow(
                    title = stringResource(
                        R.string.yfiles_provider,
                    ),
                    subtitle =
                        node.ref.providerId,
                )
                node.sizeBytes?.let {
                    YSuiteDataRow(
                        title =
                            stringResource(
                                R.string
                                    .yfiles_size,
                            ),
                        subtitle =
                            formatBytes(it),
                    )
                }
                node.modifiedAtMillis
                    ?.let {
                        YSuiteDataRow(
                            title =
                                stringResource(
                                    R.string
                                        .yfiles_modified,
                                ),
                            subtitle =
                                formatDate(it),
                        )
                    }
                YSuiteDataRow(
                    title = stringResource(
                        R.string.yfiles_access,
                    ),
                    subtitle =
                        stringResource(
                            R.string
                                .yfiles_access_value,
                            yesNo(
                                node.readable,
                            ),
                            yesNo(
                                node.writable,
                            ),
                            yesNo(
                                node.executable,
                            ),
                        ),
                )
                YSuiteActionButton(
                    text = stringResource(
                        R.string.yfiles_rename,
                    ),
                    onClick = {
                        browser.beginRename(
                            node,
                        )
                    },
                )
                YSuiteActionButton(
                    text = stringResource(
                        R.string.yfiles_close,
                    ),
                    onClick = {
                        browser.focus(null)
                    },
                )
            }
        }
    }
}

private fun LazyListScope.savedLocations(
    titleRes: Int,
    emptyRes: Int,
    locations: List<YFileLocationRecord>,
    browser: YFilesViewModel,
) {
    item {
        YSuitePanelLabel(
            title = stringResource(titleRes),
        )
    }
    if (locations.isEmpty()) {
        item {
            YSuiteDataRow(
                title =
                    stringResource(emptyRes),
            )
        }
    }
    items(
        items = locations,
        key = {
            it.ref.providerId +
                "|" +
                it.ref.path
        },
    ) { location ->
        YSuiteDataRow(
            title = location.label,
            subtitle =
                location.ref.providerId +
                    " · " +
                    location.ref.path,
            modifier = Modifier.clickable {
                browser.navigateSaved(
                    location,
                )
            },
        )
    }
}

private fun LazyListScope.trashContent(
    state: YFilesUiState,
    browser: YFilesViewModel,
    onEmptyTrash: () -> Unit,
) {
    item {
        YSuitePanelLabel(
            title = stringResource(
                R.string.yfiles_mode_trash,
            ),
        )
    }
    if (state.trashRecords.isEmpty()) {
        item {
            YSuiteDataRow(
                title = stringResource(
                    R.string.yfiles_no_trash,
                ),
            )
        }
    }
    items(
        items = state.trashRecords,
        key = { it.id },
    ) { record ->
        YSuiteDataRow(
            title = record.originalName,
            subtitle =
                record.originalParent.path,
            trailing = {
                YSuiteActionButton(
                    text = stringResource(
                        R.string.yfiles_restore,
                    ),
                    onClick = {
                        browser.restoreTrash(
                            record.id,
                        )
                    },
                )
            },
        )
    }
    if (state.trashRecords.isNotEmpty()) {
        item {
            YSuiteActionButton(
                text = stringResource(
                    R.string
                        .yfiles_empty_trash,
                ),
                onClick = onEmptyTrash,
            )
        }
    }
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
        YSuitePanel(
            title = stringResource(
                R.string
                    .yfiles_tools_current,
            ),
        ) {
            if (directory != null) {
                YSuiteActionButton(
                    text = stringResource(
                        R.string.yfiles_analyze,
                    ),
                    onClick = {
                        tools.analyze(
                            directory,
                        )
                    },
                )
                YSuiteActionButton(
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
                YSuiteActionButton(
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
                YSuiteActionButton(
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
        YSuitePanel(
            title = stringResource(
                R.string
                    .yfiles_tools_selected,
            ),
        ) {
            YSuiteDataRow(
                title = stringResource(
                    R.string
                        .yfiles_selected_count,
                    selectedNodes.size,
                ),
            )
            if (selectedNodes.isNotEmpty()) {
                YSuiteActionButton(
                    text = stringResource(
                        R.string
                            .yfiles_create_zip,
                    ),
                    onClick =
                        tools::beginZip,
                )
                YSuiteActionButton(
                    text = stringResource(
                        R.string
                            .yfiles_bulk_rename,
                    ),
                    onClick =
                        tools::beginRename,
                )
            }
            if (singleFile != null) {
                YSuiteActionButton(
                    text = stringResource(
                        R.string.yfiles_sha256,
                    ),
                    onClick = {
                        tools.hash(
                            singleFile.ref,
                        )
                    },
                )
                YSuiteActionButton(
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
                YSuiteActionButton(
                    text = stringResource(
                        R.string.yfiles_hex,
                    ),
                    onClick = {
                        tools.hex(
                            singleFile.ref,
                        )
                    },
                )
                YSuiteActionButton(
                    text = stringResource(
                        R.string.yfiles_split,
                    ),
                    onClick =
                        tools::beginSplit,
                )
                YSuiteActionButton(
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
                    YSuiteActionButton(
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
                    YSuiteActionButton(
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
                YSuiteActionButton(
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
            YSuiteStatusPill(
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
            YSuiteStatusPill(
                text = it,
                tone = YSuiteStatusTone.Error,
            )
        }
    }
    toolState.batchResult?.let {
        item {
            YSuiteStatusPill(
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
            YSuitePanel(
                title = stringResource(
                    R.string.yfiles_sha256,
                ),
            ) {
                YSuiteDataRow(
                    title = hash.algorithm,
                    subtitle = hash.hex,
                )
            }
        }
    }

    state.analysis?.let { analysis ->
        item {
            YSuitePanel(
                title = stringResource(
                    R.string
                        .yfiles_analysis,
                ),
            ) {
                YSuiteDataRow(
                    title = stringResource(
                        R.string
                            .yfiles_analysis_items,
                        analysis.fileCount,
                        analysis
                            .directoryCount,
                    ),
                )
                YSuiteDataRow(
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
                    YSuiteStatusPill(
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
                        YSuiteDataRow(
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
            YSuitePanelLabel(
                title = stringResource(
                    R.string
                        .yfiles_duplicates,
                ),
            )
        }
        items(state.duplicates) { group ->
            YSuiteDataRow(
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
            YSuitePanel(
                title = stringResource(
                    R.string.yfiles_hex,
                ),
            ) {
                YSuiteDataRow(
                    title =
                        hex.byteCount
                            .toString() +
                            " B",
                    subtitle = hex.text,
                )
                if (hex.truncated) {
                    YSuiteStatusPill(
                        text = stringResource(
                            R.string
                                .yfiles_preview_truncated,
                        ),
                        tone =
                            YSuiteStatusTone
                                .Warning,
                    )
                }
                YSuiteActionButton(
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
            YSuitePanel(
                title = stringResource(
                    R.string.yfiles_compare,
                ),
            ) {
                YSuiteStatusPill(
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
                YSuiteDataRow(
                    title = stringResource(R.string.yfiles_compare_left_hash),
                    subtitle =
                        compare.leftHash,
                )
                YSuiteDataRow(
                    title = stringResource(R.string.yfiles_compare_right_hash),
                    subtitle =
                        compare.rightHash,
                )
            }
        }
    }

    state.cleanup?.let { cleanup ->
        item {
            YSuitePanel(
                title = stringResource(
                    R.string
                        .yfiles_cleanup_scan,
                ),
            ) {
                YSuiteDataRow(
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
                YSuiteDataRow(
                    title = stringResource(
                        R.string
                            .yfiles_cleanup_large,
                        cleanup.largeFiles
                            .size,
                    ),
                )
                YSuiteDataRow(
                    title = stringResource(
                        R.string
                            .yfiles_cleanup_old,
                        cleanup.oldFiles.size,
                    ),
                )
                YSuiteDataRow(
                    title = stringResource(
                        R.string
                            .yfiles_cleanup_hidden,
                        cleanup.hiddenFiles
                            .size,
                    ),
                )
                YSuiteDataRow(
                    title = stringResource(
                        R.string
                            .yfiles_cleanup_empty,
                        cleanup
                            .emptyDirectories
                            .size,
                    ),
                )
                YSuiteDataRow(
                    title = stringResource(
                        R.string
                            .yfiles_cleanup_apk,
                        cleanup.apkFiles.size,
                    ),
                )
                YSuiteDataRow(
                    title = stringResource(
                        R.string
                            .yfiles_cleanup_downloads,
                        cleanup.oldDownloads
                            .size,
                    ),
                )
                YSuiteDataRow(
                    title = stringResource(
                        R.string
                            .yfiles_cleanup_screenshots,
                        cleanup.screenshots
                            .size,
                    ),
                )
                YSuiteDataRow(
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
            YSuiteActionButton(
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

private fun LazyListScope.settingsContent(
    state: YFilesUiState,
    environment: YFilesEnvironment,
    browser: YFilesViewModel,
    allFilesGranted: Boolean,
    onOpenAllFilesSettings: () -> Unit,
    onAddSaf: () -> Unit,
) {
    item {
        YSuitePanel(
            title = stringResource(
                R.string
                    .yfiles_settings_access,
            ),
        ) {
            YSuiteDataRow(
                title = stringResource(
                    R.string
                        .yfiles_all_files_access,
                ),
                trailing = {
                    YSuiteStatusPill(
                        text = stringResource(
                            if (
                                allFilesGranted
                            ) {
                                R.string
                                    .yfiles_granted
                            } else {
                                R.string
                                    .yfiles_not_granted
                            },
                        ),
                        tone =
                            if (
                                allFilesGranted
                            ) {
                                YSuiteStatusTone
                                    .Positive
                            } else {
                                YSuiteStatusTone
                                    .Warning
                            },
                    )
                },
            )
            if (!allFilesGranted) {
                YSuiteActionButton(
                    text = stringResource(
                        R.string
                            .yfiles_open_settings,
                    ),
                    onClick =
                        onOpenAllFilesSettings,
                )
            }
            YSuiteDataRow(
                title = stringResource(
                    R.string
                        .yfiles_root_access,
                ),
                trailing = {
                    YSuiteStatusPill(
                        text =
                            rootStatusText(
                                state
                                    .rootStatus,
                            ),
                        tone =
                            rootStatusTone(
                                state
                                    .rootStatus,
                            ),
                    )
                },
            )
            YSuiteActionButton(
                text = stringResource(
                    R.string
                        .yfiles_refresh_root,
                ),
                onClick =
                    browser
                        ::refreshRootStatus,
            )
        }
    }

    val trees =
        environment.documentTrees.trees()
    item {
        YSuitePanel(
            title = stringResource(
                R.string.yfiles_saf,
            ),
        ) {
            YSuiteActionButton(
                text = stringResource(
                    R.string.yfiles_add_saf,
                ),
                onClick = onAddSaf,
            )
            if (trees.isEmpty()) {
                YSuiteDataRow(
                    title = stringResource(
                        R.string
                            .yfiles_no_saf,
                    ),
                )
            }
            trees.forEach { tree ->
                YSuiteDataRow(
                    title = tree.toString(),
                    trailing = {
                        YSuiteActionButton(
                            text =
                                stringResource(
                                    R.string
                                        .yfiles_remove_saf,
                                ),
                            onClick = {
                                browser
                                    .removeDocumentTree(
                                        tree,
                                    )
                            },
                        )
                    },
                )
            }
        }
    }

    item {
        YSuitePanelLabel(
            title = stringResource(
                R.string
                    .yfiles_provider_capabilities,
            ),
        )
    }
    items(
        state.providers,
        key = { it.id },
    ) { provider ->
        YSuiteDataRow(
            title =
                providerLabel(
                    provider.kind,
                ),
            subtitle =
                provider.capabilities
                    .joinToString {
                        it.name
                    },
        )
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
                    YSuiteToggleRow(
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

private fun nodeItemKind(
    type: YFileType,
): com.yagay.ysuite.designsystem.component.YSuiteItemKind =
    when (type) {
        YFileType.Directory ->
            com.yagay.ysuite.designsystem.component.YSuiteItemKind.Folder
        YFileType.File ->
            com.yagay.ysuite.designsystem.component.YSuiteItemKind.File
        YFileType.SymbolicLink ->
            com.yagay.ysuite.designsystem.component.YSuiteItemKind.Link
        YFileType.Other ->
            com.yagay.ysuite.designsystem.component.YSuiteItemKind.Info
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
