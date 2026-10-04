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
import com.yagay.ysuite.designsystem.component.YSuiteFilterBar
import com.yagay.ysuite.designsystem.component.YSuiteFilterOption
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
import com.yagay.ysuite.feature.yfiles.api.YFileNode
import com.yagay.ysuite.feature.yfiles.api.YFileProviderKind
import com.yagay.ysuite.feature.yfiles.api.YFileRef
import com.yagay.ysuite.feature.yfiles.api.YFileSort
import com.yagay.ysuite.feature.yfiles.api.YFileType
import com.yagay.ysuite.logging.api.YSuiteLogger
import com.yagay.ysuite.platform.api.CapabilityStatus
import com.yagay.ysuite.ui.YSuiteLazyListPage
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

    YSuiteLazyListPage(
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
                YSuiteFilterBar(
                    options = listOf(
                        YSuiteFilterOption(
                            YFilesTab.Files.name,
                            stringResource(
                                R.string
                                    .yfiles_tab_files,
                            ),
                        ),
                        YSuiteFilterOption(
                            YFilesTab.Tools.name,
                            stringResource(
                                R.string
                                    .yfiles_tab_tools,
                            ),
                        ),
                        YSuiteFilterOption(
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
                YSuiteStatusBadge(
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
        YSuiteFilterBar(
            options = listOf(
                YSuiteFilterOption(
                    YFilesBrowserMode
                        .Directory.name,
                    stringResource(
                        R.string
                            .yfiles_mode_directory,
                    ),
                ),
                YSuiteFilterOption(
                    YFilesBrowserMode
                        .Favorites.name,
                    stringResource(
                        R.string
                            .yfiles_mode_favorites,
                    ),
                ),
                YSuiteFilterOption(
                    YFilesBrowserMode
                        .Recent.name,
                    stringResource(
                        R.string
                            .yfiles_mode_recent,
                    ),
                ),
                YSuiteFilterOption(
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
        YSuiteSection(
            title = stringResource(
                R.string.yfiles_sources,
            ),
        ) {
            YSuiteFilterBar(
                options =
                    state.providers.map {
                        YSuiteFilterOption(
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
            YSuiteSection(
                title = stringResource(
                    R.string.yfiles_location,
                ),
            ) {
                YSuiteListItem(
                    title = directory.path,
                    subtitle =
                        directory.providerId,
                )
                YSuiteSecondaryButton(
                    text = stringResource(
                        R.string.yfiles_parent,
                    ),
                    onClick = browser::parent,
                )
                YSuiteSecondaryButton(
                    text = stringResource(
                        R.string.yfiles_refresh,
                    ),
                    onClick = browser::refresh,
                )
                YSuiteSecondaryButton(
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
                YSuiteSecondaryButton(
                    text = stringResource(
                        R.string
                            .yfiles_new_folder,
                    ),
                    onClick =
                        browser
                            ::beginCreateDirectory,
                )
                YSuiteSecondaryButton(
                    text = stringResource(
                        R.string.yfiles_new_file,
                    ),
                    onClick =
                        browser::beginCreateFile,
                )
            }
        }

        item {
            YSuiteSection(
                title = stringResource(
                    R.string.yfiles_search,
                ),
            ) {
                YSuiteSearchField(
                    value = state.query,
                    onValueChange =
                        browser::setQuery,
                    label = stringResource(
                        R.string.yfiles_search,
                    ),
                )
                YSuiteSwitchItem(
                    title = stringResource(
                        R.string
                            .yfiles_recursive,
                    ),
                    checked = state.recursive,
                    onCheckedChange =
                        browser::setRecursive,
                )
                YSuiteSwitchItem(
                    title = stringResource(
                        R.string
                            .yfiles_show_hidden,
                    ),
                    checked =
                        state.showHidden,
                    onCheckedChange =
                        browser::setShowHidden,
                )
                YSuiteFilterBar(
                    options = listOf(
                        YSuiteFilterOption(
                            YFileSort.Name.name,
                            stringResource(
                                R.string
                                    .yfiles_sort_name,
                            ),
                        ),
                        YSuiteFilterOption(
                            YFileSort
                                .Modified.name,
                            stringResource(
                                R.string
                                    .yfiles_sort_modified,
                            ),
                        ),
                        YSuiteFilterOption(
                            YFileSort.Size.name,
                            stringResource(
                                R.string
                                    .yfiles_sort_size,
                            ),
                        ),
                        YSuiteFilterOption(
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
                YSuiteFilterBar(
                    options = listOf(
                        YSuiteFilterOption(
                            "asc",
                            stringResource(
                                R.string
                                    .yfiles_ascending,
                            ),
                        ),
                        YSuiteFilterOption(
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
            YSuiteSection(
                title = stringResource(
                    R.string
                        .yfiles_selected_count,
                    state.selected.size,
                ),
            ) {
                YSuiteSecondaryButton(
                    text = stringResource(
                        R.string.yfiles_copy,
                    ),
                    onClick =
                        browser::prepareCopy,
                )
                YSuiteSecondaryButton(
                    text = stringResource(
                        R.string.yfiles_move,
                    ),
                    onClick =
                        browser::prepareMove,
                )
                YSuiteSecondaryButton(
                    text = stringResource(
                        R.string
                            .yfiles_move_to_bin,
                    ),
                    onClick =
                        browser
                            ::moveSelectedToTrash,
                )
                YSuiteSecondaryButton(
                    text = stringResource(
                        R.string
                            .yfiles_delete_permanently,
                    ),
                    onClick = onDelete,
                )
                YSuiteSecondaryButton(
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
            YSuiteSection(
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
                YSuiteSecondaryButton(
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
            YSuiteStatusBadge(
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
            YSuiteStatusBadge(
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
        YSuiteSectionHeader(
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
            YSuiteListItem(
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
        YSuiteListItem(
            title = node.name,
            subtitle =
                nodeSubtitle(node),
            modifier = Modifier.clickable {
                browser.open(node)
            },
            trailing = {
                YSuiteSecondaryButton(
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
            YSuiteSection(
                title = stringResource(
                    R.string.yfiles_details,
                ),
            ) {
                YSuiteListItem(
                    title = node.name,
                    subtitle =
                        nodeTypeLabel(
                            node.type,
                        ),
                )
                YSuiteListItem(
                    title = stringResource(
                        R.string.yfiles_provider,
                    ),
                    subtitle =
                        node.ref.providerId,
                )
                node.sizeBytes?.let {
                    YSuiteListItem(
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
                        YSuiteListItem(
                            title =
                                stringResource(
                                    R.string
                                        .yfiles_modified,
                                ),
                            subtitle =
                                formatDate(it),
                        )
                    }
                YSuiteListItem(
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
                YSuiteSecondaryButton(
                    text = stringResource(
                        R.string.yfiles_rename,
                    ),
                    onClick = {
                        browser.beginRename(
                            node,
                        )
                    },
                )
                YSuiteSecondaryButton(
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
        YSuiteSectionHeader(
            title = stringResource(titleRes),
        )
    }
    if (locations.isEmpty()) {
        item {
            YSuiteListItem(
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
        YSuiteListItem(
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
        YSuiteSectionHeader(
            title = stringResource(
                R.string.yfiles_mode_trash,
            ),
        )
    }
    if (state.trashRecords.isEmpty()) {
        item {
            YSuiteListItem(
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
        YSuiteListItem(
            title = record.originalName,
            subtitle =
                record.originalParent.path,
            trailing = {
                YSuiteSecondaryButton(
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
            YSuiteSecondaryButton(
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
        YSuiteSection(
            title = stringResource(
                R.string
                    .yfiles_tools_current,
            ),
        ) {
            if (directory != null) {
                YSuiteSecondaryButton(
                    text = stringResource(
                        R.string.yfiles_analyze,
                    ),
                    onClick = {
                        tools.analyze(
                            directory,
                        )
                    },
                )
                YSuiteSecondaryButton(
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
                YSuiteSecondaryButton(
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
                YSuiteSecondaryButton(
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
        YSuiteSection(
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
                YSuiteSecondaryButton(
                    text = stringResource(
                        R.string
                            .yfiles_create_zip,
                    ),
                    onClick =
                        tools::beginZip,
                )
                YSuiteSecondaryButton(
                    text = stringResource(
                        R.string
                            .yfiles_bulk_rename,
                    ),
                    onClick =
                        tools::beginRename,
                )
            }
            if (singleFile != null) {
                YSuiteSecondaryButton(
                    text = stringResource(
                        R.string.yfiles_sha256,
                    ),
                    onClick = {
                        tools.hash(
                            singleFile.ref,
                        )
                    },
                )
                YSuiteSecondaryButton(
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
                YSuiteSecondaryButton(
                    text = stringResource(
                        R.string.yfiles_hex,
                    ),
                    onClick = {
                        tools.hex(
                            singleFile.ref,
                        )
                    },
                )
                YSuiteSecondaryButton(
                    text = stringResource(
                        R.string.yfiles_split,
                    ),
                    onClick =
                        tools::beginSplit,
                )
                YSuiteSecondaryButton(
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
                    YSuiteSecondaryButton(
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
                    YSuiteSecondaryButton(
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
                YSuiteSecondaryButton(
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
            YSuiteSection(
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
            YSuiteSection(
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
            YSuiteSection(
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
                YSuiteSecondaryButton(
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
            YSuiteSection(
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
                    title = "SHA-256 #1",
                    subtitle =
                        compare.leftHash,
                )
                YSuiteListItem(
                    title = "SHA-256 #2",
                    subtitle =
                        compare.rightHash,
                )
            }
        }
    }

    state.cleanup?.let { cleanup ->
        item {
            YSuiteSection(
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
            YSuiteSecondaryButton(
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
        YSuiteSection(
            title = stringResource(
                R.string
                    .yfiles_settings_access,
            ),
        ) {
            YSuiteListItem(
                title = stringResource(
                    R.string
                        .yfiles_all_files_access,
                ),
                trailing = {
                    YSuiteStatusBadge(
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
                YSuiteSecondaryButton(
                    text = stringResource(
                        R.string
                            .yfiles_open_settings,
                    ),
                    onClick =
                        onOpenAllFilesSettings,
                )
            }
            YSuiteListItem(
                title = stringResource(
                    R.string
                        .yfiles_root_access,
                ),
                trailing = {
                    YSuiteStatusBadge(
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
            YSuiteSecondaryButton(
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
        YSuiteSection(
            title = stringResource(
                R.string.yfiles_saf,
            ),
        ) {
            YSuiteSecondaryButton(
                text = stringResource(
                    R.string.yfiles_add_saf,
                ),
                onClick = onAddSaf,
            )
            if (trees.isEmpty()) {
                YSuiteListItem(
                    title = stringResource(
                        R.string
                            .yfiles_no_saf,
                    ),
                )
            }
            trees.forEach { tree ->
                YSuiteListItem(
                    title = tree.toString(),
                    trailing = {
                        YSuiteSecondaryButton(
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
        YSuiteSectionHeader(
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
        YSuiteListItem(
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
