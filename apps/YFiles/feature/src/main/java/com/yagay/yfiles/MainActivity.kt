package com.yagay.yfiles

import android.app.Activity
import android.content.Intent
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.lifecycleScope
import com.yagay.suite.api.HostCapability
import com.yagay.suite.api.HostCapabilityState
import com.yagay.suite.api.HostLogLevel
import com.yagay.yui.YHorizontalActions
import com.yagay.yui.YOverflowMenu
import com.yagay.yui.YListItem
import com.yagay.yui.YActionStyle
import com.yagay.yui.YActionSpec
import com.yagay.yui.YComposeActivity
import com.yagay.yui.YChoiceSetting
import com.yagay.yui.YCheckboxControl
import com.yagay.yui.YSection
import com.yagay.yui.YFilterBar
import com.yagay.yui.YFilterSpec
import com.yagay.yui.YFormDialog
import com.yagay.yui.YTabBar
import com.yagay.yui.YTabSpec
import com.yagay.yui.YToggleFilterBar
import com.yagay.yui.YActionGroup
import com.yagay.yui.YSectionHeader
import com.yagay.yui.YBreadcrumbBar
import com.yagay.yui.YBreadcrumbSegment
import com.yagay.yui.YPageList
import com.yagay.yui.YPageRole
import com.yagay.yui.YPageScaffold
import com.yagay.yui.YPrimaryActionButton
import com.yagay.yui.YSecondaryActionButton
import com.yagay.yui.YSearchField
import com.yagay.yui.YTextField
import com.yagay.yui.YSwitchItem
import com.yagay.yui.YStatusLine
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
        val extrasStore = remember { YFilesExtrasStore(this) }
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
        var checksumByPath by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
        var page by remember { mutableIntStateOf(0) }

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

        YPageScaffold(
            title = stringResource(R.string.yfiles_title),
            subtitle = stringResource(R.string.yfiles_subtitle),
            role = when (page) {
                0 -> YPageRole.BROWSER
                1 -> YPageRole.MANAGER
                else -> YPageRole.SETTINGS
            },
        ) { padding ->
            YPageList(padding) {
                item {
                    YTabBar(
                        tabs = listOf(
                            YTabSpec("files", stringResource(R.string.yfiles_tab_files)),
                            YTabSpec("tools", stringResource(R.string.yfiles_tab_tools)),
                            YTabSpec("settings", stringResource(R.string.yfiles_tab_settings)),
                        ),
                        selectedKey = when (page) {
                            0 -> "files"
                            1 -> "tools"
                            else -> "settings"
                        },
                        onSelected = {
                            page = when (it.key) {
                                "files" -> 0
                                "tools" -> 1
                                else -> 2
                            }
                        },
                    )
                }

                if (page == 2) {
                    item {
                        YSection(
                            title = stringResource(R.string.documentsui_integration),
                        subtitle = stringResource(R.string.documentsui_summary),
                    ) {
                        YStatusLine(
                            stringResource(R.string.documentsui),
                            stringResource(R.string.yfiles_preserved),
                            YStatusTone.Good,
                        )
                        YSwitchItem(
                            title = stringResource(R.string.enable_picker_patch),
                            subtitle = stringResource(R.string.enable_picker_patch_summary),
                            checked = patchSettings.enabled,
                            onCheckedChange = {
                                patchSettings = YFilesPatchSettings.update(this@MainActivity) { copy(enabled = it) }
                            },
                        )
                        YSwitchItem(
                            title = stringResource(R.string.local_files_only),
                            checked = patchSettings.localOnly,
                            onCheckedChange = {
                                patchSettings = YFilesPatchSettings.update(this@MainActivity) { copy(localOnly = it) }
                            },
                        )
                        YSwitchItem(
                            title = stringResource(R.string.allow_multiple_picker),
                            subtitle = stringResource(R.string.allow_multiple_picker_summary),
                            checked = patchSettings.allowMultiple,
                            onCheckedChange = {
                                patchSettings = YFilesPatchSettings.update(this@MainActivity) { copy(allowMultiple = it) }
                            },
                        )
                        val pickerSortValues = listOf(
                            YFilesPatchSettings.SORT_SYSTEM,
                            YFilesPatchSettings.SORT_NAME,
                            YFilesPatchSettings.SORT_DATE,
                            YFilesPatchSettings.SORT_SIZE,
                            YFilesPatchSettings.SORT_TYPE,
                        )
                        YChoiceSetting(
                            title = stringResource(R.string.default_sort),
                            options = listOf(
                                stringResource(R.string.system_default),
                                stringResource(R.string.sort_name),
                                stringResource(R.string.sort_modified),
                                stringResource(R.string.sort_size),
                                stringResource(R.string.sort_type),
                            ),
                            selectedIndex = pickerSortValues.indexOf(patchSettings.defaultSort).coerceAtLeast(0),
                            onSelected = { index ->
                                pickerSortValues.getOrNull(index)?.let { selected ->
                                    patchSettings = YFilesPatchSettings.update(this@MainActivity) {
                                        copy(defaultSort = selected)
                                    }
                                }
                            },
                        )
                        YStatusLine(
                            stringResource(R.string.default_picker_folder),
                            if (patchSettings.initialUri.isNullOrBlank()) {
                                stringResource(R.string.system_default)
                            } else {
                                stringResource(R.string.custom_folder)
                            },
                            if (patchSettings.initialUri.isNullOrBlank()) YStatusTone.Neutral else YStatusTone.Good,
                        )
                        patchSettings.initialUri?.let { Text(it) }
                        YHorizontalActions {
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
                        YHorizontalActions {
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
                        YHorizontalActions {
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
                }

                if (page == 1) {
                    item {
                        YSection(
                            title = stringResource(R.string.advanced_file_tools),
                        subtitle = stringResource(R.string.advanced_file_tools_summary),
                    ) {
                        YStatusLine(
                            stringResource(R.string.all_files_access),
                            if (allFilesGranted) stringResource(R.string.granted) else stringResource(R.string.not_granted),
                            if (allFilesGranted) YStatusTone.Good else YStatusTone.Warning,
                        )
                        YStatusLine(
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
                    if (!rootMode) {
                        item {
                            YFilesExtraToolsCard(
                                path = path,
                                onNavigate = { target ->
                                    path = target
                                    query = ""
                                    page = 0
                                },
                                onChanged = { refresh++ },
                                onError = { error = it },
                            )
                        }
                    }
                }

                if (page == 0) {
                    item {
                        YSectionHeader(title = stringResource(R.string.location))
                        YBreadcrumbBar(
                            segments = buildList {
                                add(
                                    YBreadcrumbSegment("/", "/") {
                                        path = "/"
                                        query = ""
                                    },
                                )
                                var current = ""
                                path.trim('/').split('/').filter { it.isNotBlank() }.forEach { segment ->
                                    current += "/$segment"
                                    val target = current
                                    add(
                                        YBreadcrumbSegment(target, segment) {
                                            path = target
                                            query = ""
                                        },
                                    )
                                }
                            },
                        )
                    }
                    item {
                        YSearchField(query, { query = it }, hint = stringResource(R.string.search_files))
                    }
                    item {
                        YToggleFilterBar(
                            filters = listOf(
                                YFilterSpec(
                                    label = stringResource(R.string.recursive_search),
                                    selected = recursiveSearch,
                                    enabled = !rootMode,
                                    onClick = { recursiveSearch = !recursiveSearch },
                                ),
                                YFilterSpec(
                                    label = stringResource(R.string.show_hidden),
                                    selected = showHidden,
                                    enabled = !rootMode,
                                    onClick = { showHidden = !showHidden },
                                ),
                                YFilterSpec(
                                    label = stringResource(R.string.root_mode),
                                    selected = rootMode,
                                    enabled = rootGranted,
                                    onClick = { rootMode = !rootMode },
                                ),
                                YFilterSpec(
                                    label = stringResource(R.string.descending),
                                    selected = sortDescending,
                                    enabled = !rootMode,
                                    onClick = { sortDescending = !sortDescending },
                                ),
                            ),
                        )
                    }
                    if (!rootMode) {
                        item {
                            val sortModes = FileSortMode.entries
                            YFilterBar(
                                options = listOf(
                                    stringResource(R.string.sort_name),
                                    stringResource(R.string.sort_modified),
                                    stringResource(R.string.sort_size),
                                    stringResource(R.string.sort_type),
                                ),
                                selectedIndex = sortModes.indexOf(sortMode).coerceAtLeast(0),
                                onSelected = { index ->
                                    sortModes.getOrNull(index)?.let { sortMode = it }
                                },
                            )
                        }
                    }
                    item {
                        YActionGroup(
                            actions = listOf(
                                YActionSpec(
                                    label = stringResource(R.string.parent),
                                    enabled = repository.parent(path) != null && !operationBusy,
                                    onClick = { repository.parent(path)?.let { path = it; query = "" } },
                                ),
                                YActionSpec(
                                    label = stringResource(R.string.refresh),
                                    enabled = !operationBusy,
                                    onClick = { refresh++ },
                                ),
                                YActionSpec(
                                    label = stringResource(R.string.new_folder),
                                    enabled = !operationBusy,
                                    style = YActionStyle.PRIMARY,
                                    onClick = { newFolderDialog = true },
                                ),
                                YActionSpec(
                                    label = stringResource(R.string.new_file),
                                    enabled = !operationBusy,
                                    onClick = { newFileDialog = true },
                                ),
                            ),
                        )
                    }
                    pendingTransfer?.let { transfer ->
                        item {
                            val transferLabel = if (transfer.mode == FileTransferMode.COPY) {
                                stringResource(R.string.copy)
                            } else {
                                stringResource(R.string.move)
                            }
                            YSection(
                                title = stringResource(R.string.file_clipboard),
                                subtitle = "$transferLabel: ${transfer.source.name}",
                            ) {
                                YActionGroup(
                                    actions = listOf(
                                        YActionSpec(
                                            label = stringResource(R.string.paste_here),
                                            enabled = !rootMode && !operationBusy,
                                            style = YActionStyle.PRIMARY,
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
                                        ),
                                        YActionSpec(
                                            label = stringResource(R.string.yfiles_cancel),
                                            enabled = !operationBusy,
                                            onClick = { pendingTransfer = null },
                                        ),
                                    ),
                                )
                            }
                        }
                    }

                error?.let { item { YSection(title = stringResource(R.string.error), detail = it) } }

                if (rootMode) {
                    if (rootNames.isEmpty()) {
                        item { YSection(title = stringResource(R.string.empty_folder)) }
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
                    item { YSection(title = stringResource(R.string.empty_folder)) }
                } else {
                    items(entries, key = { it.path }) { entry ->
                        val shareLabel = stringResource(R.string.share)
                        val copyLabel = stringResource(R.string.copy)
                        val moveLabel = stringResource(R.string.move)
                        val renameLabel = stringResource(R.string.rename)
                        val duplicateLabel = stringResource(R.string.duplicate)
                        val propertiesLabel = stringResource(R.string.properties)
                        val deleteLabel = stringResource(R.string.delete)
                        val compressLabel = stringResource(R.string.compress_zip)
                        val extractLabel = stringResource(R.string.extract_zip)
                        val shaLabel = stringResource(R.string.sha256)
                        val recycleLabel = stringResource(R.string.move_to_recycle_bin)

                        val menuActions = buildList {
                            if (!entry.isDirectory) {
                                add(
                                    YActionSpec(
                                        label = shareLabel,
                                        enabled = !operationBusy,
                                        onClick = { shareFile(File(entry.path)) },
                                    ),
                                )
                            }
                            add(
                                YActionSpec(
                                    label = copyLabel,
                                    enabled = !operationBusy,
                                    onClick = {
                                        pendingTransfer = PendingFileTransfer(entry, FileTransferMode.COPY)
                                        error = null
                                    },
                                ),
                            )
                            add(
                                YActionSpec(
                                    label = moveLabel,
                                    enabled = !operationBusy,
                                    onClick = {
                                        pendingTransfer = PendingFileTransfer(entry, FileTransferMode.MOVE)
                                        error = null
                                    },
                                ),
                            )
                            add(
                                YActionSpec(
                                    label = renameLabel,
                                    enabled = !operationBusy,
                                    onClick = {
                                        renameTarget = entry
                                        renameValue = entry.name
                                    },
                                ),
                            )
                            add(
                                YActionSpec(
                                    label = duplicateLabel,
                                    enabled = !operationBusy,
                                    onClick = {
                                        operationBusy = true
                                        lifecycleScope.launch {
                                            val result = withContext(Dispatchers.IO) { repository.duplicate(entry) }
                                            result.onSuccess { error = null; refresh++ }
                                                .onFailure { error = it.message }
                                            operationBusy = false
                                        }
                                    },
                                ),
                            )
                            add(
                                YActionSpec(
                                    label = propertiesLabel,
                                    enabled = !operationBusy,
                                    onClick = {
                                        operationBusy = true
                                        lifecycleScope.launch {
                                            val result = withContext(Dispatchers.IO) { repository.properties(entry) }
                                            result.onSuccess { propertyDialog = it; error = null }
                                                .onFailure { error = it.message }
                                            operationBusy = false
                                        }
                                    },
                                ),
                            )
                            add(
                                YActionSpec(
                                    label = compressLabel,
                                    enabled = !operationBusy,
                                    onClick = {
                                        operationBusy = true
                                        lifecycleScope.launch {
                                            val result = withContext(Dispatchers.IO) { repository.compressZip(entry) }
                                            result.onSuccess { error = null; refresh++ }
                                                .onFailure { error = it.message }
                                            operationBusy = false
                                        }
                                    },
                                ),
                            )
                            if (!entry.isDirectory && entry.name.endsWith(".zip", ignoreCase = true)) {
                                add(
                                    YActionSpec(
                                        label = extractLabel,
                                        enabled = !operationBusy,
                                        onClick = {
                                            operationBusy = true
                                            lifecycleScope.launch {
                                                val result = withContext(Dispatchers.IO) { repository.extractZip(entry) }
                                                result.onSuccess { error = null; refresh++ }
                                                    .onFailure { error = it.message }
                                                operationBusy = false
                                            }
                                        },
                                    ),
                                )
                            }
                            if (!entry.isDirectory) {
                                add(
                                    YActionSpec(
                                        label = shaLabel,
                                        enabled = !operationBusy,
                                        onClick = {
                                            operationBusy = true
                                            lifecycleScope.launch {
                                                val result = withContext(Dispatchers.IO) { extrasStore.sha256(entry) }
                                                result.onSuccess { checksum ->
                                                    checksumByPath = checksumByPath + (entry.path to checksum)
                                                    error = null
                                                }.onFailure { error = it.message }
                                                operationBusy = false
                                            }
                                        },
                                    ),
                                )
                            }
                            add(
                                YActionSpec(
                                    label = recycleLabel,
                                    enabled = !operationBusy,
                                    style = YActionStyle.DANGER,
                                    onClick = {
                                        operationBusy = true
                                        lifecycleScope.launch {
                                            val result = withContext(Dispatchers.IO) { extrasStore.moveToTrash(entry) }
                                            result.onSuccess {
                                                checksumByPath = checksumByPath - entry.path
                                                error = null
                                                refresh++
                                            }.onFailure { error = it.message }
                                            operationBusy = false
                                        }
                                    },
                                ),
                            )
                            add(
                                YActionSpec(
                                    label = deleteLabel,
                                    enabled = !operationBusy,
                                    style = YActionStyle.DANGER,
                                    onClick = { deleteTarget = entry },
                                ),
                            )
                        }

                        val checksum = checksumByPath[entry.path]
                        val entryDetail = when {
                            checksum != null -> stringResource(R.string.sha256_value, checksum)
                            recursiveSearch && query.isNotBlank() -> entry.path
                            else -> null
                        }
                        val selectedForBatch = YFilesBatchSelectionState.selected.containsKey(entry.path)

                        YListItem(
                            title = entry.name,
                            subtitle = if (entry.isDirectory) {
                                stringResource(R.string.folder)
                            } else {
                                formatBytes(entry.size)
                            },
                            detail = entryDetail,
                            enabled = !operationBusy,
                            onClick = {
                                if (entry.isDirectory) {
                                    path = entry.path
                                    query = ""
                                } else {
                                    openFile(File(entry.path))
                                }
                            },
                            trailing = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    YCheckboxControl(
                                        checked = selectedForBatch,
                                        onCheckedChange = { YFilesBatchSelectionState.toggle(entry) },
                                        enabled = !operationBusy,
                                    )
                                    YOverflowMenu(menuActions)
                                }
                            },
                        )
                    }
                }
                }
            }
        }

        if (newFolderDialog) {
            YFormDialog(
                title = stringResource(R.string.new_folder),
                onDismissRequest = { if (!operationBusy) newFolderDialog = false },
                actions = listOf(
                    YActionSpec(
                        label = stringResource(R.string.yfiles_cancel),
                        enabled = !operationBusy,
                        onClick = { newFolderDialog = false },
                    ),
                    YActionSpec(
                        label = stringResource(R.string.create),
                        enabled = newFolderName.isNotBlank() && !operationBusy,
                        style = YActionStyle.PRIMARY,
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
                    ),
                ),
            ) {
                YTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    label = stringResource(R.string.folder_name),
                )
            }
        }

        if (newFileDialog) {
            YFormDialog(
                title = stringResource(R.string.new_file),
                onDismissRequest = { if (!operationBusy) newFileDialog = false },
                actions = listOf(
                    YActionSpec(
                        label = stringResource(R.string.yfiles_cancel),
                        enabled = !operationBusy,
                        onClick = { newFileDialog = false },
                    ),
                    YActionSpec(
                        label = stringResource(R.string.create),
                        enabled = newFileName.isNotBlank() && !operationBusy,
                        style = YActionStyle.PRIMARY,
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
                    ),
                ),
            ) {
                YTextField(
                    value = newFileName,
                    onValueChange = { newFileName = it },
                    label = stringResource(R.string.file_name),
                )
            }
        }

        renameTarget?.let { entry ->
            YFormDialog(
                title = stringResource(R.string.rename),
                onDismissRequest = { renameTarget = null },
                actions = listOf(
                    YActionSpec(
                        label = stringResource(R.string.yfiles_cancel),
                        enabled = !operationBusy,
                        onClick = { renameTarget = null },
                    ),
                    YActionSpec(
                        label = stringResource(R.string.rename),
                        enabled = renameValue.isNotBlank() && !operationBusy,
                        style = YActionStyle.PRIMARY,
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
                    ),
                ),
            ) {
                YTextField(
                    value = renameValue,
                    onValueChange = { renameValue = it },
                    label = stringResource(R.string.new_name),
                )
            }
        }

        deleteTarget?.let { entry ->
            YFormDialog(
                title = stringResource(R.string.confirm_delete),
                onDismissRequest = { deleteTarget = null },
                actions = listOf(
                    YActionSpec(
                        label = stringResource(R.string.yfiles_cancel),
                        enabled = !operationBusy,
                        onClick = { deleteTarget = null },
                    ),
                    YActionSpec(
                        label = stringResource(R.string.delete),
                        enabled = !operationBusy,
                        style = YActionStyle.DANGER,
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
                    ),
                ),
            ) {
                Text(stringResource(R.string.confirm_delete_summary, entry.name))
            }
        }

        propertyDialog?.let { properties ->
            YFormDialog(
                title = stringResource(R.string.properties),
                onDismissRequest = { propertyDialog = null },
                actions = listOf(
                    YActionSpec(
                        label = stringResource(R.string.close),
                        style = YActionStyle.PRIMARY,
                        onClick = { propertyDialog = null },
                    ),
                ),
            ) {
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
            }
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
