package com.yagay.YTaskManager.ui

import android.graphics.drawable.Drawable
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import com.yagay.yui.YUiIcon as Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import com.yagay.yui.YUiText as Text
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
import com.yagay.suite.api.InstalledAppCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
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
import com.yagay.yui.LocalYAppearance
import com.yagay.yui.YSection
import com.yagay.yui.YBottomSheet
import com.yagay.yui.YChoiceSpec
import com.yagay.yui.YRadioGroup
import com.yagay.yui.YSectionHeader
import com.yagay.yui.YPrimaryActionButton
import com.yagay.yui.YMetricCard
import com.yagay.yui.YResponsiveFieldPair
import com.yagay.yui.YStatusStrip
import com.yagay.yui.YStatusSpec
import com.yagay.yui.YPageList
import com.yagay.yui.YListItem
import com.yagay.yui.YEmptyMessage
import com.yagay.yui.YManagerScaffold
import com.yagay.yui.YIcons
import com.yagay.yui.YNavigationSpec
import com.yagay.yui.YAppShell
import com.yagay.yui.YSearchField
import com.yagay.yui.YSwitchItem
import com.yagay.yui.YStatusPill
import com.yagay.yui.YStatusLine
import com.yagay.yui.YStatusTone
import com.yagay.yui.YActionSpec
import com.yagay.yui.YActionStyle
import com.yagay.yui.YConfirmDialog
import com.yagay.yui.YFormDialog
import com.yagay.yui.YIconAction
import com.yagay.yui.YListSkeleton
import com.yagay.yui.YMessageHost
import com.yagay.yui.YUiDropdownMenu as DropdownMenu
import com.yagay.yui.YUiDropdownMenuItem as DropdownMenuItem
import com.yagay.yui.YUiTextButton as TextButton
import com.yagay.yui.YNotice
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

    YAppShell(
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
                YIconAction(
                    icon = YIcons.Refresh,
                    contentDescription = stringResource(R.string.ytm_refresh),
                    onClick = viewModel::refresh,
                )
                YIconAction(
                    icon = YIcons.Settings,
                    contentDescription = stringResource(R.string.ytm_settings),
                    onClick = { showSettings = true },
                )
            },
            snackbarHost = { YMessageHost(snackbar) },
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
                            // Show cached details immediately; Root enrichment is optional.
                            selected = process
                            viewModel.loadProcessDetails(process) { enriched ->
                                if (selected?.pid == enriched.pid && selected?.uid == enriched.uid) {
                                    selected = enriched
                                }
                            }
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
                selected = parent
                viewModel.loadProcessDetails(parent) { enriched ->
                    if (selected?.pid == enriched.pid && selected?.uid == enriched.uid) {
                        selected = enriched
                    }
                }
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
        YConfirmDialog(
            title = stringResource(if (forceStop) R.string.ytm_force_stop_question else R.string.ytm_kill_question),
            message = process.displayName,
            confirmLabel = stringResource(R.string.ytm_confirm),
            dismissLabel = stringResource(R.string.ytm_cancel),
            onConfirm = {
                pendingKill = null
                selected = null
                if (forceStop) viewModel.forceStop(process) else viewModel.killProcess(process)
            },
            onDismiss = { pendingKill = null },
            dangerous = true,
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
        ProcessControls(
            state = state,
            onQuery = viewModel::setQuery,
            onUser = viewModel::setShowUserApps,
            onSystem = viewModel::setShowSystemApps,
            onLinux = viewModel::setShowLinuxProcesses,
            onSort = viewModel::setSort,
        )
        if (state.loading) {
            YListSkeleton(
                rows = 7,
                modifier = Modifier.padding(horizontal = LocalYAppearance.current.screenPaddingDp.dp),
            )
        } else {
            ProcessList(state, onSelect)
        }
    }
}

