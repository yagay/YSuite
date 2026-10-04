package com.yagay.ysuite.feature.yfiles

import androidx.lifecycle.viewModelScope
import com.yagay.ysuite.common.Outcome
import com.yagay.ysuite.feature.yfiles.api.YFileBatchResult
import com.yagay.ysuite.feature.yfiles.api.YFileClipboard
import com.yagay.ysuite.feature.yfiles.api.YFileEntry
import com.yagay.ysuite.feature.yfiles.api.YFileFailure
import com.yagay.ysuite.feature.yfiles.api.YFileProperties
import com.yagay.ysuite.feature.yfiles.api.YFileQuery
import com.yagay.ysuite.feature.yfiles.api.YFileSort
import com.yagay.ysuite.feature.yfiles.api.YFileTransferMode
import com.yagay.ysuite.feature.yfiles.api.YFilesPlacesRepository
import com.yagay.ysuite.feature.yfiles.api.YFilesRepository
import com.yagay.ysuite.feature.yfiles.api.YTrashEntry
import com.yagay.ysuite.logging.api.YSuiteLogger
import com.yagay.ysuite.presentation.YSuiteViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class YFilesViewMode {
    Files,
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
        val entry: YFileEntry,
        override val value: String,
    ) : YFilesNamePrompt
}

data class YFilesUiState(
    val path: String,
    val viewMode: YFilesViewMode = YFilesViewMode.Files,
    val query: String = "",
    val recursive: Boolean = false,
    val showHidden: Boolean = false,
    val sort: YFileSort = YFileSort.Name,
    val descending: Boolean = false,
    val entries: List<YFileEntry> = emptyList(),
    val trashEntries: List<YTrashEntry> = emptyList(),
    val favoritePaths: Set<String> = emptySet(),
    val recentPaths: List<String> = emptyList(),
    val selectedPaths: Set<String> = emptySet(),
    val properties: YFileProperties? = null,
    val clipboard: YFileClipboard? = null,
    val namePrompt: YFilesNamePrompt? = null,
    val operationResult: YFileBatchResult? = null,
    val loading: Boolean = true,
    val error: String? = null,
)

