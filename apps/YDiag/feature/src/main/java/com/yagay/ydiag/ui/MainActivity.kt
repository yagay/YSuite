package com.yagay.ydiag.ui

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yagay.ydiag.ModuleState
import com.yagay.ydiag.R
import com.yagay.ydiag.data.Preferences
import com.yagay.ydiag.model.DiagnosticCatalog
import com.yagay.ydiag.model.DiagnosticCategory
import com.yagay.ydiag.model.LoadLevel
import com.yagay.ydiag.model.Recommendation
import com.yagay.ydiag.model.Severity
import com.yagay.ydiag.service.MonitorState
import com.yagay.yui.YHorizontalActions
import com.yagay.yui.YSwitchItem
import com.yagay.yui.YSectionHeader
import com.yagay.yui.YPageList
import com.yagay.yui.YListItem
import com.yagay.yui.YFilterBar
import com.yagay.yui.YCheckboxItem
import com.yagay.yui.YComposeActivity
import com.yagay.yui.YSection
import com.yagay.yui.YEmptyMessage
import com.yagay.yui.YPageRole
import com.yagay.yui.YPageScaffold
import com.yagay.yui.YSectionHeader
import com.yagay.yui.YMetricCard
import com.yagay.yui.YIcons
import com.yagay.yui.YNavigationSpec
import com.yagay.yui.YAppShell
import com.yagay.yui.YPrimaryButton
import com.yagay.yui.YSearchField
import com.yagay.yui.YSecondaryButton
import com.yagay.yui.YStatusSpec
import com.yagay.yui.YStatusStrip
import com.yagay.yui.YStatusTone
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : YComposeActivity() {
    @Composable
    override fun YContent() {
        YDiagRoot()
    }
}

@Composable
private fun YDiagRoot(vm: YDiagViewModel = viewModel()) {
    val monitor by vm.monitorState.collectAsStateWithLifecycle()
    val module by vm.moduleState.collectAsStateWithLifecycle()
    val apps by vm.apps.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val enabled by vm.enabledOptions.collectAsStateWithLifecycle()
    val preset by vm.presetId.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val exportMessage by vm.exportMessage.collectAsStateWithLifecycle()
    val search by vm.search.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val activationMode by vm.deepActivationMode.collectAsStateWithLifecycle()

    var tab by remember { mutableIntStateOf(0) }
    var showPicker by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    val treeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.setCustomTree(uri)
    }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                vm.getApplication<Application>(),
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(exportMessage) {
        val message = exportMessage ?: return@LaunchedEffect
        snackbar.showSnackbar(message)
        vm.clearExportMessage()
    }

    val navigation = listOf(
        YNavigationSpec("monitor", stringResource(R.string.ydiag_tab_monitor), YIcons.Info),
        YNavigationSpec("history", stringResource(R.string.ydiag_tab_history), YIcons.History),
        YNavigationSpec("diagnostics", stringResource(R.string.ydiag_tab_diagnostics), YIcons.Warning),
        YNavigationSpec("settings", stringResource(R.string.ydiag_tab_settings), YIcons.Settings),
    )

    YAppShell(
        selectedKey = navigation[tab].key,
        items = navigation,
        onSelected = { item ->
            tab = navigation.indexOfFirst { it.key == item.key }.coerceAtLeast(0)
            if (item.key == "history") vm.refreshHistory()
        },
    ) {
        YPageScaffold(
            title = stringResource(R.string.ydiag_app_name),
            role = when (tab) {
                0 -> YPageRole.DASHBOARD
                1 -> YPageRole.TIMELINE
                2 -> YPageRole.SETTINGS
                else -> YPageRole.SETTINGS
            },
            subtitle = stringResource(R.string.ydiag_subtitle),
            snackbarHost = { SnackbarHost(snackbar) },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (tab) {
                    0 -> MonitorScreen(
                        monitor = monitor,
                        module = module,
                        selected = selected,
                        appLabels = apps.associate { it.packageName to it.label },
                        onSelectApps = { showPicker = true },
                        onMark = vm::markProblem,
                        onStop = vm::stopMonitoring,
                        onExport = vm::exportLatest,
                    )
                    1 -> HistoryScreen(history = history, onExport = vm::export)
                    2 -> DiagnosticConfigScreen(
                        enabled = enabled,
                        presetId = preset,
                        onPreset = vm::applyPreset,
                        onToggle = vm::toggleOption,
                    )
                    3 -> SettingsScreen(
                        exportMode = vm.exportMode(),
                        customTree = vm.customTree()?.toString(),
                        maxSessionMb = vm.maxSessionMb(),
                        activationMode = activationMode,
                        onActivationMode = vm::setDeepActivationMode,
                        onExportMode = vm::setExportMode,
                        onChooseTree = { treeLauncher.launch(vm.customTree()) },
                        onMaxSessionMb = vm::setMaxSessionMb,
                    )
                }
            }
        }
    }

    if (showPicker) {
        AppPickerDialog(
            apps = apps,
            selected = selected,
            search = search,
            filter = filter,
            onSearch = vm::setSearch,
            onFilter = vm::setFilter,
            onToggle = vm::togglePackage,
            onDismiss = { showPicker = false },
        )
    }
}

