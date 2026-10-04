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
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
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
import com.yagay.yui.YHorizontalActions
import com.yagay.yui.YSection
import com.yagay.yui.YEmptyMessage
import com.yagay.yui.YManagerScaffold
import com.yagay.yui.YPageList
import com.yagay.yui.YSectionHeader
import com.yagay.yui.YPrimaryButton
import com.yagay.yui.YSecondaryButton

/** Stateless Activity boundary for the complete NFC screen; operation state stays screen-local. */
@Composable
fun NfcAppScreen(
    cards: List<CardModel>,
    scannedCard: CardModel?,
    readModeEnabled: Boolean,
    initialLogsEnabled: Boolean,
    diagnosticsCollector: DiagnosticsCollector,
    readRuntimeStatus: () -> RuntimeStatus,
    buildStatusSummary: (RuntimeStatus) -> String,
    onLoggingChanged: (Boolean) -> Unit,
    onStartRead: () -> Unit,
    onStopRead: () -> Unit,
    onSaveCard: (CardModel) -> Unit,
    onClearScanned: () -> Unit,
    onSimulate: (CardModel, (String) -> Unit) -> Unit,
    onStopSimulation: ((String) -> Unit) -> Unit,
    onDeleteCard: (CardModel, Boolean) -> Unit,
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
                YSection(
                    title = stringResource(R.string.ynfc_log_display),
                    subtitle = if (logsEnabled) {
                        stringResource(R.string.ynfc_log_enabled)
                    } else {
                        stringResource(R.string.ynfc_log_disabled)
                    },
                    trailing = {
                        Switch(
                            checked = logsEnabled,
                            onCheckedChange = { enabled ->
                                logsEnabled = enabled
                                onLoggingChanged(enabled)
                                if (!enabled) logLines.clear()
                            },
                        )
                    },
                )
            }
            if (logsEnabled) {
                item {
                    YSection(
                        title = stringResource(R.string.ynfc_diagnostic_logs),
                        subtitle = stringResource(R.string.ynfc_diagnostic_logs_desc),
                    ) {
                        ScrollableTabRow(selectedTabIndex = selectedSource.ordinal, edgePadding = 4.dp) {
                            LogSource.entries.forEach { source ->
                                Tab(
                                    selected = selectedSource == source,
                                    onClick = { selectedSource = source },
                                    text = { Text(source.label, fontSize = 11.sp) },
                                )
                            }
                        }
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(340.dp)
                                .background(Color(0xFF050505), RoundedCornerShape(4.dp))
                                .padding(6.dp),
                        ) {
                            LazyColumn(state = logListState, modifier = Modifier.fillMaxSize()) {
                                items(logLines.size) { index ->
                                    val line = logLines[index]
                                    Text(
                                        line,
                                        color = logLineColor(line),
                                        fontSize = 9.sp,
                                        lineHeight = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                    )
                                }
                            }
                            LaunchedEffect(selectedSource, logLines.size) {
                                if (logLines.isNotEmpty()) {
                                    logListState.scrollToItem((logLines.size - 1).coerceAtLeast(0))
                                }
                            }
                        }
                        YHorizontalActions {
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
                                modifier = Modifier.weight(1f),
                            )
                            YSecondaryButton(
                                text = stringResource(R.string.ynfc_clear_logs),
                                onClick = { AppLogger.clear(); logLines.clear() },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun logLineColor(line: String): Color = when {
    line.contains("SUCCESS") || line.contains("APPLIED") || line.contains("ACCEPTED") ||
        line.contains("READY") -> Color.Cyan
    line.contains("FAILED") || line.contains("ERROR") || line.contains("STALE") ||
        line.contains("FATAL") -> Color.Red
    line.contains("WAITING") || line.contains("IDLE") || line.contains("RUNNING") ||
        line.contains("TRIGGERED") -> Color.Yellow
    line.contains("RF") || line.contains("NFCID1") || line.contains("NfcUIDSim") ||
        line.contains("COMMAND") -> Color.Green
    else -> Color(0xFFD4D4D4)
}
