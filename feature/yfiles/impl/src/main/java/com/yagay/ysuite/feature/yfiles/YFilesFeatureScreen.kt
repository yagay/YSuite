package com.yagay.ysuite.feature.yfiles

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yagay.ysuite.designsystem.component.YSuiteConfirmDialog
import com.yagay.ysuite.designsystem.component.YSuiteFilterBar
import com.yagay.ysuite.designsystem.component.YSuiteFilterOption
import com.yagay.ysuite.designsystem.component.YSuiteListItem
import com.yagay.ysuite.designsystem.component.YSuiteSearchField
import com.yagay.ysuite.designsystem.component.YSuiteSecondaryButton
import com.yagay.ysuite.designsystem.component.YSuiteSection
import com.yagay.ysuite.designsystem.component.YSuiteStatusBadge
import com.yagay.ysuite.designsystem.component.YSuiteStatusTone
import com.yagay.ysuite.designsystem.component.YSuiteSwitchItem
import com.yagay.ysuite.designsystem.component.YSuiteTextInputDialog
import com.yagay.ysuite.feature.yfiles.api.YFileClipboard
import com.yagay.ysuite.feature.yfiles.api.YFileEntry
import com.yagay.ysuite.feature.yfiles.api.YFileProperties
import com.yagay.ysuite.feature.yfiles.api.YFileSort
import com.yagay.ysuite.feature.yfiles.api.YFileTransferMode
import com.yagay.ysuite.feature.yfiles.api.YFilesPlacesRepository
import com.yagay.ysuite.feature.yfiles.api.YFilesRepository
import com.yagay.ysuite.feature.yfiles.api.YTrashEntry
import com.yagay.ysuite.logging.api.YSuiteLogger
import com.yagay.ysuite.ui.YSuiteListPage
import com.yagay.ysuite.ui.YSuitePageState
import java.text.DateFormat

@Composable
fun YFilesFeatureScreen(
    repository: YFilesRepository,
    placesRepository: YFilesPlacesRepository,
    logger: YSuiteLogger,
) {
    val model: YFilesViewModel = viewModel(
        factory = YFilesViewModelFactory(
            repository = repository,
            placesRepository = placesRepository,
            logger = logger,
        ),
    )
    val state by model.state.collectAsStateWithLifecycle()
    var confirmEmptyTrash by rememberSaveable {
        mutableStateOf(false)
    }

    val isEmpty = when (state.viewMode) {
        YFilesViewMode.Trash -> state.trashEntries.isEmpty()
        else -> state.entries.isEmpty()
    }

    val pageState = when {
        state.loading && isEmpty ->
            YSuitePageState.Loading(
                stringResource(R.string.yfiles_loading),
            )
        state.error != null ->
            YSuitePageState.Error(
                title = stringResource(R.string.yfiles_error),
                message = state.error,
                retryText = stringResource(R.string.yfiles_retry),
            )
        !state.loading && isEmpty ->
            YSuitePageState.Empty(
                title = emptyTitle(state.viewMode),
            )
        else -> YSuitePageState.Content
    }

    YSuiteListPage(
        title = stringResource(R.string.yfiles_title),
        subtitle = stringResource(R.string.yfiles_summary),
        state = pageState,
        header = {
            ViewModeBar(
                selected = state.viewMode,
                onSelected = model::setViewMode,
            )

            if (state.viewMode == YFilesViewMode.Files) {
                BrowserControls(
                    state = state,
                    onParent = model::parent,
                    onRefresh = model::refresh,
                    onQuery = model::setQuery,
                    onRecursive = model::setRecursive,
                    onHidden = model::setShowHidden,
                    onSort = model::setSort,
                    onDescending = model::setDescending,
                    onCreateDirectory =
                        model::beginCreateDirectory,
                    onCreateFile = model::beginCreateFile,
                    onToggleFavorite =
                        model::toggleCurrentFavorite,
                )
            }

            SelectionActions(
                state = state,
                onCopy = model::copySelected,
                onMove = model::moveSelected,
                onTrash = model::moveSelectedToTrash,
            )

            state.clipboard?.let {
                ClipboardSection(
                    clipboard = it,
                    canPaste =
                        state.viewMode ==
                            YFilesViewMode.Files,
                    onPaste = model::pasteHere,
                )
            }

            state.operationResult?.let {
                YSuiteStatusBadge(
                    text = stringResource(
                        R.string.yfiles_operation_result,
                        it.succeeded,
                        it.failed,
                    ),
                    tone = if (it.failed == 0) {
                        YSuiteStatusTone.Positive
                    } else {
                        YSuiteStatusTone.Warning
                    },
                )
            }
        },
    ) { _ ->
        when (state.viewMode) {
            YFilesViewMode.Trash ->
                TrashList(
                    entries = state.trashEntries,
                    onRestore = model::restoreTrash,
                    onEmpty = {
                        confirmEmptyTrash = true
                    },
                )
            else ->
                FileList(
                    entries = state.entries,
                    selectedPaths = state.selectedPaths,
                    onOpen = model::open,
                    onSelect = model::toggleSelection,
                )
        }

        state.properties?.let {
            PropertiesSection(
                properties = it,
                onRename = model::beginRename,
            )
        }
    }

    state.namePrompt?.let { prompt ->
        NamePromptDialog(
            prompt = prompt,
            onValueChange = model::updatePromptValue,
            onConfirm = model::confirmPrompt,
            onDismiss = model::dismissPrompt,
        )
    }

    if (confirmEmptyTrash) {
        YSuiteConfirmDialog(
            title = stringResource(
                R.string.yfiles_empty_bin_title,
            ),
            message = stringResource(
                R.string.yfiles_empty_bin_message,
            ),
            confirmText = stringResource(
                R.string.yfiles_confirm,
            ),
            dismissText = stringResource(
                R.string.yfiles_cancel,
            ),
            onConfirm = {
                confirmEmptyTrash = false
                model.emptyTrash()
            },
            onDismiss = {
                confirmEmptyTrash = false
            },
        )
    }
}

