package com.yagay.YTaskManager

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yagay.YTaskManager.data.GpuRepository
import com.yagay.YTaskManager.data.NetworkRepository
import com.yagay.YTaskManager.data.ProcessRepository
import com.yagay.YTaskManager.data.SettingsRepository
import com.yagay.YTaskManager.data.SystemStatsRepository
import com.yagay.YTaskManager.model.NetworkSnapshot
import com.yagay.YTaskManager.model.ProcessEntry
import com.yagay.YTaskManager.model.ProcessSort
import com.yagay.YTaskManager.model.RootState
import com.yagay.YTaskManager.model.TaskManagerUiState
import com.yagay.YTaskManager.root.RootShell
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val processShell = RootShell()
    private val statsShell = RootShell()
    private val gpuShell = RootShell()
    private val networkShell = RootShell()

    private val processRepository = ProcessRepository(application, processShell)
    private val statsRepository = SystemStatsRepository(statsShell)
    private val gpuRepository = GpuRepository(application, gpuShell)
    private val networkRepository = NetworkRepository(application, networkShell)
    private val settings = SettingsRepository(application)
    private val runtime = YTaskManagerRuntime.get(application)

    private val _state = MutableStateFlow(
        TaskManagerUiState(
            sort = settings.sort,
            showUserApps = settings.showUserApps,
            showSystemApps = settings.showSystemApps,
            showLinuxProcesses = settings.showLinuxProcesses,
            autoRefresh = settings.autoRefresh,
            refreshIntervalMs = settings.refreshIntervalMs,
            confirmKill = settings.confirmKill,
        )
    )
    val state: StateFlow<TaskManagerUiState> = _state.asStateFlow()

    private var monitorJob: Job? = null
    private var networkJob: Job? = null
    private var detailJob: Job? = null
    private var lastDetailSampleMs = 0L

    init {
        viewModelScope.launch {
            runtime.framework.collect { framework ->
                _state.value = _state.value.copy(framework = framework)
            }
        }
        start()
    }

    fun start() {
        if (monitorJob?.isActive == true) return
        monitorJob = viewModelScope.launch {
            val initialProbe = processShell.probeRoot()
            val rootGranted = initialProbe.granted
            _state.value = _state.value.copy(
                root = RootState(
                    checked = true,
                    granted = rootGranted,
                    uid = if (rootGranted) 0 else null,
                    message = initialProbe.detail
                ),
                loading = rootGranted,
                error = null
            )
            // Populate CPU/RAM immediately, without waiting for privileged /proc enumeration.
            refreshPublicSnapshot()
            if (rootGranted) {
                refreshInternal()
                startNetworkMonitor()
            }
            var lastRootProbe = android.os.SystemClock.elapsedRealtime()
            while (isActive) {
                val snapshot = _state.value
                if (!snapshot.root.granted) {
                    delay(1_000L)
                    if (android.os.SystemClock.elapsedRealtime() - lastRootProbe >= 5_000L) {
                        lastRootProbe = android.os.SystemClock.elapsedRealtime()
                        val probe = processShell.probeRoot()
                        _state.value = _state.value.copy(
                            root = RootState(
                                checked = true, granted = probe.granted,
                                uid = if (probe.granted) 0 else null,
                                message = probe.detail,
                            )
                        )
                        if (probe.granted) {
                            _state.value = _state.value.copy(loading = true, error = null)
                            refreshPublicSnapshot()
                            refreshInternal()
                            startNetworkMonitor()
                        } else refreshPublicSnapshot()
                    }
                } else if (snapshot.autoRefresh) {
                    delay(snapshot.refreshIntervalMs)
                    if (isActive && _state.value.autoRefresh) refreshInternal()
                } else delay(250L)
            }
        }
    }

    private fun startNetworkMonitor() {
        if (networkJob?.isActive == true) return
        networkJob = viewModelScope.launch {
            // Avoid competing with initial process discovery for the Root gateway.
            delay(5_000L)
            while (isActive) {
                refreshNetworkInternal()
                delay(5_000L)
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val probe = processShell.probeRoot()
            _state.value = _state.value.copy(root = RootState(
                checked = true, granted = probe.granted,
                uid = if (probe.granted) 0 else null, message = probe.detail,
            ))
            if (probe.granted) {
                startNetworkMonitor()
                refreshInternal()
            } else refreshPublicSnapshot()
        }
        start()
    }

    private suspend fun refreshPublicSnapshot() {
        val snapshot = runCatching { statsRepository.readUnprivileged(getApplication()) }.getOrNull()
            ?: return
        val old = _state.value
        // The persistent Root diagnostic explains whether authorization or the host
        // failed. Do not replace it with the generic "Root required" snackbar.
        _state.value = old.copy(system = snapshot, loading = false)
    }

    fun loadProcessDetails(process: ProcessEntry, onLoaded: (ProcessEntry) -> Unit) {
        viewModelScope.launch {
            val detailed = processRepository.loadDetails(process)
            val current = _state.value.processes.firstOrNull { it.pid == detailed.pid }
            onLoaded(
                if (current != null) detailed.copy(
                    rxBytesPerSecond = current.rxBytesPerSecond,
                    txBytesPerSecond = current.txBytesPerSecond,
                ) else detailed
            )
        }
    }

    fun setQuery(query: String) {
        _state.value = _state.value.copy(query = query)
    }

    fun setSort(sort: ProcessSort) {
        settings.sort = sort
        _state.value = _state.value.copy(sort = sort)
    }

    fun setShowUserApps(value: Boolean) {
        settings.showUserApps = value
        _state.value = _state.value.copy(showUserApps = value)
    }

    fun setShowSystemApps(value: Boolean) {
        settings.showSystemApps = value
        _state.value = _state.value.copy(showSystemApps = value)
    }

    fun setShowLinuxProcesses(value: Boolean) {
        settings.showLinuxProcesses = value
        _state.value = _state.value.copy(showLinuxProcesses = value)
    }

    fun setAutoRefresh(value: Boolean) {
        settings.autoRefresh = value
        _state.value = _state.value.copy(autoRefresh = value)
    }

    fun setRefreshInterval(ms: Long) {
        settings.refreshIntervalMs = ms
        _state.value = _state.value.copy(refreshIntervalMs = settings.refreshIntervalMs)
    }

    fun setConfirmKill(value: Boolean) {
        settings.confirmKill = value
        _state.value = _state.value.copy(confirmKill = value)
    }

    fun togglePin(process: ProcessEntry) {
        val key = pinKey(process)
        val pinned = settings.togglePinned(key)
        _state.value = _state.value.copy(
            processes = _state.value.processes.map {
                if (it.pid == process.pid) it.copy(isPinned = pinned) else it
            }
        )
    }

    fun killProcess(process: ProcessEntry) {
        viewModelScope.launch {
            val ok = processRepository.killProcess(process.pid)
            if (!ok) {
                _state.value = _state.value.copy(error = "Failed to kill PID ${process.pid}")
            }
            refreshInternal()
        }
    }

    fun forceStop(process: ProcessEntry) {
        val pkg = process.packageName ?: return
        viewModelScope.launch {
            val ok = processRepository.forceStop(pkg)
            if (!ok) {
                _state.value = _state.value.copy(error = "Failed to force-stop $pkg")
            }
            refreshInternal()
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    private suspend fun refreshInternal() = coroutineScope {
        runCatching {
            // Prioritize the process list over slower CPU/GPU sampling. The UI should
            // paint when ps completes instead of waiting for every telemetry source.
            val processDeferred = async {
                runCatching {
                    val pinned = settings.pinnedProcesses
                    processRepository.listProcesses().map { process ->
                        process.copy(isPinned = pinKey(process) in pinned)
                    }
                }
            }
            val systemDeferred = async { runCatching { statsRepository.read() } }
            val gpuDeferred = async { runCatching { gpuRepository.read() } }

            val oldState = _state.value
            val processResult = processDeferred.await()
            processResult.getOrNull()?.takeIf { it.isNotEmpty() }?.let { quick ->
                val preview = _state.value
                val visible = applyNetworkSpeeds(quick, preview.network)
                _state.value = preview.copy(
                    processes = visible, processCount = visible.size,
                    threadCount = visible.sumOf { it.threads }, loading = false,
                )
            }
            val systemResult = systemDeferred.await()
            val gpuResult = gpuDeferred.await()
            val system = systemResult.getOrElse { oldState.system }
            val quickProcesses = processResult.getOrElse { oldState.processes }
            // Retain rich /proc statistics between the cheaper ps-based updates.
            val previousByPid = oldState.processes.associateBy { it.pid }
            val rawProcesses = quickProcesses.map { quick ->
                val previous = previousByPid[quick.pid]?.takeIf { it.uid == quick.uid }
                if (previous == null || previous.threads == 0) quick
                else quick.copy(
                    cpuPercent = previous.cpuPercent,
                    rssKb = quick.rssKb.takeIf { it > 0 } ?: previous.rssKb,
                    virtualMemoryKb = previous.virtualMemoryKb,
                    threads = previous.threads,
                    state = previous.state,
                    nice = previous.nice,
                    oomScoreAdj = previous.oomScoreAdj,
                    isForeground = previous.isForeground,
                    startTimeMillis = previous.startTimeMillis,
                    elapsedTimeMillis = previous.elapsedTimeMillis,
                )
            }
            val gpu = gpuResult.getOrElse { oldState.gpu }
            val failed = listOfNotNull(
                systemResult.exceptionOrNull()?.let { "system: ${it.message ?: it.javaClass.simpleName}" },
                processResult.exceptionOrNull()?.let { "processes: ${it.message ?: it.javaClass.simpleName}" },
                gpuResult.exceptionOrNull()?.let { "GPU: ${it.message ?: it.javaClass.simpleName}" }
            ).joinToString("; ")

            val old = _state.value
            val processes = applyNetworkSpeeds(rawProcesses, old.network)
            _state.value = old.copy(
                system = system,
                gpu = gpu,
                processes = processes,
                processCount = processes.size,
                threadCount = processes.sumOf { it.threads },
                cpuHistory = appendHistory(old.cpuHistory, system.cpuPercent),
                ramHistory = appendHistory(old.ramHistory, percent(system.ramUsedBytes, system.ramTotalBytes)),
                swapHistory = appendHistory(old.swapHistory, percent(system.swapUsedBytes, system.swapTotalBytes)),
                gpuHistory = appendHistory(old.gpuHistory, gpu.usagePercent ?: 0f),
                loading = false,
                error = failed.takeIf { it.isNotBlank() }
                    ?: if (processes.isEmpty()) "No processes returned by Root /proc or ps" else null,
            )
            scheduleDetailedSample()
        }.onFailure {
            _state.value = _state.value.copy(
                loading = false,
                error = it.message ?: it.javaClass.simpleName,
            )
        }
    }

    /**
     * A rich /proc sample must never block first paint or every short refresh tick.
     * Run it at most once per 30 seconds, merge by PID/UID and keep the fast ps list visible.
     */
    private fun scheduleDetailedSample() {
        if (!_state.value.root.granted || detailJob?.isActive == true) return
        val now = android.os.SystemClock.elapsedRealtime()
        if (lastDetailSampleMs != 0L && now - lastDetailSampleMs < 30_000L) return
        lastDetailSampleMs = now
        detailJob = viewModelScope.launch {
            delay(1_200L)
            val rich = runCatching { processRepository.listDetailedProcesses() }
                .getOrDefault(emptyList())
            if (rich.isEmpty()) return@launch
            val byPid = rich.associateBy { it.pid }
            val old = _state.value
            val merged = old.processes.map { quick ->
                val detail = byPid[quick.pid]?.takeIf { it.uid == quick.uid } ?: return@map quick
                detail.copy(
                    isPinned = quick.isPinned,
                    rxBytesPerSecond = quick.rxBytesPerSecond,
                    txBytesPerSecond = quick.txBytesPerSecond,
                )
            }
            _state.value = old.copy(
                processes = merged,
                threadCount = merged.sumOf { it.threads },
            )
        }
    }

    private suspend fun refreshNetworkInternal() {
        if (!_state.value.root.granted) return
        runCatching {
            val currentProcesses = _state.value.processes
            val network = networkRepository.read(
                currentProcesses.map { it.uid }.filter { it >= 0 }.toSet()
            )
            val old = _state.value
            _state.value = old.copy(
                network = network,
                processes = applyNetworkSpeeds(old.processes, network),
            )
        }
    }

    /**
     * Network counters are per UID, not per process. To avoid falsely multiplying one
     * UID's traffic across every child process, show the aggregate only on one primary
     * process row for that UID. The dedicated Network page still shows the exact UID row.
     */
    private fun applyNetworkSpeeds(
        processes: List<ProcessEntry>,
        network: NetworkSnapshot,
    ): List<ProcessEntry> {
        if (processes.isEmpty()) return processes
        val speedByUid = network.entries.associateBy { it.uid }
        if (speedByUid.isEmpty()) {
            return processes.map {
                if (it.rxBytesPerSecond == 0L && it.txBytesPerSecond == 0L) it
                else it.copy(rxBytesPerSecond = 0L, txBytesPerSecond = 0L)
            }
        }

        val primaryPidByUid = processes
            .groupBy { it.uid }
            .mapValues { (_, group) ->
                group.firstOrNull { process ->
                    process.packageName != null &&
                        (process.command == process.packageName ||
                            process.command.startsWith(process.packageName + ":"))
                }?.pid ?: group.first().pid
            }

        return processes.map { process ->
            val speed = speedByUid[process.uid]
            if (speed != null && primaryPidByUid[process.uid] == process.pid) {
                process.copy(
                    rxBytesPerSecond = speed.rxBytesPerSecond,
                    txBytesPerSecond = speed.txBytesPerSecond,
                )
            } else {
                process.copy(rxBytesPerSecond = 0L, txBytesPerSecond = 0L)
            }
        }
    }

    private fun appendHistory(values: List<Float>, value: Float): List<Float> =
        (values + value).takeLast(60)

    private fun percent(used: Long, total: Long): Float =
        if (total > 0L) (used * 100f / total).coerceIn(0f, 100f) else 0f

    private fun pinKey(process: ProcessEntry): String =
        process.packageName ?: process.command.ifBlank { "pid:${process.pid}" }

    override fun onCleared() {
        monitorJob?.cancel()
        networkJob?.cancel()
        processShell.close()
        statsShell.close()
        gpuShell.close()
        networkShell.close()
        super.onCleared()
    }
}