@Composable
private fun MonitorScreen(
    monitor: MonitorState,
    module: ModuleState,
    selected: Set<String>,
    appLabels: Map<String, String>,
    onSelectApps: () -> Unit,
    onMark: () -> Unit,
    onStop: () -> Unit,
    onExport: () -> Unit,
) {
    YPageList(
        padding = PaddingValues(0.dp),
    ) {
        item {
            YStatusStrip(
                listOf(
                    YStatusSpec(
                        "Root",
                        if (monitor.rootAvailable) stringResource(R.string.ydiag_available) else stringResource(R.string.ydiag_unavailable),
                        if (monitor.rootAvailable) YStatusTone.Good else YStatusTone.Error,
                    ),
                    YStatusSpec(
                        "LSPosed",
                        if (module.connected) stringResource(R.string.ydiag_connected) else stringResource(R.string.ydiag_disconnected),
                        if (module.connected) YStatusTone.Good else YStatusTone.Warning,
                    ),
                ),
            )
        }
        item {
            YSection(
                title = if (monitor.running) stringResource(R.string.ydiag_running) else stringResource(R.string.ydiag_not_started),
                subtitle = if (selected.isEmpty()) stringResource(R.string.ydiag_select_to_start)
                else stringResource(R.string.ydiag_selected_processes, selected.size, monitor.processCount),
                trailing = { YPrimaryButton(stringResource(R.string.ydiag_select_apps), onSelectApps) },
            ) {
                if (selected.isNotEmpty()) {
                    selected.take(5).forEach { pkg ->
                        Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Text(
                                appLabels[pkg] ?: pkg,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(pkg, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (selected.size > 5) Text(stringResource(R.string.ydiag_more_apps, selected.size - 5))

                    val loaded = selected.count { it in module.loadedPackages }
                    val systemState = when {
                        module.systemLoaded -> stringResource(R.string.ydiag_loaded)
                        module.systemScoped -> stringResource(R.string.ydiag_authorized_waiting)
                        else -> stringResource(R.string.ydiag_not_authorized)
                    }
                    Text(
                        stringResource(R.string.ydiag_deep_hook_status, loaded, selected.size, systemState),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(module.message, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                YMetricCard(stringResource(R.string.ydiag_errors), monitor.errorCount.toString(), Modifier.weight(1f), YStatusTone.Error)
                YMetricCard(stringResource(R.string.ydiag_warnings), monitor.warningCount.toString(), Modifier.weight(1f), YStatusTone.Warning)
                YMetricCard(stringResource(R.string.ydiag_events), monitor.eventCount.toString(), Modifier.weight(1f))
            }
        }

        if (monitor.running) {
            item { YPrimaryButton(stringResource(R.string.ydiag_problem_happened), onMark, Modifier.fillMaxWidth()) }
        }

        item {
            YSectionHeader(
                title = stringResource(R.string.ydiag_discovered_problems),
                subtitle = stringResource(R.string.ydiag_discovered_problems_desc),
            )
        }

        if (monitor.recentIssues.isEmpty()) {
            item {
                YEmptyMessage(
                    if (monitor.running) stringResource(R.string.ydiag_no_issue_running)
                    else stringResource(R.string.ydiag_no_issue_idle),
                )
            }
        } else {
            items(monitor.recentIssues.take(12), key = { it.id }) { issue ->
                YListItem(
                    title = issue.title,
                    subtitle = stringResource(
                        R.string.ydiag_issue_subtitle,
                        severityText(issue.severity),
                        formatTime(issue.timestamp),
                    ),
                    detail = issue.detail.takeIf { it.isNotBlank() },
                )
            }
        }

        item { YSectionHeader(stringResource(R.string.ydiag_timeline)) }
        items(monitor.recentEvents.take(18), key = { it.id }) { event ->
            YListItem(
                title = event.title,
                subtitle = stringResource(R.string.ydiag_event_source, event.source, event.category),
                detail = formatTime(event.timestamp),
            )
        }

        item {
            YHorizontalActions {
                YSecondaryButton(stringResource(R.string.ydiag_export_full), onExport, Modifier.weight(1f))
                if (monitor.running) YSecondaryButton(stringResource(R.string.ydiag_stop), onStop, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun HistoryScreen(history: List<HistoryItem>, onExport: (HistoryItem) -> Unit) {
    YPageList(
        padding = PaddingValues(0.dp),
    ) {
        item {
            YSectionHeader(
                title = stringResource(R.string.ydiag_history_sessions),
                subtitle = stringResource(R.string.ydiag_history_desc),
            )
        }
        if (history.isEmpty()) item { YEmptyMessage(stringResource(R.string.ydiag_no_history)) }
        items(history, key = { it.meta.id }) { item ->
            YListItem(
                title = item.meta.targetPackages.joinToString().ifBlank { stringResource(R.string.ydiag_unknown_target) },
                subtitle = formatDate(item.meta.startedAt),
                detail = stringResource(R.string.ydiag_history_detail, item.meta.enabledOptions.size, item.meta.problemMarks.size),
                trailing = { TextButton(onClick = { onExport(item) }) { Text(stringResource(R.string.ydiag_export)) } },
            )
        }
    }
}

@Composable
private fun DiagnosticConfigScreen(
    enabled: Set<String>,
    presetId: String,
    onPreset: (String) -> Unit,
    onToggle: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            YSectionHeader(
                title = stringResource(R.string.ydiag_config_title),
                subtitle = stringResource(R.string.ydiag_config_desc),
            )
        }
        item {
            val presets = DiagnosticCatalog.presets
            val labels = presets.map { it.title } + stringResource(R.string.ydiag_custom)
            val selectedIndex = presets.indexOfFirst { it.id == presetId }
                .takeIf { it >= 0 } ?: presets.size
            YFilterBar(
                options = labels,
                selectedIndex = selectedIndex,
                onSelected = { index -> presets.getOrNull(index)?.let { onPreset(it.id) } },
            )
        }

        DiagnosticCategory.entries.forEach { category ->
            val options = DiagnosticCatalog.options.filter { it.category == category }
            if (options.isNotEmpty()) {
                item { YSectionHeader(categoryTitle(category), Modifier.padding(top = 8.dp)) }
                items(options, key = { it.id }) { option ->
                    YListItem(
                        title = option.title,
                        subtitle = stringResource(
                            R.string.ydiag_option_meta,
                            recommendationText(option.recommendation),
                            loadText(option.load),
                        ),
                        detail = option.description,
                        onClick = { onToggle(option.id) },
                        trailing = {
                            Switch(
                                checked = option.id in enabled,
                                onCheckedChange = { onToggle(option.id) },
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    exportMode: String,
    customTree: String?,
    maxSessionMb: Int,
    activationMode: String,
    onActivationMode: (String) -> Unit,
    onExportMode: (String) -> Unit,
    onChooseTree: () -> Unit,
    onMaxSessionMb: (Int) -> Unit,
) {
    var localLimit by remember(maxSessionMb) { mutableIntStateOf(maxSessionMb) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            YSectionHeader(
                title = stringResource(R.string.ydiag_hook_method_title),
                subtitle = stringResource(R.string.ydiag_hook_method_desc),
            )
        }
        item {
            YSection(title = stringResource(R.string.ydiag_activation_strategy)) {
                ActivationModeRow(
                    activationMode == Preferences.ACTIVATION_AUTO,
                    stringResource(R.string.ydiag_activation_auto),
                    stringResource(R.string.ydiag_activation_auto_desc),
                ) { onActivationMode(Preferences.ACTIVATION_AUTO) }
                ActivationModeRow(
                    activationMode == Preferences.ACTIVATION_MANUAL,
                    stringResource(R.string.ydiag_activation_manual),
                    stringResource(R.string.ydiag_activation_manual_desc),
                ) { onActivationMode(Preferences.ACTIVATION_MANUAL) }
                ActivationModeRow(
                    activationMode == Preferences.ACTIVATION_ROOT_ONLY,
                    stringResource(R.string.ydiag_activation_root),
                    stringResource(R.string.ydiag_activation_root_desc),
                ) { onActivationMode(Preferences.ACTIVATION_ROOT_ONLY) }
            }
        }
        item { YSectionHeader(stringResource(R.string.ydiag_export_location)) }
        item {
            YSection(title = stringResource(R.string.ydiag_package_directory)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = exportMode == "download", onClick = { onExportMode("download") })
                    Column {
                        Text(stringResource(R.string.ydiag_download_folder))
                        Text(stringResource(R.string.ydiag_download_desc), style = MaterialTheme.typography.bodySmall)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = exportMode == "custom", onClick = { onExportMode("custom") })
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.ydiag_custom_folder))
                        Text(
                            customTree ?: stringResource(R.string.ydiag_no_folder),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    TextButton(onClick = onChooseTree) { Text(stringResource(R.string.ydiag_choose)) }
                }
            }
        }
        item {
            YSection(
                title = stringResource(R.string.ydiag_log_segment_limit),
                subtitle = stringResource(R.string.ydiag_log_segment_desc),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    YSecondaryButton(
                        stringResource(R.string.ydiag_decrease),
                        {
                            localLimit = (localLimit - 32).coerceAtLeast(32)
                            onMaxSessionMb(localLimit)
                        },
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.ydiag_size_mb, localLimit), modifier = Modifier.width(90.dp))
                    YSecondaryButton(
                        stringResource(R.string.ydiag_increase),
                        {
                            localLimit = (localLimit + 32).coerceAtMost(1024)
                            onMaxSessionMb(localLimit)
                        },
                    )
                }
            }
        }
        item {
            HorizontalDivider()
            YSectionHeader(stringResource(R.string.ydiag_privacy))
            Text(stringResource(R.string.ydiag_privacy_desc), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ActivationModeRow(
    selected: Boolean,
    title: String,
    detail: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(Modifier.weight(1f)) {
            Text(title)
            Text(detail, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun AppPickerDialog(
    apps: List<com.yagay.ydiag.model.InstalledApp>,
    selected: Set<String>,
    search: String,
    filter: AppFilter,
    onSearch: (String) -> Unit,
    onFilter: (AppFilter) -> Unit,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val visible = remember(apps, selected, search, filter) {
        apps.filter { app ->
            val typeMatch = when (filter) {
                AppFilter.ALL -> true
                AppFilter.USER -> !app.system
                AppFilter.SYSTEM -> app.system
                AppFilter.MONITORED -> app.packageName in selected
            }
            typeMatch && (
                search.isBlank() ||
                    app.label.contains(search, true) ||
                    app.packageName.contains(search, true)
                )
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ydiag_done)) } },
        title = {
            Column {
                Text(stringResource(R.string.ydiag_picker_title))
                Text(stringResource(R.string.ydiag_picker_desc), style = MaterialTheme.typography.bodySmall)
            }
        },
        text = {
            Column {
                YSearchField(value = search, onValueChange = onSearch, hint = stringResource(R.string.ydiag_search_apps))
                Spacer(Modifier.height(8.dp))
                YFilterBar(
                    options = AppFilter.entries.map { filterLabel(it) },
                    selectedIndex = AppFilter.entries.indexOf(filter).coerceAtLeast(0),
                    onSelected = { index -> AppFilter.entries.getOrNull(index)?.let(onFilter) },
                )
                Spacer(Modifier.height(8.dp))
                if (visible.isEmpty()) {
                    YEmptyMessage(stringResource(R.string.ydiag_no_filtered_apps))
                } else {
                    LazyColumn(Modifier.height(480.dp)) {
                        items(visible, key = { it.packageName }) { app ->
                            val systemSuffix = if (app.system) {
                                " · " + stringResource(R.string.ydiag_system_app)
                            } else {
                                ""
                            }
                            YCheckboxItem(
                                title = app.label,
                                subtitle = app.packageName + systemSuffix,
                                checked = app.packageName in selected,
                                onCheckedChange = { onToggle(app.packageName) },
                            )
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun recommendationText(value: Recommendation): String = when (value) {
    Recommendation.RECOMMENDED -> stringResource(R.string.ydiag_recommended)
    Recommendation.ON_DEMAND -> stringResource(R.string.ydiag_on_demand)
    Recommendation.DEEP -> stringResource(R.string.ydiag_deep)
}

@Composable
private fun loadText(value: LoadLevel): String = when (value) {
    LoadLevel.VERY_LOW -> stringResource(R.string.ydiag_load_very_low)
    LoadLevel.LOW -> stringResource(R.string.ydiag_load_low)
    LoadLevel.MEDIUM -> stringResource(R.string.ydiag_load_medium)
    LoadLevel.HIGH -> stringResource(R.string.ydiag_load_high)
    LoadLevel.VERY_HIGH -> stringResource(R.string.ydiag_load_very_high)
}

@Composable
private fun categoryTitle(value: DiagnosticCategory): String = when (value) {
    DiagnosticCategory.BASIC -> stringResource(R.string.ydiag_category_basic)
    DiagnosticCategory.ROOT -> "Root"
    DiagnosticCategory.LSPOSED -> "LSPosed"
    DiagnosticCategory.SYSTEM -> stringResource(R.string.ydiag_category_system)
    DiagnosticCategory.WEBVIEW -> stringResource(R.string.ydiag_category_webview)
    DiagnosticCategory.NETWORK -> stringResource(R.string.ydiag_category_network)
    DiagnosticCategory.PERFORMANCE -> stringResource(R.string.ydiag_category_performance)
    DiagnosticCategory.FILES -> stringResource(R.string.ydiag_category_files)
    DiagnosticCategory.NATIVE -> stringResource(R.string.ydiag_category_native)
}

@Composable
private fun severityText(value: String): String = when (value) {
    Severity.FATAL.name, Severity.ERROR.name -> stringResource(R.string.ydiag_severity_error)
    Severity.WARNING.name -> stringResource(R.string.ydiag_severity_warning)
    else -> stringResource(R.string.ydiag_severity_info)
}

@Composable
private fun filterLabel(value: AppFilter): String = when (value) {
    AppFilter.ALL -> stringResource(R.string.ydiag_filter_all)
    AppFilter.USER -> stringResource(R.string.ydiag_filter_user)
    AppFilter.SYSTEM -> stringResource(R.string.ydiag_filter_system)
    AppFilter.MONITORED -> stringResource(R.string.ydiag_filter_monitored)
}

private fun formatTime(value: Long): String =
    SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(value))

private fun formatDate(value: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(value))
