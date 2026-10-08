package com.yagay.ysuite.feature.yentrycleaner

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.yagay.ysuite.designsystem.component.YSuiteConfirmDialog
import com.yagay.ysuite.designsystem.component.YSuiteFilterBar
import com.yagay.ysuite.designsystem.component.YSuiteFilterOption
import com.yagay.ysuite.designsystem.component.YSuiteListItem
import com.yagay.ysuite.designsystem.component.YSuiteSearchField
import com.yagay.ysuite.designsystem.component.YSuiteSecondaryButton
import com.yagay.ysuite.designsystem.component.YSuiteStatusBadge
import com.yagay.ysuite.designsystem.component.YSuiteStatusTone
import com.yagay.ysuite.designsystem.theme.YSuiteSpacing

@Composable
internal fun YEntryManagedComponentsScreen(
    state: YEntryCleanerUiState,
    model: YEntryCleanerViewModel,
) {
    var query by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf("ALL") }
    var appFilter by remember { mutableStateOf("ALL") }
    var onlyLocked by remember { mutableStateOf(false) }
    var onlyDisabled by remember { mutableStateOf(false) }
    var expandedPackages by remember { mutableStateOf(emptySet<String>()) }
    var bulkChoice by remember { mutableStateOf<String?>(null) }

    val visible = state.managedComponents.filter { component ->
        (kind == "ALL" || component.kind.name == kind) &&
            (appFilter == "ALL" ||
                (appFilter == "SYSTEM" && component.system) ||
                (appFilter == "USER" && !component.system)) &&
            (!onlyLocked || component.locked) &&
            (!onlyDisabled || !component.enabled) &&
            (query.isBlank() ||
                component.appLabel.contains(query, true) ||
                component.packageName.contains(query, true) ||
                component.label.contains(query, true) ||
                component.className.contains(query, true))
    }
    val groups = visible.groupBy { it.packageName }.entries.sortedWith(
        compareBy<Map.Entry<String, List<YEntryManagedComponent>>> {
            it.value.firstOrNull()?.appLabel?.lowercase().orEmpty()
        }.thenBy { it.key },
    )

    bulkChoice?.let { choice ->
        YSuiteConfirmDialog(
            title = stringResource(R.string.yentry_root_bulk_confirm),
            message = stringResource(R.string.yentry_root_bulk_message),
            confirmText = stringResource(R.string.yentry_root_confirm),
            dismissText = stringResource(R.string.yentry_root_cancel),
            onConfirm = {
                bulkChoice = null
                when (choice) {
                    "ENABLE" -> model.bulkManagedComponents(visible, true)
                    "DISABLE" -> model.bulkManagedComponents(visible, false)
                    "INVERT" -> model.invertManagedComponents(visible)
                }
            },
            onDismiss = { bulkChoice = null },
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(YSuiteSpacing.Small),
    ) {
        item(key = "header") {
            Column(
                modifier = Modifier.padding(YSuiteSpacing.Medium),
                verticalArrangement = Arrangement.spacedBy(YSuiteSpacing.Small),
            ) {
                YSuiteSecondaryButton(
                    text = stringResource(R.string.yentry_root_back),
                    onClick = model::closeManagedComponents,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(YSuiteSpacing.Small)) {
                    YSuiteSecondaryButton(
                        text = stringResource(R.string.yentry_root_refresh),
                        onClick = model::refreshManagedComponents,
                    )
                    if (!state.recoveryRequested) {
                        YSuiteSecondaryButton(
                            text = stringResource(R.string.yentry_root_reconcile),
                            onClick = model::requestRecovery,
                        )
                    }
                }
                if (state.recoveryRequested) {
                    YSuiteStatusBadge(
                        text = stringResource(R.string.yentry_root_reconcile_running),
                        tone = YSuiteStatusTone.Warning,
                    )
                }
                state.recoveryStatus?.let { recovery ->
                    YSuiteListItem(
                        title = stringResource(R.string.yentry_root_reconcile_result),
                        subtitle = stringResource(
                            R.string.yentry_root_reconcile_summary,
                            recovery.persisted,
                            recovery.repaired,
                            recovery.failed,
                            recovery.missing,
                        ) + " · " + recovery.reason,
                    )
                }
                YSuiteSearchField(
                    value = query,
                    onValueChange = { query = it },
                    label = stringResource(R.string.yentry_root_search),
                )
                YSuiteFilterBar(
                    options = listOf(
                        YSuiteFilterOption("ALL", stringResource(R.string.yentry_root_all)),
                        YSuiteFilterOption("Activity", stringResource(R.string.yentry_root_activities)),
                        YSuiteFilterOption("Service", stringResource(R.string.yentry_root_services)),
                        YSuiteFilterOption("Receiver", stringResource(R.string.yentry_root_receivers)),
                        YSuiteFilterOption("Provider", stringResource(R.string.yentry_root_providers)),
                    ),
                    selectedId = kind,
                    onSelected = { kind = it },
                )
                YSuiteFilterBar(
                    options = listOf(
                        YSuiteFilterOption("ALL", stringResource(R.string.yentry_root_app_all)),
                        YSuiteFilterOption("USER", stringResource(R.string.yentry_root_user)),
                        YSuiteFilterOption("SYSTEM", stringResource(R.string.yentry_root_system)),
                    ),
                    selectedId = appFilter,
                    onSelected = { appFilter = it },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(YSuiteSpacing.Small)) {
                    YSuiteSecondaryButton(
                        text = stringResource(
                            if (onlyLocked) R.string.yentry_root_show_all
                            else R.string.yentry_root_locked,
                        ),
                        onClick = { onlyLocked = !onlyLocked },
                    )
                    YSuiteSecondaryButton(
                        text = stringResource(
                            if (onlyDisabled) R.string.yentry_root_show_all
                            else R.string.yentry_root_disabled_only,
                        ),
                        onClick = { onlyDisabled = !onlyDisabled },
                    )
                }
                YSuiteListItem(
                    title = stringResource(
                        R.string.yentry_root_summary,
                        groups.size,
                        visible.size,
                    ),
                )
                if (state.managedLoading) {
                    YSuiteStatusBadge(
                        text = stringResource(R.string.yentry_root_loading),
                        tone = YSuiteStatusTone.Neutral,
                    )
                }
                state.managedError?.let {
                    YSuiteStatusBadge(
                        text = stringResource(R.string.yentry_root_error) + ": " + it,
                        tone = YSuiteStatusTone.Error,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(YSuiteSpacing.Small)) {
                    YSuiteSecondaryButton(
                        text = stringResource(R.string.yentry_root_disable),
                        onClick = { bulkChoice = "DISABLE" },
                    )
                    YSuiteSecondaryButton(
                        text = stringResource(R.string.yentry_root_enable),
                        onClick = { bulkChoice = "ENABLE" },
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(YSuiteSpacing.Small)) {
                    YSuiteSecondaryButton(
                        text = stringResource(R.string.yentry_root_invert),
                        onClick = { bulkChoice = "INVERT" },
                    )
                    YSuiteSecondaryButton(
                        text = stringResource(R.string.yentry_root_lock_all),
                        onClick = { model.lockManagedComponents(visible, true) },
                    )
                    YSuiteSecondaryButton(
                        text = stringResource(R.string.yentry_root_unlock_all),
                        onClick = { model.lockManagedComponents(visible, false) },
                    )
                }
            }
        }

        if (visible.isEmpty() && !state.managedLoading && state.managedError == null) {
            item(key = "empty") {
                YSuiteListItem(
                    title = stringResource(R.string.yentry_root_empty),
                )
            }
        }

        groups.forEach { group ->
            val expanded = query.isNotBlank() || group.key in expandedPackages
            item(key = "app:" + group.key) {
                YSuiteListItem(
                    title = group.value.first().appLabel,
                    subtitle = group.key + " · " + group.value.size,
                    modifier = Modifier.clickable {
                        expandedPackages =
                            if (group.key in expandedPackages) expandedPackages - group.key
                            else expandedPackages + group.key
                    },
                )
            }
            if (expanded) {
                items(
                    items = group.value,
                    key = { it.kind.name + ":" + it.id },
                ) { component ->
                    Column(modifier = Modifier.padding(YSuiteSpacing.Medium)) {
                        YSuiteListItem(
                            title = component.label,
                            subtitle = component.kind.name + " · " + component.className,
                            trailing = {
                                if (component.blocked || component.locked || !component.enabled) {
                                    YSuiteStatusBadge(
                                        text = stringResource(
                                            when {
                                                component.blocked -> R.string.yentry_root_protected
                                                component.locked -> R.string.yentry_root_locked
                                                else -> R.string.yentry_root_disabled
                                            },
                                        ),
                                        tone = YSuiteStatusTone.Warning,
                                    )
                                }
                            },
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(YSuiteSpacing.Small),
                        ) {
                            if (!component.blocked && !state.busy) {
                                YSuiteSecondaryButton(
                                    text = stringResource(
                                        if (component.enabled) R.string.yentry_root_disable
                                        else R.string.yentry_root_enable,
                                    ),
                                    onClick = {
                                        model.changeManagedComponent(
                                            component, !component.enabled,
                                        )
                                    },
                                )
                            }
                            YSuiteSecondaryButton(
                                text = stringResource(
                                    if (component.locked) R.string.yentry_root_unlock
                                    else R.string.yentry_root_lock,
                                ),
                                onClick = {
                                    model.setManagedLock(component, !component.locked)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
