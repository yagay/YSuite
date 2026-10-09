package com.yagay.ysuite.feature.yentrycleaner

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yagay.ysuite.feature.yentrycleaner.api.YEntryCandidate
import com.yagay.ysuite.feature.yentrycleaner.api.YEntrySurface
import com.yagay.ysuite.platform.api.CapabilityStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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

internal data class YEntryCleanerUiState(
    val surface: YEntrySurface =
        YEntrySurface.ShareText,
    val candidates: List<YEntryCandidate> =
        emptyList(),
    val managingComponents: Boolean = false,
    val managedComponents: List<YEntryManagedComponent> = emptyList(),
    val managedLoading: Boolean = false,
    val managedError: String? = null,
    val recoveryStatus: YEntryComponentRecovery? = null,
    val recoveryRequested: Boolean = false,
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
    val customSlot: String = "CUSTOM_1",
    val customTitle: String = "",
    val customMimeTypes: String = "",
    val customExtensions: String = "",
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
    private var refreshJob: Job? = null
    private var managedJob: Job? = null
    private var recoveryPollJob: Job? = null

    init {
        setCustomSlot("CUSTOM_1")
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


    fun openManagedComponents() {
        mutableState.value = mutableState.value.copy(managingComponents = true)
        refreshManagedComponents()
    }

    fun closeManagedComponents() {
        managedJob?.cancel()
        mutableState.value = mutableState.value.copy(managingComponents = false)
    }

    fun refreshManagedComponents() {
        managedJob?.cancel()
        managedJob = viewModelScope.launch {
            mutableState.value = mutableState.value.copy(
                managedLoading = true, managedError = null,
            )
            try {
                val items = withContext(Dispatchers.IO) {
                    repository.managedComponents()
                }
                mutableState.value = mutableState.value.copy(
                    managedComponents = items,
                    managedLoading = false,
                    managedError = null,
                    recoveryStatus = repository.recoveryStatus(),
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableState.value = mutableState.value.copy(
                    managedLoading = false,
                    managedError = error.message ?: error.javaClass.simpleName,
                )
            }
        }
    }

    fun requestRecovery() {
        if (mutableState.value.recoveryRequested) return
        val previousAt = mutableState.value.recoveryStatus?.finishedAt ?: 0L
        val scheduled = repository.scheduleRecovery()
        mutableState.value = mutableState.value.copy(
            recoveryRequested = scheduled,
            managedError = if (scheduled) null else "Unable to schedule component recovery",
        )
        if (!scheduled) return
        recoveryPollJob?.cancel()
        recoveryPollJob = viewModelScope.launch {
            repeat(18) {
                delay(2_000L)
                val status = withContext(Dispatchers.IO) {
                    repository.recoveryStatus()
                }
                if (status != null && status.finishedAt > previousAt) {
                    mutableState.value = mutableState.value.copy(
                        recoveryStatus = status,
                        recoveryRequested = false,
                    )
                    if (mutableState.value.managingComponents) {
                        refreshManagedComponents()
                    }
                    return@launch
                }
            }
            mutableState.value = mutableState.value.copy(
                recoveryRequested = false,
            )
        }
    }

    fun setManagedLock(component: YEntryManagedComponent, locked: Boolean) {
        repository.setLocked(component.id, locked)
        mutableState.value = mutableState.value.copy(
            managedComponents = mutableState.value.managedComponents.map {
                if (it.id == component.id) it.copy(locked = locked) else it
            },
        )
    }

    fun changeManagedComponent(component: YEntryManagedComponent, enable: Boolean) {
        if (mutableState.value.busy || component.blocked) return
        mutableState.value = mutableState.value.copy(busy = true)
        viewModelScope.launch {
            try {
                val changed = withContext(Dispatchers.IO) {
                    repository.changeManagedComponent(component, enable)
                }
                mutableState.value = mutableState.value.copy(
                    busy = false,
                    statusToken = if (changed) "component_changed" else "component_failed",
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableState.value = mutableState.value.copy(
                    busy = false,
                    managedError = error.message ?: error.javaClass.simpleName,
                )
            }
            refreshManagedComponents()
        }
    }

    fun lockManagedComponents(components: List<YEntryManagedComponent>, locked: Boolean) {
        repository.setLocked(components.mapTo(hashSetOf()) { it.id }, locked)
        val selected = components.mapTo(hashSetOf()) { it.id }
        mutableState.value = mutableState.value.copy(
            managedComponents = mutableState.value.managedComponents.map {
                if (it.id in selected) it.copy(locked = locked) else it
            },
        )
    }

    fun invertManagedComponents(components: List<YEntryManagedComponent>) {
        if (mutableState.value.busy) return
        mutableState.value = mutableState.value.copy(busy = true)
        viewModelScope.launch {
            try {
                val (changed, failed) = withContext(Dispatchers.IO) {
                    repository.invertManagedComponents(components)
                }
                mutableState.value = mutableState.value.copy(
                    busy = false,
                    statusToken = "components_partial:$changed:$failed",
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableState.value = mutableState.value.copy(
                    busy = false,
                    managedError = error.message ?: error.javaClass.simpleName,
                )
            }
            refreshManagedComponents()
        }
    }

    fun bulkManagedComponents(components: List<YEntryManagedComponent>, enable: Boolean) {
        if (mutableState.value.busy) return
        mutableState.value = mutableState.value.copy(busy = true)
        viewModelScope.launch {
            try {
                val (changed, failed) = withContext(Dispatchers.IO) {
                    repository.changeManagedComponents(components, enable)
                }
                mutableState.value = mutableState.value.copy(
                    busy = false,
                    statusToken = "components_partial:$changed:$failed",
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableState.value = mutableState.value.copy(
                    busy = false,
                    managedError = error.message ?: error.javaClass.simpleName,
                )
            }
            refreshManagedComponents()
        }
    }

    fun setSurface(value: YEntrySurface) {
        mutableState.value =
            mutableState.value.copy(
                surface = value,
                candidates = emptyList(),
                selectedId = null,
                statusToken = null,
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

    fun rename(candidate: YEntryCandidate, name: String) {
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) {
                repository.setComponentTitle(candidate.id, name)
            }
            mutableState.value = mutableState.value.copy(
                statusToken = if (saved) "title_updated" else "title_invalid",
            )
            if (saved) refresh()
        }
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

    fun setGroupPriority(
        packageName: String, candidates: List<YEntryCandidate>,
        pinned: Boolean,
    ) {
        repository.setAppPriority(candidates, packageName, pinned)
        syncAndRefresh()
    }

    fun moveGroupPriority(
        packageName: String, candidates: List<YEntryCandidate>,
        delta: Int,
    ) {
        repository.moveAppPriority(candidates, packageName, delta)
        syncAndRefresh()
    }

    /** All manual and bulk edits share one in-flight guard and always
     * release it, including when Root execution throws or the screen closes.
     */
    private fun changeComponents(
        failureToken: String,
        action: suspend () -> String,
    ) {
        if (mutableState.value.busy) return
        mutableState.value = mutableState.value.copy(busy = true)
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { action() }
                mutableState.value = mutableState.value.copy(
                    statusToken = result,
                    managedError = null,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableState.value = mutableState.value.copy(
                    statusToken = failureToken,
                    managedError = error.message ?: error.javaClass.simpleName,
                )
            } finally {
                mutableState.value = mutableState.value.copy(busy = false)
                refresh()
            }
        }
    }

    fun component(candidate: YEntryCandidate, enable: Boolean) {
        // Explicit component changes are permitted even when a bulk lock
        // is active, but concurrent Root changes are not.
        changeComponents("component_failed") {
            if (repository.changeComponent(candidate, enable)) {
                "component_changed"
            } else {
                "component_failed"
            }
        }
    }

    fun bulk(hidden: Boolean) {
        val items = visible()
        changeComponents("rules_sync_failed") {
            when (repository.bulkHidden(items, hidden)) {
                is com.yagay.ysuite.common.Outcome.Failure -> "rules_sync_failed"
                is com.yagay.ysuite.common.Outcome.Success ->
                    if (hidden) "rules_hidden" else "rules_shown"
            }
        }
    }

    fun bulkComponents(enable: Boolean) {
        val items = visible()
        changeComponents("component_failed") {
            val (changed, failed) = repository.bulkComponents(items, enable)
            if (failed == 0) "components_changed:$changed"
            else "components_partial:$changed:$failed"
        }
    }

    fun invertComponents() {
        val items = visible()
        changeComponents("component_failed") {
            val (changed, failed) = repository.invertComponents(items)
            if (failed == 0) "components_changed:$changed"
            else "components_partial:$changed:$failed"
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

    fun useCustomSlot() {
        setOpenMime("preset:" + mutableState.value.customSlot)
    }

    fun setCustomSlot(slot: String) {
        if (slot !in (1..8).map { "CUSTOM_" + it }) return
        val draft = repository.customDraft(slot)
        mutableState.value = mutableState.value.copy(
            customSlot = slot,
            customTitle = draft.title,
            customMimeTypes = draft.mimeTypes,
            customExtensions = draft.extensions,
        )
    }

    fun setCustomTitle(value: String) {
        mutableState.value = mutableState.value.copy(customTitle = value)
    }

    fun setCustomMimeTypes(value: String) {
        mutableState.value = mutableState.value.copy(customMimeTypes = value)
    }

    fun setCustomExtensions(value: String) {
        mutableState.value = mutableState.value.copy(customExtensions = value)
    }

    fun resetCustom() {
        mutableState.value = mutableState.value.copy(
            customTitle = "", customMimeTypes = "", customExtensions = "",
        )
        saveCustom()
    }

    fun saveCustom() {
        val state = mutableState.value
        val draft = YEntryCustomDraft(
            state.customTitle, state.customMimeTypes, state.customExtensions,
        )
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) {
                repository.saveCustomDefinition(state.customSlot, draft)
            }
            if (!saved) {
                mutableState.value = mutableState.value.copy(statusToken = "custom_invalid")
                return@launch
            }
            val synced = withContext(Dispatchers.IO) {
                repository.sync()
            }
            mutableState.value = mutableState.value.copy(
                statusToken = if (synced is com.yagay.ysuite.common.Outcome.Failure) {
                    "rules_sync_failed"
                } else "custom_saved",
            )
            refresh()
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
            val result =
                runCatching {
                    withContext(
                        Dispatchers.IO,
                    ) {
                        repository
                            .importBackup(uri)
                    }
                }.getOrDefault(YEntryImportResult.Invalid)
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
                        when (result) {
                            YEntryImportResult.Synced -> "backup_restored"
                            YEntryImportResult.SavedLocally -> "backup_saved_local"
                            YEntryImportResult.Invalid -> "backup_failed"
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
        refreshJob?.cancel()
        val surface = mutableState.value.surface
        refreshJob = viewModelScope.launch {
            val items = try {
                withContext(Dispatchers.IO) {
                    repository.candidates(surface)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                if (mutableState.value.surface == surface) {
                    mutableState.value = mutableState.value.copy(
                        candidates = emptyList(),
                        statusToken = "candidate_load_failed",
                    )
                }
                return@launch
            }
            // Rapid surface changes must never restore another page's results.
            if (mutableState.value.surface != surface) return@launch
            mutableState.value = mutableState.value.copy(
                candidates = items,
                statusToken = if (mutableState.value.statusToken == "candidate_load_failed") {
                    null
                } else {
                    mutableState.value.statusToken
                },
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
