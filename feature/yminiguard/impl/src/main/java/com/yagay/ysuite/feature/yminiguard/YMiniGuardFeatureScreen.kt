package com.yagay.ysuite.feature.yminiguard

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
import com.yagay.ysuite.designsystem.component.YSuiteSecondaryButton
import com.yagay.ysuite.designsystem.component.YSuiteSearchField
import com.yagay.ysuite.designsystem.component.YSuiteSection
import com.yagay.ysuite.designsystem.component.YSuiteStatusBadge
import com.yagay.ysuite.designsystem.component.YSuiteStatusTone
import com.yagay.ysuite.designsystem.component.YSuiteSwitchItem
import com.yagay.ysuite.designsystem.theme.YSuiteSpacing
import com.yagay.ysuite.feature.yminiguard.api.YMiniGuardApp
import com.yagay.ysuite.platform.api.CapabilityStatus
import com.yagay.ysuite.productui.featurelayout.YMiniGuardWorkspace
import com.yagay.ysuite.ui.YSuiteFeatureBackHandler
import com.yagay.ysuite.ui.YSuiteHostNavigationButton

@Composable
fun YMiniGuardFeatureScreen(
    environment: YMiniGuardEnvironment,
) {
    val model: YMiniGuardViewModel =
        viewModel(
            factory = YMiniGuardViewModel.Factory(
                YMiniGuardRepository(
                    environment.applicationContext,
                    environment.rootGateway,
                    environment.hookGateway,
                ),
            ),
        )
    val state by model.state.collectAsStateWithLifecycle()
    val selected =
        state.apps.firstOrNull { it.packageName == state.selectedPackage }

    if (selected != null) {
        YSuiteFeatureBackHandler { model.select(null) }
    }

    YMiniGuardWorkspace(
        title = stringResource(R.string.yminiguard_title),
        navigationIcon = { YSuiteHostNavigationButton() },
        searchAndFilters = {
            Column(
                modifier = Modifier.padding(
                    horizontal = YSuiteSpacing.Medium,
                    vertical = YSuiteSpacing.Small,
                ),
                verticalArrangement = Arrangement.spacedBy(YSuiteSpacing.Small),
            ) {
                YSuiteSearchField(
                    value = state.query,
                    onValueChange = model::setQuery,
                    label = stringResource(R.string.yminiguard_search),
                )
                YSuiteFilterBar(
                    options = YMiniGuardFilter.entries.map {
                        YSuiteFilterOption(it.name, filterLabel(it))
                    },
                    selectedId = state.filter.name,
                    onSelected = {
                        runCatching { YMiniGuardFilter.valueOf(it) }
                            .getOrNull()?.let(model::setFilter)
                    },
                )
            }
        },
        navigationPane = {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item {
                    YSuiteListItem(
                        title = stringResource(R.string.yminiguard_engine),
                        subtitle = stringResource(
                            R.string.yminiguard_engine_summary,
                            state.engineStatus.pid,
                            state.engineStatus.hookCount,
                            state.engineStatus.activeSessions,
                        ),
                    )
                }
                item {
                    YSuiteListItem(
                        title = stringResource(R.string.yminiguard_hook),
                        trailing = {
                            YSuiteStatusBadge(
                                text = stringResource(
                                    if (state.hookStatus == CapabilityStatus.Available) {
                                        R.string.yminiguard_ready
                                    } else {
                                        R.string.yminiguard_not_ready
                                    },
                                ),
                                tone =
                                    if (state.hookStatus == CapabilityStatus.Available) {
                                        YSuiteStatusTone.Positive
                                    } else {
                                        YSuiteStatusTone.Warning
                                    },
                            )
                        },
                    )
                }
            }
        },
        detailPane =
            selected?.let {
                { AppDetail(selected, model) }
            },
    ) { adaptive ->
        if (!adaptive.isExpanded && selected != null) {
            AppDetail(selected, model)
        } else {
            MainList(state, model)
        }
    }
}

