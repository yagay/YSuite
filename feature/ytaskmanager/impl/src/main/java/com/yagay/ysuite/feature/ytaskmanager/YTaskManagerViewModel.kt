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

data class YTaskResourceHistoryState(
    val cpu: List<Float> = emptyList(),
    val ram: List<Float> = emptyList(),
    val swap: List<Float> = emptyList(),
    val gpu: List<Float> = emptyList(),
)

data class YTaskManagerUiState(
    val rootStatus: CapabilityStatus = CapabilityStatus.Unavailable,
    val hookStatus: CapabilityStatus = CapabilityStatus.Unavailable,
    val snapshot: YTaskSnapshot = YTaskSnapshot(),
    val resourceHistory:
        YTaskResourceHistoryState =
        YTaskResourceHistoryState(),
    val page: YTaskPage = YTaskPage.Processes,
    val query: String = "",
    val sort: YTaskProcessSort = YTaskProcessSort.Memory,
    val showUser: Boolean = true,
    val showSystem: Boolean = true,
    val showLinux: Boolean = false,
    val autoRefresh: Boolean = true,
    val refreshIntervalMs: Long = 800L,
    val confirmKill: Boolean = true,
    val selectedPid: Int? = null,
    val selectedDetail: YTaskProcess? = null,
    val pendingKillPid: Int? = null,
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
    private val settings =
        YTaskManagerSettings(
            environment.applicationContext,
        )

    private val mutableState =
        MutableStateFlow(
            YTaskManagerUiState(
                sort = settings.sort,
                showUser = settings.showUser,
                showSystem = settings.showSystem,
                showLinux = settings.showLinux,
                autoRefresh = settings.autoRefresh,
                refreshIntervalMs = settings.refreshIntervalMs,
                confirmKill = settings.confirmKill,
            ),
        )
    val state: StateFlow<YTaskManagerUiState> =
        mutableState.asStateFlow()

    private var monitor: Job? = null

    init {
        monitor =
            viewModelScope.launch {
                refreshInternal()
                while (isActive) {
                    val current = mutableState.value
                    if (current.autoRefresh) {
                        delay(current.refreshIntervalMs)
                        if (
                            isActive &&
                            mutableState.value.autoRefresh
                        ) {
                            refreshInternal()
                        }
                    } else {
                        delay(250L)
                    }
                }
            }
    }

    fun refresh() {
        viewModelScope.launch { refreshInternal() }
    }

    fun setPage(value: YTaskPage) {
        mutableState.value =
            mutableState.value.copy(page = value)
    }

    fun setQuery(value: String) {
        mutableState.value =
            mutableState.value.copy(query = value)
    }

    fun setSort(value: YTaskProcessSort) {
        settings.sort = value
        mutableState.value =
            mutableState.value.copy(sort = value)
    }

    fun setKind(kind: YTaskProcessKind) {
        val old = mutableState.value
        mutableState.value =
            when (kind) {
                YTaskProcessKind.UserApp -> {
                    val value = !old.showUser
                    settings.showUser = value
                    old.copy(showUser = value)
                }
                YTaskProcessKind.SystemApp -> {
                    val value = !old.showSystem
                    settings.showSystem = value
                    old.copy(showSystem = value)
                }
                YTaskProcessKind.Linux -> {
                    val value = !old.showLinux
                    settings.showLinux = value
                    old.copy(showLinux = value)
                }
            }
    }

    fun setAutoRefresh(value: Boolean) {
        settings.autoRefresh = value
        mutableState.value =
            mutableState.value.copy(
                autoRefresh = value,
            )
        if (value) refresh()
    }

    fun setRefreshInterval(value: Long) {
        settings.refreshIntervalMs = value
        mutableState.value =
            mutableState.value.copy(
                refreshIntervalMs =
                    settings.refreshIntervalMs,
            )
    }

    fun setConfirmKill(value: Boolean) {
        settings.confirmKill = value
        mutableState.value =
            mutableState.value.copy(
                confirmKill = value,
            )
    }

    fun select(process: YTaskProcess?) {
        mutableState.value =
            mutableState.value.copy(
                selectedPid = process?.pid,
                selectedDetail = process,
            )
        if (process != null) {
            viewModelScope.launch {
                val detail =
                    runCatching {
                        repository.loadDetails(
                            process,
                        )
                    }.getOrDefault(process)
                if (
                    mutableState.value.selectedPid ==
                    process.pid
                ) {
                    mutableState.value =
                        mutableState.value.copy(
                            selectedDetail =
                                detail.copy(
                                    isPinned =
                                        settings.isPinned(
                                            detail,
                                        ),
                                ),
                        )
                }
            }
        }
    }

    fun selectParent(process: YTaskProcess) {
        if (process.ppid <= 0) return
        val parent =
            mutableState.value.snapshot.processes
                .firstOrNull {
                    it.pid == process.ppid
                }
        if (parent != null) {
            select(parent)
        }
    }

    fun selectedProcess(): YTaskProcess? {
        val state = mutableState.value
        state.selectedDetail
            ?.takeIf {
                it.pid == state.selectedPid
            }
            ?.let { return it }
        return state.snapshot.processes
            .firstOrNull {
                it.pid == state.selectedPid
            }
    }

    fun visibleProcesses(): List<YTaskProcess> {
        val state = mutableState.value
        val needle = state.query.trim()
        val filtered =
            state.snapshot.processes.filter { process ->
                val kindMatch =
                    when (process.kind) {
                        YTaskProcessKind.UserApp ->
                            state.showUser
                        YTaskProcessKind.SystemApp ->
                            state.showSystem
                        YTaskProcessKind.Linux ->
                            state.showLinux
                    }
                val textMatch =
                    needle.isBlank() ||
                        process.displayName.contains(
                            needle,
                            true,
                        ) ||
                        process.command.contains(
                            needle,
                            true,
                        ) ||
                        process.packageNames.any {
                            it.contains(
                                needle,
                                true,
                            )
                        } ||
                        process.pid.toString()
                            .contains(needle)
                kindMatch && textMatch
            }

        return when (state.sort) {
            YTaskProcessSort.Memory ->
                filtered.sortedWith(
                    compareByDescending<YTaskProcess> {
                        it.isPinned
                    }.thenByDescending {
                        it.rssKb
                    },
                )
            YTaskProcessSort.Cpu ->
                filtered.sortedWith(
                    compareByDescending<YTaskProcess> {
                        it.isPinned
                    }.thenByDescending {
                        it.cpuPercent
                    },
                )
            YTaskProcessSort.Download ->
                filtered.sortedWith(
                    compareByDescending<YTaskProcess> {
                        it.isPinned
                    }.thenByDescending {
                        it.rxBytesPerSecond
                    },
                )
            YTaskProcessSort.Upload ->
                filtered.sortedWith(
                    compareByDescending<YTaskProcess> {
                        it.isPinned
                    }.thenByDescending {
                        it.txBytesPerSecond
                    },
                )
            YTaskProcessSort.Name ->
                filtered.sortedWith(
                    compareByDescending<YTaskProcess> {
                        it.isPinned
                    }.thenBy {
                        it.displayName.lowercase()
                    },
                )
            YTaskProcessSort.Pid ->
                filtered.sortedWith(
                    compareByDescending<YTaskProcess> {
                        it.isPinned
                    }.thenBy {
                        it.pid
                    },
                )
        }
    }

    fun togglePin(process: YTaskProcess) {
        val pinned = settings.togglePin(process)
        val state = mutableState.value
        val processes =
            state.snapshot.processes.map {
                if (it.pid == process.pid) {
                    it.copy(isPinned = pinned)
                } else {
                    it
                }
            }
        mutableState.value =
            state.copy(
                snapshot =
                    state.snapshot.copy(
                        processes = processes,
                    ),
                selectedDetail =
                    state.selectedDetail?.let {
                        if (it.pid == process.pid) {
                            it.copy(isPinned = pinned)
                        } else {
                            it
                        }
                    },
            )
    }

    fun kill(process: YTaskProcess) {
        if (mutableState.value.confirmKill) {
            mutableState.value =
                mutableState.value.copy(
                    pendingKillPid = process.pid,
                )
        } else {
            performKill(process)
        }
    }

    fun confirmKill() {
        val pid =
            mutableState.value.pendingKillPid
                ?: return
        val process =
            mutableState.value.snapshot.processes
                .firstOrNull { it.pid == pid }
                ?: mutableState.value.selectedDetail
                    ?.takeIf { it.pid == pid }
        mutableState.value =
            mutableState.value.copy(
                pendingKillPid = null,
            )
        if (process != null) {
            performKill(process)
        }
    }

    fun cancelKill() {
        mutableState.value =
            mutableState.value.copy(
                pendingKillPid = null,
            )
    }

    private fun performKill(process: YTaskProcess) {
        viewModelScope.launch {
            val result =
                runCatching {
                    repository.kill(process.pid)
                }
            if (result.getOrDefault(false)) {
                mutableState.value =
                    mutableState.value.copy(error = null)
                select(null)
                refreshInternal()
            } else {
                result.exceptionOrNull()?.let(::report)
                    ?: run {
                        mutableState.value =
                            mutableState.value.copy(
                                error = "kill_failed:" + process.pid,
                            )
                    }
            }
        }
    }

    fun forceStop(process: YTaskProcess) {
        val packageName =
            process.packageName ?: return
        viewModelScope.launch {
            val result =
                runCatching {
                    repository.forceStop(packageName)
                }
            if (result.getOrDefault(false)) {
                mutableState.value =
                    mutableState.value.copy(error = null)
                select(null)
                refreshInternal()
            } else {
                result.exceptionOrNull()?.let(::report)
                    ?: run {
                        mutableState.value =
                            mutableState.value.copy(
                                error = "force_stop_failed:" + packageName,
                            )
                    }
            }
        }
    }

    private suspend fun refreshInternal() {
        val rootStatus =
            runCatching {
                repository.rootStatus()
            }.getOrDefault(
                CapabilityStatus.Error,
            )
        val hookStatus =
            runCatching {
                environment.hookGateway.status()
            }.getOrDefault(
                CapabilityStatus.Error,
            )
        if (
            rootStatus !=
            CapabilityStatus.Available
        ) {
            mutableState.value =
                mutableState.value.copy(
                    rootStatus = rootStatus,
                    hookStatus = hookStatus,
                    loading = false,
                )
            return
        }
        runCatching {
            repository.snapshot()
        }.onSuccess { snapshot ->
            val pinnedProcesses =
                snapshot.processes.map {
                    it.copy(
                        isPinned =
                            settings.isPinned(it),
                    )
                }
            val nextSnapshot =
                snapshot.copy(
                    processes = pinnedProcesses,
                )
            val currentState =
                mutableState.value
            val selectedPid =
                currentState.selectedPid
            val currentDetail =
                currentState.selectedDetail
            val system = nextSnapshot.system
            val nextHistory =
                currentState.resourceHistory.copy(
                    cpu =
                        currentState.resourceHistory.cpu
                            .appendSample(
                                system.cpuPercent,
                            ),
                    ram =
                        currentState.resourceHistory.ram
                            .appendSample(
                                percent(
                                    system.ramUsedBytes,
                                    system.ramTotalBytes,
                                ),
                            ),
                    swap =
                        currentState.resourceHistory.swap
                            .appendSample(
                                percent(
                                    system.swapUsedBytes,
                                    system.swapTotalBytes,
                                ),
                            ),
                    gpu =
                        currentState.resourceHistory.gpu
                            .appendSample(
                                nextSnapshot.gpu
                                    .usagePercent
                                    ?: 0f,
                            ),
                )
            val nextDetail =
                if (selectedPid != null) {
                    pinnedProcesses
                        .firstOrNull {
                            it.pid == selectedPid
                        }?.let { fresh ->
                            if (
                                currentDetail != null &&
                                currentDetail.pid ==
                                fresh.pid
                            ) {
                                fresh.copy(
                                    executablePath =
                                        currentDetail
                                            .executablePath,
                                    cgroup =
                                        currentDetail.cgroup,
                                )
                            } else {
                                fresh
                            }
                        }
                } else {
                    null
                }
            mutableState.value =
                mutableState.value.copy(
                    rootStatus = rootStatus,
                    hookStatus = hookStatus,
                    snapshot = nextSnapshot,
                    resourceHistory = nextHistory,
                    selectedDetail = nextDetail,
                    loading = false,
                    error =
                        currentState.error?.takeIf { message ->
                            message.startsWith("kill_failed:") ||
                                message.startsWith("force_stop_failed:")
                        },
                )
        }.onFailure(::report)
    }

    private fun report(error: Throwable) {
        environment.logger.error(
            "YSuite/YTaskManager",
            "Collection failed",
            error,
        )
        mutableState.value =
            mutableState.value.copy(
                loading = false,
                error =
                    error.message
                        ?: error.javaClass.simpleName,
            )
    }

    override fun onCleared() {
        monitor?.cancel()
        super.onCleared()
    }

    class Factory(
        private val environment:
            YTaskManagerEnvironment,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(
            modelClass: Class<T>,
        ): T =
            YTaskManagerViewModel(
                environment,
            ) as T
    }
}

private fun List<Float>.appendSample(
    value: Float,
): List<Float> =
    (this + value.coerceIn(0f, 100f))
        .takeLast(60)

private fun percent(
    used: Long,
    total: Long,
): Float =
    if (total <= 0L) {
        0f
    } else {
        (
            used.toDouble() /
                total.toDouble() *
                100.0
            ).toFloat()
            .coerceIn(0f, 100f)
    }
