package com.yagay.ysuite.feature.yentrycleaner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yagay.ysuite.feature.yentrycleaner.api.YEntryCandidate
import com.yagay.ysuite.feature.yentrycleaner.api.YEntrySurface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class YEntryAppFilter {
    All,
    User,
    System,
    Hidden,
    Locked,
}

data class YEntryCleanerUiState(
    val surface: YEntrySurface =
        YEntrySurface.ShareText,
    val candidates: List<YEntryCandidate> =
        emptyList(),
    val query: String = "",
    val filter: YEntryAppFilter =
        YEntryAppFilter.All,
    val selectedId: String? = null,
    val browserHost: String = "example.com",
    val displayMode: String = "HIDE_SELECTED",
    val diagnostic: Boolean = false,
    val statusToken: String? = null,
)

internal class YEntryCleanerViewModel(
    private val repository:
        YEntryCleanerRepository,
) : ViewModel() {
    private val mutableState =
        MutableStateFlow(
            YEntryCleanerUiState(
                browserHost =
                    repository.browserHost(),
                displayMode =
                    repository.displayMode(),
                diagnostic =
                    repository.diagnostic(),
            ),
        )
    val state: StateFlow<YEntryCleanerUiState> =
        mutableState.asStateFlow()

    init {
        refresh()
        viewModelScope.launch {
            repository.sync()
        }
    }

    fun setSurface(value: YEntrySurface) {
        mutableState.value =
            mutableState.value.copy(
                surface = value,
                selectedId = null,
            )
        refresh()
    }

    fun setQuery(value: String) {
        mutableState.value =
            mutableState.value.copy(
                query = value,
            )
    }

    fun setFilter(value: YEntryAppFilter) {
        mutableState.value =
            mutableState.value.copy(
                filter = value,
            )
    }

    fun visible(): List<YEntryCandidate> {
        val state = mutableState.value
        val q = state.query.trim()
        return state.candidates.filter {
            candidate ->
            val filterMatch =
                when (state.filter) {
                    YEntryAppFilter.All -> true
                    YEntryAppFilter.User ->
                        !candidate.system
                    YEntryAppFilter.System ->
                        candidate.system
                    YEntryAppFilter.Hidden ->
                        candidate.hidden
                    YEntryAppFilter.Locked ->
                        candidate.locked
                }
            filterMatch &&
                (
                    q.isBlank() ||
                        candidate.label
                            .contains(q, true) ||
                        candidate.packageName
                            .contains(q, true) ||
                        candidate.className
                            .contains(q, true)
                    )
        }
    }

    fun select(id: String?) {
        mutableState.value =
            mutableState.value.copy(
                selectedId = id,
            )
    }

    fun selected(): YEntryCandidate? =
        mutableState.value.candidates
            .firstOrNull {
                it.id ==
                    mutableState.value.selectedId
            }

    fun toggleHidden(
        candidate: YEntryCandidate,
        value: Boolean,
    ) {
        if (candidate.locked) return
        repository.setHidden(candidate.id, value)
        syncAndRefresh()
    }

    fun toggleLock(
        candidate: YEntryCandidate,
        value: Boolean,
    ) {
        repository.setLocked(candidate.id, value)
        refresh()
    }

    fun move(
        candidate: YEntryCandidate,
        delta: Int,
    ) {
        repository.movePriority(
            candidate,
            delta,
        )
        syncAndRefresh()
    }

    fun component(
        candidate: YEntryCandidate,
        enable: Boolean,
    ) {
        if (candidate.locked) return
        viewModelScope.launch {
            val ok =
                withContext(Dispatchers.IO) {
                    repository.changeComponent(
                        candidate,
                        enable,
                    )
                }
            mutableState.value =
                mutableState.value.copy(
                    statusToken =
                        if (ok) {
                            "component_changed"
                        } else {
                            "component_failed"
                        },
                )
            refresh()
        }
    }

    fun bulk(hidden: Boolean) {
        val visible = visible()
        viewModelScope.launch {
            repository.bulkHidden(
                visible,
                hidden,
            )
            refresh()
        }
    }

    fun setBrowserHost(value: String) {
        repository.setBrowserHost(value)
        mutableState.value =
            mutableState.value.copy(
                browserHost =
                    repository.browserHost(),
            )
        if (
            mutableState.value.surface ==
            YEntrySurface.Browser
        ) {
            refresh()
        }
    }

    fun toggleDisplayMode() {
        val next =
            if (
                mutableState.value
                    .displayMode ==
                "HIDE_SELECTED"
            ) {
                "SHOW_SELECTED"
            } else {
                "HIDE_SELECTED"
            }
        repository.setDisplayMode(next)
        mutableState.value =
            mutableState.value.copy(
                displayMode = next,
            )
        syncAndRefresh()
    }

    fun setDiagnostic(value: Boolean) {
        repository.setDiagnostic(value)
        mutableState.value =
            mutableState.value.copy(
                diagnostic = value,
            )
        syncAndRefresh()
    }

    fun refresh() {
        val surface = mutableState.value.surface
        viewModelScope.launch {
            val items =
                withContext(Dispatchers.IO) {
                    repository.candidates(surface)
                }
            mutableState.value =
                mutableState.value.copy(
                    candidates = items,
                )
        }
    }

    private fun syncAndRefresh() {
        viewModelScope.launch {
            repository.sync()
            refresh()
        }
    }

    class Factory(
        private val repository:
            YEntryCleanerRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(
            modelClass: Class<T>,
        ): T =
            YEntryCleanerViewModel(
                repository,
            ) as T
    }
}
