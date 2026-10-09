package com.yagay.ysuite.feature.yfiles

import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.yagay.ysuite.common.Outcome
import com.yagay.ysuite.feature.yfiles.api.YFileBatchResult
import com.yagay.ysuite.feature.yfiles.api.YFileClipboard
import com.yagay.ysuite.feature.yfiles.api.YFileNode
import com.yagay.ysuite.feature.yfiles.api.YFileOperationProgress
import com.yagay.ysuite.feature.yfiles.api.YFileProviderDescriptor
import com.yagay.ysuite.feature.yfiles.api.YFileQuery
import com.yagay.ysuite.feature.yfiles.api.YFileRef
import com.yagay.ysuite.feature.yfiles.api.YFileSort
import com.yagay.ysuite.feature.yfiles.api.YFileType
import com.yagay.ysuite.feature.yfiles.provider.archive.UniversalArchiveProvider
import com.yagay.ysuite.logging.api.YSuiteLogger
import com.yagay.ysuite.platform.api.CapabilityStatus
import com.yagay.ysuite.presentation.YSuiteViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class YFilesTab {
    Files,
    Transfers,
    Tools,
    Settings,
}

enum class YFilesBrowserMode {
    Directory,
    Favorites,
    Recent,
    Trash,
}

sealed interface YFilesNamePrompt {
    val value: String

    data class CreateDirectory(
        override val value: String = "",
    ) : YFilesNamePrompt

    data class CreateFile(
        override val value: String = "",
    ) : YFilesNamePrompt

    data class Rename(
        val ref: YFileRef,
        override val value: String,
    ) : YFilesNamePrompt
}

data class YFilesUiState(
    val tab: YFilesTab = YFilesTab.Files,
    val mode: YFilesBrowserMode =
        YFilesBrowserMode.Directory,
    val providers: List<YFileProviderDescriptor>,
    val activeProviderId: String? = null,
    val directory: YFileRef? = null,
    val canNavigateUp: Boolean = false,
    val entries: List<YFileNode> = emptyList(),
    val query: String = "",
    val recursive: Boolean = false,
    val showHidden: Boolean = false,
    val sort: YFileSort = YFileSort.Name,
    val descending: Boolean = false,
    val viewMode: YFilesViewMode = YFilesViewMode.List,
    val browserTabs: List<YFilesBrowserTabRecord> = emptyList(),
    val activeBrowserTabId: String? = null,
    val dualPaneEnabled: Boolean = false,
    val savedSearches: List<YFilesSavedSearch> = emptyList(),
    val taggedRefs: Map<YFileRef, Set<String>> = emptyMap(),
    val selected: Set<YFileRef> = emptySet(),
    val focused: YFileNode? = null,
    val clipboard: YFileClipboard? = null,
    val places: YFilesPlacesSnapshot =
        YFilesPlacesSnapshot(
            favorites = emptyList(),
            recent = emptyList(),
        ),
    val trashRecords: List<YTrashRecord> =
        emptyList(),
    val namePrompt: YFilesNamePrompt? = null,
    val operationResult: YFileBatchResult? = null,
    val progress: YFileOperationProgress? = null,
    val rootStatus: CapabilityStatus =
        CapabilityStatus.Unavailable,
    val shizukuStatus: CapabilityStatus =
        CapabilityStatus.Unavailable,
    val loading: Boolean = true,
    val error: String? = null,
)

