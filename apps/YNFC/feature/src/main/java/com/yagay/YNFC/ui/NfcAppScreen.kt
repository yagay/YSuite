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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yagay.YNFC.AppLogger
import com.yagay.YNFC.BuildConfig
import com.yagay.YNFC.CardModel
import com.yagay.YNFC.DiagnosticsCollector
import com.yagay.YNFC.LogSource
import com.yagay.YNFC.RuntimeStatus
import com.yagay.YNFC.RuntimeStatusViewModel
import com.yagay.YNFC.RuntimeText
import com.yagay.yui.YActionRow
import com.yagay.yui.YFeatureCard
import com.yagay.yui.YFeatureEmpty
import com.yagay.yui.YFeatureList
import com.yagay.yui.YFeatureScaffold
import com.yagay.yui.YFeatureSectionHeader
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

    // Logs remain independent from runtime status observation and only poll while visible.
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

    YFeatureScaffold(
        title = "YNFC ${BuildConfig.VERSION_NAME}",
        subtitle = "NFC 门禁卡与 HCE 状态",
    ) { padding ->
        YFeatureList(padding = padding) {
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
                YFeatureSectionHeader(
                    title = "已保存卡片 (${cards.size})",
                    subtitle = "点击卡片可查看 UID / SAK / ATQA，并启动或停止模拟。",
                )
            }
            if (cards.isEmpty()) {
                item {
                    YFeatureEmpty("暂无保存卡片。进入读卡模式后贴卡，确认信息无误再保存。")
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
                            operationMessage = "正在通过 NFC 进程应用 UID ${card.uid}..."
                            onSimulate(card) { operationMessage = it }
                        },
                        onStop = {
                            operationMessage = "正在恢复原厂 RF..."
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
                YFeatureCard(
                    title = "日志显示",
                    subtitle = if (logsEnabled) "已开启 · 正在抓取日志" else "已关闭 · 不抓取日志，减少性能影响",
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
                    YFeatureCard(
                        title = "诊断日志",
                        subtitle = "日志区域保留高对比度控制台配色，页面结构仍由 YUI 管理。",
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
                        YActionRow {
                            YPrimaryButton(
                                text = if (diagnosticRunning) "保存中" else "导出日志",
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
                                text = "清空日志",
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
