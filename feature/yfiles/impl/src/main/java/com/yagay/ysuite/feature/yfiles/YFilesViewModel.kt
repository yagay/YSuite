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
import com.yagay.ysuite.logging.api.YSuiteLogger
import com.yagay.ysuite.platform.api.CapabilityStatus
import com.yagay.ysuite.presentation.YSuiteViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class YFilesTab {
    Files,
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
    val entries: List<YFileNode> = emptyList(),
    val query: String = "",
    val recursive: Boolean = false,
    val showHidden: Boolean = false,
    val sort: YFileSort = YFileSort.Name,
    val descending: Boolean = false,
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
    val loading: Boolean = true,
    val error: String? = null,
)

class YFilesViewModel(
    private val environment: YFilesEnvironment,
    private val logger: YSuiteLogger,
) : YSuiteViewModel<YFilesUiState, Nothing>(
    initialState = YFilesUiState(
        providers = environment.engine.providers,
    ),
) {
    private var loadJob: Job? = null

    init {
        refreshPlaces()
        refreshRootStatus()
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
                    error = NO_PROVIDER_MESSAGE,
                )
            }
        }
    }

    fun setTab(tab: YFilesTab) {
        updateState {
            it.copy(tab = tab)
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
                node.name.endsWith(
                    ".zip",
                    ignoreCase = true,
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
                val stat =
                    environment.engine
                        .stat(record.ref)
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
        refresh()
    }

    fun setSort(value: YFileSort) {
        updateState {
            it.copy(sort = value)
        }
        refresh()
    }

    fun setDescending(value: Boolean) {
        updateState {
            it.copy(descending = value)
        }
        refresh()
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
            val result = when (prompt) {
                is YFilesNamePrompt
                    .CreateDirectory -> {
                    if (directory == null) {
                        return@launch
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
                        return@launch
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
            updateState {
                it.copy(progress = null)
            }
            val listener:
                (YFileOperationProgress) -> Unit =
                { progress ->
                    updateState {
                        it.copy(
                            progress = progress,
                        )
                    }
                }

            val result =
                if (clipboard.move) {
                    environment.engine
                        .moveBatch(
                            sources =
                                clipboard.refs,
                            destinationDirectory =
                                destination,
                            onProgress = listener,
                        )
                } else {
                    environment.engine
                        .copyBatch(
                            sources =
                                clipboard.refs,
                            destinationDirectory =
                                destination,
                            onProgress = listener,
                        )
                }

            updateState {
                it.copy(
                    clipboard =
                        if (clipboard.move) {
                            null
                        } else {
                            clipboard
                        },
                    operationResult = result,
                    progress = null,
                )
            }
            refresh()
        }
    }

    fun moveSelectedToTrash() {
        val refs =
            state.value.selected.toList()
        if (refs.isEmpty()) {
            return
        }
        viewModelScope.launch {
            val result =
                environment.trash
                    .moveToTrash(refs)
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
            val result =
                environment.engine
                    .deleteBatch(refs)
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
            val result =
                environment.trash.restore(
                    setOf(id),
                )
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
            val result =
                environment.trash.empty()
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

    fun refreshRootStatus() {
        viewModelScope.launch {
            val status =
                runCatching {
                    environment.rootGateway
                        .status()
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

    private fun navigate(
        ref: YFileRef,
        label: String,
    ) {
        updateState {
            it.copy(
                mode =
                    YFilesBrowserMode.Directory,
                activeProviderId =
                    ref.providerId,
                directory = ref,
                query = "",
                entries = emptyList(),
                selected = emptySet(),
                focused = null,
                error = null,
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

    private fun mountArchive(
        node: YFileNode,
    ) {
        viewModelScope.launch {
            updateState {
                it.copy(loading = true)
            }
            when (
                val mounted =
                    environment.archives
                        .mount(node.ref)
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

        when (
            val result =
                environment.engine.list(
                    directory,
                    YFileQuery(
                        text = current.query,
                        recursive =
                            current.recursive,
                        showHidden =
                            current.showHidden,
                        sort = current.sort,
                        descending =
                            current.descending,
                    ),
                )
        ) {
            is Outcome.Success ->
                updateState {
                    it.copy(
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
            is Outcome.Failure ->
                showFailure(result)
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
            val records =
                environment.trash.records()
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
