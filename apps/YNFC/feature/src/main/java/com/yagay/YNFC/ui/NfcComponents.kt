package com.yagay.YNFC.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.yagay.YNFC.BuildConfig
import com.yagay.YNFC.CardModel
import com.yagay.YNFC.R
import com.yagay.YNFC.RuntimeStatus
import com.yagay.YNFC.StatusTone
import com.yagay.yui.LocalYAppearance
import com.yagay.yui.YHorizontalActions
import com.yagay.yui.YSection
import com.yagay.yui.YPrimaryButton
import com.yagay.yui.YSecondaryButton
import com.yagay.yui.YStatusLine
import com.yagay.yui.YStatusTone
import com.yagay.yui.YDivider

@Composable
fun RuntimeStatusPanel(status: RuntimeStatus, operationMessage: String?, readModeEnabled: Boolean) {
    val hookReady = status.currentPid > 0 && status.scopeOk && status.hookInstalled && status.hookBuild == BuildConfig.HOOK_BUILD
    val commandInFlight = status.commandStatus in setOf("PENDING", "RUNNING", "TRIGGERED", "RESTART_REQUIRED") &&
        status.commandGeneration != status.handledGeneration
    val semanticBusy = status.operationState in setOf("APPLYING", "STOPPING", "RESETTING_CONTROLLER")
    val commandFailed = status.commandStatus in setOf("FAILED", "TRIGGER_FAILED", "OBSERVER_FAILED") || status.operationState == "FAILED"
    val commandTone = if (commandFailed) StatusTone.ERROR else StatusTone.IDLE
    val unknown = stringResource(R.string.ynfc_unknown)
    val commandDisplay = when {
        status.operationState == "RESETTING_CONTROLLER" -> stringResource(
            R.string.ynfc_cmd_resetting,
            status.commandGeneration,
            status.commandPid,
        )
        commandInFlight || semanticBusy -> stringResource(
            R.string.ynfc_cmd_in_flight,
            status.commandAction.ifBlank { unknown },
            status.operationState,
            status.commandGeneration,
            status.consumedGeneration,
            status.handledGeneration,
            status.commandPid,
        )
        status.commandStatus == "SUCCESS" && status.commandGeneration == status.handledGeneration -> stringResource(
            R.string.ynfc_cmd_success,
            status.commandAction.ifBlank { unknown },
            status.commandGeneration,
            status.commandPid,
        )
        commandFailed -> stringResource(
            R.string.ynfc_cmd_failed,
            status.commandAction.ifBlank { unknown },
            status.commandStatus,
            status.commandGeneration,
            status.commandPid,
        )
        else -> stringResource(R.string.ynfc_cmd_idle)
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
                simulationDetail = stringResource(R.string.ynfc_sim_active, status.selectedUid ?: "-")
            }
            commandFailed -> {
                simulationTone = StatusTone.ERROR
                simulationDetail = stringResource(R.string.ynfc_sim_failed, status.commandStatus)
            }
            status.rfStatus.startsWith("STALE") -> {
                simulationTone = StatusTone.WARNING
                simulationDetail = stringResource(R.string.ynfc_sim_stale)
            }
            else -> {
                simulationTone = StatusTone.BUSY
                simulationDetail = stringResource(
                    R.string.ynfc_sim_applying,
                    status.operationState,
                    status.selectedUid ?: "-",
                )
            }
        }
    } else {
        when {
            stockVerified -> {
                simulationTone = StatusTone.STOCK
                simulationDetail = if (status.rfVerification == "PROCESS_RESTART") {
                    stringResource(R.string.ynfc_sim_stopped_lifecycle)
                } else {
                    stringResource(R.string.ynfc_sim_stopped_verified)
                }
            }
            status.operationState in setOf("STOPPING", "RESETTING_CONTROLLER") || (status.commandAction == "STOP" && commandInFlight) -> {
                simulationTone = StatusTone.BUSY
                simulationDetail = if (status.operationState == "RESETTING_CONTROLLER") {
                    stringResource(R.string.ynfc_sim_resetting)
                } else {
                    stringResource(R.string.ynfc_sim_stopping)
                }
            }
            commandFailed -> {
                simulationTone = StatusTone.ERROR
                simulationDetail = stringResource(
                    R.string.ynfc_stop_failed,
                    status.commandDetail ?: status.rfError ?: "unknown",
                )
            }
            status.rfStatus.startsWith("STALE") -> {
                simulationTone = StatusTone.WARNING
                simulationDetail = stringResource(R.string.ynfc_stale_disabled)
            }
            else -> {
                simulationTone = StatusTone.IDLE
                simulationDetail = stringResource(R.string.ynfc_sim_disabled)
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

    YSection(
        title = stringResource(R.string.ynfc_runtime_status),
        subtitle = stringResource(R.string.ynfc_runtime_status_desc),
    ) {
        YStatusLine(
            label = stringResource(R.string.ynfc_nfc_hook),
            value = "pid=${status.currentPid} · runtimePid=${status.runtimePid} · hookBuild=${status.hookBuild}/${BuildConfig.HOOK_BUILD} · hookPid=${status.hookPid}",
            tone = if (hookReady) YStatusTone.Good else YStatusTone.Error,
        )
        YStatusLine(stringResource(R.string.ynfc_simulation_status), simulationDetail, simulationTone.toYUiTone())
        YStatusLine(stringResource(R.string.ynfc_command), commandDisplay, commandTone.toYUiTone())
        YStatusLine(
            stringResource(R.string.ynfc_rf_status),
            "effective=${status.effectiveState} · op=${status.operationState} · confidence=${status.verificationConfidence} · accepted=${status.rfAccepted} · uid=${status.rfUid ?: "-"}",
            rfTone.toYUiTone(),
        )
        Text(
            stringResource(
                R.string.ynfc_low_level_diag,
                status.rfStatus,
                status.rfGeneration,
                status.rfPid,
                status.rfNativeResult ?: "-",
                status.rfNativeResultType ?: "-",
                status.rfVerification ?: "-",
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(stringResource(R.string.ynfc_trigger_method), style = MaterialTheme.typography.bodySmall)
        Text(
            stringResource(
                R.string.ynfc_read_mode_status,
                if (readModeEnabled) stringResource(R.string.ynfc_on) else stringResource(R.string.ynfc_off),
            ),
            style = MaterialTheme.typography.bodySmall,
        )
        operationMessage?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        status.commandDetail?.takeIf { it.isNotBlank() }?.let {
            Text(
                stringResource(R.string.ynfc_last_command, it),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        status.rfError?.takeIf { it.isNotBlank() }?.let {
            Text(
                stringResource(R.string.ynfc_rf_error, it),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
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
    YSection(
        title = stringResource(R.string.ynfc_read_card),
        subtitle = when {
            simulationActive -> stringResource(R.string.ynfc_read_disabled_during_sim)
            readMode -> stringResource(R.string.ynfc_read_enabled)
            card == null -> stringResource(R.string.ynfc_read_default)
            else -> stringResource(R.string.ynfc_read_success_desc)
        },
    ) {
        when {
            simulationActive -> {
                YPrimaryButton(
                    text = stringResource(R.string.ynfc_simulating_read_off),
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            readMode -> {
                YSecondaryButton(stringResource(R.string.ynfc_exit_read), onStopRead, Modifier.fillMaxWidth())
            }
            card == null -> {
                YPrimaryButton(stringResource(R.string.ynfc_enter_read), onStartRead, Modifier.fillMaxWidth())
            }
            else -> {
                YStatusLine(
                    stringResource(R.string.ynfc_read_status),
                    stringResource(R.string.ynfc_read_success),
                    YStatusTone.Good,
                )
                CardDetails(card)
                YHorizontalActions {
                    YPrimaryButton(stringResource(R.string.ynfc_save_card), { onSave(card) }, Modifier.weight(1f))
                    YSecondaryButton(
                        stringResource(R.string.ynfc_read_again),
                        { onClear(); onStartRead() },
                        Modifier.weight(1f),
                    )
                }
                YSecondaryButton(
                    text = stringResource(R.string.ynfc_close_read_result),
                    onClick = onClear,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
fun CardDetails(card: CardModel) {
    Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy((LocalYAppearance.current.effectiveGapDp / 4f).dp)) {
        Text(stringResource(R.string.ynfc_card_name, card.name), style = MaterialTheme.typography.bodyMedium)
        Text(
            stringResource(R.string.ynfc_card_uid, card.uid),
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            stringResource(R.string.ynfc_card_sak, card.sak),
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            stringResource(R.string.ynfc_card_atqa, card.atqa),
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            stringResource(
                R.string.ynfc_uid_length,
                card.uid.replace(Regex("[^0-9A-Fa-f]"), "").length / 2,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            stringResource(R.string.ynfc_card_type),
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
    YSection(
        title = card.name,
        subtitle = stringResource(R.string.ynfc_card_uid_subtitle, card.uid),
        detail = if (expanded) {
            stringResource(R.string.ynfc_collapse_details)
        } else {
            stringResource(R.string.ynfc_view_details)
        },
        modifier = Modifier.clickable { onToggleDetails() },
        trailing = {
            if (isActive) {
                YSecondaryButton(stringResource(R.string.ynfc_stop), onStop)
            } else {
                YPrimaryButton(stringResource(R.string.ynfc_simulate), onSimulate)
            }
            Spacer(Modifier.width((LocalYAppearance.current.effectiveGapDp / 3f).dp))
            YSecondaryButton(
                text = stringResource(R.string.ynfc_delete),
                onClick = onDelete,
            )
        },
    ) {
        if (expanded) {
            YDivider()
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