@Composable
private fun StatusSection(state: TaskManagerUiState) {
    Column {
    YStatusStrip(
        statuses = listOf(
            YStatusSpec(
                label = "Root",
                value = when {
                    !state.root.checked -> stringResource(R.string.ytm_root_connecting)
                    state.root.granted -> stringResource(R.string.ytm_ok)
                    else -> stringResource(R.string.ytm_root_unavailable)
                },
                tone = when {
                    !state.root.checked -> YStatusTone.Warning
                    state.root.granted -> YStatusTone.Good
                    else -> YStatusTone.Error
                },
            ),
            YStatusSpec(
                label = "LSPosed",
                value = if (state.framework.detected) stringResource(R.string.ytm_detected) else stringResource(R.string.ytm_not_detected),
                tone = if (state.framework.detected) YStatusTone.Good else YStatusTone.Warning,
            ),
        ),
        modifier = Modifier.padding(horizontal = LocalYAppearance.current.screenPaddingDp.dp, vertical = (LocalYAppearance.current.effectiveGapDp / 3f).dp),
    )
    if (!state.root.granted && state.root.message != "Not checked") {
        YNotice(
            text = state.root.message,
            modifier = Modifier.padding(
                horizontal = LocalYAppearance.current.screenPaddingDp.dp,
                vertical = (LocalYAppearance.current.effectiveGapDp / 3f).dp,
            ),
        )
    }
    }
}

