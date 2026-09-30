package com.yagay.YNFC.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.yagay.YNFC.BuildConfig
import com.yagay.YNFC.CardModel
import com.yagay.YNFC.RuntimeStatus
import com.yagay.YNFC.StatusTone
import com.yagay.yui.YActionRow
import com.yagay.yui.YFeatureCard
import com.yagay.yui.YPrimaryButton
import com.yagay.yui.YSecondaryButton
import com.yagay.yui.YStatusRow
import com.yagay.yui.YStatusTone

@Composable
fun RuntimeStatusPanel(status: RuntimeStatus, operationMessage: String?, readModeEnabled: Boolean) {
    val hookReady = status.currentPid > 0 && status.scopeOk && status.hookInstalled && status.hookBuild == BuildConfig.HOOK_BUILD
    val commandInFlight = status.commandStatus in setOf("PENDING", "RUNNING", "TRIGGERED", "RESTART_REQUIRED") &&
        status.commandGeneration != status.handledGeneration
    val semanticBusy = status.operationState in setOf("APPLYING", "STOPPING", "RESETTING_CONTROLLER")
    val commandFailed = status.commandStatus in setOf("FAILED", "TRIGGER_FAILED", "OBSERVER_FAILED") || status.operationState == "FAILED"
    val commandTone = if (commandFailed) StatusTone.ERROR else StatusTone.IDLE
    val commandDisplay = when {
        status.operationState == "RESETTING_CONTROLLER" ->
            "已提交 STOP · 正在重新初始化 NFC Controller · gen=${status.commandGeneration} · pid=${status.commandPid}"
        commandInFlight || semanticBusy ->
            "已提交 ${status.commandAction.ifBlank { "UNKNOWN" }} · ${status.operationState} · gen=${status.commandGeneration} consumed=${status.consumedGeneration} completed=${status.handledGeneration} · pid=${status.commandPid}"
        status.commandStatus == "SUCCESS" && status.commandGeneration == status.handledGeneration ->
            "当前空闲 · 最近操作 ${status.commandAction.ifBlank { "UNKNOWN" }} 成功 · gen=${status.commandGeneration} · pid=${status.commandPid}"
        commandFailed ->
            "最近操作 ${status.commandAction.ifBlank { "UNKNOWN" }} 失败 · ${status.commandStatus} · gen=${status.commandGeneration} · pid=${status.commandPid}"
        else -> "当前空闲 · 暂无命令"
    }

    val applyVerified = status.simulationEnabled &&
        status.effectiveState == "ACTIVE" && status.verificationConfidence == "VERIFIED" && status.rfAccepted &&
        status.rfUid.equals(status.selectedUid, ignoreCase = true)
    val stockVerified = !status.simulationEnabled &&
        status.effectiveState == "STOCK" && status.verificationConfidence == "VERIFIED" && status.rfAccepted

    val simulationTone: StatusTone
    val simulationDetail: String
    if (status.simulationEnabled) {
        when {
            applyVerified -> {
                simulationTone = StatusTone.OK
                simulationDetail = "模拟已生效 · UID=${status.selectedUid ?: "-"}"
            }
            commandFailed -> {
                simulationTone = StatusTone.ERROR
                simulationDetail = "模拟失败 · ${status.commandStatus}"
            }
            status.rfStatus.startsWith("STALE") -> {
                simulationTone = StatusTone.WARNING
                simulationDetail = "模拟已请求，但 RF 状态来自旧 NFC 进程"
            }
            else -> {
                simulationTone = StatusTone.BUSY
                simulationDetail = "正在应用 · ${status.operationState} · UID=${status.selectedUid ?: "-"}"
            }
        }
    } else {
        when {
            stockVerified -> {
                simulationTone = StatusTone.STOCK
                simulationDetail = if (status.rfVerification == "PROCESS_RESTART")
                    "模拟已停止 · 原厂 RF 已通过 NFC 生命周期恢复"
                else "模拟已停止 · 原厂 RF 已验证恢复"
            }
            status.operationState in setOf("STOPPING", "RESETTING_CONTROLLER") || (status.commandAction == "STOP" && commandInFlight) -> {
                simulationTone = StatusTone.BUSY
                simulationDetail = if (status.operationState == "RESETTING_CONTROLLER")
                    "正在重新初始化 NFC Controller 并恢复原厂 RF" else "正在停止模拟并恢复原厂 RF"
            }
            commandFailed -> {
                simulationTone = StatusTone.ERROR
                simulationDetail = "停止模拟失败 · ${status.commandDetail ?: status.rfError ?: "unknown"}"
            }
            status.rfStatus.startsWith("STALE") -> {
                simulationTone = StatusTone.WARNING
                simulationDetail = "模拟未启用 · 检测到旧 NFC 进程遗留 RF 状态"
            }
            else -> {
                simulationTone = StatusTone.IDLE
                simulationDetail = "未启用模拟"
            }
        }
    }

    val rfTone = when {
        status.effectiveState == "ACTIVE" && status.verificationConfidence == "VERIFIED" && status.rfAccepted -> StatusTone.OK
        status.effectiveState == "STOCK" && status.verificationConfidence == "VERIFIED" && status.rfAccepted -> StatusTone.STOCK
        status.operationState in setOf("APPLYING", "STOPPING", "RESETTING_CONTROLLER") -> StatusTone.WARNING
        status.rfStatus == "IDLE" && status.operationState == "IDLE" -> StatusTone.IDLE
        status.rfStatus.startsWith("STALE") -> StatusTone.WARNING
        status.operationState == "FAILED" || status.rfStatus.contains("FAILED") || !status.rfError.isNullOrBlank() -> StatusTone.ERROR
        else -> StatusTone.WARNING
    }

    YFeatureCard(
        title = "运行状态",
        subtitle = "LSPosed / NFC 进程 / RF 验证",
    ) {
        YStatusRow(
            label = "NFC / Hook",
            value = "pid=${status.currentPid} · runtimePid=${status.runtimePid} · hookBuild=${status.hookBuild}/${BuildConfig.HOOK_BUILD} · hookPid=${status.hookPid}",
            tone = if (hookReady) YStatusTone.Good else YStatusTone.Error,
        )
        YStatusRow("模拟状态", simulationDetail, simulationTone.toYUiTone())
        YStatusRow("命令", commandDisplay, commandTone.toYUiTone())
        YStatusRow(
            "RF 状态",
            "effective=${status.effectiveState} · op=${status.operationState} · confidence=${status.verificationConfidence} · accepted=${status.rfAccepted} · uid=${status.rfUid ?: "-"}",
            rfTone.toYUiTone(),
        )
        Text(
            "底层诊断: ${status.rfStatus} · gen=${status.rfGeneration} · pid=${status.rfPid} · raw=${status.rfNativeResult ?: "-"} (${status.rfNativeResultType ?: "-"}) · verify=${status.rfVerification ?: "-"}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text("触发方式: LSPosed · com.android.nfc 进程内控制", style = MaterialTheme.typography.bodySmall)
        Text("读卡模式: ${if (readModeEnabled) "开启" else "关闭"}", style = MaterialTheme.typography.bodySmall)
        operationMessage?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        status.commandDetail?.takeIf { it.isNotBlank() }?.let {
            Text("最近命令: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        status.rfError?.takeIf { it.isNotBlank() }?.let {
            Text("RF error: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
fun ReadCardPanel(
    card: CardModel?,
    readMode: Boolean,
    simulationActive: Boolean,
    onStartRead: () -> Unit,
    onStopRead: () -> Unit,
    onSave: (CardModel) -> Unit,
    onClear: () -> Unit,
) {
    YFeatureCard(
        title = "读卡模式",
        subtitle = when {
            simulationActive -> "当前正在模拟，读卡保持关闭。"
            readMode -> "读卡已开启，请把门禁卡贴到手机背部。"
            card == null -> "默认不读取卡片，需要添加新卡时再开启。"
            else -> "读取成功，确认卡片信息后保存。"
        },
    ) {
        when {
            simulationActive -> {
                YPrimaryButton(
                    text = "模拟中 · 读卡已关闭",
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            readMode -> {
                YSecondaryButton("退出读卡模式", onStopRead, Modifier.fillMaxWidth())
            }
            card == null -> {
                YPrimaryButton("进入读卡模式", onStartRead, Modifier.fillMaxWidth())
            }
            else -> {
                YStatusRow("读取状态", "读取成功", YStatusTone.Good)
                CardDetails(card)
                YActionRow {
                    YPrimaryButton("保存卡片", { onSave(card) }, Modifier.weight(1f))
                    YSecondaryButton(
                        "重新读取",
                        { onClear(); onStartRead() },
                        Modifier.weight(1f),
                    )
                }
                TextButton(onClick = onClear, modifier = Modifier.fillMaxWidth()) { Text("关闭读取结果") }
            }
        }
    }
}

@Composable
fun CardDetails(card: CardModel) {
    Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(3.dp)) {
        Text("名称: ${card.name}", style = MaterialTheme.typography.bodyMedium)
        Text("UID: ${card.uid}", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
        Text("SAK: ${card.sak}", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
        Text("ATQA: ${card.atqa}", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
        Text(
            "UID 长度: ${card.uid.replace(Regex("[^0-9A-Fa-f]"), "").length / 2} bytes",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "类型: ISO/IEC 14443 Type A",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun CardItem(
    card: CardModel,
    isActive: Boolean,
    expanded: Boolean,
    onToggleDetails: () -> Unit,
    onSimulate: () -> Unit,
    onStop: () -> Unit,
    onDelete: () -> Unit,
) {
    YFeatureCard(
        title = card.name,
        subtitle = "UID ${card.uid}",
        detail = if (expanded) "点击收起详情" else "点击查看详情",
        modifier = Modifier.clickable { onToggleDetails() },
        trailing = {
            if (isActive) {
                YSecondaryButton("停止", onStop)
            } else {
                YPrimaryButton("模拟", onSimulate)
            }
            Spacer(Modifier.width(4.dp))
            TextButton(onClick = onDelete, contentPadding = PaddingValues(horizontal = 6.dp)) { Text("删除") }
        },
    ) {
        if (expanded) {
            HorizontalDivider()
            CardDetails(card)
        }
    }
}

private fun StatusTone.toYUiTone(): YStatusTone = when (this) {
    StatusTone.OK, StatusTone.STOCK -> YStatusTone.Good
    StatusTone.IDLE -> YStatusTone.Neutral
    StatusTone.BUSY, StatusTone.WARNING -> YStatusTone.Warning
    StatusTone.ERROR -> YStatusTone.Error
}