@Composable
private fun MainList(
    state: YMiniGuardUiState,
    model: YMiniGuardViewModel,
) {
    var showAdvanced by remember { mutableStateOf(false) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(YSuiteSpacing.Medium),
    ) {
        item {
            YSuiteSection(
                title = stringResource(R.string.yminiguard_global),
                modifier = Modifier.padding(YSuiteSpacing.Medium),
            ) {
                YSuiteSwitchItem(
                    title = stringResource(R.string.yminiguard_master),
                    checked = state.settings.masterEnabled,
                    onCheckedChange = {
                        model.updateSettings { old -> old.copy(masterEnabled = it) }
                    },
                )
                YSuiteSecondaryButton(
                    text = stringResource(
                        if (showAdvanced) R.string.yminiguard_hide_advanced
                        else R.string.yminiguard_show_advanced,
                    ),
                    onClick = { showAdvanced = !showAdvanced },
                )
                state.statusToken?.let {
                    YSuiteStatusBadge(
                        text =
                            stringResource(
                                when (it) {
                                    "synced" -> R.string.yminiguard_synced
                                    "config_pending" -> R.string.yminiguard_config_pending
                                    "reloaded" -> R.string.yminiguard_reloaded
                                    "hook_inactive" -> R.string.yminiguard_hook_inactive
                                    "reload_pending" -> R.string.yminiguard_reload_pending
                                    else -> R.string.yminiguard_sync_failed
                                },
                            ),
                        tone =
                            if (it in setOf("synced", "reloaded")) {
                                YSuiteStatusTone.Positive
                            } else {
                                YSuiteStatusTone.Warning
                            },
                    )
                }
                if (showAdvanced) {
                YSuiteSwitchItem(
                    title = stringResource(R.string.yminiguard_importance_top),
                    checked = state.settings.importanceTop,
                    onCheckedChange = {
                        model.updateSettings { old -> old.copy(importanceTop = it) }
                    },
                )
                YSuiteSwitchItem(
                    title = stringResource(R.string.yminiguard_has_resumed),
                    checked = state.settings.hasResumed,
                    onCheckedChange = {
                        model.updateSettings { old -> old.copy(hasResumed = it) }
                    },
                )
                YSuiteSwitchItem(
                    title = stringResource(R.string.yminiguard_block_remove_kill),
                    checked = state.settings.blockRemoveKill,
                    onCheckedChange = {
                        model.updateSettings { old -> old.copy(blockRemoveKill = it) }
                    },
                )
                YSuiteSwitchItem(
                    title = stringResource(R.string.yminiguard_diagnostics),
                    checked = state.settings.diagnostics,
                    onCheckedChange = {
                        model.updateSettings { old -> old.copy(diagnostics = it) }
                    },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(YSuiteSpacing.Small)) {
                    YSuiteSecondaryButton(
                        text = stringResource(R.string.yminiguard_reload_engine),
                        onClick = model::reloadEngine,
                    )
                    YSuiteSecondaryButton(
                        text = stringResource(R.string.yminiguard_collect_diagnostics),
                        onClick = model::diagnostics,
                    )
                }
                if (state.diagnostics.isNotBlank()) {
                    YSuiteListItem(
                        title = stringResource(R.string.yminiguard_diagnostic_snapshot),
                        subtitle = state.diagnostics.take(4_000),
                    )
                }
                }
            }
        }

        val apps = model.visibleApps()
        state.appLoadError?.let { error ->
            item(key = "app-load-error") {
                YSuiteListItem(
                    title = stringResource(R.string.yminiguard_list_read_failed),
                    subtitle = error,
                    modifier = Modifier.padding(horizontal = YSuiteSpacing.Medium),
                )
                YSuiteSecondaryButton(
                    text = stringResource(R.string.yminiguard_retry_apps),
                    onClick = model::refresh,
                )
            }
        }
        if (apps.isEmpty()) {
            item {
                YSuiteListItem(
                    title = stringResource(R.string.yminiguard_no_apps),
                    modifier = Modifier.padding(horizontal = YSuiteSpacing.Medium),
                )
            }
        }
        items(apps, key = { it.packageName }) { app ->
            YSuiteListItem(
                title = app.label,
                subtitle =
                    app.packageName +
                        (if (app.alwaysForeground) " · FG" else "") +
                        (if (app.backgroundPlayback) " · Media" else "") +
                        (if (app.forceFlexibleSupport) " · OPlus" else ""),
                modifier = Modifier
                    .clickable { model.select(app.packageName) }
                    .padding(
                        horizontal = YSuiteSpacing.Medium,
                        vertical = YSuiteSpacing.Small,
                    ),
                trailing =
                    if (app.alwaysForeground || app.backgroundPlayback) {
                        {
                            YSuiteStatusBadge(
                                text = stringResource(R.string.yminiguard_protected),
                                tone = YSuiteStatusTone.Positive,
                            )
                        }
                    } else null,
            )
        }
    }
}

@Composable
private fun AppDetail(
    app: YMiniGuardApp,
    model: YMiniGuardViewModel,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            YSuiteSection(
                title = app.label,
                modifier = Modifier.padding(YSuiteSpacing.Medium),
            ) {
                YSuiteListItem(title = app.packageName)
                YSuiteSwitchItem(
                    title = stringResource(R.string.yminiguard_always_foreground),
                    subtitle = stringResource(R.string.yminiguard_always_foreground_desc),
                    checked = app.alwaysForeground,
                    onCheckedChange = {
                        model.updateApp(app.packageName, foreground = it)
                    },
                )
                YSuiteSwitchItem(
                    title = stringResource(R.string.yminiguard_background_playback),
                    subtitle = stringResource(R.string.yminiguard_background_playback_desc),
                    checked = app.backgroundPlayback,
                    onCheckedChange = {
                        model.updateApp(app.packageName, playback = it)
                    },
                )
                YSuiteSwitchItem(
                    title = stringResource(R.string.yminiguard_force_support),
                    subtitle = stringResource(R.string.yminiguard_force_support_desc),
                    checked = app.forceFlexibleSupport,
                    onCheckedChange = {
                        model.updateApp(app.packageName, forceSupport = it)
                    },
                )
            }
        }
    }
}

@Composable
private fun filterLabel(value: YMiniGuardFilter): String =
    when (value) {
        YMiniGuardFilter.All -> stringResource(R.string.yminiguard_filter_all)
        YMiniGuardFilter.Protected -> stringResource(R.string.yminiguard_filter_protected)
        YMiniGuardFilter.Playback -> stringResource(R.string.yminiguard_filter_playback)
        YMiniGuardFilter.ForceSupport -> stringResource(R.string.yminiguard_filter_force)
        YMiniGuardFilter.User -> stringResource(R.string.yminiguard_filter_user)
        YMiniGuardFilter.System -> stringResource(R.string.yminiguard_filter_system)
    }