class YFilesViewModel(
    private val environment: YFilesEnvironment,
    private val logger: YSuiteLogger,
    private val workspaceId: String = "primary",
) : YSuiteViewModel<YFilesUiState, Nothing>(
    initialState = YFilesUiState(
        providers = environment.engine.providers,
        dualPaneEnabled =
            environment.workspace
                .dualPaneEnabled(),
        savedSearches =
            environment.workspace
                .savedSearches(),
        taggedRefs =
            environment.workspace
                .allTaggedRefs(),
    ),
) {
    private var loadJob: Job? = null

    init {
        refreshPlaces()
        refreshRootStatus()
        refreshShizukuStatus()

        val restoredTabs =
            environment.workspace
                .tabs(workspaceId)
        val restoredActiveId =
            environment.workspace
                .activeTabId(workspaceId)
        val restoredActive =
            restoredTabs.firstOrNull {
                it.id == restoredActiveId
            } ?: restoredTabs.firstOrNull()

        if (restoredActive != null) {
            updateState {
                it.copy(
                    browserTabs =
                        restoredTabs,
                    activeBrowserTabId =
                        restoredActive.id,
                )
            }
            navigate(
                ref = restoredActive.ref,
                label = restoredActive.title,
                syncTab = false,
            )
        } else {
            val initial =
                environment.engine.providers
                    .firstOrNull {
                        it.id == "local"
                    }
                    ?: environment.engine.providers
                        .firstOrNull()
            if (initial != null) {
                selectProvider(initial.id)
            } else {
                updateState {
                    it.copy(
                        loading = false,
                        error =
                            NO_PROVIDER_MESSAGE,
                    )
                }
            }
        }
    }

    fun setTab(tab: YFilesTab) {
        updateState {
            it.copy(
                tab = tab,
                selected = emptySet(),
                focused = null,
                error = null,
            )
        }
    }

    fun canHandleBack(): Boolean {
        val current = state.value
        return current.selected.isNotEmpty() ||
            current.focused != null ||
            current.tab != YFilesTab.Files ||
            current.mode != YFilesBrowserMode.Directory ||
            current.canNavigateUp
    }

    fun navigateBack() {
        val current = state.value
        when {
            current.selected.isNotEmpty() ->
                clearSelection()
            current.focused != null ->
                focus(null)
            current.tab != YFilesTab.Files ->
                setTab(YFilesTab.Files)
            current.mode != YFilesBrowserMode.Directory ->
                setMode(YFilesBrowserMode.Directory)
            current.canNavigateUp ->
                parent()
        }
    }

    fun setMode(
        mode: YFilesBrowserMode,
    ) {
        updateState {
            it.copy(
                mode = mode,
                selected = emptySet(),
                focused = null,
                error = null,
            )
        }
        when (mode) {
            YFilesBrowserMode.Directory ->
                refresh()
            YFilesBrowserMode.Favorites,
            YFilesBrowserMode.Recent ->
                refreshPlaces()
            YFilesBrowserMode.Trash ->
                refreshTrash()
        }
    }

    fun selectProvider(
        providerId: String,
    ) {
        when (
            val root =
                environment.engine.root(
                    providerId,
                )
        ) {
            is Outcome.Success ->
                navigate(
                    ref = root.value,
                    label = providerId,
                )
            is Outcome.Failure ->
                showFailure(root)
        }
    }

    fun open(node: YFileNode) {
        when {
            node.type ==
                YFileType.Directory ->
                navigate(
                    node.ref,
                    node.name,
                )
            node.type ==
                YFileType.File &&
                UniversalArchiveProvider
                    .isSupported(
                        node.name,
                    ) ->
                mountArchive(node)
            else ->
                updateState {
                    it.copy(
                        focused = node,
                    )
                }
        }
    }

    fun navigateSaved(
        record: YFileLocationRecord,
    ) {
        viewModelScope.launch {
            when (
                val stat = withContext(Dispatchers.IO) {
                    environment.engine.stat(record.ref)
                }
            ) {
                is Outcome.Success ->
                    if (
                        stat.value.type ==
                            YFileType.Directory
                    ) {
                        navigate(
                            record.ref,
                            record.label,
                        )
                    } else {
                        updateState {
                            it.copy(
                                focused =
                                    stat.value,
                            )
                        }
                    }
                is Outcome.Failure ->
                    showFailure(stat)
            }
        }
    }

    fun parent() {
        val current =
            state.value.directory ?: return
        when (
            val parent =
                environment.engine.parent(
                    current,
                )
        ) {
            is Outcome.Success -> {
                val next =
                    parent.value ?: return
                navigate(
                    next,
                    next.path,
                )
            }
            is Outcome.Failure ->
                showFailure(parent)
        }
    }

    fun setQuery(value: String) {
        updateState {
            it.copy(query = value)
        }
        scheduleRefresh()
    }

    fun setRecursive(value: Boolean) {
        updateState {
            it.copy(recursive = value)
        }
        refresh()
    }

    fun setShowHidden(value: Boolean) {
        updateState {
            it.copy(showHidden = value)
        }
        persistDirectoryPreference()
        refresh()
    }

    fun setSort(value: YFileSort) {
        updateState {
            it.copy(sort = value)
        }
        persistDirectoryPreference()
        refresh()
    }

    fun setDescending(value: Boolean) {
        updateState {
            it.copy(descending = value)
        }
        persistDirectoryPreference()
        refresh()
    }

    fun setViewMode(
        value: YFilesViewMode,
    ) {
        updateState {
            it.copy(viewMode = value)
        }
        persistDirectoryPreference()
    }

    fun toggleViewMode() {
        setViewMode(
            if (
                state.value.viewMode ==
                    YFilesViewMode.List
            ) {
                YFilesViewMode.Grid
            } else {
                YFilesViewMode.List
            },
        )
    }

    fun setDualPaneEnabled(
        enabled: Boolean,
    ) {
        environment.workspace
            .setDualPaneEnabled(enabled)
        updateState {
            it.copy(
                dualPaneEnabled = enabled,
            )
        }
    }

    fun toggleDualPane() =
        setDualPaneEnabled(
            !state.value.dualPaneEnabled,
        )

    fun addBrowserTab() {
        val current =
            state.value.directory ?: return
        val title =
            current.path
                .trimEnd('/')
                .substringAfterLast('/')
                .ifBlank {
                    current.providerId
                }
        val tab =
            YFilesBrowserTabRecord(
                title = title,
                ref = current,
            )
        val tabs =
            state.value.browserTabs + tab
        environment.workspace.saveTabs(
            workspaceId,
            tabs,
            tab.id,
        )
        updateState {
            it.copy(
                browserTabs = tabs,
                activeBrowserTabId =
                    tab.id,
            )
        }
    }

    fun selectBrowserTab(id: String) {
        val tab =
            state.value.browserTabs
                .firstOrNull {
                    it.id == id
                } ?: return
        environment.workspace.saveTabs(
            workspaceId,
            state.value.browserTabs,
            id,
        )
        updateState {
            it.copy(
                activeBrowserTabId = id,
            )
        }
        navigate(
            ref = tab.ref,
            label = tab.title,
            syncTab = false,
        )
    }

    fun closeBrowserTab(id: String) {
        val current = state.value
        if (current.browserTabs.size <= 1) {
            return
        }
        val index =
            current.browserTabs
                .indexOfFirst {
                    it.id == id
                }
        if (index < 0) return
        val tabs =
            current.browserTabs
                .filterNot {
                    it.id == id
                }
        val next =
            if (
                current.activeBrowserTabId ==
                    id
            ) {
                tabs.getOrNull(
                    index.coerceAtMost(
                        tabs.lastIndex,
                    ),
                ) ?: tabs.last()
            } else {
                tabs.firstOrNull {
                    it.id ==
                        current
                            .activeBrowserTabId
                } ?: tabs.first()
            }
        environment.workspace.saveTabs(
            workspaceId,
            tabs,
            next.id,
        )
        updateState {
            it.copy(
                browserTabs = tabs,
                activeBrowserTabId =
                    next.id,
            )
        }
        if (
            current.activeBrowserTabId ==
                id
        ) {
            navigate(
                ref = next.ref,
                label = next.title,
                syncTab = false,
            )
        }
    }

    fun moveBrowserTab(
        id: String,
        delta: Int,
    ) {
        val tabs =
            state.value.browserTabs
                .toMutableList()
        val index =
            tabs.indexOfFirst {
                it.id == id
            }
        if (index < 0) return
        val target =
            (index + delta)
                .coerceIn(
                    0,
                    tabs.lastIndex,
                )
        if (target == index) return
        val tab = tabs.removeAt(index)
        tabs.add(target, tab)
        environment.workspace.saveTabs(
            workspaceId,
            tabs,
            state.value.activeBrowserTabId,
        )
        updateState {
            it.copy(browserTabs = tabs)
        }
    }

    fun setTags(
        ref: YFileRef,
        raw: String,
    ) {
        val tags =
            raw.split(',', ';', '\n')
                .map(String::trim)
                .filter(String::isNotBlank)
                .toSet()
        environment.workspace
            .setTags(ref, tags)
        updateState {
            it.copy(
                taggedRefs =
                    environment.workspace
                        .allTaggedRefs(),
            )
        }
    }

    fun saveCurrentSearch(
        name: String,
    ) {
        val current = state.value
        val root = current.directory ?: return
        if (
            name.isBlank() ||
            current.query.isBlank()
        ) {
            return
        }
        environment.workspace.saveSearch(
            YFilesSavedSearch(
                name = name.trim(),
                root = root,
                query = current.query,
                recursive = current.recursive,
                showHidden =
                    current.showHidden,
            ),
        )
        updateState {
            it.copy(
                savedSearches =
                    environment.workspace
                        .savedSearches(),
            )
        }
    }

    fun runSavedSearch(
        search: YFilesSavedSearch,
    ) {
        navigate(
            ref = search.root,
            label = search.name,
        )
        updateState {
            it.copy(
                query = search.query,
                recursive =
                    search.recursive,
                showHidden =
                    search.showHidden,
            )
        }
        refresh()
    }

    fun deleteSavedSearch(id: String) {
        environment.workspace
            .deleteSearch(id)
        updateState {
            it.copy(
                savedSearches =
                    environment.workspace
                        .savedSearches(),
            )
        }
    }

    fun selectAll() {
        updateState { current ->
            current.copy(
                selected =
                    current.entries
                        .map { it.ref }
                        .toSet(),
            )
        }
    }

    fun toggleSelection(
        node: YFileNode,
    ) {
        updateState { current ->
            val next =
                current.selected
                    .toMutableSet()
            if (!next.add(node.ref)) {
                next.remove(node.ref)
            }
            current.copy(
                selected = next,
            )
        }
    }

    fun clearSelection() {
        updateState {
            it.copy(selected = emptySet())
        }
    }

    fun toggleFavorite() {
        val directory =
            state.value.directory ?: return
        val label = directory.path
            .substringAfterLast('/')
            .ifBlank {
                directory.providerId
            }
        val snapshot =
            environment.places
                .toggleFavorite(
                    directory,
                    label,
                )
        updateState {
            it.copy(places = snapshot)
        }
    }

    fun addDocumentTree(
        uri: Uri,
        flags: Int,
    ) {
        val added =
            environment.documentTrees.add(
                uri,
                flags,
            )
        if (added) {
            selectProvider("document")
        } else {
            updateState {
                it.copy(
                    error =
                        DOCUMENT_PERMISSION_MESSAGE,
                )
            }
        }
    }

    fun removeDocumentTree(
        uri: Uri,
    ) {
        environment.documentTrees.remove(uri)
        if (
            state.value.activeProviderId ==
                "document"
        ) {
            selectProvider("document")
        }
    }

    fun beginCreateDirectory() {
        updateState {
            it.copy(
                namePrompt =
                    YFilesNamePrompt
                        .CreateDirectory(),
            )
        }
    }

    fun beginCreateFile() {
        updateState {
            it.copy(
                namePrompt =
                    YFilesNamePrompt
                        .CreateFile(),
            )
        }
    }

    fun beginRename(
        node: YFileNode,
    ) {
        updateState {
            it.copy(
                namePrompt =
                    YFilesNamePrompt.Rename(
                        ref = node.ref,
                        value = node.name,
                    ),
            )
        }
    }

    fun updatePromptValue(
        value: String,
    ) {
        updateState { current ->
            val prompt =
                when (
                    val existing =
                        current.namePrompt
                ) {
                    is YFilesNamePrompt
                        .CreateDirectory ->
                        existing.copy(
                            value = value,
                        )
                    is YFilesNamePrompt
                        .CreateFile ->
                        existing.copy(
                            value = value,
                        )
                    is YFilesNamePrompt
                        .Rename ->
                        existing.copy(
                            value = value,
                        )
                    null -> null
                }
            current.copy(
                namePrompt = prompt,
            )
        }
    }

    fun dismissPrompt() {
        updateState {
            it.copy(namePrompt = null)
        }
    }

    fun confirmPrompt() {
        val prompt =
            state.value.namePrompt ?: return
        val directory =
            state.value.directory
        updateState {
            it.copy(namePrompt = null)
        }

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                when (prompt) {
                is YFilesNamePrompt
                    .CreateDirectory -> {
                    if (directory == null) {
                        return@withContext Outcome.Failure(
                            code = "missing_directory",
                            message = environment.messages(
                                R.string.yfiles_select_folder_first,
                            ),
                        )
                    }
                    environment.engine
                        .createDirectory(
                            directory,
                            prompt.value,
                        )
                }
                is YFilesNamePrompt
                    .CreateFile -> {
                    if (directory == null) {
                        return@withContext Outcome.Failure(
                            code = "missing_directory",
                            message = environment.messages(
                                R.string.yfiles_select_folder_first,
                            ),
                        )
                    }
                    environment.engine
                        .createFile(
                            directory,
                            prompt.value,
                        )
                }
                is YFilesNamePrompt
                    .Rename ->
                    environment.engine
                        .rename(
                            prompt.ref,
                            prompt.value,
                        )
            }

            }

            when (result) {
                is Outcome.Success -> {
                    markSingleSuccess()
                    refresh()
                }
                is Outcome.Failure ->
                    showFailure(result)
            }
        }
    }

    fun prepareCopy() {
        prepareClipboard(move = false)
    }

    fun prepareMove() {
        prepareClipboard(move = true)
    }

    fun pasteHere() {
        val clipboard =
            state.value.clipboard ?: return
        val destination =
            state.value.directory ?: return

        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                environment.transfers.enqueue(
                    sources = clipboard.refs,
                    destination = destination,
                    move = clipboard.move,
                )
            }
            updateState {
                it.copy(
                    clipboard = null,
                    selected = emptySet(),
                    progress = null,
                    tab =
                        YFilesTab.Transfers,
                )
            }
        }
    }

    fun moveSelectedToTrash() {
        val refs =
            state.value.selected.toList()
        if (refs.isEmpty()) {
            return
        }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                environment.trash.moveToTrash(refs)
            }
            updateState {
                it.copy(
                    selected = emptySet(),
                    operationResult = result,
                    focused = null,
                )
            }
            refresh()
            refreshTrash()
        }
    }

    fun deleteSelectedPermanently() {
        val refs =
            state.value.selected.toList()
        if (refs.isEmpty()) {
            return
        }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                environment.engine.deleteBatch(refs)
            }
            updateState {
                it.copy(
                    selected = emptySet(),
                    operationResult = result,
                    focused = null,
                )
            }
            refresh()
        }
    }

    fun restoreTrash(
        id: String,
    ) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                environment.trash.restore(setOf(id))
            }
            updateState {
                it.copy(
                    operationResult = result,
                )
            }
            refreshTrash()
        }
    }

    fun emptyTrash() {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                environment.trash.empty()
            }
            updateState {
                it.copy(
                    operationResult = result,
                )
            }
            refreshTrash()
        }
    }

    fun focus(node: YFileNode?) {
        updateState {
            it.copy(focused = node)
        }
    }

    private fun scheduleRefresh() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            delay(250L)
            loadDirectory()
        }
    }

    fun refresh() {
        if (
            state.value.mode !=
                YFilesBrowserMode.Directory
        ) {
            when (state.value.mode) {
                YFilesBrowserMode.Favorites,
                YFilesBrowserMode.Recent ->
                    refreshPlaces()
                YFilesBrowserMode.Trash ->
                    refreshTrash()
                YFilesBrowserMode.Directory ->
                    Unit
            }
            return
        }

        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            delay(50L)
            loadDirectory()
        }
    }

    fun refreshShizukuStatus() {
        viewModelScope.launch {
            val status =
                runCatching {
                    withContext(Dispatchers.IO) {
                        environment.shizukuGateway.status()
                    }
                }.getOrDefault(
                    CapabilityStatus.Error,
                )
            updateState {
                it.copy(
                    shizukuStatus = status,
                )
            }
        }
    }

    fun refreshRootStatus() {
        viewModelScope.launch {
            val status =
                runCatching {
                    withContext(Dispatchers.IO) {
                        environment.rootGateway.status()
                    }
                }.getOrDefault(
                    CapabilityStatus.Error,
                )
            updateState {
                it.copy(
                    rootStatus = status,
                )
            }
        }
    }

    fun navigatePath(
        path: String,
    ) {
        val providerId =
            state.value.activeProviderId ?: return
        navigate(
            ref = YFileRef(
                providerId = providerId,
                path = path,
            ),
            label = path,
        )
    }

    private fun navigate(
        ref: YFileRef,
        label: String,
        syncTab: Boolean = true,
    ) {
        val canNavigateUp =
            when (
                val parent =
                    environment.engine.parent(ref)
            ) {
                is Outcome.Success ->
                    parent.value != null
                is Outcome.Failure ->
                    false
            }
        val preference =
            environment.workspace
                .directoryPreference(ref)
        updateState { current ->
            val tabs =
                if (syncTab) {
                    syncTab(
                        current.browserTabs,
                        current.activeBrowserTabId,
                        ref,
                        label,
                    )
                } else {
                    current.browserTabs
                }
            val activeId =
                if (
                    syncTab &&
                    current.activeBrowserTabId ==
                        null
                ) {
                    tabs.lastOrNull()?.id
                } else {
                    current.activeBrowserTabId
                }
            if (syncTab) {
                environment.workspace
                    .saveTabs(
                        workspaceId,
                        tabs,
                        activeId,
                    )
            }
            current.copy(
                mode =
                    YFilesBrowserMode.Directory,
                activeProviderId =
                    ref.providerId,
                directory = ref,
                canNavigateUp = canNavigateUp,
                query = "",
                entries = emptyList(),
                selected = emptySet(),
                focused = null,
                error = null,
                viewMode =
                    preference.viewMode,
                sort = preference.sort,
                descending =
                    preference.descending,
                showHidden =
                    preference.showHidden,
                browserTabs = tabs,
                activeBrowserTabId =
                    activeId,
            )
        }
        val snapshot =
            environment.places
                .rememberRecent(
                    ref,
                    label,
                )
        updateState {
            it.copy(places = snapshot)
        }
        refresh()
    }

    private fun persistDirectoryPreference() {
        val current = state.value
        val directory =
            current.directory ?: return
        environment.workspace
            .saveDirectoryPreference(
                directory,
                YFilesDirectoryPreference(
                    viewMode =
                        current.viewMode,
                    sort = current.sort,
                    descending =
                        current.descending,
                    showHidden =
                        current.showHidden,
                ),
            )
    }

    private fun syncTab(
        tabs: List<YFilesBrowserTabRecord>,
        activeId: String?,
        ref: YFileRef,
        label: String,
    ): List<YFilesBrowserTabRecord> {
        if (
            tabs.isEmpty() ||
            activeId == null
        ) {
            return listOf(
                YFilesBrowserTabRecord(
                    title = label,
                    ref = ref,
                ),
            )
        }
        return tabs.map { tab ->
            if (tab.id == activeId) {
                tab.copy(
                    title =
                        label.ifBlank {
                            ref.path
                                .substringAfterLast('/')
                                .ifBlank {
                                    ref.providerId
                                }
                        },
                    ref = ref,
                )
            } else {
                tab
            }
        }
    }

    private fun mountArchive(
        node: YFileNode,
    ) {
        viewModelScope.launch {
            updateState {
                it.copy(loading = true)
            }
            when (
                val mounted = withContext(Dispatchers.IO) {
                    environment.archives.mount(node.ref)
                }
            ) {
                is Outcome.Success ->
                    navigate(
                        mounted.value,
                        node.name,
                    )
                is Outcome.Failure ->
                    showFailure(mounted)
            }
        }
    }

    private suspend fun loadDirectory() {
        val current = state.value
        val directory =
            current.directory ?: return
        updateState {
            it.copy(
                loading = true,
                error = null,
            )
        }

        val result = try {
            withContext(Dispatchers.IO) {
                environment.engine.list(
                    directory,
                    YFileQuery(
                        text = current.query,
                        recursive = current.recursive,
                        showHidden = current.showHidden,
                        sort = current.sort,
                        descending = current.descending,
                    ),
                )
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            updateState { latest ->
                if (latest.directory == directory &&
                    latest.mode == YFilesBrowserMode.Directory &&
                    latest.query == current.query &&
                    latest.sort == current.sort &&
                    latest.descending == current.descending &&
                    latest.showHidden == current.showHidden &&
                    latest.recursive == current.recursive
                ) {
                    latest.copy(
                        loading = false,
                        error = error.message ?: error.javaClass.simpleName,
                    )
                } else latest
            }
            logger.error(TAG, "YFiles directory listing crashed", error)
            return
        }
        when (result) {
            is Outcome.Success ->
                updateState {
                    if (
                        it.directory != directory ||
                        it.mode != YFilesBrowserMode.Directory ||
                        it.query != current.query ||
                        it.sort != current.sort ||
                        it.descending != current.descending ||
                        it.showHidden != current.showHidden ||
                        it.recursive != current.recursive
                    ) it else it.copy(
                        entries =
                            result.value
                                .filterNot {
                                    node ->
                                    node.name ==
                                        ".YSuiteTrash"
                                },
                        loading = false,
                        error = null,
                    )
                }
            is Outcome.Failure -> {
                val latest = state.value
                if (latest.directory == directory &&
                    latest.mode == YFilesBrowserMode.Directory &&
                    latest.query == current.query &&
                    latest.sort == current.sort &&
                    latest.descending == current.descending &&
                    latest.showHidden == current.showHidden &&
                    latest.recursive == current.recursive
                ) {
                    showFailure(result)
                }
            }
        }
    }

    private fun refreshPlaces() {
        updateState {
            it.copy(
                places =
                    environment.places
                        .snapshot(),
                loading = false,
            )
        }
    }

    private fun refreshTrash() {
        viewModelScope.launch {
            val records = withContext(Dispatchers.IO) {
                environment.trash.records()
            }
            updateState {
                it.copy(
                    trashRecords = records,
                    loading = false,
                )
            }
        }
    }

    private fun prepareClipboard(
        move: Boolean,
    ) {
        val refs =
            state.value.selected.toList()
        if (refs.isEmpty()) {
            return
        }
        updateState {
            it.copy(
                clipboard =
                    YFileClipboard(
                        refs = refs,
                        move = move,
                    ),
                selected = emptySet(),
            )
        }
    }

    private fun markSingleSuccess() {
        updateState {
            it.copy(
                operationResult =
                    YFileBatchResult(
                        succeeded = 1,
                        skipped = 0,
                        failures = emptyList(),
                    ),
            )
        }
    }

    private fun showFailure(
        failure: Outcome.Failure,
    ) {
        updateState {
            it.copy(
                loading = false,
                error = failure.message,
            )
        }
        logger.error(
            TAG,
            "YFiles operation failed",
            failure.cause,
        )
    }

    companion object {
        private const val TAG = "YSuite/YFiles"
        private const val NO_PROVIDER_MESSAGE =
            "No file providers are available"
        private const val DOCUMENT_PERMISSION_MESSAGE =
            "Unable to retain document tree access"
    }
}
