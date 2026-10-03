package com.yagay.YTaskManager.ui

import android.graphics.drawable.Drawable
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.yagay.YTaskManager.MainViewModel
import com.yagay.YTaskManager.R
import com.yagay.YTaskManager.model.CpuCoreInfo
import com.yagay.YTaskManager.model.NetworkEntry
import com.yagay.YTaskManager.model.ProcessEntry
import com.yagay.YTaskManager.model.ProcessKind
import com.yagay.YTaskManager.model.ProcessSort
import com.yagay.YTaskManager.model.TaskManagerUiState
import com.yagay.yui.YDimens
import com.yagay.yui.YFeatureCard
import com.yagay.yui.YFeatureEmpty
import com.yagay.yui.YManagerScaffold
import com.yagay.yui.YIcons
import com.yagay.yui.YNavigationSpec
import com.yagay.yui.YNavigationSuite
import com.yagay.yui.YSearchField
import com.yagay.yui.YSettingSwitch
import com.yagay.yui.YStatusPill
import com.yagay.yui.YStatusRow
import com.yagay.yui.YStatusTone
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

enum class HomePage { PROCESSES, RESOURCES, NETWORK }

@Composable
fun TaskManagerApp(viewModel: MainViewModel) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var selected by remember { mutableStateOf<ProcessEntry?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var pendingKill by remember { mutableStateOf<Pair<ProcessEntry, Boolean>?>(null) }
    var page by remember { mutableStateOf(HomePage.PROCESSES) }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbar.showSnackbar(it)
            viewModel.clearError()
        }
    }

    val navigation = listOf(
        YNavigationSpec("processes", stringResource(R.string.ytm_processes), YIcons.List),
        YNavigationSpec("resources", stringResource(R.string.ytm_resources), YIcons.Memory),
        YNavigationSpec("network", stringResource(R.string.ytm_network), YIcons.Network),
    )

    YNavigationSuite(
        selectedKey = when (page) {
            HomePage.PROCESSES -> "processes"
            HomePage.RESOURCES -> "resources"
            HomePage.NETWORK -> "network"
        },
        items = navigation,
        onSelected = { item ->
            page = when (item.key) {
                "resources" -> HomePage.RESOURCES
                "network" -> HomePage.NETWORK
                else -> HomePage.PROCESSES
            }
        },
    ) {
        YManagerScaffold(
            title = stringResource(R.string.ytm_app_name),
            subtitle = when (page) {
                HomePage.PROCESSES -> stringResource(R.string.ytm_process_summary, state.processCount, state.threadCount)
                HomePage.RESOURCES -> stringResource(R.string.ytm_resource_summary)
                HomePage.NETWORK -> stringResource(R.string.ytm_network_summary)
            },
            actions = {
                IconButton(onClick = viewModel::refresh) {
                    Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.ytm_refresh))
                }
                IconButton(onClick = { showSettings = true }) {
                    Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.ytm_settings))
                }
            },
            snackbarHost = { SnackbarHost(snackbar) },
        ) { padding ->
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
            ) {
                when (page) {
                    HomePage.PROCESSES -> ProcessPage(
                        state = state,
                        viewModel = viewModel,
                        onSelect = { process ->
                            viewModel.loadProcessDetails(process) { selected = it }
                        },
                    )
                    HomePage.RESOURCES -> ResourcePage(state)
                    HomePage.NETWORK -> NetworkPage(state)
                }
            }
        }
    }

    selected?.let { process ->
        ProcessDialog(
            process = process,
            parent = state.processes.firstOrNull { it.pid == process.ppid },
            onDismiss = { selected = null },
            onOpenParent = { parent ->
                viewModel.loadProcessDetails(parent) { selected = it }
            },
            onPin = { viewModel.togglePin(process) },
            onKill = {
                if (state.confirmKill) pendingKill = process to false
                else {
                    selected = null
                    viewModel.killProcess(process)
                }
            },
            onForceStop = {
                if (state.confirmKill) pendingKill = process to true
                else {
                    selected = null
                    viewModel.forceStop(process)
                }
            },
        )
    }

    pendingKill?.let { (process, forceStop) ->
        AlertDialog(
            onDismissRequest = { pendingKill = null },
            title = { Text(stringResource(if (forceStop) R.string.ytm_force_stop_question else R.string.ytm_kill_question)) },
            text = { Text(process.displayName) },
            confirmButton = {
                Button(onClick = {
                    pendingKill = null
                    selected = null
                    if (forceStop) viewModel.forceStop(process) else viewModel.killProcess(process)
                }) { Text(stringResource(R.string.ytm_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingKill = null }) { Text(stringResource(R.string.ytm_cancel)) }
            },
        )
    }

    if (showSettings) {
        SettingsDialog(
            state = state,
            onDismiss = { showSettings = false },
            onAutoRefresh = viewModel::setAutoRefresh,
            onRefreshInterval = viewModel::setRefreshInterval,
            onConfirmKill = viewModel::setConfirmKill,
        )
    }
}

