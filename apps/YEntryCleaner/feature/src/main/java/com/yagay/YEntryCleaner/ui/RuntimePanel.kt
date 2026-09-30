package com.yagay.YEntryCleaner.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yagay.YEntryCleaner.R
import com.yagay.yui.YFeatureCard
import com.yagay.yui.YStatusRow
import com.yagay.yui.YStatusTone

@Composable
internal fun RuntimePanel(state: MainState, vm: MainViewModel, showUpdateTools: Boolean = true) {
    val updating by vm.updating.collectAsState()
    val result by vm.updateMessage.collectAsState()
    var confirmReset by remember { mutableStateOf(false) }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(stringResource(R.string.runtime_reset_title)) },
            text = { Text(stringResource(R.string.runtime_reset_help)) },
            confirmButton = {
                TextButton(onClick = { confirmReset = false; vm.resolveRecovery(false) }) {
                    Text(stringResource(R.string.runtime_confirm_reset))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }

    YFeatureCard(
        title = stringResource(R.string.capability_title),
        subtitle = if (!state.runtime.ready) state.runtime.message else null,
        detail = if (!state.runtime.ready && showUpdateTools) stringResource(R.string.runtime_validation_failed_help) else null
    ) {
        runtimeCapabilities(state.module, state.runtime).forEach { capability ->
            val name = stringResource(
                when (capability.capability) {
                    RuntimeCapability.FILTERING -> R.string.capability_filtering
                    RuntimeCapability.ORDERING -> R.string.capability_ordering
                    RuntimeCapability.PACKAGE_VISIBILITY -> R.string.capability_visibility
                }
            )
            val status = when (capability.state) {
                CapabilityState.OBSERVED -> stringResource(R.string.capability_observed, capability.hits)
                CapabilityState.LOADED_UNOBSERVED -> stringResource(R.string.capability_loaded_unobserved)
                CapabilityState.MISSING_SCOPE -> stringResource(R.string.capability_missing_scope)
                CapabilityState.OUTDATED -> stringResource(R.string.capability_outdated)
                CapabilityState.DISCONNECTED -> stringResource(R.string.capability_disconnected)
                CapabilityState.NOT_READY -> stringResource(R.string.capability_not_ready)
            }
            val tone = when (capability.state) {
                CapabilityState.OBSERVED -> YStatusTone.Good
                CapabilityState.LOADED_UNOBSERVED -> YStatusTone.Neutral
                CapabilityState.MISSING_SCOPE,
                CapabilityState.NOT_READY -> YStatusTone.Warning
                CapabilityState.OUTDATED,
                CapabilityState.DISCONNECTED -> YStatusTone.Error
            }
            YStatusRow(label = name, value = status, tone = tone)
        }

        if (state.runtime.needsDecision) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.resolveRecovery(true) }, enabled = !state.runtime.recoveryCorrupt) {
                    Text(stringResource(R.string.runtime_restore_remote))
                }
                OutlinedButton(onClick = { confirmReset = true }) {
                    Text(stringResource(R.string.runtime_reset_pause))
                }
            }
        }

        if (showUpdateTools) {
            OutlinedButton(
                onClick = vm::applyModuleUpdate,
                enabled = state.module.connected && !updating,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(if (updating) R.string.runtime_checking_update else R.string.runtime_apply_update))
            }
            result?.let {
                YStatusRow(
                    label = stringResource(R.string.dashboard_sync_status),
                    value = it,
                    tone = if (state.runtime.ready) YStatusTone.Good else YStatusTone.Warning
                )
            }
        }
    }
}
