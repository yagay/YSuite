package com.yagay.ysuite.feature.ydiag

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
import com.yagay.ysuite.feature.ydiag.api.YDiagCatalog
import com.yagay.ysuite.feature.ydiag.api.YDiagEvent
import com.yagay.ysuite.feature.ydiag.api.YDiagLoad
import com.yagay.ysuite.feature.ydiag.api.YDiagRecommendation
import com.yagay.ysuite.feature.ydiag.api.YDiagSeverity
import com.yagay.ysuite.platform.api.CapabilityStatus
import com.yagay.ysuite.productui.featurelayout.YDiagWorkspace
import com.yagay.ysuite.ui.YSuiteFeatureBackHandler
import com.yagay.ysuite.ui.YSuiteHostNavigationButton

@Composable
fun YDiagFeatureScreen(
    environment: YDiagEnvironment,
) {
    val model: YDiagViewModel =
        viewModel(factory = YDiagViewModel.Factory(environment))
    val state by model.state.collectAsStateWithLifecycle()
    val selectedApp =
        state.apps.firstOrNull {
            it.packageName == state.selectedPackage
        }

    if (selectedApp != null) {
        YSuiteFeatureBackHandler(
            onBack = { model.selectPackage(null) },
        )
    }

    YDiagWorkspace(
        title = stringResource(R.string.ydiag_title),
        navigationIcon = { YSuiteHostNavigationButton() },
        search = {
            YSuiteSearchField(
                value = state.query,
                onValueChange = model::setQuery,
                label =
                    stringResource(
                        if (selectedApp == null) {
                            R.string.ydiag_search_apps
                        } else {
                            R.string.ydiag_search_results
                        },
                    ),
                modifier = Modifier.padding(
                    horizontal = YSuiteSpacing.Medium,
                    vertical = YSuiteSpacing.Small,
                ),
            )
        },
        filters = {
            Column(
                modifier = Modifier.padding(
                    horizontal = YSuiteSpacing.Medium,
                    vertical = YSuiteSpacing.Small,
                ),
                verticalArrangement =
                    Arrangement.spacedBy(YSuiteSpacing.Small),
            ) {
                if (selectedApp == null) {
                    YSuiteFilterBar(
                        options =
                            YDiagAppFilter.entries.map {
                                YSuiteFilterOption(
                                    it.name,
                                    appFilterLabel(it),
                                )
                            },
                        selectedId = state.appFilter.name,
                        onSelected = {
                            runCatching {
                                YDiagAppFilter.valueOf(it)
                            }.getOrNull()
                                ?.let(model::setAppFilter)
                        },
                    )
                } else {
                    YSuiteFilterBar(
                        options =
                            YDiagCatalog.presets.map {
                                YSuiteFilterOption(
                                    it.id,
                                    presetLabel(it.id),
                                )
                            },
                        selectedId = state.presetId,
                        onSelected = model::applyPreset,
                    )
                }
            }
        },
        details =
            model.selectedEvent()?.let { event ->
                {
                    EventDetail(event)
                }
            },
    ) {
        if (selectedApp == null) {
            AppPickerContent(model)
        } else {
            DiagnosticContent(
                appLabel = selectedApp.label,
                packageName = selectedApp.packageName,
                state = state,
                model = model,
            )
        }
    }
}

@Composable
private fun AppPickerContent(model: YDiagViewModel) {
    val apps = model.visibleApps()
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        if (apps.isEmpty()) {
            item {
                YSuiteListItem(
                    title = stringResource(R.string.ydiag_no_apps),
                    modifier = Modifier.padding(YSuiteSpacing.Medium),
                )
            }
        }
        items(apps, key = { it.packageName }) { app ->
            YSuiteListItem(
                title = app.label,
                subtitle = app.packageName,
                modifier =
                    Modifier
                        .clickable {
                            model.selectPackage(app.packageName)
                        }
                        .padding(
                            horizontal = YSuiteSpacing.Medium,
                            vertical = YSuiteSpacing.Small,
                        ),
            )
        }
    }
}