@Composable
private fun ProcessPage(
    state: TaskManagerUiState,
    viewModel: MainViewModel,
    onSelect: (ProcessEntry) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        StatusSection(state)
        FilterSection(
            state = state,
            onQuery = viewModel::setQuery,
            onSort = viewModel::setSort,
            onUser = viewModel::setShowUserApps,
            onSystem = viewModel::setShowSystemApps,
            onLinux = viewModel::setShowLinuxProcesses,
        )
        if (state.loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            ProcessList(state, onSelect)
        }
    }
}

@Composable
private fun StatusSection(state: TaskManagerUiState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = YDimens.ScreenHorizontal, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(YDimens.ControlGap),
    ) {
        YStatusPill(
            label = "Root",
            value = if (state.root.granted) stringResource(R.string.ytm_ok) else stringResource(R.string.ytm_root_unavailable),
            tone = if (state.root.granted) YStatusTone.Good else YStatusTone.Error,
        )
        YStatusPill(
            label = "LSPosed",
            value = if (state.framework.detected) stringResource(R.string.ytm_detected) else stringResource(R.string.ytm_not_detected),
            tone = if (state.framework.detected) YStatusTone.Good else YStatusTone.Warning,
        )
    }
}

@Composable
private fun FilterSection(
    state: TaskManagerUiState,
    onQuery: (String) -> Unit,
    onSort: (ProcessSort) -> Unit,
    onUser: (Boolean) -> Unit,
    onSystem: (Boolean) -> Unit,
    onLinux: (Boolean) -> Unit,
) {
    YFeatureCard(
        title = stringResource(R.string.ytm_process_filter),
        subtitle = stringResource(R.string.ytm_process_filter_desc),
        modifier = Modifier.padding(horizontal = YDimens.ScreenHorizontal, vertical = 6.dp),
    ) {
        YSearchField(
            value = state.query,
            onValueChange = onQuery,
            hint = stringResource(R.string.ytm_search_hint),
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = if (state.query.isNotEmpty()) {
                {
                    IconButton(onClick = { onQuery("") }) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.ytm_clear))
                    }
                }
            } else null,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = state.showUserApps, onClick = { onUser(!state.showUserApps) }, label = { Text(stringResource(R.string.ytm_user)) })
            FilterChip(selected = state.showSystemApps, onClick = { onSystem(!state.showSystemApps) }, label = { Text(stringResource(R.string.ytm_system)) })
            FilterChip(selected = state.showLinuxProcesses, onClick = { onLinux(!state.showLinuxProcesses) }, label = { Text(stringResource(R.string.ytm_linux)) })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ProcessSort.entries.forEach { item ->
                FilterChip(
                    selected = state.sort == item,
                    onClick = { onSort(item) },
                    label = { Text(sortLabel(item)) },
                )
            }
        }
    }
}

