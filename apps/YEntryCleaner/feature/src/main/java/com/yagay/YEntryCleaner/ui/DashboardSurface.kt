package com.yagay.YEntryCleaner.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ExpandMore
import com.yagay.yui.YUiCircularProgressIndicator as CircularProgressIndicator
import com.yagay.yui.YUiDropdownMenu as DropdownMenu
import com.yagay.yui.YUiDropdownMenuItem as DropdownMenuItem
import com.yagay.yui.YUiIcon as Icon
import androidx.compose.material3.MaterialTheme
import com.yagay.yui.YUiText as Text
import com.yagay.yui.YUiTextButton as TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yagay.YEntryCleaner.BuildConfig
import com.yagay.YEntryCleaner.R
import com.yagay.YEntryCleaner.domain.DisplayMode
import com.yagay.yui.YHorizontalActions
import com.yagay.yui.YSection
import com.yagay.yui.YDimens
import com.yagay.yui.YPrimaryActionButton
import com.yagay.yui.YSecondaryActionButton
import com.yagay.yui.YSwitchItem
import com.yagay.yui.YStatusLine
import com.yagay.yui.YStatusTone

/**
 * Unified dashboard surface for YEntryCleaner.
 *
 * Business actions stay in [MainViewModel]; this file only owns layout/presentation so the dashboard
 * follows the same YUI card/status/switch vocabulary as the rest of YSuite.
 */