@Composable
private fun DiagnosticContent(
    appLabel: String,
    packageName: String,
    state: YDiagUiState,
    model: YDiagViewModel,
) {
    val events = model.visibleEvents()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(YSuiteSpacing.Medium),
    ) {
        item {
            YSuiteSection(
                title = appLabel,
                modifier = Modifier.padding(YSuiteSpacing.Medium),
            ) {
                YSuiteListItem(
                    title = packageName,
                    subtitle =
                        stringResource(
                            R.string.ydiag_enabled_count,
                            state.enabledOptions.size,
                        ),
                )
                Row(
                    horizontalArrangement =
                        Arrangement.spacedBy(YSuiteSpacing.Small),
                ) {
                    YSuiteStatusBadge(
                        text =
                            if (
                                state.rootStatus ==
                                CapabilityStatus.Available
                            ) {
                                stringResource(R.string.ydiag_root_ready)
                            } else {
                                stringResource(R.string.ydiag_root_missing)
                            },
                        tone =
                            if (
                                state.rootStatus ==
                                CapabilityStatus.Available
                            ) {
                                YSuiteStatusTone.Positive
                            } else {
                                YSuiteStatusTone.Warning
                            },
                    )
                    YSuiteStatusBadge(
                        text =
                            if (
                                state.hookStatus ==
                                CapabilityStatus.Available
                            ) {
                                stringResource(R.string.ydiag_hook_ready)
                            } else {
                                stringResource(R.string.ydiag_hook_missing)
                            },
                        tone =
                            if (
                                state.hookStatus ==
                                CapabilityStatus.Available
                            ) {
                                YSuiteStatusTone.Positive
                            } else {
                                YSuiteStatusTone.Warning
                            },
                    )
                }
                Row(
                    horizontalArrangement =
                        Arrangement.spacedBy(YSuiteSpacing.Small),
                ) {
                    YSuitePrimaryButton(
                        text =
                            stringResource(
                                if (state.running) {
                                    R.string.ydiag_running
                                } else {
                                    R.string.ydiag_start
                                },
                            ),
                        onClick = model::runDiagnostics,
                    )
                    if (state.events.isNotEmpty() || state.liveSessionActive) {
                        YSuiteSecondaryButton(
                            text = stringResource(R.string.ydiag_export),
                            onClick = model::export,
                        )
                    }
                }
                Row(
                    horizontalArrangement =
                        Arrangement.spacedBy(YSuiteSpacing.Small),
                ) {
                    if (!state.liveSessionActive) {
                        YSuiteSecondaryButton(
                            text =
                                stringResource(
                                    R.string.ydiag_live_start,
                                ),
                            onClick = model::startLiveSession,
                        )
                    } else {
                        YSuiteSecondaryButton(
                            text =
                                stringResource(
                                    R.string.ydiag_mark_problem,
                                ),
                            onClick = model::markProblem,
                        )
                        YSuiteSecondaryButton(
                            text =
                                stringResource(
                                    R.string.ydiag_live_stop,
                                ),
                            onClick = model::stopLiveSession,
                        )
                    }
                }
                state.exportUri?.let {
                    YSuiteStatusBadge(
                        text = stringResource(R.string.ydiag_exported),
                        tone = YSuiteStatusTone.Positive,
                    )
                }
                state.error?.let {
                    YSuiteStatusBadge(
                        text = it,
                        tone = YSuiteStatusTone.Error,
                    )
                }
            }
        }

        item {
            YSuiteSection(
                title = stringResource(R.string.ydiag_options),
                modifier = Modifier.padding(horizontal = YSuiteSpacing.Medium),
            ) {
                YDiagCatalog.options.forEach { option ->
                    YSuiteSwitchItem(
                        title = optionLabel(option.id),
                        subtitle =
                            stringResource(
                                R.string.ydiag_option_meta,
                                recommendationLabel(
                                    option.recommendation,
                                ),
                                loadLabel(option.load),
                            ),
                        checked =
                            option.id in state.enabledOptions,
                        onCheckedChange = {
                            model.toggleOption(option.id, it)
                        },
                    )
                }
            }
        }

        item {
            YSuiteListItem(
                title = stringResource(R.string.ydiag_results),
                subtitle =
                    stringResource(
                        R.string.ydiag_result_count,
                        events.size,
                    ),
                modifier = Modifier.padding(
                    horizontal = YSuiteSpacing.Medium,
                ),
            )
        }

        if (events.isEmpty()) {
            item {
                YSuiteListItem(
                    title =
                        stringResource(
                            if (state.running) {
                                R.string.ydiag_collecting
                            } else {
                                R.string.ydiag_no_results
                            },
                        ),
                    modifier = Modifier.padding(
                        horizontal = YSuiteSpacing.Medium,
                    ),
                )
            }
        }

        items(events, key = { it.id }) { event ->
            YSuiteListItem(
                title = optionLabel(event.optionId),
                subtitle =
                    stringResource(
                        R.string.ydiag_event_summary,
                        severityLabel(event.severity),
                        event.detail.lineSequence()
                            .firstOrNull()
                            .orEmpty()
                            .take(180),
                    ),
                modifier =
                    Modifier
                        .clickable { model.selectEvent(event) }
                        .padding(
                            horizontal = YSuiteSpacing.Medium,
                            vertical = YSuiteSpacing.Small,
                        ),
                trailing = {
                    YSuiteStatusBadge(
                        text = severityLabel(event.severity),
                        tone = severityTone(event.severity),
                    )
                },
            )
        }
    }
}