@Composable
private fun ProcessList(state: TaskManagerUiState, onClick: (ProcessEntry) -> Unit) {
    val filtered = remember(
        state.processes,
        state.query,
        state.sort,
        state.showUserApps,
        state.showSystemApps,
        state.showLinuxProcesses,
    ) {
        state.processes
            .asSequence()
            .filter {
                when (it.kind) {
                    ProcessKind.USER_APP -> state.showUserApps
                    ProcessKind.SYSTEM_APP -> state.showSystemApps
                    ProcessKind.LINUX -> state.showLinuxProcesses
                }
            }
            .filter {
                val q = state.query.trim()
                q.isEmpty() ||
                    it.displayName.contains(q, true) ||
                    it.packageNames.any { pkg -> pkg.contains(q, true) } ||
                    it.command.contains(q, true) ||
                    it.userName.contains(q, true) ||
                    it.pid.toString() == q ||
                    it.uid.toString() == q
            }
            .let { seq ->
                when (state.sort) {
                    ProcessSort.CPU -> seq.sortedByDescending { it.cpuPercent }
                    ProcessSort.MEMORY -> seq.sortedByDescending { it.rssKb }
                    ProcessSort.DOWNLOAD -> seq.sortedByDescending { it.rxBytesPerSecond }
                    ProcessSort.UPLOAD -> seq.sortedByDescending { it.txBytesPerSecond }
                    ProcessSort.NAME -> seq.sortedBy { it.displayName.lowercase() }
                    ProcessSort.PID -> seq.sortedBy { it.pid }
                }
            }
            .sortedByDescending { it.isPinned }
            .toList()
    }

    if (filtered.isEmpty()) {
        YFeatureEmpty(
            message = stringResource(R.string.ytm_no_processes),
            modifier = Modifier.padding(horizontal = YDimens.ScreenHorizontal, vertical = 8.dp),
        )
        return
    }

    LazyColumn(Modifier.fillMaxSize()) {
        items(filtered, key = { it.pid }) { process ->
            ProcessRow(process, onClick)
            HorizontalDivider()
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProcessRow(process: ProcessEntry, onClick: (ProcessEntry) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = { onClick(process) })
            .padding(horizontal = YDimens.ScreenHorizontal, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(process.icon)
        Spacer(Modifier.size(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    process.displayName,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (process.isPinned) {
                    Icon(Icons.Default.PushPin, contentDescription = stringResource(R.string.ytm_pinned), modifier = Modifier.size(15.dp))
                }
                if (process.isForeground) Text(" " + stringResource(R.string.ytm_foreground_short), style = MaterialTheme.typography.labelSmall)
            }
            Text(
                stringResource(R.string.ytm_process_row, process.pid, process.userName, kindLabel(process.kind), process.threads),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                stringResource(R.string.ytm_download_upload, formatSpeed(process.rxBytesPerSecond), formatSpeed(process.txBytesPerSecond)),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(String.format(Locale.getDefault(), "%.1f%%", process.cpuPercent))
            Text(formatBytes(process.rssKb * 1024L), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ResourcePage(state: TaskManagerUiState) {
    val s = state.system
    val g = state.gpu
    val unknown = stringResource(R.string.ytm_unknown)
    val noData = stringResource(R.string.ytm_no_data)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(YDimens.SectionGap),
    ) {
        item {
            SectionCard("CPU") {
                MetricLine(stringResource(R.string.ytm_usage), String.format(Locale.getDefault(), "%.1f%%", s.cpuPercent))
                HistoryChart(state.cpuHistory)
            }
        }
        item {
            SectionCard("RAM") {
                MetricLine(stringResource(R.string.ytm_used), "${formatBytes(s.ramUsedBytes)} / ${formatBytes(s.ramTotalBytes)}")
                MetricLine(stringResource(R.string.ytm_available), formatBytes(s.ramAvailableBytes))
                MetricLine(stringResource(R.string.ytm_cached), formatBytes(s.cachedBytes))
                MetricLine(stringResource(R.string.ytm_buffers), formatBytes(s.buffersBytes))
                HistoryChart(state.ramHistory)
            }
        }
        item {
            SectionCard("SWAP") {
                MetricLine(stringResource(R.string.ytm_used), "${formatBytes(s.swapUsedBytes)} / ${formatBytes(s.swapTotalBytes)}")
                HistoryChart(state.swapHistory)
            }
        }
        item {
            SectionCard(stringResource(R.string.ytm_processor_information)) {
                MetricLine("SoC", s.soc.ifBlank { unknown })
                MetricLine(stringResource(R.string.ytm_architecture), s.architecture.ifBlank { unknown })
                MetricLine("ABI", s.abi.ifBlank { unknown })
                MetricLine(stringResource(R.string.ytm_cpu_cores), s.cpuCoreCount.toString())
                MetricLine(stringResource(R.string.ytm_governor), s.governor.ifBlank { unknown })
                MetricLine(stringResource(R.string.ytm_temperature), s.cpuTemperatureC?.let { String.format(Locale.US, "%.1f C", it) } ?: noData)
                MetricLine(stringResource(R.string.ytm_uptime), formatDuration(s.uptimeMillis))
                MetricLine(stringResource(R.string.ytm_load), String.format(Locale.US, "%.2f", s.load1))
                MetricLine(stringResource(R.string.ytm_processes), state.processCount.toString())
                MetricLine(stringResource(R.string.ytm_threads), state.threadCount.toString())
            }
        }
        item {
            SectionCard(stringResource(R.string.ytm_cpu_frequencies)) {
                if (s.cpuCores.isEmpty()) Text(stringResource(R.string.ytm_no_cpufreq), style = MaterialTheme.typography.bodySmall)
                else s.cpuCores.forEach { CpuCoreRow(it) }
            }
        }
        item {
            SectionCard("GPU") {
                MetricLine(stringResource(R.string.ytm_usage), g.usagePercent?.let { String.format(Locale.US, "%.1f%%", it) } ?: noData)
                HistoryChart(state.gpuHistory)
                MetricLine(stringResource(R.string.ytm_vendor), g.vendor ?: noData)
                MetricLine(stringResource(R.string.ytm_renderer), g.renderer ?: noData)
                MetricLine("OpenGL", g.openGlVersion ?: noData)
                MetricLine("GLSL", g.glslVersion ?: noData)
                MetricLine("Vulkan", if (g.vulkanSupported) stringResource(R.string.ytm_vulkan_supported) else stringResource(R.string.ytm_vulkan_not_supported))
                MetricLine("Vulkan API", g.vulkanApiVersion ?: noData)
                MetricLine(stringResource(R.string.ytm_gpu_current), formatHz(g.currentHz))
                MetricLine(stringResource(R.string.ytm_gpu_min), formatHz(g.minHz))
                MetricLine(stringResource(R.string.ytm_gpu_max), formatHz(g.maxHz))
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun NetworkPage(state: TaskManagerUiState) {
    val network = state.network
    Column(Modifier.fillMaxSize()) {
        YFeatureCard(
            title = stringResource(R.string.ytm_realtime_speed),
            subtitle = stringResource(R.string.ytm_realtime_speed_desc),
            detail = stringResource(R.string.ytm_backend_detail, network.backend),
            modifier = Modifier.padding(horizontal = YDimens.ScreenHorizontal, vertical = 6.dp),
        )
        if (network.entries.isEmpty()) {
            YFeatureEmpty(
                stringResource(R.string.ytm_no_traffic),
                Modifier.padding(horizontal = YDimens.ScreenHorizontal, vertical = 8.dp),
            )
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(network.entries, key = { it.uid }) { entry ->
                    NetworkRow(entry)
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun NetworkRow(entry: NetworkEntry) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = YDimens.ScreenHorizontal, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(entry.icon)
        Spacer(Modifier.size(10.dp))
        Column(Modifier.weight(1f)) {
            Text(entry.label, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                stringResource(
                    R.string.ytm_network_identity,
                    entry.uid,
                    if (entry.system) stringResource(R.string.ytm_system_suffix) else "",
                    if (entry.packageNames.isNotEmpty()) stringResource(R.string.ytm_package_suffix, entry.packageNames.joinToString(", ")) else "",
                ),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(stringResource(R.string.ytm_download_value, formatSpeed(entry.rxBytesPerSecond)), fontWeight = FontWeight.Medium)
            Text(stringResource(R.string.ytm_upload_value, formatSpeed(entry.txBytesPerSecond)), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    YFeatureCard(
        title = title,
        modifier = Modifier.padding(horizontal = YDimens.ScreenHorizontal),
        content = content,
    )
}

@Composable
private fun MetricLine(label: String, value: String) {
    YStatusRow(label = label, value = value)
}

@Composable
private fun CpuCoreRow(core: CpuCoreInfo) {
    Column(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.ytm_cpu_core_label, core.core), style = MaterialTheme.typography.labelMedium)
        Text(
            stringResource(R.string.ytm_frequency_order, formatKHz(core.minKHz), formatKHz(core.currentKHz), formatKHz(core.maxKHz)),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun HistoryChart(values: List<Float>) {
    val lineColor = MaterialTheme.colorScheme.primary
    val guideColor = MaterialTheme.colorScheme.outlineVariant
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp),
    ) {
        drawRect(guideColor, style = Stroke(width = 1f))
        if (values.size < 2) return@Canvas
        val step = size.width / (values.size - 1).coerceAtLeast(1)
        var previousX = 0f
        var previousY = size.height * (1f - values.first().coerceIn(0f, 100f) / 100f)
        values.drop(1).forEachIndexed { index, value ->
            val x = step * (index + 1)
            val y = size.height * (1f - value.coerceIn(0f, 100f) / 100f)
            drawLine(
                lineColor,
                start = androidx.compose.ui.geometry.Offset(previousX, previousY),
                end = androidx.compose.ui.geometry.Offset(x, y),
                strokeWidth = 3f,
            )
            previousX = x
            previousY = y
        }
    }
}

@Composable
private fun AppIcon(drawable: Drawable?) {
    if (drawable == null) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Memory, contentDescription = null)
        }
        return
    }
    val density = LocalDensity.current
    val px = with(density) { 40.dp.roundToPx() }
    val bitmap = remember(drawable, px) {
        drawable.toBitmap(max(1, px), max(1, px)).asImageBitmap()
    }
    Image(
        bitmap = bitmap,
        contentDescription = null,
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(10.dp)),
    )
}

@Composable
private fun ProcessDialog(
    process: ProcessEntry,
    parent: ProcessEntry?,
    onDismiss: () -> Unit,
    onOpenParent: (ProcessEntry) -> Unit,
    onPin: () -> Unit,
    onKill: () -> Unit,
    onForceStop: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(process.displayName) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                item { CopyDetail("PID", process.pid.toString()) }
                if (process.ppid != 0) item { ParentDetail(process.ppid, parent, onOpenParent) }
                item { CopyDetail("UID", process.uid.toString()) }
                item { CopyDetail(stringResource(R.string.ytm_user), process.userName) }
                item { CopyDetail(stringResource(R.string.ytm_cpu_usage), String.format(Locale.getDefault(), "%.1f%%", process.cpuPercent)) }
                item { CopyDetail(stringResource(R.string.ytm_ram_usage), formatBytes(process.rssKb * 1024L)) }
                item { CopyDetail(stringResource(R.string.ytm_realtime_download), formatSpeed(process.rxBytesPerSecond)) }
                item { CopyDetail(stringResource(R.string.ytm_realtime_upload), formatSpeed(process.txBytesPerSecond)) }
                if (process.virtualMemoryKb > 0L) item { CopyDetail(stringResource(R.string.ytm_virtual_memory), formatBytes(process.virtualMemoryKb * 1024L)) }
                item { CopyDetail(stringResource(R.string.ytm_foreground), if (process.isForeground) stringResource(R.string.ytm_yes) else stringResource(R.string.ytm_no)) }
                item { CopyDetail(stringResource(R.string.ytm_threads), process.threads.toString()) }
                item { CopyDetail(stringResource(R.string.ytm_nice_value), process.nice.toString()) }
                item { CopyDetail(stringResource(R.string.ytm_status), process.state) }
                item { CopyDetail(stringResource(R.string.ytm_start_time), formatStartTime(process.startTimeMillis, stringResource(R.string.ytm_unknown))) }
                item { CopyDetail(stringResource(R.string.ytm_elapsed_time), formatDuration(process.elapsedTimeMillis)) }
                process.executablePath?.let { value -> item { CopyDetail(stringResource(R.string.ytm_executable_path), value) } }
                process.cgroup?.let { value -> item { CopyDetail("Cgroup", value) } }
                if (process.packageNames.isNotEmpty()) item { CopyDetail(stringResource(R.string.ytm_package), process.packageNames.joinToString("\n")) }
                item { CopyDetail(stringResource(R.string.ytm_command), process.command) }
                process.oomScoreAdj?.let { value -> item { CopyDetail(stringResource(R.string.ytm_oom_score_adj), value.toString()) } }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(onClick = onPin) { Text(stringResource(if (process.isPinned) R.string.ytm_unpin else R.string.ytm_pin)) }
                if (process.packageName != null) Button(onClick = onForceStop) { Text(stringResource(R.string.ytm_force_stop)) }
                Button(onClick = onKill) { Text(stringResource(R.string.ytm_kill_pid)) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ytm_close)) } },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CopyDetail(label: String, value: String) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val copied = stringResource(R.string.ytm_copied, label)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {},
                onLongClick = {
                    clipboard.setText(AnnotatedString(value))
                    Toast.makeText(context, copied, Toast.LENGTH_SHORT).show()
                },
            )
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Text(value, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ParentDetail(ppid: Int, parent: ProcessEntry?, onOpenParent: (ProcessEntry) -> Unit) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val copied = stringResource(R.string.ytm_parent_copied)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { parent?.let(onOpenParent) },
                onLongClick = {
                    clipboard.setText(AnnotatedString(ppid.toString()))
                    Toast.makeText(context, copied, Toast.LENGTH_SHORT).show()
                },
            )
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(stringResource(R.string.ytm_parent_pid), style = MaterialTheme.typography.bodySmall)
        Text(if (parent != null) ppid.toString() else stringResource(R.string.ytm_parent_not_found, ppid), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SettingsDialog(
    state: TaskManagerUiState,
    onDismiss: () -> Unit,
    onAutoRefresh: (Boolean) -> Unit,
    onRefreshInterval: (Long) -> Unit,
    onConfirmKill: (Boolean) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ytm_process_settings)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ToggleRow(stringResource(R.string.ytm_auto_refresh), state.autoRefresh, onAutoRefresh)
                ToggleRow(stringResource(R.string.ytm_confirm_before_kill), state.confirmKill, onConfirmKill)
                Text(stringResource(R.string.ytm_refresh_interval), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(500L, 800L, 1000L, 2000L).forEach { value ->
                        FilterChip(
                            selected = state.refreshIntervalMs == value,
                            onClick = { onRefreshInterval(value) },
                            label = { Text(stringResource(R.string.ytm_milliseconds, value)) },
                        )
                    }
                }
                Text(stringResource(R.string.ytm_network_sampling), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ytm_done)) } },
    )
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    YSettingSwitch(title = label, checked = checked, onCheckedChange = onCheckedChange)
}

@Composable
private fun sortLabel(sort: ProcessSort): String = when (sort) {
    ProcessSort.MEMORY -> "RAM"
    ProcessSort.CPU -> "CPU"
    ProcessSort.DOWNLOAD -> stringResource(R.string.ytm_sort_download)
    ProcessSort.UPLOAD -> stringResource(R.string.ytm_sort_upload)
    ProcessSort.NAME -> "A-Z"
    ProcessSort.PID -> "PID"
}

@Composable
private fun kindLabel(kind: ProcessKind): String = when (kind) {
    ProcessKind.USER_APP -> stringResource(R.string.ytm_user)
    ProcessKind.SYSTEM_APP -> stringResource(R.string.ytm_system)
    ProcessKind.LINUX -> stringResource(R.string.ytm_linux)
}

private fun formatStartTime(timeMs: Long, unknown: String): String {
    if (timeMs <= 0L) return unknown
    return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(Date(timeMs))
}

private fun formatDuration(ms: Long): String {
    var seconds = (ms / 1000L).coerceAtLeast(0L)
    val days = seconds / 86400L
    seconds %= 86400L
    val hours = seconds / 3600L
    seconds %= 3600L
    val minutes = seconds / 60L
    val secs = seconds % 60L
    return buildString {
        if (days > 0) append("${days}d ")
        if (hours > 0 || days > 0) append("${hours}h ")
        if (minutes > 0 || hours > 0 || days > 0) append("${minutes}m ")
        append("${secs}s")
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unit = -1
    while (value >= 1024.0 && unit < units.lastIndex) {
        value /= 1024.0
        unit++
    }
    return String.format(Locale.getDefault(), "%.1f %s", value, units[unit])
}

private fun formatSpeed(bytesPerSecond: Long): String = "${formatBytes(bytesPerSecond)}/s"

private fun formatKHz(khz: Long?): String =
    if (khz == null || khz <= 0L) "-" else String.format(Locale.US, "%.2f GHz", khz / 1_000_000.0)

private fun formatHz(hz: Long?): String =
    if (hz == null || hz <= 0L) "-" else String.format(Locale.US, "%.0f MHz", hz / 1_000_000.0)