@Composable
private fun ViewModeBar(
    selected: YFilesViewMode,
    onSelected: (YFilesViewMode) -> Unit,
) {
    YSuiteFilterBar(
        options = listOf(
            YSuiteFilterOption(
                YFilesViewMode.Files.name,
                stringResource(R.string.yfiles_files),
            ),
            YSuiteFilterOption(
                YFilesViewMode.Favorites.name,
                stringResource(R.string.yfiles_favorites),
            ),
            YSuiteFilterOption(
                YFilesViewMode.Recent.name,
                stringResource(R.string.yfiles_recent),
            ),
            YSuiteFilterOption(
                YFilesViewMode.Trash.name,
                stringResource(R.string.yfiles_recycle_bin),
            ),
        ),
        selectedId = selected.name,
        onSelected = {
            onSelected(YFilesViewMode.valueOf(it))
        },
    )
}

@Composable
private fun BrowserControls(
    state: YFilesUiState,
    onParent: () -> Unit,
    onRefresh: () -> Unit,
    onQuery: (String) -> Unit,
    onRecursive: (Boolean) -> Unit,
    onHidden: (Boolean) -> Unit,
    onSort: (YFileSort) -> Unit,
    onDescending: (Boolean) -> Unit,
    onCreateDirectory: () -> Unit,
    onCreateFile: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    YSuiteSection(
        title = stringResource(R.string.yfiles_location),
    ) {
        YSuiteListItem(title = state.path)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            YSuiteSecondaryButton(
                text = stringResource(R.string.yfiles_parent),
                onClick = onParent,
            )
            YSuiteSecondaryButton(
                text = stringResource(R.string.yfiles_refresh),
                onClick = onRefresh,
            )
        }

        YSuiteSecondaryButton(
            text = stringResource(
                if (state.path in state.favoritePaths) {
                    R.string.yfiles_remove_favorite
                } else {
                    R.string.yfiles_add_favorite
                },
            ),
            onClick = onToggleFavorite,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            YSuiteSecondaryButton(
                text = stringResource(
                    R.string.yfiles_create_folder,
                ),
                onClick = onCreateDirectory,
            )
            YSuiteSecondaryButton(
                text = stringResource(
                    R.string.yfiles_create_file,
                ),
                onClick = onCreateFile,
            )
        }

        YSuiteSearchField(
            value = state.query,
            onValueChange = onQuery,
            label = stringResource(R.string.yfiles_search),
        )

        YSuiteSwitchItem(
            title = stringResource(R.string.yfiles_recursive),
            checked = state.recursive,
            onCheckedChange = onRecursive,
        )
        YSuiteSwitchItem(
            title = stringResource(R.string.yfiles_show_hidden),
            checked = state.showHidden,
            onCheckedChange = onHidden,
        )

        YSuiteListItem(
            title = stringResource(R.string.yfiles_sort),
        )
        YSuiteFilterBar(
            options = listOf(
                YSuiteFilterOption(
                    YFileSort.Name.name,
                    stringResource(R.string.yfiles_sort_name),
                ),
                YSuiteFilterOption(
                    YFileSort.Modified.name,
                    stringResource(
                        R.string.yfiles_sort_modified,
                    ),
                ),
                YSuiteFilterOption(
                    YFileSort.Size.name,
                    stringResource(R.string.yfiles_sort_size),
                ),
                YSuiteFilterOption(
                    YFileSort.Type.name,
                    stringResource(R.string.yfiles_sort_type),
                ),
            ),
            selectedId = state.sort.name,
            onSelected = {
                onSort(YFileSort.valueOf(it))
            },
        )
        YSuiteFilterBar(
            options = listOf(
                YSuiteFilterOption(
                    "ascending",
                    stringResource(R.string.yfiles_ascending),
                ),
                YSuiteFilterOption(
                    "descending",
                    stringResource(R.string.yfiles_descending),
                ),
            ),
            selectedId = if (state.descending) {
                "descending"
            } else {
                "ascending"
            },
            onSelected = {
                onDescending(it == "descending")
            },
        )
    }
}

