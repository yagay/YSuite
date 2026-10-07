package com.yagay.ysuite.feature.ynfc

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yagay.YNFC.CardModel
import com.yagay.ysuite.designsystem.component.YSuiteListItem
import com.yagay.ysuite.designsystem.component.YSuitePrimaryButton
import com.yagay.ysuite.designsystem.component.YSuiteSecondaryButton
import com.yagay.ysuite.designsystem.component.YSuiteSection
import com.yagay.ysuite.designsystem.component.YSuiteStatusBadge
import com.yagay.ysuite.designsystem.component.YSuiteStatusTone
import com.yagay.ysuite.designsystem.theme.YSuiteSpacing
import com.yagay.ysuite.platform.api.CapabilityStatus
import com.yagay.ysuite.productui.featurelayout.YNfcWorkspace
import com.yagay.ysuite.ui.YSuiteHostNavigationButton

@Composable
fun YNfcFeatureScreen(environment: YNfcEnvironment) {
    val model: YNfcViewModel = viewModel(factory = YNfcViewModel.Factory(environment))
    val state by model.state.collectAsStateWithLifecycle()
    val activity = LocalContext.current as? Activity
    val reader = remember(activity) { activity?.let(::YNfcReaderController) }
    var reading by remember { mutableStateOf(false) }
    var scanned by remember { mutableStateOf<CardModel?>(null) }
    DisposableEffect(reader) { onDispose { reader?.disable() } }

    YNfcWorkspace(
        title = stringResource(R.string.ynfc_title),
        navigationIcon = { YSuiteHostNavigationButton() },
        status = {
            Row(
                modifier = Modifier.padding(YSuiteSpacing.Medium),
                horizontalArrangement = Arrangement.spacedBy(YSuiteSpacing.Small),
            ) {
                RuntimeStatusBadge(
                    state.rootStatus == CapabilityStatus.Available,
                    stringResource(R.string.ynfc_root_ready),
                    stringResource(R.string.ynfc_root_missing),
                )
                RuntimeStatusBadge(
                    state.runtime.hookInstalled,
                    stringResource(R.string.ynfc_hook_ready),
                    stringResource(R.string.ynfc_hook_missing),
                )
            }
        },
        modes = { RuntimePane(state.runtime) },
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(YSuiteSpacing.Medium),
        ) {
            item {
                YSuiteSection(
                    title = stringResource(R.string.ynfc_read_card),
                    modifier = Modifier.padding(YSuiteSpacing.Medium),
                ) {
                    scanned?.let {
                        YSuiteListItem(title = it.name, subtitle = cardSummary(it))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(YSuiteSpacing.Small)) {
                        YSuitePrimaryButton(
                            text = stringResource(if (reading) R.string.ynfc_stop_read else R.string.ynfc_start_read),
                            onClick = {
                                if (reading) {
                                    reader?.disable()
                                    reading = false
                                } else {
                                    reader?.enable {
                                        scanned = it
                                        reading = false
                                        reader.disable()
                                    }?.onSuccess { reading = true }
                                }
                            },
                        )
                        scanned?.let { card ->
                            YSuiteSecondaryButton(
                                text = stringResource(R.string.ynfc_save_card),
                                onClick = { model.saveCard(card) },
                            )
                        }
                    }
                }
            }
            item {
                YSuiteListItem(
                    title = stringResource(R.string.ynfc_saved_cards),
                    subtitle = state.cards.size.toString(),
                    modifier = Modifier.padding(horizontal = YSuiteSpacing.Medium),
                )
            }
            if (state.cards.isEmpty()) {
                item {
                    YSuiteListItem(
                        title = stringResource(R.string.ynfc_no_cards),
                        modifier = Modifier.padding(horizontal = YSuiteSpacing.Medium),
                    )
                }
            }
            items(state.cards, key = { it.uid }) { card ->
                val active = state.runtime.simulationEnabled && state.runtime.selectedUid?.equals(card.uid, true) == true
                YSuiteSection(title = card.name, modifier = Modifier.padding(horizontal = YSuiteSpacing.Medium)) {
                    YSuiteListItem(
                        title = card.uid,
                        subtitle = cardSummary(card),
                        trailing = if (active) {
                            { YSuiteStatusBadge(stringResource(R.string.ynfc_active), YSuiteStatusTone.Positive) }
                        } else null,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(YSuiteSpacing.Small)) {
                        YSuitePrimaryButton(
                            text = stringResource(R.string.ynfc_apply),
                            onClick = { if (!state.busy && !active) model.apply(card) },
                        )
                        if (active) {
                            YSuiteSecondaryButton(
                                text = stringResource(R.string.ynfc_restore),
                                onClick = model::stop,
                            )
                        }
                        YSuiteSecondaryButton(
                            text = stringResource(R.string.ynfc_delete),
                            onClick = { if (!active) model.delete(card) },
                        )
                    }
                }
            }
            item {
                YSuiteSection(
                    title = stringResource(R.string.ynfc_diagnostics),
                    modifier = Modifier.padding(horizontal = YSuiteSpacing.Medium),
                ) {
                    state.statusToken?.let {
                        YSuiteStatusBadge(
                            text = messageText(it),
                            tone = if (it in setOf("card_saved", "apply_success", "stop_success")) {
                                YSuiteStatusTone.Positive
                            } else {
                                YSuiteStatusTone.Warning
                            },
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(YSuiteSpacing.Small)) {
                        YSuiteSecondaryButton(
                            text = stringResource(R.string.ynfc_collect_diagnostics),
                            onClick = model::collectDiagnostics,
                        )
                        if (state.diagnostics.isNotBlank()) {
                            YSuiteSecondaryButton(
                                text = stringResource(R.string.ynfc_export),
                                onClick = model::exportDiagnostics,
                            )
                        }
                    }
                    if (state.diagnostics.isNotBlank()) {
                        YSuiteListItem(
                            title = stringResource(R.string.ynfc_diagnostic_snapshot),
                            subtitle = state.diagnostics.take(4_000),
                        )
                    }
                    if (state.exportUri != null) {
                        YSuiteStatusBadge(
                            text = stringResource(R.string.ynfc_exported),
                            tone = YSuiteStatusTone.Positive,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RuntimePane(runtime: YNfcRuntimeSnapshot) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            YSuiteListItem(
                title = stringResource(R.string.ynfc_runtime),
                subtitle = stringResource(
                    R.string.ynfc_runtime_summary,
                    runtime.currentPid,
                    runtime.hookBuild,
                    runtime.commandStatus,
                ),
            )
        }
        item {
            YSuiteListItem(
                title = stringResource(R.string.ynfc_effective_state),
                subtitle = runtime.effectiveState + " · " + runtime.verification,
            )
        }
        item {
            YSuiteListItem(
                title = stringResource(R.string.ynfc_rf_state),
                subtitle = runtime.rfStatus + (runtime.rfUid?.let { " · " + it } ?: ""),
            )
        }
    }
}

@Composable
private fun RuntimeStatusBadge(ok: Boolean, okText: String, badText: String) {
    YSuiteStatusBadge(
        text = if (ok) okText else badText,
        tone = if (ok) YSuiteStatusTone.Positive else YSuiteStatusTone.Warning,
    )
}

@Composable
private fun cardSummary(card: CardModel): String =
    stringResource(R.string.ynfc_card_summary, card.uid, card.sak, card.atqa)

@Composable
private fun messageText(token: String): String = when (token) {
    "card_saved" -> stringResource(R.string.ynfc_card_saved)
    "applying" -> stringResource(R.string.ynfc_applying)
    "scope_failed" -> stringResource(R.string.ynfc_scope_failed)
    "apply_success" -> stringResource(R.string.ynfc_apply_success)
    "hook_not_ready" -> stringResource(R.string.ynfc_hook_not_ready)
    "apply_failed" -> stringResource(R.string.ynfc_apply_failed)
    "waiting_rf" -> stringResource(R.string.ynfc_waiting_rf)
    "stopping" -> stringResource(R.string.ynfc_stopping)
    "stop_success" -> stringResource(R.string.ynfc_stop_success)
    "stop_unconfirmed" -> stringResource(R.string.ynfc_stop_unconfirmed)
    else -> token
}
