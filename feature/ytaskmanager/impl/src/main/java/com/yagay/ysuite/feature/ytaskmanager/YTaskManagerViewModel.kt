package com.yagay.ysuite.feature.ytaskmanager

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yagay.ysuite.feature.ytaskmanager.api.YTaskPage
import com.yagay.ysuite.feature.ytaskmanager.api.YTaskProcess
import com.yagay.ysuite.feature.ytaskmanager.api.YTaskProcessKind
import com.yagay.ysuite.feature.ytaskmanager.api.YTaskProcessSort
import com.yagay.ysuite.feature.ytaskmanager.api.YTaskSnapshot
import com.yagay.ysuite.platform.api.CapabilityStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class YTaskManagerUiState(
    val rootStatus: CapabilityStatus = CapabilityStatus.Unavailable,
    val hookStatus: CapabilityStatus = CapabilityStatus.Unavailable,
    val snapshot: YTaskSnapshot = YTaskSnapshot(),
    val page: YTaskPage = YTaskPage.Processes,
    val query: String = "",
    val sort: YTaskProcessSort = YTaskProcessSort.Memory,
    val showUser: Boolean = true,
    val showSystem: Boolean = true,
    val showLinux: Boolean = false,
    val selectedPid: Int? = null,
    val loading: Boolean = true,
    val error: String? = null,
)

class YTaskManagerViewModel(
    private val environment: YTaskManagerEnvironment,
) : ViewModel() {
    private val repository =
        YTaskManagerRepository(
            environment.applicationContext,
            environment.rootGateway,
            environment.logger,
        )

    private val mutableState = MutableStateFlow(YTaskManagerUiState())
    val state: StateFlow<YTaskManagerUiState> = mutableState.asStateFlow()

    private var monitor: Job? = null

    init {
        monitor = viewModelScope.launch {
            while (isActive) {
                refreshInternal()
                delay(2_000L)
            }
        }
    }

    fun refresh() {
        viewModelScope.launch { refreshInternal() }
    }

    fun setPage(value: YTaskPage) {
        mutableState.value = mutableState.value.copy(page = value)
    }

    fun setQuery(value: String) {
        mutableState.value = mutableState.value.copy(query = value)
    }

    fun setSort(value: YTaskProcessSort) {
        mutableState.value = mutableState.value.copy(sort = value)
    }

    fun setKind(kind: YTaskProcessKind) {
        val old = mutableState.value
        mutableState.value = when (kind) {
            YTaskProcessKind.UserApp -> old.copy(showUser = !old.showUser)
            YTaskProcessKind.SystemApp -> old.copy(showSystem = !old.showSystem)
            YTaskProcessKind.Linux -> old.copy(showLinux = !old.showLinux)
        }
    }

    fun select(process: YTaskProcess?) {
        mutableState.value = mutableState.value.copy(
            selectedPid = process?.pid,
        )
    }

    fun selectedProcess(): YTaskProcess? =
        mutableState.value.snapshot.processes
            .firstOrNull { it.pid == mutableState.value.selectedPid }

    fun visibleProcesses(): List<YTaskProcess> {
        val state = mutableState.value
        val needle = state.query.trim()
        val filtered = state.snapshot.processes.filter { process ->
            val kindMatch = when (process.kind) {
                YTaskProcessKind.UserApp -> state.showUser
                YTaskProcessKind.SystemApp -> state.showSystem
                YTaskProcessKind.Linux -> state.showLinux
            }
            val textMatch =
                needle.isBlank() ||
                    process.displayName.contains(needle, true) ||
                    process.command.contains(needle, true) ||
                    process.pid.toString().contains(needle)
            kindMatch && textMatch
        }
        return when (state.sort) {
            YTaskProcessSort.Memory -> filtered.sortedByDescending { it.rssKb }
            YTaskProcessSort.Cpu -> filtered.sortedByDescending { it.cpuPercent }
            YTaskProcessSort.Download -> filtered.sortedByDescending { it.rxBytesPerSecond }
            YTaskProcessSort.Upload -> filtered.sortedByDescending { it.txBytesPerSecond }
            YTaskProcessSort.Name -> filtered.sortedBy { it.displayName.lowercase() }
            YTaskProcessSort.Pid -> filtered.sortedBy { it.pid }
        }
    }

    fun kill(process: YTaskProcess) {
        viewModelScope.launch {
            runCatching { repository.kill(process.pid) }
                .onFailure { report(it) }
            select(null)
            refreshInternal()
        }
    }

    fun forceStop(process: YTaskProcess) {
        val packageName = process.packageName ?: return
        viewModelScope.launch {
            runCatching { repository.forceStop(packageName) }
                .onFailure { report(it) }
            select(null)
            refreshInternal()
        }
    }

    private suspend fun refreshInternal() {
        val rootStatus =
            runCatching { repository.rootStatus() }
                .getOrDefault(CapabilityStatus.Error)
        val hookStatus =
            runCatching { environment.hookGateway.status() }
                .getOrDefault(CapabilityStatus.Error)
        if (rootStatus != CapabilityStatus.Available) {
            mutableState.value = mutableState.value.copy(
                rootStatus = rootStatus,
                hookStatus = hookStatus,
                loading = false,
            )
            return
        }
        runCatching { repository.snapshot() }
            .onSuccess { snapshot ->
                mutableState.value = mutableState.value.copy(
                    rootStatus = rootStatus,
                    hookStatus = hookStatus,
                    snapshot = snapshot,
                    loading = false,
                    error = null,
                )
            }
            .onFailure(::report)
    }

    private fun report(error: Throwable) {
        environment.logger.error(
            "YSuite/YTaskManager",
            "Collection failed",
            error,
        )
        mutableState.value = mutableState.value.copy(
            loading = false,
            error = error.message ?: error.javaClass.simpleName,
        )
    }

    class Factory(
        private val environment: YTaskManagerEnvironment,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            YTaskManagerViewModel(environment) as T
    }
}