@Composable
private fun SelectionActions(
    state: YFilesUiState,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onTrash: () -> Unit,
) {
    if (state.selectedPaths.isEmpty()) return

    YSuiteSection(
        title = stringResource(
            R.string.yfiles_selected_count,
            state.selectedPaths.size,
        ),
    ) {
        YSuiteSecondaryButton(
            text = stringResource(
                R.string.yfiles_copy_selected,
            ),
            onClick = onCopy,
        )
        YSuiteSecondaryButton(
            text = stringResource(
                R.string.yfiles_move_selected,
            ),
            onClick = onMove,
        )
        YSuiteSecondaryButton(
            text = stringResource(
                R.string.yfiles_move_to_bin,
            ),
            onClick = onTrash,
        )
    }
}

@Composable
private fun ClipboardSection(
    clipboard: YFileClipboard,
    canPaste: Boolean,
    onPaste: () -> Unit,
) {
    val operation = when (clipboard.mode) {
        YFileTransferMode.Copy ->
            stringResource(R.string.yfiles_clipboard_copy)
        YFileTransferMode.Move ->
            stringResource(R.string.yfiles_clipboard_move)
    }

    YSuiteSection(
        title = stringResource(
            R.string.yfiles_clipboard,
            operation,
            clipboard.entries.size,
        ),
    ) {
        if (canPaste) {
            YSuiteSecondaryButton(
                text = stringResource(
                    R.string.yfiles_paste_here,
                ),
                onClick = onPaste,
            )
        }
    }
}

@Composable
private fun FileList(
    entries: List<YFileEntry>,
    selectedPaths: Set<String>,
    onOpen: (YFileEntry) -> Unit,
    onSelect: (YFileEntry) -> Unit,
) {
    YSuiteSection(
        title = stringResource(R.string.yfiles_files),
    ) {
        entries.forEach { entry ->
            val selected = entry.path in selectedPaths
            YSuiteListItem(
                title = entry.name,
                subtitle = entrySubtitle(entry),
                modifier = Modifier.clickable {
                    onOpen(entry)
                },
                trailing = {
                    YSuiteSecondaryButton(
                        text = stringResource(
                            if (selected) {
                                R.string.yfiles_unselect
                            } else {
                                R.string.yfiles_select
                            },
                        ),
                        onClick = { onSelect(entry) },
                    )
                },
            )
        }
    }
}

@Composable
private fun TrashList(
    entries: List<YTrashEntry>,
    onRestore: (String) -> Unit,
    onEmpty: () -> Unit,
) {
    YSuiteSection(
        title = stringResource(R.string.yfiles_recycle_bin),
    ) {
        entries.forEach { item ->
            YSuiteListItem(
                title = item.entry.name,
                subtitle = item.originalPath,
                trailing = {
                    YSuiteSecondaryButton(
                        text = stringResource(
                            R.string.yfiles_restore,
                        ),
                        onClick = {
                            onRestore(item.id)
                        },
                    )
                },
            )
        }

        if (entries.isNotEmpty()) {
            YSuiteSecondaryButton(
                text = stringResource(
                    R.string.yfiles_empty_bin,
                ),
                onClick = onEmpty,
            )
        }
    }
}