@Composable
private fun EventDetail(event: YDiagEvent) {
    Column(
        modifier = Modifier.padding(YSuiteSpacing.Medium),
        verticalArrangement = Arrangement.spacedBy(YSuiteSpacing.Medium),
    ) {
        YSuiteSection(title = optionLabel(event.optionId)) {
            YSuiteStatusBadge(
                text = severityLabel(event.severity),
                tone = severityTone(event.severity),
            )
            YSuiteListItem(
                title = stringResource(R.string.ydiag_detail),
                subtitle = event.detail,
            )
        }
    }
}

@Composable
private fun appFilterLabel(value: YDiagAppFilter): String =
    when (value) {
        YDiagAppFilter.All -> stringResource(R.string.ydiag_filter_all)
        YDiagAppFilter.User -> stringResource(R.string.ydiag_filter_user)
        YDiagAppFilter.System -> stringResource(R.string.ydiag_filter_system)
    }

@Composable
private fun presetLabel(id: String): String =
    when (id) {
        "quick" -> stringResource(R.string.ydiag_preset_quick)
        "crash" -> stringResource(R.string.ydiag_preset_crash)
        "hook" -> stringResource(R.string.ydiag_preset_hook)
        "root" -> stringResource(R.string.ydiag_preset_root)
        "complete" -> stringResource(R.string.ydiag_preset_complete)
        else -> stringResource(R.string.ydiag_preset_custom)
    }

@Composable
private fun optionLabel(id: String): String =
    when (id) {
        "logcat" -> stringResource(R.string.ydiag_option_logcat)
        "crash" -> stringResource(R.string.ydiag_option_crash)
        "exit_info" -> stringResource(R.string.ydiag_option_exit_info)
        "anr" -> stringResource(R.string.ydiag_option_anr)
        "process" -> stringResource(R.string.ydiag_option_process)
        "lsposed_status" -> stringResource(R.string.ydiag_option_lsposed_status)
        "hook_health" -> stringResource(R.string.ydiag_option_hook_health)
        "lifecycle" -> stringResource(R.string.ydiag_option_lifecycle)
        "intent" -> stringResource(R.string.ydiag_option_intent)
        "method_trace" -> stringResource(R.string.ydiag_option_method_trace)
        "stack_trace" -> stringResource(R.string.ydiag_option_stack_trace)
        "webview" -> stringResource(R.string.ydiag_option_webview)
        "network" -> stringResource(R.string.ydiag_option_network)
        "file_io" -> stringResource(R.string.ydiag_option_file_io)
        "binder" -> stringResource(R.string.ydiag_option_binder)
        "memory" -> stringResource(R.string.ydiag_option_memory)
        "perfetto" -> stringResource(R.string.ydiag_option_perfetto)
        "kernel" -> stringResource(R.string.ydiag_option_kernel)
        "selinux" -> stringResource(R.string.ydiag_option_selinux)
        "root_module" -> stringResource(R.string.ydiag_option_root_module)
        "lsposed_log" -> stringResource(R.string.ydiag_option_lsposed_log)
        "tombstone" -> stringResource(R.string.ydiag_option_tombstone)
        "native" -> stringResource(R.string.ydiag_option_native)
        else -> id
    }

@Composable
private fun recommendationLabel(
    value: YDiagRecommendation,
): String =
    when (value) {
        YDiagRecommendation.Recommended ->
            stringResource(R.string.ydiag_recommended)
        YDiagRecommendation.OnDemand ->
            stringResource(R.string.ydiag_on_demand)
        YDiagRecommendation.Deep ->
            stringResource(R.string.ydiag_deep)
    }

@Composable
private fun loadLabel(value: YDiagLoad): String =
    when (value) {
        YDiagLoad.VeryLow ->
            stringResource(R.string.ydiag_load_very_low)
        YDiagLoad.Low ->
            stringResource(R.string.ydiag_load_low)
        YDiagLoad.Medium ->
            stringResource(R.string.ydiag_load_medium)
        YDiagLoad.High ->
            stringResource(R.string.ydiag_load_high)
        YDiagLoad.VeryHigh ->
            stringResource(R.string.ydiag_load_very_high)
    }

@Composable
private fun severityLabel(value: YDiagSeverity): String =
    when (value) {
        YDiagSeverity.Info ->
            stringResource(R.string.ydiag_severity_info)
        YDiagSeverity.Warning ->
            stringResource(R.string.ydiag_severity_warning)
        YDiagSeverity.Error ->
            stringResource(R.string.ydiag_severity_error)
        YDiagSeverity.Fatal ->
            stringResource(R.string.ydiag_severity_fatal)
    }

private fun severityTone(value: YDiagSeverity): YSuiteStatusTone =
    when (value) {
        YDiagSeverity.Info -> YSuiteStatusTone.Neutral
        YDiagSeverity.Warning -> YSuiteStatusTone.Warning
        YDiagSeverity.Error,
        YDiagSeverity.Fatal -> YSuiteStatusTone.Error
    }
