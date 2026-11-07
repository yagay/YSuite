package com.yagay.YNFC.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import com.yagay.yui.YUiText as Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yagay.YNFC.AppLogger
import com.yagay.YNFC.BuildConfig
import com.yagay.YNFC.CardModel
import com.yagay.YNFC.DiagnosticsCollector
import com.yagay.YNFC.LogSource
import com.yagay.YNFC.R
import com.yagay.YNFC.RuntimeStatus
import com.yagay.YNFC.RuntimeStatusViewModel
import com.yagay.YNFC.RuntimeText
import com.yagay.yui.LocalYAppearance
import com.yagay.yui.YHorizontalActions
import com.yagay.yui.YSection
import com.yagay.yui.YEmptyMessage
import com.yagay.yui.YManagerScaffold
import com.yagay.yui.YPageList
import com.yagay.yui.YSectionHeader
import com.yagay.yui.YPrimaryButton
import com.yagay.yui.YSecondaryButton
import com.yagay.yui.YSwitchItem
import com.yagay.yui.YTabBar
import com.yagay.yui.YTabSpec
import com.yagay.yui.YUiLogSurface
import com.yagay.yui.YUiLogLine

/** Stateless Activity boundary for the complete NFC screen; operation state stays screen-local. */
@Composable
fun NfcAppScreen(
    cards: List<CardModel>,
    scannedCard: CardModel?,
    readModeEnabled: Boolean,
    pollingDisabled: Boolean,
    vendorTypeAEnabled: Boolean,
    onVendorTypeAEnabledChange: (Boolean) -> Unit,
    fileInterceptEnabled: Boolean,
    onFileInterceptEnabledChange: (Boolean) -> Unit,
    onReloadFileIntercept: ((String) -> Unit) -> Unit,
    initialLogsEnabled: Boolean,
    diagnosticsCollector: DiagnosticsCollector,
    readRuntimeStatus: () -> RuntimeStatus,
    buildStatusSummary: (RuntimeStatus) -> String,
    onLoggingChanged: (Boolean) -> Unit,
    onPollingDisabledChange: (Boolean) -> Boolean,
    onStartRead: () -> Unit,
    onStopRead: () -> Unit,
    onSaveCard: (CardModel) -> Unit,
    onClearScanned: () -> Unit,
    onSimulate: (CardModel, (String) -> Unit) -> Unit,
    onStopSimulation: ((String) -> Unit) -> Unit,
    onDeleteCard: (CardModel, Boolean) -> Unit,
    onReloadNfcHook: ((String) -> Unit) -> Unit,
    onExportLogs: (() -> Unit) -> Unit,
) {
    val runtimeViewModel: RuntimeStatusViewModel = viewModel()
    val status by runtimeViewModel.status.collectAsState()
    val logLines = remember { mutableStateListOf<String>() }
    var selectedSource by remember { mutableStateOf(LogSource.STATUS) }
    var diagnosticRunning by remember { mutableStateOf(false) }
    var logsEnabled by remember { mutableStateOf(initialLogsEnabled) }
    var expandedUid by remember { mutableStateOf<String?>(null) }
    var operationMessage by remember { mutableStateOf<String?>(null) }
    val logListState = rememberLazyListState()
    val applyUidText = stringResource(R.string.ynfc_apply_uid, "%s")
    val restoreRfText = stringResource(R.string.ynfc_restore_stock_rf)

    LaunchedEffect(selectedSource, logsEnabled) {
        logLines.clear()
        if (!logsEnabled) return@LaunchedEffect
        while (true) {
            val incoming = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val logStatus = readRuntimeStatus()
                val raw = diagnosticsCollector.fetchLogs(selectedSource)
                val text = if (selectedSource == LogSource.STATUS) {
                    buildStatusSummary(logStatus) + "\n\n" + raw
                } else raw
                RuntimeText.boundedLines(text)
            }
            RuntimeText.updateWindow(logLines, incoming)
            kotlinx.coroutines.delay(2000)
        }
    }

    YManagerScaffold(
        title = stringResource(R.string.ynfc_title_version, BuildConfig.VERSION_NAME),
        subtitle = stringResource(R.string.ynfc_subtitle),
    ) { padding ->
        YPageList(padding = padding) {
            item { RuntimeStatusPanel(status, operationMessage, readModeEnabled) }
            // Protocol 46 can remain unchanged across multiple implementations.
            // Warn when this exact NFC PID still runs code from an older APK.
            if (status.currentPid > 0 && status.hookInstalled &&
                status.hookPid == status.currentPid &&
                (status.hookImplementationRevision != BuildConfig.INTERCEPT_IMPL_REVISION ||
                    status.hookImplementationPid != status.currentPid)
            ) {
                item {
                    YSection(
                        title = stringResource(R.string.ynfc_intercept_old_hook_title),
                        subtitle = stringResource(R.string.ynfc_intercept_hook_reload_required),
                    ) {
                        YSecondaryButton(
                            text = stringResource(R.string.ynfc_hook_reload_button),
                            onClick = {
                                operationMessage = null
                                onReloadNfcHook { operationMessage = it }
                            },
                            enabled = !status.simulationEnabled,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            if (!fileInterceptEnabled && !vendorTypeAEnabled) {
                item {
                    YSection(
                        title = stringResource(R.string.ynfc_rf_legacy_mode_title),
                        subtitle = stringResource(R.string.ynfc_rf_legacy_mode_desc),
                    ) {
                        YSecondaryButton(
                            text = stringResource(R.string.ynfc_switch_to_intercept),
                            onClick = { onFileInterceptEnabledChange(true) },
                            enabled = !status.simulationEnabled,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            if (status.currentPid > 0 && status.scopeOk && status.hookInstalled &&
                status.hookPid == status.currentPid && status.hookBuild > 0 &&
                status.hookBuild != BuildConfig.HOOK_BUILD
            ) {
                item {
                    YSection(
                        title = stringResource(R.string.ynfc_hook_reload_title),
                        subtitle = stringResource(R.string.ynfc_hook_reload_desc),
                    ) {
                        YSecondaryButton(
                            text = stringResource(R.string.ynfc_hook_reload_button),
                            onClick = {
                                operationMessage = null
                                onReloadNfcHook { operationMessage = it }
                            },
                            enabled = !status.simulationEnabled,
                        )
                    }
                }
            }
            item {
                YSwitchItem(
                    title = stringResource(R.string.ynfc_polling_guard),
                    subtitle = stringResource(R.string.ynfc_polling_guard_desc),
                    checked = pollingDisabled,
                    onCheckedChange = { onPollingDisabledChange(it) },
                )
            }
            item {
                YSwitchItem(
                    title = stringResource(R.string.ynfc_vendor_type_a_mode),
                    subtitle = if (status.simulationEnabled) {
                        stringResource(R.string.ynfc_vendor_type_a_stop_first)
                    } else {
                        stringResource(R.string.ynfc_vendor_type_a_desc)
                    },
                    checked = vendorTypeAEnabled,
                    onCheckedChange = { requested ->
                        if (!status.simulationEnabled) onVendorTypeAEnabledChange(requested)
                    },
                )
            }
            item {
                YSwitchItem(
                    title = stringResource(R.string.ynfc_file_intercept_mode),
                    subtitle = if (status.simulationEnabled) {
                        stringResource(R.string.ynfc_vendor_type_a_stop_first)
                    } else {
                        stringResource(R.string.ynfc_file_intercept_desc)
                    },
                    checked = fileInterceptEnabled,
                    onCheckedChange = { requested ->
                        if (!status.simulationEnabled) onFileInterceptEnabledChange(requested)
                    },
                )
            }
            if (fileInterceptEnabled) {
                item {
                    YSection(
                        title = stringResource(R.string.ynfc_profile_auto_title),
                        subtitle = stringResource(R.string.ynfc_profile_auto_desc),
                    ) {
                        if (status.simulationEnabled &&
                            status.currentPid > 0 && status.hookInstalled &&
                            status.hookPid == status.currentPid &&
                            status.hookBuild == BuildConfig.HOOK_BUILD
                        ) {
                            YSecondaryButton(
                                text = stringResource(R.string.ynfc_intercept_reload_button),
                                onClick = {
                                    onReloadFileIntercept { operationMessage = it }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
            item {
                ReadCardPanel(
                    scannedCard,
                    readModeEnabled,
                    status.simulationEnabled,
                    onStartRead,
                    onStopRead,
                    onSaveCard,
                    onClearScanned,
                )
            }
            item {
                YSectionHeader(
                    title = stringResource(R.string.ynfc_saved_cards, cards.size),
                    subtitle = stringResource(R.string.ynfc_saved_cards_desc),
                )
            }
            if (cards.isEmpty()) {
                item {
                    YEmptyMessage(stringResource(R.string.ynfc_no_saved_cards))
                }
            } else {
                items(cards, key = { it.uid }) { card ->
                    val active = status.simulationEnabled && card.uid.equals(status.selectedUid, true)
                    CardItem(
                        card = card,
                        isActive = active,
                        expanded = expandedUid?.equals(card.uid, true) == true,
                        onToggleDetails = {
                            expandedUid = if (expandedUid?.equals(card.uid, true) == true) null else card.uid
                        },
                        onSimulate = {
                            operationMessage = applyUidText.replace("%s", card.uid)
                            onSimulate(card) { operationMessage = it }
                        },
                        onStop = {
                            operationMessage = restoreRfText
                            onStopSimulation { operationMessage = it }
                        },
                        onDelete = {
                            onDeleteCard(card, active)
                            if (expandedUid?.equals(card.uid, true) == true) expandedUid = null
                        },
                    )
                }
            }
            item {
                YSwitchItem(
                    title = stringResource(R.string.ynfc_log_display),
                    subtitle = if (logsEnabled) {
                        stringResource(R.string.ynfc_log_enabled)
                    } else {
                        stringResource(R.string.ynfc_log_disabled)
                    },
                    checked = logsEnabled,
                    onCheckedChange = { enabled ->
                        logsEnabled = enabled
                        onLoggingChanged(enabled)
                        if (!enabled) logLines.clear()
                    },
                )
            }
            // Export must remain available when live log display is disabled.
            item {
                YSection(
                    title = stringResource(R.string.ynfc_export_logs),
                    subtitle = stringResource(R.string.ynfc_diagnostic_logs_desc),
                ) {
                    YPrimaryButton(
                        text = if (diagnosticRunning) {
                            stringResource(R.string.ynfc_saving)
                        } else {
                            stringResource(R.string.ynfc_export_logs)
                        },
                        onClick = {
                            if (!diagnosticRunning) {
                                diagnosticRunning = true
                                onExportLogs { diagnosticRunning = false }
                            }
                        },
                        enabled = !diagnosticRunning,
                    )
                }
            }
            // When an APK updates Hook internals but retains the same
            // stable protocol version, LSPosed cannot replace already-loaded
            // classes until com.android.nfc itself is reloaded. This remains
            // optional, explicit, and never reboots the whole phone.
            if (status.currentPid > 0 && status.scopeOk && status.hookInstalled &&
                status.hookPid == status.currentPid && status.hookBuild == BuildConfig.HOOK_BUILD
            ) {
                item {
                    YSection(
                        title = stringResource(R.string.ynfc_hook_manual_reload_title),
                        subtitle = stringResource(R.string.ynfc_hook_manual_reload_desc),
                    ) {
                        YSecondaryButton(
                            text = stringResource(R.string.ynfc_hook_reload_button),
                            onClick = {
                                operationMessage = null
                                onReloadNfcHook { operationMessage = it }
                            },
                            enabled = !status.simulationEnabled,
                        )
                    }
                }
            }
            if (logsEnabled) {
                item {
                    YSection(
                        title = stringResource(R.string.ynfc_diagnostic_logs),
                        subtitle = stringResource(R.string.ynfc_diagnostic_logs_desc),
                    ) {
                        YTabBar(
                            tabs = LogSource.entries.map { source ->
                                YTabSpec(source.name, source.label)
                            },
                            selectedKey = selectedSource.name,
                            onSelected = { tab ->
                                LogSource.entries.firstOrNull { it.name == tab.key }?.let {
                                    selectedSource = it
                                }
                            },
                        )
                        YUiLogSurface(
                            modifier = Modifier.fillMaxWidth()
                                .height((LocalYAppearance.current.rowHeightDp * 6f).dp),
                        ) {
                            LazyColumn(state = logListState, modifier = Modifier.fillMaxSize()) {
                                items(logLines.size) { index ->
                                    YUiLogLine(logLines[index])
                                }
                            }
                            LaunchedEffect(selectedSource, logLines.size) {
                                if (logLines.isNotEmpty()) {
                                    logListState.scrollToItem((logLines.size - 1).coerceAtLeast(0))
                                }
                            }
                        }
                        YHorizontalActions {
                            YSecondaryButton(
                                text = stringResource(R.string.ynfc_clear_logs),
                                onClick = { AppLogger.clear(); logLines.clear() },
                            )
                        }
                    }
                }
            }
        }
    }
}