@Composable
private fun PropertiesSection(
    properties: YFileProperties,
    onRename: (String) -> Unit,
) {
    YSuiteSection(
        title = stringResource(R.string.yfiles_details),
    ) {
        YSuiteListItem(
            title = properties.name,
            subtitle = if (properties.directory) {
                stringResource(R.string.yfiles_folder)
            } else {
                stringResource(R.string.yfiles_file)
            },
        )
        YSuiteListItem(
            title = stringResource(R.string.yfiles_path),
            subtitle = properties.path,
        )
        YSuiteListItem(
            title = stringResource(R.string.yfiles_size),
            subtitle = formatBytes(properties.sizeBytes),
        )
        YSuiteListItem(
            title = stringResource(R.string.yfiles_modified),
            subtitle = formatDate(properties.modifiedAtMillis),
        )
        YSuiteListItem(
            title = stringResource(R.string.yfiles_access),
            subtitle = stringResource(
                R.string.yfiles_access_value,
                yesNo(properties.readable),
                yesNo(properties.writable),
                yesNo(properties.executable),
            ),
        )
        properties.childCount?.let {
            YSuiteListItem(
                title = stringResource(R.string.yfiles_children),
                subtitle = it.toString(),
            )
        }
        YSuiteSecondaryButton(
            text = stringResource(R.string.yfiles_rename),
            onClick = { onRename(properties.path) },
        )
    }
}

@Composable
private fun NamePromptDialog(
    prompt: YFilesNamePrompt,
    onValueChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val title = when (prompt) {
        is YFilesNamePrompt.CreateDirectory ->
            stringResource(R.string.yfiles_create_folder)
        is YFilesNamePrompt.CreateFile ->
            stringResource(R.string.yfiles_create_file)
        is YFilesNamePrompt.Rename ->
            stringResource(R.string.yfiles_rename)
    }
    val label = when (prompt) {
        is YFilesNamePrompt.CreateDirectory ->
            stringResource(R.string.yfiles_folder_name)
        is YFilesNamePrompt.CreateFile ->
            stringResource(R.string.yfiles_file_name)
        is YFilesNamePrompt.Rename ->
            stringResource(R.string.yfiles_new_name)
    }

    YSuiteTextInputDialog(
        title = title,
        label = label,
        value = prompt.value,
        confirmText = stringResource(R.string.yfiles_confirm),
        dismissText = stringResource(R.string.yfiles_cancel),
        onValueChange = onValueChange,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

@Composable
private fun emptyTitle(
    mode: YFilesViewMode,
): String =
    when (mode) {
        YFilesViewMode.Files ->
            stringResource(R.string.yfiles_empty)
        YFilesViewMode.Favorites ->
            stringResource(R.string.yfiles_favorites_empty)
        YFilesViewMode.Recent ->
            stringResource(R.string.yfiles_recent_empty)
        YFilesViewMode.Trash ->
            stringResource(R.string.yfiles_trash_empty)
    }

@Composable
private fun entrySubtitle(
    entry: YFileEntry,
): String =
    if (entry.directory) {
        stringResource(R.string.yfiles_folder)
    } else {
        formatBytes(entry.sizeBytes) +
            " · " +
            formatDate(entry.modifiedAtMillis)
    }

@Composable
private fun yesNo(value: Boolean): String =
    stringResource(
        if (value) {
            R.string.yfiles_yes
        } else {
            R.string.yfiles_no
        },
    )

private fun formatDate(value: Long): String =
    if (value <= 0L) {
        "-"
    } else {
        DateFormat.getDateTimeInstance(
            DateFormat.SHORT,
            DateFormat.SHORT,
        ).format(value)
    }

private fun formatBytes(value: Long): String {
    if (value < 1024L) {
        return value.toString() + " B"
    }

    val units = arrayOf("KiB", "MiB", "GiB", "TiB")
    var amount = value.toDouble()
    var index = -1
    do {
        amount /= 1024.0
        index += 1
    } while (
        amount >= 1024.0 &&
        index < units.lastIndex
    )

    return String.format("%.1f %s", amount, units[index])
}

private class YFilesViewModelFactory(
    private val repository: YFilesRepository,
    private val placesRepository: YFilesPlacesRepository,
    private val logger: YSuiteLogger,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(
        modelClass: Class<T>,
    ): T =
        YFilesViewModel(
            repository = repository,
            placesRepository = placesRepository,
            logger = logger,
        ) as T
}
