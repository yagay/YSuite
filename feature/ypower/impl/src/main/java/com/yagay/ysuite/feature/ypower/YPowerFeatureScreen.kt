package com.yagay.ysuite.feature.ypower

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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yagay.ysuite.designsystem.component.YSuiteFilterBar
import com.yagay.ysuite.designsystem.component.YSuiteFilterOption
import com.yagay.ysuite.designsystem.component.YSuiteListItem
import com.yagay.ysuite.designsystem.component.YSuitePrimaryButton
import com.yagay.ysuite.designsystem.component.YSuiteSearchField
import com.yagay.ysuite.designsystem.component.YSuiteSecondaryButton
import com.yagay.ysuite.designsystem.component.YSuiteSection
import com.yagay.ysuite.designsystem.component.YSuiteStatusBadge
import com.yagay.ysuite.designsystem.component.YSuiteStatusTone
import com.yagay.ysuite.designsystem.component.YSuiteSwitchItem
import com.yagay.ysuite.designsystem.theme.YSuiteSpacing
import com.yagay.ysuite.feature.ypower.api.YPowerFinding
import com.yagay.ysuite.feature.ypower.api.YPowerFindingStatus
import com.yagay.ysuite.feature.ypower.api.YPowerProfile
import com.yagay.ysuite.platform.api.CapabilityStatus
import com.yagay.ysuite.productui.featurelayout.YPowerWorkspace
import com.yagay.ysuite.ui.YSuiteFeatureBackHandler
import com.yagay.ysuite.ui.YSuiteHostNavigationButton

@Composable
fun YPowerFeatureScreen(
    environment: YPowerEnvironment,
) {
    val model: YPowerViewModel =
        viewModel(
            factory =
                YPowerViewModel.Factory(
                    environment,
                ),
        )
    val state by
        model.state.collectAsStateWithLifecycle()
    val selected =
        state.apps.firstOrNull {
            it.packageName ==
                state.selectedPackage
        }

    if (state.selectedPackage != null) {
        YSuiteFeatureBackHandler(
            onBack = { model.select(null) },
        )
    }

    YPowerWorkspace(
        title = stringResource(R.string.ypower_title),
        navigationIcon = {
            YSuiteHostNavigationButton()
        },
        searchAndFilters = {
            Column(
                modifier =
                    Modifier.padding(
                        horizontal =
                            YSuiteSpacing.Medium,
                        vertical =
                            YSuiteSpacing.Small,
                    ),
                verticalArrangement =
                    Arrangement.spacedBy(
                        YSuiteSpacing.Small,
                    ),
            ) {
                YSuiteSearchField(
                    value = state.query,
                    onValueChange = model::setQuery,
                    label =
                        stringResource(
                            R.string.ypower_search,
                        ),
                )
                YSuiteFilterBar(
                    options =
                        YPowerAppFilter.entries
                            .map {
                                YSuiteFilterOption(
                                    it.name,
                                    filterLabel(it),
                                )
                            },
                    selectedId =
                        state.filter.name,
                    onSelected = {
                        runCatching {
                            YPowerAppFilter
                                .valueOf(it)
                        }.getOrNull()
                            ?.let(
                                model::setFilter,
                            )
                    },
                )
            }
        },
        navigationPane = {
            RuntimePane(state)
        },
        detailPane =
            selected?.let {
                {
                    AppDetail(
                        appLabel = selected.label,
                        recommended =
                            selected.recommended,
                        state = state,
                        model = model,
                    )
                }
            },
    ) { adaptive ->
        if (
            !adaptive.isExpanded &&
            selected != null
        ) {
            AppDetail(
                appLabel = selected.label,
                recommended =
                    selected.recommended,
                state = state,
                model = model,
            )
        } else {
            AppList(model)
        }
    }
}

@Composable
private fun RuntimePane(
    state: YPowerUiState,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            YSuiteListItem(
                title =
                    stringResource(
                        R.string.ypower_runtime,
                    ),
            )
        }
        item {
            YSuiteListItem(
                title =
                    stringResource(
                        R.string.ypower_root,
                    ),
                trailing = {
                    RuntimeBadge(
                        state.rootStatus,
                    )
                },
            )
        }
        item {
            YSuiteListItem(
                title =
                    stringResource(
                        R.string.ypower_hook,
                    ),
                trailing = {
                    RuntimeBadge(
                        state.hookStatus,
                    )
                },
            )
        }
        item {
            YSuiteListItem(
                title =
                    stringResource(
                        R.string.ypower_enabled_apps,
                    ),
                subtitle =
                    state.apps
                        .count { it.enabled }
                        .toString(),
            )
        }
    }
}

