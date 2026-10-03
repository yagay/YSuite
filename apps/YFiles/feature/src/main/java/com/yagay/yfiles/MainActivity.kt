package com.yagay.yfiles

import android.app.Activity
import android.content.Intent
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.lifecycleScope
import com.yagay.suite.api.HostCapability
import com.yagay.suite.api.HostCapabilityState
import com.yagay.suite.api.HostLogLevel
import com.yagay.yui.YActionRow
import com.yagay.yui.YComposeActivity
import com.yagay.yui.YFeatureCard
import com.yagay.yui.YBrowserScaffold
import com.yagay.yui.YPageList
import com.yagay.yui.YPrimaryActionButton
import com.yagay.yui.YSecondaryActionButton
import com.yagay.yui.YSearchField
import com.yagay.yui.YSettingSwitch
import com.yagay.yui.YStatusRow
import com.yagay.yui.YStatusTone
import java.io.File
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : YComposeActivity() {
    private var capabilityRevision by mutableStateOf(0)
    private var pickerRevision by mutableStateOf(0)

    override fun onResume() {
        super.onResume()
        capabilityRevision++
    }

    @Deprecated("Legacy activity result API is intentionally used for broad standalone compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != Activity.RESULT_OK) return
        val uri = data?.data
        val grantFlags = data?.flags ?: 0
        when (requestCode) {
            SystemPickerBridge.REQUEST_DEFAULT_TREE -> {
                SystemPickerBridge.rememberReturnedUri(this, uri, grantFlags)
                YFilesPatchSettings.setInitialUri(this, uri)
                pickerRevision++
            }
            SystemPickerBridge.REQUEST_TREE,
            SystemPickerBridge.REQUEST_OPEN,
            SystemPickerBridge.REQUEST_CREATE -> {
                SystemPickerBridge.rememberReturnedUri(this, uri, grantFlags)
            }
            SystemPickerBridge.REQUEST_OPEN_MULTIPLE -> {
                uri?.let { SystemPickerBridge.rememberReturnedUri(this, it, grantFlags) }
                data?.clipData?.let { clips ->
                    for (i in 0 until clips.itemCount) {
                        SystemPickerBridge.rememberReturnedUri(this, clips.getItemAt(i).uri, grantFlags)
                    }
                }
            }
        }
    }

    @Composable
    override fun YContent() {
        val repository = remember { FileRepository(this) }
        var path by remember { mutableStateOf(intent?.getStringExtra("path") ?: repository.initialPath()) }
        var query by remember { mutableStateOf("") }
        var showHidden by remember { mutableStateOf(false) }
        var recursiveSearch by remember { mutableStateOf(false) }
        var sortMode by remember { mutableStateOf(FileSortMode.NAME) }
        var sortDescending by remember { mutableStateOf(false) }
        var entries by remember { mutableStateOf<List<FileEntry>>(emptyList()) }
        var error by remember { mutableStateOf<String?>(null) }
        var newFolderDialog by remember { mutableStateOf(false) }
        var newFolderName by remember { mutableStateOf("") }
        var newFileDialog by remember { mutableStateOf(false) }
        var newFileName by remember { mutableStateOf("") }
        var refresh by remember { mutableStateOf(0) }
        var rootMode by remember { mutableStateOf(false) }
        var rootNames by remember { mutableStateOf<List<String>>(emptyList()) }
        var patchSettings by remember(pickerRevision) { mutableStateOf(YFilesPatchSettings.load(this)) }
        var pendingTransfer by remember { mutableStateOf<PendingFileTransfer?>(null) }
        var renameTarget by remember { mutableStateOf<FileEntry?>(null) }
        var renameValue by remember { mutableStateOf("") }
        var propertyDialog by remember { mutableStateOf<FileProperties?>(null) }
        var deleteTarget by remember { mutableStateOf<FileEntry?>(null) }
        var operationBusy by remember { mutableStateOf(false) }

        val allFilesState = remember(capabilityRevision) {
            YFilesSuiteRuntime.capabilityState(HostCapability.ALL_FILES)
        }
        val rootGranted = remember(capabilityRevision) { YFilesSuiteRuntime.rootAvailable() }
        val allFilesGranted = allFilesState == HostCapabilityState.GRANTED

        LaunchedEffect(
            path,
            query,
            showHidden,
            recursiveSearch,
            sortMode,
            sortDescending,
            refresh,
            rootMode,
        ) {
            if (rootMode) {
                val result = withContext(Dispatchers.IO) { YFilesSuiteRuntime.rootList(path) }
                result.onSuccess { names ->
                    val needle = query.trim().lowercase()
                    rootNames = names
                        .filter { needle.isBlank() || it.lowercase().contains(needle) }
                        .let { if (sortDescending) it.sortedDescending() else it.sorted() }
                    error = null
                }.onFailure { error = it.message }
            } else {
                val result = withContext(Dispatchers.IO) {
                    if (recursiveSearch && query.isNotBlank()) {
                        repository.searchRecursive(path, showHidden, query, sortMode, sortDescending)
                    } else {
                        repository.list(path, showHidden, query, sortMode, sortDescending)
                    }
                }
                result.onSuccess { entries = it; error = null }
                    .onFailure { error = it.message }
            }
        }

        YBrowserScaffold(
            title = stringResource(R.string.yfiles_title),
            subtitle = stringResource(R.string.yfiles_subtitle),
        ) { padding ->
            YPageList(padding) {
                item {
                    YFeatureCard(
                        title = stringResource(R.string.documentsui_integration),
                        subtitle = stringResource(R.string.documentsui_summary),
                    ) {
                        YStatusRow(
                            stringResource(R.string.documentsui),
                            stringResource(R.string.yfiles_preserved),
                            YStatusTone.Good,
                        )
                        YSettingSwitch(
                            title = stringResource(R.string.enable_picker_patch),
                            subtitle = stringResource(R.string.enable_picker_patch_summary),
                            checked = patchSettings.enabled,
                            onCheckedChange = {
                                patchSettings = YFilesPatchSettings.update(this@MainActivity) { copy(enabled = it) }
                            },
                        )
                        YSettingSwitch(
                            title = stringResource(R.string.local_files_only),
                            checked = patchSettings.localOnly,
                            onCheckedChange = {
                                patchSettings = YFilesPatchSettings.update(this@MainActivity) { copy(localOnly = it) }
                            },
                        )
                        YSettingSwitch(
                            title = stringResource(R.string.allow_multiple_picker),
                            subtitle = stringResource(R.string.allow_multiple_picker_summary),
                            checked = patchSettings.allowMultiple,
                            onCheckedChange = {
                                patchSettings = YFilesPatchSettings.update(this@MainActivity) { copy(allowMultiple = it) }
                            },
                        )
                        val sortLabel = when (patchSettings.defaultSort) {
                            YFilesPatchSettings.SORT_NAME -> stringResource(R.string.sort_name)
                            YFilesPatchSettings.SORT_DATE -> stringResource(R.string.sort_modified)
                            YFilesPatchSettings.SORT_SIZE -> stringResource(R.string.sort_size)
                            YFilesPatchSettings.SORT_TYPE -> stringResource(R.string.sort_type)
                            else -> stringResource(R.string.system_default)
                        }
                        YStatusRow(stringResource(R.string.default_sort), sortLabel, YStatusTone.Neutral)
                        YActionRow {
                            YSecondaryActionButton(onClick = {
                                patchSettings = YFilesPatchSettings.update(this@MainActivity) {
                                    copy(defaultSort = YFilesPatchSettings.SORT_SYSTEM)
                                }
                            }) { Text(stringResource(R.string.system_default)) }
                            YSecondaryActionButton(onClick = {
                                patchSettings = YFilesPatchSettings.update(this@MainActivity) {
                                    copy(defaultSort = YFilesPatchSettings.SORT_NAME)
                                }
                            }) { Text(stringResource(R.string.sort_name)) }
                            YSecondaryActionButton(onClick = {
                                patchSettings = YFilesPatchSettings.update(this@MainActivity) {
                                    copy(defaultSort = YFilesPatchSettings.SORT_DATE)
                                }
                            }) { Text(stringResource(R.string.sort_modified)) }
                        }
                        YActionRow {
                            YSecondaryActionButton(onClick = {
                                patchSettings = YFilesPatchSettings.update(this@MainActivity) {
                                    copy(defaultSort = YFilesPatchSettings.SORT_SIZE)
                                }
                            }) { Text(stringResource(R.string.sort_size)) }
                            YSecondaryActionButton(onClick = {
                                patchSettings = YFilesPatchSettings.update(this@MainActivity) {
                                    copy(defaultSort = YFilesPatchSettings.SORT_TYPE)
                                }
                            }) { Text(stringResource(R.string.sort_type)) }
                        }
                        YStatusRow(
                            stringResource(R.string.default_picker_folder),
                            if (patchSettings.initialUri.isNullOrBlank()) {
                                stringResource(R.string.system_default)
                            } else {
                                stringResource(R.string.custom_folder)
                            },
                            if (patchSettings.initialUri.isNullOrBlank()) YStatusTone.Neutral else YStatusTone.Good,
                        )
                        patchSettings.initialUri?.let { Text(it) }
                        YActionRow {
                            YPrimaryActionButton(onClick = {
                                @Suppress("DEPRECATION")
                                startActivityForResult(
                                    SystemPickerBridge.openDocument(this@MainActivity),
                                    SystemPickerBridge.REQUEST_OPEN,
                                )
                            }) { Text(stringResource(R.string.system_pick_file)) }
                            YSecondaryActionButton(onClick = {
                                @Suppress("DEPRECATION")
                                startActivityForResult(
                                    SystemPickerBridge.openDocument(this@MainActivity, multiple = true),
                                    SystemPickerBridge.REQUEST_OPEN_MULTIPLE,
                                )
                            }) { Text(stringResource(R.string.system_pick_multiple)) }
                        }
                        YActionRow {
                            YSecondaryActionButton(onClick = {
                                @Suppress("DEPRECATION")
                                startActivityForResult(
                                    SystemPickerBridge.openTree(this@MainActivity),
                                    SystemPickerBridge.REQUEST_TREE,
                                )
                            }) { Text(stringResource(R.string.system_pick_folder)) }
                            YSecondaryActionButton(onClick = {
                                @Suppress("DEPRECATION")
                                startActivityForResult(
                                    SystemPickerBridge.createDocument(this@MainActivity),
                                    SystemPickerBridge.REQUEST_CREATE,
                                )
                            }) { Text(stringResource(R.string.system_create_file)) }
                        }
                        YActionRow {
                            YSecondaryActionButton(onClick = {
                                @Suppress("DEPRECATION")
                                startActivityForResult(
                                    SystemPickerBridge.openTree(this@MainActivity),
                                    SystemPickerBridge.REQUEST_DEFAULT_TREE,
                                )
                            }) { Text(stringResource(R.string.set_default_folder)) }
                            YSecondaryActionButton(
                                onClick = {
                                    patchSettings = YFilesPatchSettings.setInitialUri(this@MainActivity, null)
                                    pickerRevision++
                                },
                                enabled = !patchSettings.initialUri.isNullOrBlank(),
                            ) { Text(stringResource(R.string.clear_default_folder)) }
                        }
                        Text(stringResource(R.string.documentsui_note))
                    }
                }

                item { YFilesHookScopeCard(this@MainActivity) }

                item {
                    YFeatureCard(
                        title = stringResource(R.string.advanced_file_tools),
                        subtitle = stringResource(R.string.advanced_file_tools_summary),
                    ) {
                        YStatusRow(
                            stringResource(R.string.all_files_access),
                            if (allFilesGranted) stringResource(R.string.granted) else stringResource(R.string.not_granted),
                            if (allFilesGranted) YStatusTone.Good else YStatusTone.Warning,
                        )
                        YStatusRow(
                            stringResource(R.string.root_access),
                            if (rootGranted) stringResource(R.string.available) else stringResource(R.string.unavailable),
                            if (rootGranted) YStatusTone.Good else YStatusTone.Neutral,
                        )
                        if (!allFilesGranted) {
                            YSecondaryActionButton(onClick = {
                                YFilesSuiteRuntime.requestCapability(this@MainActivity, HostCapability.ALL_FILES)
                            }) { Text(stringResource(R.string.open_settings)) }
                        }
                        Text(stringResource(R.string.advanced_file_tools_reference_note))
                    }
                }

                item {
                    YFeatureCard(title = stringResource(R.string.location), subtitle = path) {
                        YSearchField(query, { query = it }, hint = stringResource(R.string.search_files))
                        YSettingSwitch(
                            title = stringResource(R.string.recursive_search),
                            subtitle = stringResource(R.string.recursive_search_summary),
                            checked = recursiveSearch,
                            onCheckedChange = { recursiveSearch = it },
                            enabled = !rootMode,
                        )
                        YSettingSwitch(
                            title = stringResource(R.string.show_hidden),
                            checked = showHidden,
                            onCheckedChange = { showHidden = it },
                            enabled = !rootMode,
                        )
                        YSettingSwitch(
                            title = stringResource(R.string.root_mode),
                            checked = rootMode,
                            onCheckedChange = { rootMode = it },
                            subtitle = stringResource(R.string.root_mode_summary),
                            enabled = rootGranted,
                        )
                        if (!rootMode) {
                            val localSortLabel = when (sortMode) {
                                FileSortMode.NAME -> stringResource(R.string.sort_name)
                                FileSortMode.MODIFIED -> stringResource(R.string.sort_modified)
                                FileSortMode.SIZE -> stringResource(R.string.sort_size)
                                FileSortMode.TYPE -> stringResource(R.string.sort_type)
                            }
                            YStatusRow(
                                stringResource(R.string.local_sort),
                                localSortLabel + " · " + if (sortDescending) {
                                    stringResource(R.string.descending)
                                } else {
                                    stringResource(R.string.ascending)
                                },
                                YStatusTone.Neutral,
                            )
                            YActionRow {
                                YSecondaryActionButton(onClick = { sortMode = FileSortMode.NAME }) {
                                    Text(stringResource(R.string.sort_name))
                                }
                                YSecondaryActionButton(onClick = { sortMode = FileSortMode.MODIFIED }) {
                                    Text(stringResource(R.string.sort_modified))
                                }
                                YSecondaryActionButton(onClick = { sortMode = FileSortMode.SIZE }) {
                                    Text(stringResource(R.string.sort_size))
                                }
                            }
                            YActionRow {
                                YSecondaryActionButton(onClick = { sortMode = FileSortMode.TYPE }) {
                                    Text(stringResource(R.string.sort_type))
                                }
                                YSecondaryActionButton(onClick = { sortDescending = !sortDescending }) {
                                    Text(
                                        if (sortDescending) {
                                            stringResource(R.string.descending)
                                        } else {
                                            stringResource(R.string.ascending)
                                        },
                                    )
                                }
                            }
                        }
                        pendingTransfer?.let { transfer ->
                            val transferLabel = if (transfer.mode == FileTransferMode.COPY) {
                                stringResource(R.string.copy)
                            } else {
                                stringResource(R.string.move)
                            }
                            YStatusRow(
                                stringResource(R.string.file_clipboard),
                                "$transferLabel: ${transfer.source.name}",
                                YStatusTone.Warning,
                            )
                            YActionRow {
                                YPrimaryActionButton(
                                    onClick = {
                                        operationBusy = true
                                        lifecycleScope.launch {
                                            val result = withContext(Dispatchers.IO) {
                                                repository.transfer(transfer, path)
                                            }
                                            result.onSuccess {
                                                pendingTransfer = null
                                                error = null
                                                refresh++
                                            }.onFailure { error = it.message }
                                            operationBusy = false
                                        }
                                    },
                                    enabled = !rootMode && !operationBusy,
                                ) { Text(stringResource(R.string.paste_here)) }
                                YSecondaryActionButton(
                                    onClick = { pendingTransfer = null },
                                    enabled = !operationBusy,
                                ) { Text(stringResource(R.string.yfiles_cancel)) }
                            }
                        }
                        YActionRow {
                            YSecondaryActionButton(
                                onClick = { repository.parent(path)?.let { path = it; query = "" } },
                                enabled = repository.parent(path) != null && !operationBusy,
                            ) { Text(stringResource(R.string.parent)) }
                            YSecondaryActionButton(onClick = { refresh++ }, enabled = !operationBusy) {
                                Text(stringResource(R.string.refresh))
                            }
                        }
                        YActionRow {
                            YPrimaryActionButton(onClick = { newFolderDialog = true }, enabled = !operationBusy) {
                                Text(stringResource(R.string.new_folder))
                            }
                            YSecondaryActionButton(onClick = { newFileDialog = true }, enabled = !operationBusy) {
                                Text(stringResource(R.string.new_file))
                            }
                        }
                    }
                }

                if (!rootMode) {
                    item {
                        YFilesExtraToolsCard(
                            path = path,
                            onNavigate = { target ->
                                path = target
                                query = ""
                            },
                            onChanged = { refresh++ },
                            onError = { error = it },
                        )
                    }
                }

                error?.let { item { YFeatureCard(title = stringResource(R.string.error), detail = it) } }

                if (rootMode) {
                    if (rootNames.isEmpty()) {
                        item { YFeatureCard(title = stringResource(R.string.empty_folder)) }
                    } else {
                        items(rootNames, key = { it }) { name ->
                            YFilesRootEntryCard(
                                parentPath = path,
                                name = name,
                                onNavigate = { target ->
                                    path = target
                                    query = ""
                                },
                                onChanged = { refresh++ },
                                onError = { error = it },
                            )
                        }
                    }
                } else if (entries.isEmpty()) {
                    item { YFeatureCard(title = stringResource(R.string.empty_folder)) }
                } else {
                    items(entries, key = { it.path }) { entry ->
                        YFeatureCard(
                            title = entry.name,
                            subtitle = if (entry.isDirectory) stringResource(R.string.folder) else formatBytes(entry.size),
                            detail = entry.path,
                        ) {
                            YActionRow {
                                if (entry.isDirectory) {
                                    YPrimaryActionButton(onClick = { path = entry.path; query = "" }, enabled = !operationBusy) {
                                        Text(stringResource(R.string.yfiles_open))
                                    }
                                } else {
                                    YPrimaryActionButton(onClick = { openFile(File(entry.path)) }, enabled = !operationBusy) {
                                        Text(stringResource(R.string.yfiles_open))
                                    }
                                    YSecondaryActionButton(onClick = { shareFile(File(entry.path)) }, enabled = !operationBusy) {
                                        Text(stringResource(R.string.share))
                                    }
                                }
                                YSecondaryActionButton(onClick = {
                                    pendingTransfer = PendingFileTransfer(entry, FileTransferMode.COPY)
                                    error = null
                                }, enabled = !operationBusy) { Text(stringResource(R.string.copy)) }
                                YSecondaryActionButton(onClick = {
                                    pendingTransfer = PendingFileTransfer(entry, FileTransferMode.MOVE)
                                    error = null
                                }, enabled = !operationBusy) { Text(stringResource(R.string.move)) }
                            }
                            YActionRow {
                                YSecondaryActionButton(onClick = {
                                    renameTarget = entry
                                    renameValue = entry.name
                                }, enabled = !operationBusy) { Text(stringResource(R.string.rename)) }
                                YSecondaryActionButton(onClick = {
                                    operationBusy = true
                                    lifecycleScope.launch {
                                        val result = withContext(Dispatchers.IO) { repository.duplicate(entry) }
                                        result.onSuccess { error = null; refresh++ }
                                            .onFailure { error = it.message }
                                        operationBusy = false
                                    }
                                }, enabled = !operationBusy) { Text(stringResource(R.string.duplicate)) }
                                YSecondaryActionButton(onClick = {
                                    operationBusy = true
                                    lifecycleScope.launch {
                                        val result = withContext(Dispatchers.IO) { repository.properties(entry) }
                                        result.onSuccess { propertyDialog = it; error = null }
                                            .onFailure { error = it.message }
                                        operationBusy = false
                                    }
                                }, enabled = !operationBusy) { Text(stringResource(R.string.properties)) }
                                YSecondaryActionButton(onClick = { deleteTarget = entry }, enabled = !operationBusy) {
                                    Text(stringResource(R.string.delete))
                                }
                            }
                            YActionRow {
                                YSecondaryActionButton(onClick = {
                                    operationBusy = true
                                    lifecycleScope.launch {
                                        val result = withContext(Dispatchers.IO) { repository.compressZip(entry) }
                                        result.onSuccess { error = null; refresh++ }
                                            .onFailure { error = it.message }
                                        operationBusy = false
                                    }
                                }, enabled = !operationBusy) { Text(stringResource(R.string.compress_zip)) }
                                if (!entry.isDirectory && entry.name.endsWith(".zip", ignoreCase = true)) {
                                    YSecondaryActionButton(onClick = {
                                        operationBusy = true
                                        lifecycleScope.launch {
                                            val result = withContext(Dispatchers.IO) { repository.extractZip(entry) }
                                            result.onSuccess { error = null; refresh++ }
                                                .onFailure { error = it.message }
                                            operationBusy = false
                                        }
                                    }, enabled = !operationBusy) { Text(stringResource(R.string.extract_zip)) }
                                }
                            }
                            YFilesEntryExtraActions(
                                entry = entry,
                                onChanged = { refresh++ },
                                onError = { error = it },
                            )
                        }
                    }
                }
            }
        }

        if (newFolderDialog) {
            AlertDialog(
                onDismissRequest = { if (!operationBusy) newFolderDialog = false },
                title = { Text(stringResource(R.string.new_folder)) },
                text = {
                    OutlinedTextField(
                        value = newFolderName,
                        onValueChange = { newFolderName = it },
                        label = { Text(stringResource(R.string.folder_name)) },
                    )
                },
                confirmButton = {
                    YPrimaryActionButton(
                        onClick = {
                            operationBusy = true
                            lifecycleScope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    if (rootMode) {
                                        YFilesSuiteRuntime.rootCreateFolder(path, newFolderName)
                                    } else {
                                        repository.createFolder(path, newFolderName).map { Unit }
                                    }
                                }
                                result.onSuccess {
                                    newFolderName = ""
                                    newFolderDialog = false
                                    error = null
                                    refresh++
                                }.onFailure { error = it.message }
                                operationBusy = false
                            }
                        },
                        enabled = newFolderName.isNotBlank() && !operationBusy,
                    ) { Text(stringResource(R.string.create)) }
                },
                dismissButton = {
                    YSecondaryActionButton(onClick = { newFolderDialog = false }, enabled = !operationBusy) {
                        Text(stringResource(R.string.yfiles_cancel))
                    }
                },
            )
        }

        if (newFileDialog) {
            AlertDialog(
                onDismissRequest = { if (!operationBusy) newFileDialog = false },
                title = { Text(stringResource(R.string.new_file)) },
                text = {
                    OutlinedTextField(
                        value = newFileName,
                        onValueChange = { newFileName = it },
                        label = { Text(stringResource(R.string.file_name)) },
                    )
                },
                confirmButton = {
                    YPrimaryActionButton(
                        onClick = {
                            operationBusy = true
                            lifecycleScope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    if (rootMode) {
                                        YFilesSuiteRuntime.rootCreateFile(path, newFileName)
                                    } else {
                                        repository.createFile(path, newFileName).map { Unit }
                                    }
                                }
                                result.onSuccess {
                                    newFileName = ""
                                    newFileDialog = false
                                    error = null
                                    refresh++
                                }.onFailure { error = it.message }
                                operationBusy = false
                            }
                        },
                        enabled = newFileName.isNotBlank() && !operationBusy,
                    ) { Text(stringResource(R.string.create)) }
                },
                dismissButton = {
                    YSecondaryActionButton(onClick = { newFileDialog = false }, enabled = !operationBusy) {
                        Text(stringResource(R.string.yfiles_cancel)) }
                },
            )
        }

        renameTarget?.let { entry ->
            AlertDialog(
                onDismissRequest = { renameTarget = null },
                title = { Text(stringResource(R.string.rename)) },
                text = {
                    OutlinedTextField(
                        value = renameValue,
                        onValueChange = { renameValue = it },
                        label = { Text(stringResource(R.string.new_name)) },
                        singleLine = true,
                    )
                },
                confirmButton = {
                    YPrimaryActionButton(
                        onClick = {
                            operationBusy = true
                            lifecycleScope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    repository.rename(entry, renameValue)
                                }
                                result.onSuccess {
                                    renameTarget = null
                                    error = null
                                    refresh++
                                }.onFailure { error = it.message }
                                operationBusy = false
                            }
                        },
                        enabled = renameValue.isNotBlank() && !operationBusy,
                    ) { Text(stringResource(R.string.rename)) }
                },
                dismissButton = {
                    YSecondaryActionButton(onClick = { renameTarget = null }, enabled = !operationBusy) {
                        Text(stringResource(R.string.yfiles_cancel)) }
                },
            )
        }

        deleteTarget?.let { entry ->
            AlertDialog(
                onDismissRequest = { deleteTarget = null },
                title = { Text(stringResource(R.string.confirm_delete)) },
                text = { Text(stringResource(R.string.confirm_delete_summary, entry.name)) },
                confirmButton = {
                    YPrimaryActionButton(
                        onClick = {
                            operationBusy = true
                            lifecycleScope.launch {
                                val result = withContext(Dispatchers.IO) { repository.delete(entry) }
                                result.onSuccess {
                                    deleteTarget = null
                                    error = null
                                    refresh++
                                }.onFailure { error = it.message }
                                operationBusy = false
                            }
                        },
                        enabled = !operationBusy,
                    ) { Text(stringResource(R.string.delete)) }
                },
                dismissButton = {
                    YSecondaryActionButton(onClick = { deleteTarget = null }, enabled = !operationBusy) {
                        Text(stringResource(R.string.yfiles_cancel)) }
                },
            )
        }

        propertyDialog?.let { properties ->
            AlertDialog(
                onDismissRequest = { propertyDialog = null },
                title = { Text(stringResource(R.string.properties)) },
                text = {
                    Text(
                        buildString {
                            appendLine(properties.name)
                            appendLine(properties.path)
                            appendLine()
                            appendLine(getString(R.string.size) + ": " + formatBytes(properties.size))
                            appendLine(
                                getString(R.string.modified) + ": " +
                                    DateFormat.getDateTimeInstance().format(Date(properties.modified))
                            )
                            properties.childCount?.let {
                                appendLine(getString(R.string.children) + ": " + it)
                            }
                            appendLine(
                                getString(R.string.permissions) + ": " +
                                    "R=${properties.readable} W=${properties.writable} X=${properties.executable}"
                            )
                            append(getString(R.string.hidden) + ": " + properties.hidden)
                        },
                    )
                },
                confirmButton = {
                    YPrimaryActionButton(onClick = { propertyDialog = null }) { Text(stringResource(R.string.close)) }
                },
            )
        }
    }

    private fun openFile(file: File) {
        runCatching {
            val uri = requireNotNull(YFilesSuiteRuntime.sharedFileUri(file)) {
                "Host file-share capability is unavailable"
            }
            startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, contentResolver.getType(uri) ?: "*/*")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
            )
        }.onFailure {
            YFilesSuiteRuntime.log(HostLogLevel.WARN, "open file failed: ${file.absolutePath}", it)
        }
    }

    private fun shareFile(file: File) {
        runCatching {
            val uri = requireNotNull(YFilesSuiteRuntime.sharedFileUri(file)) {
                "Host file-share capability is unavailable"
            }
            startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND)
                        .setType(contentResolver.getType(uri) ?: "application/octet-stream")
                        .putExtra(Intent.EXTRA_STREAM, uri)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                    getString(R.string.share),
                ),
            )
        }.onFailure {
            YFilesSuiteRuntime.log(HostLogLevel.WARN, "share file failed: ${file.absolutePath}", it)
        }
    }

    private fun formatBytes(value: Long): String {
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var size = value.toDouble()
        var unit = 0
        while (size >= 1024.0 && unit < units.lastIndex) {
            size /= 1024.0
            unit++
        }
        return if (unit == 0) "$value B" else "%.1f %s".format(size, units[unit])
    }
}