class YFilesViewModel(
    private val repository: YFilesRepository,
    private val placesRepository: YFilesPlacesRepository,
    private val logger: YSuiteLogger,
) : YSuiteViewModel<YFilesUiState, Nothing>(
    initialState = YFilesUiState(
        path = repository.initialPath(),
    ),
) {
    private var loadJob: Job? = null

    init {
        rememberCurrentPath()
        refresh()
    }

    fun setViewMode(mode: YFilesViewMode) {
        updateState {
            it.copy(
                viewMode = mode,
                selectedPaths = emptySet(),
                properties = null,
                error = null,
            )
        }
        refresh()
    }

    fun open(entry: YFileEntry) {
        if (entry.directory) {
            updateState {
                it.copy(
                    viewMode = YFilesViewMode.Files,
                    path = entry.path,
                    query = "",
                    selectedPaths = emptySet(),
                    properties = null,
                )
            }
            rememberCurrentPath()
            refresh()
        } else {
            loadProperties(entry)
        }
    }

    fun parent() {
        val parent = repository.parent(state.value.path)
            ?: return
        updateState {
            it.copy(
                viewMode = YFilesViewMode.Files,
                path = parent,
                query = "",
                selectedPaths = emptySet(),
                properties = null,
            )
        }
        rememberCurrentPath()
        refresh()
    }

    fun setQuery(value: String) {
        updateState { it.copy(query = value) }
        scheduleRefresh()
    }

    fun setRecursive(value: Boolean) {
        updateState { it.copy(recursive = value) }
        refresh()
    }

    fun setShowHidden(value: Boolean) {
        updateState { it.copy(showHidden = value) }
        refresh()
    }

    fun setSort(value: YFileSort) {
        updateState { it.copy(sort = value) }
        refresh()
    }

    fun setDescending(value: Boolean) {
        updateState { it.copy(descending = value) }
        refresh()
    }

    fun toggleSelection(entry: YFileEntry) {
        updateState { current ->
            val next = current.selectedPaths.toMutableSet()
            if (!next.add(entry.path)) {
                next.remove(entry.path)
            }
            current.copy(selectedPaths = next)
        }
    }

    fun toggleCurrentFavorite() {
        val snapshot =
            placesRepository.toggleFavorite(state.value.path)
        updateState {
            it.copy(
                favoritePaths = snapshot.favoritePaths,
                recentPaths = snapshot.recentPaths,
            )
        }
        if (state.value.viewMode == YFilesViewMode.Favorites) {
            refresh()
        }
    }

    fun beginCreateDirectory() {
        updateState {
            it.copy(
                namePrompt =
                    YFilesNamePrompt.CreateDirectory(),
            )
        }
    }

    fun beginCreateFile() {
        updateState {
            it.copy(
                namePrompt = YFilesNamePrompt.CreateFile(),
            )
        }
    }

    fun beginRename(path: String) {
        val entry = state.value.entries
            .firstOrNull { it.path == path }
            ?: return
        updateState {
            it.copy(
                namePrompt = YFilesNamePrompt.Rename(
                    entry = entry,
                    value = entry.name,
                ),
            )
        }
    }

    fun updatePromptValue(value: String) {
        updateState { current ->
            val prompt = when (
                val existing = current.namePrompt
            ) {
                is YFilesNamePrompt.CreateDirectory ->
                    existing.copy(value = value)
                is YFilesNamePrompt.CreateFile ->
                    existing.copy(value = value)
                is YFilesNamePrompt.Rename ->
                    existing.copy(value = value)
                null -> null
            }
            current.copy(namePrompt = prompt)
        }
    }

    fun dismissPrompt() {
        updateState { it.copy(namePrompt = null) }
    }

    fun confirmPrompt() {
        val prompt = state.value.namePrompt ?: return
        updateState { it.copy(namePrompt = null) }

        viewModelScope.launch {
            val result = when (prompt) {
                is YFilesNamePrompt.CreateDirectory ->
                    repository.createDirectory(
                        state.value.path,
                        prompt.value,
                    )
                is YFilesNamePrompt.CreateFile ->
                    repository.createFile(
                        state.value.path,
                        prompt.value,
                    )
                is YFilesNamePrompt.Rename ->
                    repository.rename(
                        prompt.entry,
                        prompt.value,
                    )
            }

            when (result) {
                is Outcome.Success -> {
                    updateState {
                        it.copy(
                            operationResult =
                                successResult(),
                            properties = null,
                        )
                    }
                    refresh()
                }
                is Outcome.Failure ->
                    recordFailure(result)
            }
        }
    }

    fun copySelected() {
        prepareTransfer(YFileTransferMode.Copy)
    }

    fun moveSelected() {
        prepareTransfer(YFileTransferMode.Move)
    }

    fun pasteHere() {
        val clipboard = state.value.clipboard ?: return
        if (state.value.viewMode != YFilesViewMode.Files) return

        viewModelScope.launch {
            when (
                val result = repository.transfer(
                    entries = clipboard.entries,
                    destinationPath = state.value.path,
                    mode = clipboard.mode,
                )
            ) {
                is Outcome.Success -> {
                    updateState {
                        it.copy(
                            clipboard = if (
                                clipboard.mode ==
                                    YFileTransferMode.Move
                            ) {
                                null
                            } else {
                                clipboard
                            },
                            operationResult = result.value,
                        )
                    }
                    refresh()
                }
                is Outcome.Failure ->
                    recordFailure(result)
            }
        }
    }

    fun moveSelectedToTrash() {
        val entries = selectedEntries()
        if (entries.isEmpty()) return

        viewModelScope.launch {
            when (
                val result = repository.moveToTrash(entries)
            ) {
                is Outcome.Success -> {
                    updateState {
                        it.copy(
                            selectedPaths = emptySet(),
                            operationResult = result.value,
                            properties = null,
                        )
                    }
                    refresh()
                }
                is Outcome.Failure ->
                    recordFailure(result)
            }
        }
    }

    fun restoreTrash(id: String) {
        viewModelScope.launch {
            when (
                val result = repository.restoreTrash(setOf(id))
            ) {
                is Outcome.Success -> {
                    updateState {
                        it.copy(operationResult = result.value)
                    }
                    refresh()
                }
                is Outcome.Failure ->
                    recordFailure(result)
            }
        }
    }

    fun emptyTrash() {
        viewModelScope.launch {
            when (val result = repository.emptyTrash()) {
                is Outcome.Success -> {
                    updateState {
                        it.copy(
                            operationResult =
                                YFileBatchResult(
                                    succeeded = result.value,
                                    failures = emptyList(),
                                ),
                        )
                    }
                    refresh()
                }
                is Outcome.Failure ->
                    recordFailure(result)
            }
        }
    }

    fun refresh() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            loadCurrent()
        }
    }

    private fun prepareTransfer(mode: YFileTransferMode) {
        val entries = selectedEntries()
        if (entries.isEmpty()) return

        updateState {
            it.copy(
                clipboard = YFileClipboard(
                    entries = entries,
                    mode = mode,
                ),
                selectedPaths = emptySet(),
            )
        }
    }

    private fun selectedEntries(): List<YFileEntry> {
        val selected = state.value.selectedPaths
        return state.value.entries.filter {
            it.path in selected
        }
    }

    private fun scheduleRefresh() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            delay(250L)
            loadCurrent()
        }
    }

    private suspend fun loadCurrent() {
        val current = state.value
        val places = placesRepository.snapshot()
        updateState {
            it.copy(
                loading = true,
                error = null,
                favoritePaths = places.favoritePaths,
                recentPaths = places.recentPaths,
            )
        }

        when (current.viewMode) {
            YFilesViewMode.Files -> loadFiles(current)
            YFilesViewMode.Favorites ->
                loadPaths(places.favoritePaths)
            YFilesViewMode.Recent ->
                loadPaths(places.recentPaths)
            YFilesViewMode.Trash -> loadTrash()
        }
    }

    private suspend fun loadFiles(current: YFilesUiState) {
        when (
            val result = repository.list(
                YFileQuery(
                    path = current.path,
                    text = current.query,
                    recursive = current.recursive,
                    showHidden = current.showHidden,
                    sort = current.sort,
                    descending = current.descending,
                ),
            )
        ) {
            is Outcome.Success -> {
                updateState {
                    it.copy(
                        entries = result.value,
                        trashEntries = emptyList(),
                        loading = false,
                        error = null,
                    )
                }
                logger.debug(
                    TAG,
                    "Loaded " + result.value.size + " entries",
                )
            }
            is Outcome.Failure ->
                loadFailure(
                    result,
                    "Unable to list " + current.path,
                )
        }
    }

    private suspend fun loadPaths(
        paths: Collection<String>,
    ) {
        when (val result = repository.resolve(paths)) {
            is Outcome.Success ->
                updateState {
                    it.copy(
                        entries = result.value,
                        trashEntries = emptyList(),
                        loading = false,
                        error = null,
                    )
                }
            is Outcome.Failure ->
                loadFailure(
                    result,
                    "Unable to resolve saved locations",
                )
        }
    }

    private suspend fun loadTrash() {
        when (val result = repository.listTrash()) {
            is Outcome.Success ->
                updateState {
                    it.copy(
                        entries = emptyList(),
                        trashEntries = result.value,
                        loading = false,
                        error = null,
                    )
                }
            is Outcome.Failure ->
                loadFailure(
                    result,
                    "Unable to list recycle bin",
                )
        }
    }

    private fun loadProperties(entry: YFileEntry) {
        viewModelScope.launch {
            when (
                val result = repository.properties(entry)
            ) {
                is Outcome.Success ->
                    updateState {
                        it.copy(properties = result.value)
                    }
                is Outcome.Failure ->
                    logger.error(
                        TAG,
                        "Unable to read file properties",
                        result.cause,
                    )
            }
        }
    }

    private fun rememberCurrentPath() {
        val snapshot =
            placesRepository.rememberRecent(state.value.path)
        updateState {
            it.copy(
                favoritePaths = snapshot.favoritePaths,
                recentPaths = snapshot.recentPaths,
            )
        }
    }

    private fun loadFailure(
        result: Outcome.Failure,
        logMessage: String,
    ) {
        updateState {
            it.copy(
                entries = emptyList(),
                trashEntries = emptyList(),
                loading = false,
                error = result.message,
            )
        }
        logger.error(
            TAG,
            logMessage,
            result.cause,
        )
    }

    private fun recordFailure(result: Outcome.Failure) {
        updateState {
            it.copy(
                operationResult =
                    YFileBatchResult(
                        succeeded = 0,
                        failures = listOf(
                            YFileFailure(
                                path = state.value.path,
                                code = result.error.code,
                            ),
                        ),
                    ),
            )
        }
        logger.error(
            TAG,
            "File operation failed",
            result.cause,
        )
    }

    private fun successResult(): YFileBatchResult =
        YFileBatchResult(
            succeeded = 1,
            failures = emptyList(),
        )

    companion object {
        private const val TAG = "YSuite/YFiles"
    }
}