@Composable
private fun AppList(
    model: YPowerViewModel,
) {
    val apps = model.visibleApps()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
    ) {
        if (apps.isEmpty()) {
            item {
                YSuiteListItem(
                    title =
                        stringResource(
                            R.string.ypower_no_apps,
                        ),
                    modifier =
                        Modifier.padding(
                            YSuiteSpacing.Medium,
                        ),
                )
            }
        }
        items(
            items = apps,
            key = { it.packageName },
        ) { app ->
            val recommendedText =
                if (app.recommended) {
                    stringResource(
                        R.string.ypower_recommended,
                    )
                } else {
                    null
                }
            val subtitle =
                buildString {
                    append(app.packageName)
                    recommendedText?.let {
                        append(" · ")
                        append(it)
                    }
                }
            YSuiteListItem(
                title = app.label,
                subtitle = subtitle,
                modifier =
                    Modifier
                        .clickable {
                            model.select(
                                app.packageName,
                            )
                        }
                        .padding(
                            horizontal =
                                YSuiteSpacing.Medium,
                            vertical =
                                YSuiteSpacing.Small,
                        ),
                trailing =
                    if (app.enabled) {
                        {
                            YSuiteStatusBadge(
                                text =
                                    stringResource(
                                        R.string
                                            .ypower_enabled,
                                    ),
                                tone =
                                    YSuiteStatusTone
                                        .Positive,
                            )
                        }
                    } else {
                        null
                    },
            )
        }
    }
}

