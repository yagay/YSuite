package com.yagay.ysuite.feature.yentrycleaner

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yagay.ysuite.feature.yentrycleaner.api.YEntryCandidate
import com.yagay.ysuite.feature.yentrycleaner.api.YEntrySurface
import com.yagay.ysuite.platform.api.CapabilityStatus
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
}

enum class YEntrySelectionFilter {
    All,
    Selected,
    Unselected,
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
    val selectionFilter: YEntrySelectionFilter =
        YEntrySelectionFilter.All,
    val selectedId: String? = null,
    val browserHost: String = "example.com",
    val browserHosts: List<String> =
        emptyList(),
    val openMime: String =
        "application/pdf",
    val displayMode: String =
        "HIDE_SELECTED",
    val diagnostic: Boolean = false,
    val rootStatus: CapabilityStatus =
        CapabilityStatus.Unavailable,
    val hookStatus: CapabilityStatus =
        CapabilityStatus.Unavailable,
    val backupUri: String? = null,
    val busy: Boolean = false,
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
                openMime =
                    repository.openMime(),
                displayMode =
                    repository.displayMode(),
                diagnostic =
                    repository.diagnostic(),
            ),
        )
    val state:
        StateFlow<YEntryCleanerUiState> =
        mutableState.asStateFlow()

    init {
        refresh()
        refreshRuntime()
        discoverBrowserHosts()
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                // This only requests missing scopes; it does not restart Android.
                repository.sync(reload = true)
            }
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

    fun setSelectionFilter(value: YEntrySelectionFilter) {
        mutableState.value =
            mutableState.value.copy(
                selectionFilter = value,
            )
    }

    fun visible(): List<YEntryCandidate> {
        val state = mutableState.value
        val q = state.query.trim()
        return state.candidates.filter { candidate ->
            val filterMatch =
                when (state.filter) {
                    YEntryAppFilter.All -> true
                    YEntryAppFilter.User ->
                        !candidate.system
                    YEntryAppFilter.System ->
                        candidate.system
                }
            val selectionMatch =
                when (state.selectionFilter) {
                    YEntrySelectionFilter.All -> true
                    YEntrySelectionFilter.Selected ->
                        candidate.hidden
                    YEntrySelectionFilter.Unselected ->
                        !candidate.hidden
                    YEntrySelectionFilter.Locked ->
                        candidate.locked
                }
            filterMatch && selectionMatch &&
                (
                    q.isBlank() ||
                        candidate.label.contains(q, true) ||
                        candidate.packageName.contains(q, true) ||
                        candidate.className.contains(q, true)
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
        // Locking only protects bulk actions; explicit edits are allowed.
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

    fun toggleGroupLock(
        packageName: String,
        value: Boolean,
    ) {
        val ids = mutableState.value.candidates
            .asSequence()
            .filter { it.packageName == packageName }
            .map { it.id }
            .toSet()
        repository.setLocked(ids, value)
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

    fun removePriority(candidate: YEntryCandidate) {
        repository.removePriority(candidate)
        syncAndRefresh()
    }

    fun component(
        candidate: YEntryCandidate,
        enable: Boolean,
    ) {
        // Explicit component changes remain allowed even when locked.
        viewModelScope.launch {
            mutableState.value =
                mutableState.value.copy(
                    busy = true,
                )
            val ok =
                withContext(Dispatchers.IO) {
                    repository.changeComponent(
                        candidate,
                        enable,
                    )
                }
            mutableState.value =
                mutableState.value.copy(
                    busy = false,
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
        val items = visible()
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                repository.bulkHidden(
                    items,
                    hidden,
                )
            }
            mutableState.value =
                mutableState.value.copy(
                    statusToken =
                        if (result is com.yagay.ysuite.common.Outcome.Failure) {
                            "rules_sync_failed"
                        } else if (hidden) {
                            "rules_hidden"
                        } else {
                            "rules_shown"
                        },
                )
            refresh()
        }
    }

    fun bulkComponents(enable: Boolean) {
        val items = visible()
        viewModelScope.launch {
            mutableState.value =
                mutableState.value.copy(
                    busy = true,
                )
            val (changed, failed) =
                withContext(Dispatchers.IO) {
                    repository.bulkComponents(
                        items,
                        enable,
                    )
                }
            mutableState.value =
                mutableState.value.copy(
                    busy = false,
                    statusToken =
                        if (failed == 0) {
                            "components_changed:$changed"
                        } else {
                            "components_partial:$changed:$failed"
                        },
                )
            refresh()
        }
    }

    fun invertComponents() {
        val items = visible()
        viewModelScope.launch {
            mutableState.value =
                mutableState.value.copy(
                    busy = true,
                )
            val (changed, failed) =
                withContext(Dispatchers.IO) {
                    repository
                        .invertComponents(
                            items,
                        )
                }
            mutableState.value =
                mutableState.value.copy(
                    busy = false,
                    statusToken =
                        if (failed == 0) {
                            "components_changed:$changed"
                        } else {
                            "components_partial:$changed:$failed"
                        },
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

    fun discoverBrowserHosts() {
        viewModelScope.launch {
            val hosts =
                withContext(Dispatchers.IO) {
                    repository
                        .discoverBrowserHosts()
                }
            mutableState.value =
                mutableState.value.copy(
                    browserHosts = hosts,
                    statusToken =
                        if (hosts.isNotEmpty()) {
                            "hosts_discovered:" +
                                hosts.size
                        } else {
                            mutableState.value
                                .statusToken
                        },
                )
        }
    }

    fun setOpenMime(value: String) {
        repository.setOpenMime(value)
        mutableState.value =
            mutableState.value.copy(
                openMime =
                    repository.openMime(),
            )
        if (
            mutableState.value.surface ==
            YEntrySurface.Open
        ) {
            refresh()
        }
    }

    fun exportBackup() {
        viewModelScope.launch {
            val uri =
                runCatching {
                    withContext(
                        Dispatchers.IO,
                    ) {
                        repository
                            .exportBackup()
                    }
                }.getOrNull()
            mutableState.value =
                mutableState.value.copy(
                    backupUri = uri,
                    statusToken =
                        if (uri != null) {
                            "backup_exported"
                        } else {
                            "backup_failed"
                        },
                )
        }
    }

    fun importBackup(uri: Uri) {
        viewModelScope.launch {
            mutableState.value =
                mutableState.value.copy(
                    busy = true,
                )
            val ok =
                runCatching {
                    withContext(
                        Dispatchers.IO,
                    ) {
                        repository
                            .importBackup(uri)
                    }
                }.getOrDefault(false)
            mutableState.value =
                mutableState.value.copy(
                    busy = false,
                    browserHost =
                        repository.browserHost(),
                    openMime =
                        repository.openMime(),
                    displayMode =
                        repository.displayMode(),
                    diagnostic =
                        repository.diagnostic(),
                    statusToken =
                        if (ok) {
                            "backup_restored"
                        } else {
                            "backup_failed"
                        },
                )
            refresh()
            refreshRuntime()
        }
    }

    fun toggleDisplayMode() {
        val next =
            when (mutableState.value.displayMode) {
                "HIDE_SELECTED" -> "SHOW_SELECTED"
                "SHOW_SELECTED" -> "SHOW_ALL"
                else -> "HIDE_SELECTED"
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

    fun refreshRuntime() {
        viewModelScope.launch {
            val root =
                runCatching {
                    withContext(Dispatchers.IO) {
                        repository.rootStatus()
                    }
                }.getOrDefault(
                    CapabilityStatus.Error,
                )
            val hook =
                runCatching {
                    withContext(Dispatchers.IO) {
                        repository.hookStatus()
                    }
                }.getOrDefault(
                    CapabilityStatus.Error,
                )
            mutableState.value =
                mutableState.value.copy(
                    rootStatus = root,
                    hookStatus = hook,
                )
        }
    }

    fun refresh() {
        val surface =
            mutableState.value.surface
        viewModelScope.launch {
            val items =
                withContext(Dispatchers.IO) {
                    repository.candidates(
                        surface,
                    )
                }
            mutableState.value =
                mutableState.value.copy(
                    candidates = items,
                )
        }
    }

    private fun syncAndRefresh() {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                repository.sync()
            }
            if (result is com.yagay.ysuite.common.Outcome.Failure) {
                mutableState.value =
                    mutableState.value.copy(
                        statusToken = "rules_sync_failed",
                    )
            }
            refreshRuntime()
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
