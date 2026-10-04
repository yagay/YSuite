package com.yagay.ysuite.feature.yfiles

import androidx.lifecycle.viewModelScope
import com.yagay.ysuite.common.Outcome
import com.yagay.ysuite.feature.yfiles.api.YFileEntry
import com.yagay.ysuite.feature.yfiles.api.YFileProperties
import com.yagay.ysuite.feature.yfiles.api.YFileQuery
import com.yagay.ysuite.feature.yfiles.api.YFileSort
import com.yagay.ysuite.feature.yfiles.api.YFilesRepository
import com.yagay.ysuite.logging.api.YSuiteLogger
import com.yagay.ysuite.presentation.YSuiteViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class YFilesUiState(
    val path: String,
    val query: String = "",
    val recursive: Boolean = false,
    val showHidden: Boolean = false,
    val sort: YFileSort = YFileSort.Name,
    val descending: Boolean = false,
    val entries: List<YFileEntry> = emptyList(),
    val selectedPaths: Set<String> = emptySet(),
    val properties: YFileProperties? = null,
    val loading: Boolean = true,
    val error: String? = null,
)

class YFilesViewModel(
    private val repository: YFilesRepository,
    private val logger: YSuiteLogger,
) : YSuiteViewModel<YFilesUiState, Nothing>(
    initialState = YFilesUiState(
        path = repository.initialPath(),
    ),
) {
    private var loadJob: Job? = null

    init {
        refresh()
    }

    fun open(entry: YFileEntry) {
        if (entry.directory) {
            updateState {
                it.copy(
                    path = entry.path,
                    query = "",
                    selectedPaths = emptySet(),
                    properties = null,
                )
            }
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
                path = parent,
                query = "",
                selectedPaths = emptySet(),
                properties = null,
            )
        }
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

    fun refresh() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            loadCurrent()
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
        updateState {
            it.copy(
                loading = true,
                error = null,
            )
        }

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
                        loading = false,
                        error = null,
                    )
                }
                logger.debug(
                    TAG,
                    "Loaded " + result.value.size + " entries",
                )
            }
            is Outcome.Failure -> {
                updateState {
                    it.copy(
                        entries = emptyList(),
                        loading = false,
                        error = result.message,
                    )
                }
                logger.error(
                    TAG,
                    "Unable to list " + current.path,
                    result.cause,
                )
            }
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

    companion object {
        private const val TAG = "YSuite/YFiles"
    }
}