@Composable
private fun AppDetail(
    appLabel: String,
    recommended: Boolean,
    state: YPowerUiState,
    model: YPowerViewModel,
) {
    val profile = state.draft ?: return
    var activeSection by remember(profile.packageName) { mutableStateOf("root") }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement =
            Arrangement.spacedBy(
                YSuiteSpacing.Medium,
            ),
    ) {
        item {
            YSuiteSection(
                title = appLabel,
                modifier =
                    Modifier.padding(
                        YSuiteSpacing.Medium,
                    ),
            ) {
                YSuiteListItem(
                    title =
                        profile.packageName,
                )
                YSuiteSwitchItem(
                    title =
                        stringResource(
                            R.string
                                .ypower_enable,
                        ),
                    checked = profile.enabled,
                    onCheckedChange = {
                        model.update { old ->
                            old.copy(
                                enabled = it,
                            )
                        }
                    },
                )
                YSuitePrimaryButton(
                    text = stringResource(
                        if (state.applying) R.string.ypower_applying
                        else R.string.ypower_save_apply,
                    ),
                    onClick = model::saveAndApply,
                )
                state.statusToken?.let { token ->
                    YSuiteStatusBadge(
                        text = statusTokenText(token),
                        tone = if (token == "applied") YSuiteStatusTone.Positive
                            else YSuiteStatusTone.Warning,
                    )
                }
                state.applyResult?.let { result ->
                    YSuiteListItem(
                        title = stringResource(
                            R.string.ypower_apply_summary,
                            result.applied.size, result.notes.size, result.errors.size,
                        ),
                    )
                }
                if (recommended) {
                    YSuiteStatusBadge(
                        text =
                            stringResource(
                                R.string
                                    .ypower_recommended_available,
                            ),
                        tone =
                            YSuiteStatusTone
                                .Positive,
                    )
                    YSuiteSecondaryButton(
                        text =
                            stringResource(
                                R.string
                                    .ypower_apply_recommended,
                            ),
                        onClick =
                            model::applyRecommended,
                    )
                }
            }
        }

        item {
            YSuiteFilterBar(
                options = listOf(
                    YSuiteFilterOption("root", stringResource(R.string.ypower_root_enhancements)),
                    YSuiteFilterOption("hook", stringResource(R.string.ypower_hook_compatibility)),
                    YSuiteFilterOption("trace", stringResource(R.string.ypower_tracing)),
                    YSuiteFilterOption("diag", stringResource(R.string.ypower_diagnostics)),
                ),
                selectedId = activeSection,
                onSelected = { activeSection = it },
            )
        }

        if (activeSection == "root") item {
            YSuiteSection(
                title =
                    stringResource(
                        R.string
                            .ypower_root_enhancements,
                    ),
                modifier =
                    Modifier.padding(
                        horizontal =
                            YSuiteSpacing.Medium,
                    ),
            ) {
                RootSwitches(profile, model)
            }
        }

        if (activeSection == "hook") item {
            YSuiteSection(
                title =
                    stringResource(
                        R.string
                            .ypower_hook_compatibility,
                    ),
                modifier =
                    Modifier.padding(
                        horizontal =
                            YSuiteSpacing.Medium,
                    ),
            ) {
                HookCompatibilitySwitches(
                    profile,
                    model,
                )
            }
        }

        if (activeSection == "trace") item {
            YSuiteSection(
                title =
                    stringResource(
                        R.string
                            .ypower_tracing,
                    ),
                modifier =
                    Modifier.padding(
                        horizontal =
                            YSuiteSpacing.Medium,
                    ),
            ) {
                TraceSwitches(profile, model)
            }
        }

        if (activeSection == "diag") item {
            YSuiteSection(
                title =
                    stringResource(
                        R.string
                            .ypower_diagnostics,
                    ),
                modifier =
                    Modifier.padding(
                        horizontal =
                            YSuiteSpacing.Medium,
                    ),
            ) {
                Row(
                    horizontalArrangement =
                        Arrangement.spacedBy(
                            YSuiteSpacing.Small,
                        ),
                ) {
                    YSuiteSecondaryButton(
                        text =
                            stringResource(
                                if (
                                    state.diagnosing
                                ) {
                                    R.string
                                        .ypower_diagnosing
                                } else {
                                    R.string
                                        .ypower_run_diagnostics
                                },
                            ),
                        onClick = model::diagnose,
                    )
                }
                if (
                    !state.diagnosticSessionActive
                ) {
                    Row(
                        horizontalArrangement =
                            Arrangement.spacedBy(
                                YSuiteSpacing.Small,
                            ),
                    ) {
                        YSuiteSecondaryButton(
                            text =
                                stringResource(
                                    R.string
                                        .ypower_diag_quick,
                                ),
                            onClick = {
                                model.startDiagnosticSession(
                                    YPowerDiagnosticLevel.Quick,
                                )
                            },
                        )
                        YSuiteSecondaryButton(
                            text =
                                stringResource(
                                    R.string
                                        .ypower_diag_standard,
                                ),
                            onClick = {
                                model.startDiagnosticSession(
                                    YPowerDiagnosticLevel.Standard,
                                )
                            },
                        )
                        YSuiteSecondaryButton(
                            text =
                                stringResource(
                                    R.string
                                        .ypower_diag_deep,
                                ),
                            onClick = {
                                model.startDiagnosticSession(
                                    YPowerDiagnosticLevel.Deep,
                                )
                            },
                        )
                    }
                } else {
                    Row(
                        horizontalArrangement =
                            Arrangement.spacedBy(
                                YSuiteSpacing.Small,
                            ),
                    ) {
                        YSuiteSecondaryButton(
                            text =
                                stringResource(
                                    R.string
                                        .ypower_diag_launch,
                                ),
                            onClick =
                                model::launchDiagnosticTarget,
                        )
                        YSuitePrimaryButton(
                            text =
                                stringResource(
                                    R.string
                                        .ypower_diag_finish,
                                ),
                            onClick =
                                model::finishDiagnosticSession,
                        )
                    }
                    YSuiteStatusBadge(
                        text =
                            stringResource(
                                R.string
                                    .ypower_diag_active,
                                state.diagnosticLevel.name,
                            ),
                        tone =
                            YSuiteStatusTone.Positive,
                    )
                }
                state.reportUri?.let {
                    YSuiteStatusBadge(
                        text =
                            stringResource(
                                R.string
                                    .ypower_diag_exported,
                            ),
                        tone =
                            YSuiteStatusTone.Positive,
                    )
                }
                state.statusToken?.let {
                    YSuiteStatusBadge(
                        text = statusTokenText(it),
                        tone =
                            if (it == "applied") {
                                YSuiteStatusTone
                                    .Positive
                            } else {
                                YSuiteStatusTone
                                    .Warning
                            },
                    )
                }
                state.applyResult?.let {
                    YSuiteListItem(
                        title =
                            stringResource(
                                R.string
                                    .ypower_apply_summary,
                                it.applied.size,
                                it.notes.size,
                                it.errors.size,
                            ),
                    )
                }
                state.findings.forEach {
                    finding ->
                    FindingRow(finding)
                }
            }
        }
    }
}