/** Keep search visible and expose filter and sort as two independent YUI dropdowns. */
@Composable
private fun ProcessControls(
    state: TaskManagerUiState,
    onQuery: (String) -> Unit,
    onUser: (Boolean) -> Unit,
    onSystem: (Boolean) -> Unit,
    onLinux: (Boolean) -> Unit,
    onSort: (ProcessSort) -> Unit,
) {
    var filterExpanded by remember { mutableStateOf(false) }
    var sortExpanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.padding(
            horizontal = LocalYAppearance.current.screenPaddingDp.dp,
            vertical = (LocalYAppearance.current.effectiveGapDp / 2f).dp,
        ),
        verticalArrangement = Arrangement.spacedBy((LocalYAppearance.current.effectiveGapDp / 2f).dp),
    ) {
        YSearchField(
            value = state.query,
            onValueChange = onQuery,
            hint = stringResource(R.string.ytm_search_hint),
            leadingIcon = { Icon(YIcons.Search, contentDescription = null) },
            trailingIcon = if (state.query.isNotEmpty()) {
                {
                    YIconAction(
                        icon = Icons.Default.Close,
                        contentDescription = stringResource(R.string.ytm_clear),
                        onClick = { onQuery("") },
                    )
                }
            } else null,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(LocalYAppearance.current.effectiveGapDp.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) {
                TextButton(onClick = {
                    sortExpanded = false
                    filterExpanded = true
                }) {
                    Text(
                        stringResource(R.string.ytm_process_filter) + " (" +
                            listOf(state.showUserApps, state.showSystemApps, state.showLinuxProcesses)
                                .count { it } + ") ▾",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                DropdownMenu(
                    expanded = filterExpanded,
                    onDismissRequest = { filterExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text((if (state.showUserApps) "✓  " else "     ") + stringResource(R.string.ytm_user)) },
                        onClick = { onUser(!state.showUserApps) },
                    )
                    DropdownMenuItem(
                        text = { Text((if (state.showSystemApps) "✓  " else "     ") + stringResource(R.string.ytm_system)) },
                        onClick = { onSystem(!state.showSystemApps) },
                    )
                    DropdownMenuItem(
                        text = { Text((if (state.showLinuxProcesses) "✓  " else "     ") + stringResource(R.string.ytm_linux)) },
                        onClick = { onLinux(!state.showLinuxProcesses) },
                    )
                }
            }
            Box(Modifier.weight(1f)) {
                TextButton(onClick = {
                    filterExpanded = false
                    sortExpanded = true
                }) {
                    Text(
                        stringResource(R.string.ytm_sort_by) + ": " + sortLabel(state.sort) + " ▾",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                DropdownMenu(
                    expanded = sortExpanded,
                    onDismissRequest = { sortExpanded = false },
                ) {
                    ProcessSort.entries.forEach { sort ->
                        DropdownMenuItem(
                            text = { Text((if (state.sort == sort) "✓  " else "     ") + sortLabel(sort)) },
                            onClick = {
                                onSort(sort)
                                sortExpanded = false
                            },
                        )
                    }
                }
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
        YEmptyMessage(
            message = stringResource(R.string.ytm_no_processes),
            modifier = Modifier.padding(horizontal = LocalYAppearance.current.screenPaddingDp.dp, vertical = LocalYAppearance.current.effectiveGapDp.dp),
        )
        return
    }

    LazyColumn(Modifier.fillMaxSize()) {
        items(filtered, key = { it.pid }) { process ->
            ProcessRow(process, onClick)
        }
    }
}

@Composable
private fun ProcessRow(process: ProcessEntry, onClick: (ProcessEntry) -> Unit) {
    val foregroundSuffix = if (process.isForeground) {
        " · " + stringResource(R.string.ytm_foreground_short)
    } else {
        ""
    }
    val title = process.displayName + foregroundSuffix
    YListItem(
        title = title,
        subtitle = stringResource(R.string.ytm_process_row, process.pid, process.userName, kindLabel(process.kind), process.threads),
        detail = stringResource(
            R.string.ytm_download_upload,
            if (process.networkSampled) formatSpeed(process.rxBytesPerSecond) else "—",
            if (process.networkSampled) formatSpeed(process.txBytesPerSecond) else "—",
        ),
        leading = { AppIcon(process.icon, process.packageName) },
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (process.isPinned) {
                    Icon(
                        Icons.Default.PushPin,
                        contentDescription = stringResource(R.string.ytm_pinned),
                        modifier = Modifier.size((LocalYAppearance.current.iconVisualSizeDp * 0.625f).dp),
                    )
                    Spacer(Modifier.size((LocalYAppearance.current.effectiveGapDp / 2f).dp))
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(if (process.cpuSampled) String.format(Locale.getDefault(), "%.1f%%", process.cpuPercent) else "—")
                    Text(formatBytes(process.rssKb * 1024L), style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        onClick = { onClick(process) },
    )
}

@Composable
private fun ResourcePage(state: TaskManagerUiState) {
    val s = state.system
    val g = state.gpu
    val unknown = stringResource(R.string.ytm_unknown)
    val noData = stringResource(R.string.ytm_no_data)
    YPageList(
        padding = PaddingValues(),
    ) {
        item {
            YResponsiveFieldPair(
                modifier = Modifier.padding(horizontal = LocalYAppearance.current.screenPaddingDp.dp),
                first = { slot ->
                    YMetricCard(
                        label = "CPU",
                        value = String.format(Locale.getDefault(), "%.1f%%", s.cpuPercent),
                        modifier = slot,
                    )
                },
                second = { slot ->
                    val ramPercentage = if (s.ramTotalBytes > 0L)
                        (s.ramUsedBytes.toDouble() * 100.0 / s.ramTotalBytes).toFloat()
                    else 0f
                    YMetricCard(
                        label = "RAM",
                        value = String.format(Locale.getDefault(), "%.1f%%", ramPercentage),
                        modifier = slot,
                    )
                },
            )
        }
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
        item { Spacer(Modifier.height(LocalYAppearance.current.effectiveGapDp.dp)) }
    }
}

@Composable
private fun NetworkPage(state: TaskManagerUiState) {
    val network = state.network
    YPageList(
        padding = PaddingValues(),
        compact = true,
    ) {
        item {
            YSection(
                title = stringResource(R.string.ytm_realtime_speed),
                subtitle = stringResource(R.string.ytm_realtime_speed_desc),
                detail = stringResource(R.string.ytm_backend_detail, network.backend),
            )
        }
        if (network.entries.isEmpty()) {
            item { YEmptyMessage(stringResource(R.string.ytm_no_traffic)) }
        } else {
            items(network.entries, key = { it.uid }) { entry ->
                NetworkRow(entry)
            }
        }
    }
}

@Composable
private fun NetworkRow(entry: NetworkEntry) {
    YListItem(
        title = entry.label,
        subtitle = stringResource(
            R.string.ytm_network_identity,
            entry.uid,
            if (entry.system) stringResource(R.string.ytm_system_suffix) else "",
            if (entry.packageNames.isNotEmpty())
                stringResource(R.string.ytm_package_suffix, entry.packageNames.joinToString(", "))
            else "",
        ),
        leading = { AppIcon(entry.icon, entry.packageNames.firstOrNull()) },
        trailing = {
            Column(horizontalAlignment = Alignment.End) {
                Text(stringResource(R.string.ytm_download_value, formatSpeed(entry.rxBytesPerSecond)),
                    style = MaterialTheme.typography.labelMedium)
                Text(stringResource(R.string.ytm_upload_value, formatSpeed(entry.txBytesPerSecond)),
                    style = MaterialTheme.typography.bodySmall)
            }
        },
    )
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    YSection(
        title = title,
        modifier = Modifier.padding(horizontal = LocalYAppearance.current.screenPaddingDp.dp),
        content = content,
    )
}

@Composable
private fun MetricLine(label: String, value: String) {
    YStatusLine(label = label, value = value)
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
    val guideWidth = LocalYAppearance.current.cardOutlineWidthDp.dp
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height((LocalYAppearance.current.rowHeightDp * 1.25f).dp),
    ) {
        drawRect(guideColor, style = Stroke(width = guideWidth.toPx()))
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
private fun AppIcon(drawable: Drawable?, packageName: String? = null) {
    val iconSize = LocalYAppearance.current.listIconSizeDp.dp
    val density = LocalDensity.current
    val px = with(density) { max(1, iconSize.roundToPx()) }
    val context = LocalContext.current.applicationContext
    // LazyColumn only composes visible rows. Decode through the shared app cache
    // off the UI thread, rather than decoding every process during /proc enumeration.
    var bitmap by remember(drawable, packageName, px) {
        mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null)
    }
    LaunchedEffect(drawable, packageName, px) {
        bitmap = withContext(Dispatchers.IO) {
            when {
                packageName != null -> InstalledAppCatalog.iconBitmap(
                    context, packageName, px
                )?.asImageBitmap()
                drawable != null -> drawable.toBitmap(px, px).asImageBitmap()
                else -> null
            }
        }
    }
    val loaded = bitmap
    if (loaded == null) {
        Box(
            modifier = Modifier.size(iconSize).clip(MaterialTheme.shapes.small),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Memory, contentDescription = null)
        }
    } else {
        Image(
            bitmap = loaded,
            contentDescription = null,
            modifier = Modifier.size(iconSize).clip(MaterialTheme.shapes.small),
        )
    }
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
    val actions = buildList {
        add(
            YActionSpec(
                label = stringResource(if (process.isPinned) R.string.ytm_unpin else R.string.ytm_pin),
                onClick = onPin,
            )
        )
        if (process.packageName != null) {
            add(
                YActionSpec(
                    label = stringResource(R.string.ytm_force_stop),
                    style = YActionStyle.DANGER,
                    onClick = onForceStop,
                )
            )
        }
        add(
            YActionSpec(
                label = stringResource(R.string.ytm_kill_pid),
                style = YActionStyle.DANGER,
                onClick = onKill,
            )
        )
        add(YActionSpec(label = stringResource(R.string.ytm_close), onClick = onDismiss))
    }

    YFormDialog(
        title = process.displayName,
        onDismissRequest = onDismiss,
        actions = actions,
    ) {
        LazyColumn(
            modifier = Modifier.heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.58f).dp),
            verticalArrangement = Arrangement.spacedBy((LocalYAppearance.current.effectiveGapDp / 3f).dp),
        ) {
            item { YNotice(stringResource(R.string.ytm_long_press_copy)) }
            item { YSectionHeader(stringResource(R.string.ytm_process_identity)) }
            item { CopyDetail("PID", process.pid.toString()) }
            if (process.ppid != 0) item { ParentDetail(process.ppid, parent, onOpenParent) }
            item { CopyDetail("UID", process.uid.toString()) }
            item { CopyDetail(stringResource(R.string.ytm_user), process.userName) }
            item { YSectionHeader(stringResource(R.string.ytm_process_performance)) }
            item { CopyDetail(stringResource(R.string.ytm_cpu_usage), if (process.cpuSampled) String.format(Locale.getDefault(), "%.1f%%", process.cpuPercent) else "—") }
            item { CopyDetail(stringResource(R.string.ytm_ram_usage), formatBytes(process.rssKb * 1024L)) }
            item { CopyDetail(stringResource(R.string.ytm_realtime_download), if (process.networkSampled) formatSpeed(process.rxBytesPerSecond) else "—") }
            item { CopyDetail(stringResource(R.string.ytm_realtime_upload), if (process.networkSampled) formatSpeed(process.txBytesPerSecond) else "—") }
            if (process.virtualMemoryKb > 0L) item { CopyDetail(stringResource(R.string.ytm_virtual_memory), formatBytes(process.virtualMemoryKb * 1024L)) }
            item { CopyDetail(stringResource(R.string.ytm_foreground), if (process.isForeground) stringResource(R.string.ytm_yes) else stringResource(R.string.ytm_no)) }
            item { CopyDetail(stringResource(R.string.ytm_threads), process.threads.toString()) }
            item { YSectionHeader(stringResource(R.string.ytm_process_advanced)) }
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
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CopyDetail(label: String, value: String) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val copied = stringResource(R.string.ytm_copied, label)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {},
                onLongClick = {
                    clipboard.setText(AnnotatedString(value))
                    Toast.makeText(context, copied, Toast.LENGTH_SHORT).show()
                },
            )
            .padding(vertical = (LocalYAppearance.current.effectiveGapDp / 4f).dp),
        verticalArrangement = Arrangement.spacedBy((LocalYAppearance.current.effectiveGapDp / 4f).dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
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
            .padding(vertical = (LocalYAppearance.current.effectiveGapDp / 4f).dp),
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
    val intervals = listOf(1000L, 2500L, 5000L, 10000L)
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.7f).dp
    YBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = maxHeight)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy((LocalYAppearance.current.effectiveGapDp / 2f).dp),
        ) {
            YSectionHeader(stringResource(R.string.ytm_process_settings))
            YSwitchItem(
                title = stringResource(R.string.ytm_auto_refresh),
                subtitle = stringResource(R.string.ytm_auto_refresh_desc),
                checked = state.autoRefresh,
                onCheckedChange = onAutoRefresh,
            )
            YSwitchItem(
                title = stringResource(R.string.ytm_confirm_before_kill),
                subtitle = stringResource(R.string.ytm_confirm_kill_desc),
                checked = state.confirmKill,
                onCheckedChange = onConfirmKill,
            )
            YSectionHeader(stringResource(R.string.ytm_refresh_interval))
            YRadioGroup(
                value = state.refreshIntervalMs,
                options = intervals.map { interval ->
                    YChoiceSpec(interval, stringResource(R.string.ytm_milliseconds, interval))
                },
                onSelected = onRefreshInterval,
                enabled = state.autoRefresh,
            )
            YNotice(stringResource(R.string.ytm_network_sampling))
            YPrimaryActionButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.ytm_done))
            }
        }
    }
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