@Composable
internal fun UnifiedDashboardTabContent(
    state: MainState,
    vm: MainViewModel,
    onRestore: () -> Unit,
    onExport: () -> Unit,
    collectingDiagnostics: Boolean,
    onCollectDiagnostics: () -> Unit,
    onInspectFile: () -> Unit
) {
    val fileCheckStatus by vm.fileCheckStatus.collectAsState()
    val checkingFile by vm.checkingFile.collectAsState()
    var modeMenu by remember { mutableStateOf(false) }
    var showScopeDetails by remember { mutableStateOf(false) }
    var showAppScopePicker by remember { mutableStateOf(false) }

    if (showScopeDetails) {
        ScopeDialog(state.module, vm::requestScope, vm::refreshModuleStatus) { showScopeDetails = false }
    }
    if (showAppScopePicker) {
        AppScopePickerDialog(
            selected = state.hiddenFromApps,
            onSelectedChange = vm::setHiddenFromApps
        ) { showAppScopePicker = false }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = YDimens.ScreenHorizontal, vertical = YDimens.ScreenVertical),
        verticalArrangement = Arrangement.spacedBy(YDimens.SectionGap)
    ) {
        RuntimePanel(state, vm)

        YSection(
            title = stringResource(R.string.dashboard_global_mode),
            subtitle = stringResource(R.string.dashboard_global_mode_help),
            trailing = {
                Box {
                    TextButton(onClick = { modeMenu = true }) {
                        Text(stringResource(R.string.dashboard_switch))
                        Icon(Icons.Rounded.ExpandMore, contentDescription = null, modifier = Modifier.size(20.dp))
                    }
                    DropdownMenu(expanded = modeMenu, onDismissRequest = { modeMenu = false }) {
                        DisplayMode.entries.forEach { mode ->
                            DropdownMenuItem(
                                text = { Text(stringResource(mode.titleRes())) },
                                leadingIcon = {
                                    if (mode == state.displayMode) Icon(Icons.Rounded.Check, contentDescription = null)
                                },
                                onClick = {
                                    modeMenu = false
                                    vm.setDisplayMode(mode)
                                }
                            )
                        }
                    }
                }
            }
        ) {
            YStatusLine(
                label = stringResource(R.string.dashboard_global_mode),
                value = stringResource(state.displayMode.titleRes())
            )
        }

        YSection(
            title = stringResource(R.string.dashboard_sync_status),
            subtitle = stringResource(R.string.dashboard_sync_help)
        ) {
            YStatusLine(
                label = stringResource(R.string.dashboard_sync_status),
                value = state.syncStatus,
                tone = if (state.runtime.ready) YStatusTone.Good else YStatusTone.Warning
            )
        }

        YSection(
            title = stringResource(R.string.dashboard_runtime_hits),
            subtitle = stringResource(R.string.dashboard_hits_help)
        ) {
            YStatusLine(
                label = stringResource(R.string.dashboard_module_status),
                value = stringResource(if (state.runtime.ready) R.string.dashboard_ack_confirmed else R.string.dashboard_ack_missing),
                tone = if (state.runtime.ready) YStatusTone.Good else YStatusTone.Warning
            )
            YStatusLine(
                label = stringResource(R.string.capability_filtering),
                value = state.runtime.queryHits.toString()
            )
            YStatusLine(
                label = stringResource(R.string.capability_visibility),
                value = state.runtime.visibilityHits.toString()
            )
            YStatusLine(
                label = stringResource(R.string.capability_ordering),
                value = state.runtime.orderingHits.toString()
            )
            YSecondaryActionButton(onClick = vm::refreshModuleStatus, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.dashboard_refresh_runtime))
            }
        }

        YSection(title = stringResource(R.string.dashboard_data_backup)) {
            YHorizontalActions {
                YPrimaryActionButton(onClick = onRestore, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.dashboard_restore_json))
                }
                YSecondaryActionButton(onClick = onExport, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.dashboard_export_json))
                }
            }
        }

        DashboardModuleStatusCard(state) {
            showScopeDetails = true
            vm.refreshModuleStatus()
        }

        YSection(
            title = stringResource(R.string.dashboard_app_visibility),
            subtitle = stringResource(R.string.dashboard_app_visibility_help),
            detail = stringResource(R.string.dashboard_app_visibility_warning)
        ) {
            YStatusLine(
                label = stringResource(R.string.dashboard_app_visibility),
                value = state.hiddenFromApps.size.toString()
            )
            YPrimaryActionButton(onClick = { showAppScopePicker = true }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.dashboard_manage_app_visibility, state.hiddenFromApps.size))
            }
        }

        YSection(
            title = stringResource(R.string.dashboard_diagnostics),
            subtitle = stringResource(R.string.dashboard_scan_disclaimer),
            detail = stringResource(R.string.diagnostic_export_help)
        ) {
            state.error?.let {
                YStatusLine(
                    label = stringResource(R.string.rules_summary_status),
                    value = it,
                    tone = YStatusTone.Error
                )
            }
            YSecondaryActionButton(
                onClick = onInspectFile,
                enabled = !checkingFile,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(if (checkingFile) R.string.dashboard_inspecting_file else R.string.dashboard_inspect_file))
            }
            fileCheckStatus?.let {
                YStatusLine(
                    label = stringResource(R.string.dashboard_inspect_file),
                    value = it
                )
            }
            YSwitchItem(
                title = stringResource(R.string.diagnostic_mode),
                subtitle = stringResource(R.string.diagnostic_mode_help),
                checked = state.diagnosticMode,
                onCheckedChange = vm::setDiagnosticMode
            )
            if (state.diagnosticMode) {
                YStatusLine(
                    label = stringResource(R.string.diagnostic_mode),
                    value = stringResource(R.string.diagnostic_enabled_help),
                    tone = YStatusTone.Warning
                )
            }
            YPrimaryActionButton(
                onClick = onCollectDiagnostics,
                enabled = !collectingDiagnostics,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (collectingDiagnostics) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(YDimens.ControlGap))
                }
                Text(stringResource(if (collectingDiagnostics) R.string.dashboard_collecting else R.string.dashboard_export_diagnostics))
            }
        }

        Text(
            stringResource(R.string.app_version_format, stringResource(R.string.app_name), BuildConfig.VERSION_NAME),
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = YDimens.SectionGap),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DashboardModuleStatusCard(state: MainState, onClick: () -> Unit) {
    val status = state.module
    val titleRes = when {
        !status.connected -> R.string.module_lsposed_disconnected
        status.outdated -> R.string.module_old_running
        status.error != null || (status.scopeKnown && status.missingScope.isNotEmpty()) -> R.string.module_needs_attention
        status.resolverLoaded -> R.string.module_loaded
        else -> R.string.module_lsposed_connected
    }
    val tone = when {
        !status.connected -> YStatusTone.Error
        status.outdated -> YStatusTone.Error
        status.error != null || (status.scopeKnown && status.missingScope.isNotEmpty()) -> YStatusTone.Warning
        state.runtime.ready -> YStatusTone.Good
        else -> YStatusTone.Warning
    }
    YSection(
        title = stringResource(R.string.dashboard_module_status),
        subtitle = stringResource(titleRes),
        detail = state.syncStatus,
        modifier = Modifier.clickable(onClick = onClick),
        trailing = {
            Icon(Icons.Rounded.ExpandMore, contentDescription = stringResource(R.string.module_view_status))
        }
    ) {
        YStatusLine(
            label = stringResource(R.string.dashboard_module_status),
            value = if (state.runtime.ready) stringResource(R.string.module_system_confirmed)
            else stringResource(R.string.module_check_status),
            tone = tone
        )
    }
}
