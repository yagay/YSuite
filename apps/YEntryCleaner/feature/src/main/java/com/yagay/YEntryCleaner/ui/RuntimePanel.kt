package com.yagay.YEntryCleaner.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.yagay.YEntryCleaner.R
import com.yagay.yui.YActionSpec
import com.yagay.yui.YActionStyle
import com.yagay.yui.YActionGroup
import com.yagay.yui.YConfirmDialog
import com.yagay.yui.YSectionHeader
import com.yagay.yui.YStatusItem
import com.yagay.yui.YStatusTone

@Composable
internal fun RuntimePanel(state: MainState, vm: MainViewModel, showUpdateTools: Boolean = true) {
    val updating by vm.updating.collectAsState()
    val result by vm.updateMessage.collectAsState()
    var confirmReset by remember { mutableStateOf(false) }

    if (confirmReset) {
        YConfirmDialog(
            title = stringResource(R.string.runtime_reset_title),
            message = stringResource(R.string.runtime_reset_help),
            confirmLabel = stringResource(R.string.runtime_confirm_reset),
            dismissLabel = stringResource(R.string.common_cancel),
            onConfirm = {
                confirmReset = false
                vm.resolveRecovery(false)
            },
            onDismiss = { confirmReset = false },
            dangerous = true,
        )
    }

    YSectionHeader(
        title = stringResource(R.string.capability_title),
        subtitle = if (!state.runtime.ready) state.runtime.message else null,
    )
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
        YStatusItem(title = name, value = status, tone = tone)
    }

    if (state.runtime.needsDecision) {
        YActionGroup(
            listOf(
                YActionSpec(
                    label = stringResource(R.string.runtime_restore_remote),
                    enabled = !state.runtime.recoveryCorrupt,
                    style = YActionStyle.PRIMARY,
                    onClick = { vm.resolveRecovery(true) },
                ),
                YActionSpec(
                    label = stringResource(R.string.runtime_reset_pause),
                    style = YActionStyle.DANGER,
                    onClick = { confirmReset = true },
                ),
            )
        )
    }

    if (showUpdateTools) {
        YActionGroup(
            listOf(
                YActionSpec(
                    label = stringResource(if (updating) R.string.runtime_checking_update else R.string.runtime_apply_update),
                    enabled = state.module.connected && !updating,
                    onClick = vm::applyModuleUpdate,
                ),
            )
        )
        result?.let {
            YStatusItem(
                title = stringResource(R.string.dashboard_sync_status),
                value = it,
                tone = if (state.runtime.ready) YStatusTone.Good else YStatusTone.Warning,
            )
        }
    }
}