@Composable
private fun RootSwitches(
    profile: YPowerProfile,
    model: YPowerViewModel,
) {
    YSuiteSwitchItem(
        title =
            stringResource(
                R.string
                    .ypower_doze_whitelist,
            ),
        checked = profile.dozeWhitelist,
        onCheckedChange = {
            model.update { old ->
                old.copy(dozeWhitelist = it)
            }
        },
    )
    YSuiteSwitchItem(
        title =
            stringResource(
                R.string
                    .ypower_background_ops,
            ),
        checked = profile.backgroundOps,
        onCheckedChange = {
            model.update { old ->
                old.copy(backgroundOps = it)
            }
        },
    )
    YSuiteSwitchItem(
        title =
            stringResource(
                R.string
                    .ypower_standby_active,
            ),
        checked = profile.standbyActive,
        onCheckedChange = {
            model.update { old ->
                old.copy(standbyActive = it)
            }
        },
    )
    YSuiteSwitchItem(
        title =
            stringResource(
                R.string
                    .ypower_background_data,
            ),
        checked = profile.backgroundData,
        onCheckedChange = {
            model.update { old ->
                old.copy(backgroundData = it)
            }
        },
    )
    YSuiteSwitchItem(
        title =
            stringResource(
                R.string
                    .ypower_auto_grant,
            ),
        checked =
            profile.autoGrantDangerous,
        onCheckedChange = {
            model.update { old ->
                old.copy(
                    autoGrantDangerous = it,
                )
            }
        },
    )
}

@Composable
private fun HookCompatibilitySwitches(
    profile: YPowerProfile,
    model: YPowerViewModel,
) {
    YSuiteSwitchItem(
        title =
            stringResource(
                R.string
                    .ypower_simulate_system,
            ),
        checked =
            profile.simulateSystemApp,
        onCheckedChange = {
            model.update { old ->
                old.copy(
                    simulateSystemApp = it,
                )
            }
        },
    )
    YSuiteSwitchItem(
        title =
            stringResource(
                R.string
                    .ypower_simulate_permissions,
            ),
        checked =
            profile.simulatePermissions,
        onCheckedChange = {
            model.update { old ->
                old.copy(
                    simulatePermissions = it,
                )
            }
        },
    )
}

@Composable
private fun TraceSwitches(
    profile: YPowerProfile,
    model: YPowerViewModel,
) {
    val rows =
        listOf(
            Triple(
                R.string.ypower_trace_packages,
                profile.tracePackageScan,
            ) {
                value: Boolean ->
                model.update {
                    it.copy(
                        tracePackageScan = value,
                    )
                }
            },
            Triple(
                R.string.ypower_trace_files,
                profile.traceFiles,
            ) {
                value: Boolean ->
                model.update {
                    it.copy(traceFiles = value)
                }
            },
            Triple(
                R.string.ypower_trace_commands,
                profile.traceCommands,
            ) {
                value: Boolean ->
                model.update {
                    it.copy(
                        traceCommands = value,
                    )
                }
            },
            Triple(
                R.string.ypower_trace_properties,
                profile.traceProperties,
            ) {
                value: Boolean ->
                model.update {
                    it.copy(
                        traceProperties = value,
                    )
                }
            },
            Triple(
                R.string.ypower_trace_permissions,
                profile.tracePermissions,
            ) {
                value: Boolean ->
                model.update {
                    it.copy(
                        tracePermissions = value,
                    )
                }
            },
            Triple(
                R.string.ypower_trace_debugger,
                profile.traceDebugger,
            ) {
                value: Boolean ->
                model.update {
                    it.copy(
                        traceDebugger = value,
                    )
                }
            },
            Triple(
                R.string.ypower_trace_exceptions,
                profile.traceExceptions,
            ) {
                value: Boolean ->
                model.update {
                    it.copy(
                        traceExceptions = value,
                    )
                }
            },
            Triple(
                R.string.ypower_trace_security,
                profile.traceSecurityApis,
            ) {
                value: Boolean ->
                model.update {
                    it.copy(
                        traceSecurityApis = value,
                    )
                }
            },
            Triple(
                R.string.ypower_trace_native,
                profile.traceNative,
            ) {
                value: Boolean ->
                model.update {
                    it.copy(traceNative = value)
                }
            },
            Triple(
                R.string.ypower_trace_syscalls,
                profile.traceSyscalls,
            ) {
                value: Boolean ->
                model.update {
                    it.copy(
                        traceSyscalls = value,
                    )
                }
            },
            Triple(
                R.string.ypower_trace_stacks,
                profile.traceStacks,
            ) {
                value: Boolean ->
                model.update {
                    it.copy(
                        traceStacks = value,
                    )
                }
            },
        )
    rows.forEach {
        (titleRes, checked, onChange) ->
        YSuiteSwitchItem(
            title = stringResource(titleRes),
            checked = checked,
            onCheckedChange = onChange,
        )
    }
}

@Composable
private fun FindingRow(
    finding: YPowerFinding,
) {
    val recommendation =
        finding.recommendation?.let {
            recommendationText(it)
        }
    val subtitle =
        buildString {
            append(finding.summary)
            if (finding.detail.isNotBlank()) {
                append("\n")
                append(finding.detail)
            }
            recommendation?.let {
                append("\n")
                append(it)
            }
        }
    YSuiteListItem(
        title = findingTitle(finding.id),
        subtitle = subtitle,
        trailing = {
            YSuiteStatusBadge(
                text =
                    findingStatusText(
                        finding.status,
                    ),
                tone =
                    findingTone(
                        finding.status,
                    ),
            )
        },
    )
}

@Composable
private fun RuntimeBadge(
    status: CapabilityStatus,
) {
    YSuiteStatusBadge(
        text = capabilityText(status),
        tone =
            if (
                status ==
                CapabilityStatus.Available
            ) {
                YSuiteStatusTone.Positive
            } else {
                YSuiteStatusTone.Warning
            },
    )
}

@Composable
private fun filterLabel(
    filter: YPowerAppFilter,
): String =
    when (filter) {
        YPowerAppFilter.All ->
            stringResource(
                R.string.ypower_filter_all,
            )
        YPowerAppFilter.Enabled ->
            stringResource(
                R.string.ypower_filter_enabled,
            )
        YPowerAppFilter.Recommended ->
            stringResource(
                R.string.ypower_filter_recommended,
            )
        YPowerAppFilter.User ->
            stringResource(
                R.string.ypower_filter_user,
            )
        YPowerAppFilter.System ->
            stringResource(
                R.string.ypower_filter_system,
            )
    }

@Composable
private fun statusTokenText(
    token: String,
): String =
    when (token) {
        "applied" ->
            stringResource(
                R.string.ypower_applied,
            )
        "apply_failed" ->
            stringResource(
                R.string.ypower_apply_failed,
            )
        "diagnostic_failed" ->
            stringResource(
                R.string
                    .ypower_diagnostic_failed,
            )
        else -> token
    }

@Composable
private fun capabilityText(
    status: CapabilityStatus,
): String =
    when (status) {
        CapabilityStatus.Available ->
            stringResource(
                R.string.ypower_available,
            )
        CapabilityStatus.PermissionRequired ->
            stringResource(
                R.string
                    .ypower_permission_required,
            )
        CapabilityStatus.Unavailable ->
            stringResource(
                R.string.ypower_unavailable,
            )
        CapabilityStatus.Error ->
            stringResource(
                R.string.ypower_error,
            )
    }

@Composable
private fun findingTitle(
    id: String,
): String =
    when (id) {
        "root" ->
            stringResource(R.string.ypower_root)
        "hook" ->
            stringResource(R.string.ypower_hook)
        "exit" ->
            stringResource(
                R.string.ypower_exit_info,
            )
        "runtime_trace" ->
            stringResource(
                R.string.ypower_runtime_trace,
            )
        else -> id
    }

@Composable
private fun recommendationText(
    id: String,
): String =
    when (id) {
        "grant_root" ->
            stringResource(
                R.string.ypower_recommend_root,
            )
        "enable_hooks" ->
            stringResource(
                R.string.ypower_recommend_hook,
            )
        "inspect_runtime_trace" ->
            stringResource(
                R.string
                    .ypower_recommend_trace,
            )
        "correlate_latest_exit" ->
            stringResource(
                R.string
                    .ypower_recommend_correlate,
            )
        else -> id
    }

@Composable
private fun findingStatusText(
    status: YPowerFindingStatus,
): String =
    when (status) {
        YPowerFindingStatus.Pass ->
            stringResource(
                R.string.ypower_status_pass,
            )
        YPowerFindingStatus.Detected ->
            stringResource(
                R.string
                    .ypower_status_detected,
            )
        YPowerFindingStatus.Warning ->
            stringResource(
                R.string
                    .ypower_status_warning,
            )
        YPowerFindingStatus.Failure ->
            stringResource(
                R.string
                    .ypower_status_failure,
            )
    }

private fun findingTone(
    status: YPowerFindingStatus,
): YSuiteStatusTone =
    when (status) {
        YPowerFindingStatus.Pass ->
            YSuiteStatusTone.Positive
        YPowerFindingStatus.Detected ->
            YSuiteStatusTone.Neutral
        YPowerFindingStatus.Warning ->
            YSuiteStatusTone.Warning
        YPowerFindingStatus.Failure ->
            YSuiteStatusTone.Error
    }
