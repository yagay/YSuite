package com.yagay.ysuite.feature.yentrycleaner

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.yagay.ysuite.feature.yentrycleaner.api.YEntryCandidate
import com.yagay.ysuite.feature.yentrycleaner.api.YEntryCandidateState
import com.yagay.ysuite.feature.yentrycleaner.api.YEntrySurface
import com.yagay.ysuite.platform.api.CapabilityStatus
import com.yagay.ysuite.productui.featurelayout.YEntryCleanerWorkspace
import com.yagay.ysuite.ui.YSuiteFeatureBackHandler
import com.yagay.ysuite.ui.YSuiteHostNavigationButton

private val OPEN_MIME_PRESETS =
    listOf(
        "application/pdf",
        "image/*",
        "video/*",
        "audio/*",
        "text/plain",
        "application/zip",
        "application/vnd.android.package-archive",
    )

@Composable
fun YEntryCleanerFeatureScreen(
    environment: YEntryCleanerEnvironment,
) {
    val model: YEntryCleanerViewModel =
        viewModel(
            factory =
                YEntryCleanerViewModel.Factory(
                    YEntryCleanerRepository(
                        environment.applicationContext,
                        environment.rootGateway,
                        environment.hookGateway,
                    ),
                ),
        )
    val state by
        model.state.collectAsStateWithLifecycle()
    val selected = model.selected()
    val importLauncher =
        rememberLauncherForActivityResult(
            contract =
                ActivityResultContracts
                    .OpenDocument(),
        ) { uri ->
            uri?.let(model::importBackup)
        }

    if (selected != null) {
        YSuiteFeatureBackHandler {
            model.select(null)
        }
    }

    YEntryCleanerWorkspace(
        title =
            stringResource(
                R.string.yentry_title,
            ),
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
                    onValueChange =
                        model::setQuery,
                    label =
                        stringResource(
                            R.string.yentry_search,
                        ),
                )
                YSuiteFilterBar(
                    options =
                        YEntrySurface.entries.map {
                            YSuiteFilterOption(
                                it.name,
                                surfaceLabel(it),
                            )
                        },
                    selectedId =
                        state.surface.name,
                    onSelected = {
                        runCatching {
                            YEntrySurface
                                .valueOf(it)
                        }.getOrNull()
                            ?.let(
                                model::setSurface,
                            )
                    },
                )
                YSuiteFilterBar(
                    options =
                        YEntryAppFilter.entries.map {
                            YSuiteFilterOption(
                                it.name,
                                filterLabel(it),
                            )
                        },
                    selectedId =
                        state.filter.name,
                    onSelected = {
                        runCatching {
                            YEntryAppFilter
                                .valueOf(it)
                        }.getOrNull()
                            ?.let(
                                model::setFilter,
                            )
                    },
                )
                YSuiteFilterBar(
                    options =
                        YEntrySelectionFilter.entries.map {
                            YSuiteFilterOption(
                                it.name,
                                selectionFilterLabel(it),
                            )
                        },
                    selectedId = state.selectionFilter.name,
                    onSelected = {
                        runCatching {
                            YEntrySelectionFilter.valueOf(it)
                        }.getOrNull()
                            ?.let(model::setSelectionFilter)
                    },
                )
                when (state.surface) {
                    YEntrySurface.Open -> {
                        YSuiteSearchField(
                            value = state.openMime,
                            onValueChange =
                                model::setOpenMime,
                            label =
                                stringResource(
                                    R.string
                                        .yentry_open_mime,
                                ),
                        )
                        YSuiteFilterBar(
                            options =
                                OPEN_MIME_PRESETS.map {
                                    YSuiteFilterOption(
                                        it,
                                        it,
                                    )
                                },
                            selectedId =
                                state.openMime
                                    .takeIf {
                                        it in
                                            OPEN_MIME_PRESETS
                                    },
                            onSelected =
                                model::setOpenMime,
                        )
                    }
                    YEntrySurface.Browser -> {
                        YSuiteSearchField(
                            value =
                                state.browserHost,
                            onValueChange =
                                model::setBrowserHost,
                            label =
                                stringResource(
                                    R.string
                                        .yentry_browser_host,
                                ),
                        )
                        if (
                            state.browserHosts
                                .isNotEmpty()
                        ) {
                            YSuiteFilterBar(
                                options =
                                    state.browserHosts
                                        .take(16)
                                        .map {
                                            YSuiteFilterOption(
                                                it,
                                                it,
                                            )
                                        },
                                selectedId =
                                    state.browserHost,
                                onSelected =
                                    model::setBrowserHost,
                            )
                        }
                    }
                    else -> Unit
                }
            }
        },
        navigationPane = {
            LazyColumn(
                modifier =
                    Modifier.fillMaxSize(),
            ) {
                item {
                    YSuiteSection(
                        title =
                            stringResource(
                                R.string
                                    .yentry_runtime,
                            ),
                        modifier =
                            Modifier.padding(
                                YSuiteSpacing.Medium,
                            ),
                    ) {
                        YSuiteStatusBadge(
                            text =
                                if (
                                    state.rootStatus ==
                                    CapabilityStatus
                                        .Available
                                ) {
                                    stringResource(
                                        R.string
                                            .yentry_root_ready,
                                    )
                                } else {
                                    stringResource(
                                        R.string
                                            .yentry_root_missing,
                                    )
                                },
                            tone =
                                if (
                                    state.rootStatus ==
                                    CapabilityStatus
                                        .Available
                                ) {
                                    YSuiteStatusTone
                                        .Positive
                                } else {
                                    YSuiteStatusTone
                                        .Warning
                                },
                        )
                        YSuiteStatusBadge(
                            text =
                                if (
                                    state.hookStatus ==
                                    CapabilityStatus
                                        .Available
                                ) {
                                    stringResource(
                                        R.string
                                            .yentry_hook_ready,
                                    )
                                } else {
                                    stringResource(
                                        R.string
                                            .yentry_hook_missing,
                                    )
                                },
                            tone =
                                if (
                                    state.hookStatus ==
                                    CapabilityStatus
                                        .Available
                                ) {
                                    YSuiteStatusTone
                                        .Positive
                                } else {
                                    YSuiteStatusTone
                                        .Warning
                                },
                        )
                    }
                }
                item {
                    YSuiteListItem(
                        title =
                            stringResource(
                                R.string
                                    .yentry_mode,
                            ),
                        subtitle =
                            when (state.displayMode) {
                                "HIDE_SELECTED" ->
                                    stringResource(R.string.yentry_hide_selected)
                                "SHOW_SELECTED" ->
                                    stringResource(R.string.yentry_show_selected)
                                else ->
                                    stringResource(R.string.yentry_show_all)
                            },
                        modifier =
                            Modifier.clickable {
                                model
                                    .toggleDisplayMode()
                            },
                    )
                }
                item {
                    YSuiteSwitchItem(
                        title =
                            stringResource(
                                R.string
                                    .yentry_diagnostics,
                            ),
                        checked =
                            state.diagnostic,
                        onCheckedChange =
                            model::setDiagnostic,
                    )
                }
                item {
                    Column(
                        modifier =
                            Modifier.padding(
                                YSuiteSpacing.Medium,
                            ),
                        verticalArrangement =
                            Arrangement.spacedBy(
                                YSuiteSpacing.Small,
                            ),
                    ) {
                        YSuiteSecondaryButton(
                            text =
                                stringResource(
                                    R.string
                                        .yentry_discover_hosts,
                                ),
                            onClick =
                                model::discoverBrowserHosts,
                        )
                        YSuiteSecondaryButton(
                            text =
                                stringResource(
                                    R.string
                                        .yentry_export_backup,
                                ),
                            onClick =
                                model::exportBackup,
                        )
                        YSuiteSecondaryButton(
                            text =
                                stringResource(
                                    R.string
                                        .yentry_import_backup,
                                ),
                            onClick = {
                                importLauncher.launch(
                                    arrayOf(
                                        "application/json",
                                        "text/plain",
                                    ),
                                )
                            },
                        )
                    }
                }
            }
        },
        detailPane =
            selected?.let {
                {
                    CandidateDetail(
                        selected,
                        model,
                    )
                }
            },
        selectionBar = {
            val componentSurface =
                state.surface in
                    setOf(
                        YEntrySurface.Tile,
                        YEntrySurface.Shortcut,
                        YEntrySurface.Widget,
                    )
            if (
                state.surface !=
                YEntrySurface.Historical
            ) {
                Row(
                    modifier =
                        Modifier.padding(
                            horizontal =
                                YSuiteSpacing.Medium,
                            vertical =
                                YSuiteSpacing.Small,
                        ),
                    horizontalArrangement =
                        Arrangement.spacedBy(
                            YSuiteSpacing.Small,
                        ),
                ) {
                    if (componentSurface) {
                        YSuiteSecondaryButton(
                            text =
                                stringResource(
                                    R.string
                                        .yentry_disable_visible,
                                ),
                            onClick = {
                                model.bulkComponents(
                                    false,
                                )
                            },
                        )
                        YSuiteSecondaryButton(
                            text =
                                stringResource(
                                    R.string
                                        .yentry_enable_visible,
                                ),
                            onClick = {
                                model.bulkComponents(
                                    true,
                                )
                            },
                        )
                        YSuiteSecondaryButton(
                            text =
                                stringResource(
                                    R.string
                                        .yentry_invert_visible,
                                ),
                            onClick =
                                model::invertComponents,
                        )
                    } else {
                        YSuiteSecondaryButton(
                            text =
                                stringResource(
                                    R.string
                                        .yentry_hide_visible,
                                ),
                            onClick = {
                                model.bulk(true)
                            },
                        )
                        YSuiteSecondaryButton(
                            text =
                                stringResource(
                                    R.string
                                        .yentry_show_visible,
                                ),
                            onClick = {
                                model.bulk(false)
                            },
                        )
                    }
                }
            }
        },
    ) { adaptive ->
        if (
            !adaptive.isExpanded &&
            selected != null
        ) {
            CandidateDetail(
                selected,
                model,
            )
        } else {
            CandidateList(model)
        }
    }
}

private sealed interface CandidateListRow {
    val key: String

    data class Group(
        val packageName: String,
        val candidates: List<YEntryCandidate>,
    ) : CandidateListRow {
        override val key = "group:" + packageName
    }

    data class Component(
        val candidate: YEntryCandidate,
    ) : CandidateListRow {
        override val key = "component:" + candidate.id
    }
}

@Composable
private fun CandidateList(
    model: YEntryCleanerViewModel,
) {
    val state by model.state.collectAsStateWithLifecycle()
    val candidates = model.visible()
    var expandedPackages by remember(state.surface) {
        mutableStateOf(emptySet<String>())
    }
    val rows = buildList<CandidateListRow> {
        candidates.groupBy { it.packageName }
            .toSortedMap(String.CASE_INSENSITIVE_ORDER)
            .forEach { (packageName, items) ->
                add(CandidateListRow.Group(packageName, items))
                if (
                    packageName in expandedPackages ||
                    state.query.isNotBlank()
                ) {
                    items.forEach { add(CandidateListRow.Component(it)) }
                }
            }
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        state.statusToken?.let { token ->
            item(key = "status") {
                val status = statusPresentation(token)
                YSuiteStatusBadge(
                    text = stringResource(status.first),
                    tone = status.second,
                    modifier = Modifier.padding(YSuiteSpacing.Medium),
                )
            }
        }
        if (candidates.isEmpty()) {
            item(key = "empty") {
                YSuiteListItem(
                    title = stringResource(R.string.yentry_empty),
                    modifier = Modifier.padding(YSuiteSpacing.Medium),
                )
            }
        }
        items(items = rows, key = { it.key }) { row ->
            when (row) {
                is CandidateListRow.Group -> {
                    YSuiteListItem(
                        title = row.packageName,
                        subtitle = stringResource(
                            R.string.yentry_group_summary,
                            row.candidates.size,
                            row.candidates.count { it.hidden },
                        ),
                        modifier = Modifier
                            .clickable(enabled = state.query.isBlank()) {
                                expandedPackages =
                                    if (row.packageName in expandedPackages) {
                                        expandedPackages - row.packageName
                                    } else {
                                        expandedPackages + row.packageName
                                    }
                            }
                            .padding(
                                horizontal = YSuiteSpacing.Medium,
                                vertical = YSuiteSpacing.Small,
                            ),
                        trailing = {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(
                                    YSuiteSpacing.Small,
                                ),
                            ) {
                                val fullyLocked = row.candidates.all { it.locked }
                                YSuiteSecondaryButton(
                                    text = stringResource(
                                        if (fullyLocked) R.string.yentry_unlock_group
                                        else R.string.yentry_lock_group,
                                    ),
                                    onClick = {
                                        model.toggleGroupLock(row.packageName, !fullyLocked)
                                    },
                                )
                                YSuiteStatusBadge(
                                    text = stringResource(
                                        if (
                                            row.packageName in expandedPackages ||
                                            state.query.isNotBlank()
                                        ) R.string.yentry_group_collapse
                                        else R.string.yentry_group_expand,
                                    ),
                                    tone = YSuiteStatusTone.Neutral,
                                )
                            }
                        },
                    )
                }
                is CandidateListRow.Component -> {
                    val candidate = row.candidate
                    YSuiteListItem(
                        title = candidate.label,
                        subtitle = candidate.className +
                            if (candidate.state != YEntryCandidateState.Current) {
                                " · " + candidate.state.name
                            } else {
                                ""
                            },
                        modifier = Modifier
                            .clickable { model.select(candidate.id) }
                            .padding(
                                horizontal = YSuiteSpacing.Medium,
                                vertical = YSuiteSpacing.Small,
                            ),
                        trailing = {
                            when {
                                candidate.locked ->
                                    YSuiteStatusBadge(
                                        text = stringResource(R.string.yentry_locked),
                                        tone = YSuiteStatusTone.Warning,
                                    )
                                candidate.hidden ->
                                    YSuiteStatusBadge(
                                        text = stringResource(R.string.yentry_hidden),
                                        tone = YSuiteStatusTone.Neutral,
                                    )
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun CandidateDetail(
    candidate: YEntryCandidate,
    model: YEntryCleanerViewModel,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            YSuiteSection(
                title = candidate.label,
                modifier =
                    Modifier.padding(
                        YSuiteSpacing.Medium,
                    ),
            ) {
                YSuiteListItem(
                    title =
                        candidate.packageName,
                    subtitle =
                        candidate.className,
                )
                var titleDraft by remember(candidate.id) {
                    mutableStateOf(candidate.label)
                }
                YSuiteSearchField(
                    value = titleDraft,
                    onValueChange = { titleDraft = it },
                    label = stringResource(R.string.yentry_custom_title),
                )
                YSuiteSecondaryButton(
                    text = stringResource(R.string.yentry_save_title),
                    onClick = { model.rename(candidate, titleDraft) },
                )
                YSuiteSwitchItem(
                    title =
                        stringResource(
                            R.string.yentry_locked,
                        ),
                    subtitle =
                        stringResource(
                            R.string
                                .yentry_locked_desc,
                        ),
                    checked =
                        candidate.locked,
                    onCheckedChange = {
                        model.toggleLock(
                            candidate,
                            it,
                        )
                    },
                )
                if (
                    candidate.surface in
                    setOf(
                        YEntrySurface.Tile,
                        YEntrySurface.Shortcut,
                        YEntrySurface.Widget,
                    )
                ) {
                    YSuiteSwitchItem(
                        title =
                            stringResource(
                                R.string
                                    .yentry_component_enabled,
                            ),
                        checked =
                            candidate.rootEnabled
                                ?: false,
                        onCheckedChange = {
                            model.component(
                                candidate,
                                it,
                            )
                        },
                    )
                    if (candidate.rootBlocked) {
                        YSuiteStatusBadge(
                            text =
                                stringResource(
                                    R.string
                                        .yentry_component_protected,
                                ),
                            tone =
                                YSuiteStatusTone.Warning,
                        )
                    }
                } else {
                    YSuiteSwitchItem(
                        title =
                            stringResource(
                                R.string
                                    .yentry_hidden,
                            ),
                        checked =
                            candidate.hidden,
                        onCheckedChange = {
                            model.toggleHidden(
                                candidate,
                                it,
                            )
                        },
                    )
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
                                        .yentry_priority_up,
                                ),
                            onClick = {
                                model.move(
                                    candidate,
                                    -1,
                                )
                            },
                        )
                        YSuiteSecondaryButton(
                            text =
                                stringResource(
                                    R.string
                                        .yentry_priority_down,
                                ),
                            onClick = {
                                model.move(
                                    candidate,
                                    1,
                                )
                            },
                        )
                        if (candidate.priority != null) {
                            YSuiteSecondaryButton(
                                text = stringResource(R.string.yentry_priority_remove),
                                onClick = { model.removePriority(candidate) },
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun statusPresentation(
    token: String,
): Pair<Int, YSuiteStatusTone> =
    when {
        token == "component_changed" ->
            R.string.yentry_component_changed to
                YSuiteStatusTone.Positive
        token == "component_failed" ->
            R.string.yentry_component_failed to
                YSuiteStatusTone.Error
        token == "backup_exported" ->
            R.string.yentry_backup_exported to
                YSuiteStatusTone.Positive
        token == "backup_restored" ->
            R.string.yentry_backup_restored to
                YSuiteStatusTone.Positive
        token == "backup_saved_local" ->
            R.string.yentry_backup_saved_local to
                YSuiteStatusTone.Warning
        token == "backup_failed" ->
            R.string.yentry_backup_failed to
                YSuiteStatusTone.Error
        token.startsWith(
            "components_partial:",
        ) ->
            R.string.yentry_components_partial to
                YSuiteStatusTone.Warning
        token.startsWith(
            "components_changed:",
        ) ->
            R.string.yentry_components_changed to
                YSuiteStatusTone.Positive
        token.startsWith(
            "hosts_discovered:",
        ) ->
            R.string.yentry_hosts_discovered to
                YSuiteStatusTone.Positive
        token == "rules_hidden" ->
            R.string.yentry_rules_hidden to
                YSuiteStatusTone.Positive
        token == "rules_shown" ->
            R.string.yentry_rules_shown to
                YSuiteStatusTone.Positive
        token == "rules_sync_failed" ->
            R.string.yentry_rules_sync_failed to
                YSuiteStatusTone.Error
        token == "title_updated" ->
            R.string.yentry_title_updated to
                YSuiteStatusTone.Positive
        token == "title_invalid" ->
            R.string.yentry_title_invalid to
                YSuiteStatusTone.Error
        token == "candidate_load_failed" ->
            R.string.yentry_candidate_load_failed to
                YSuiteStatusTone.Error
        else ->
            R.string.yentry_component_failed to
                YSuiteStatusTone.Error
    }

@Composable
private fun surfaceLabel(
    value: YEntrySurface,
): String =
    when (value) {
        YEntrySurface.ShareText ->
            stringResource(
                R.string.yentry_share_text,
            )
        YEntrySurface.ShareImage ->
            stringResource(
                R.string.yentry_share_image,
            )
        YEntrySurface.ShareMultiple ->
            stringResource(R.string.yentry_share_multiple)
        YEntrySurface.ProcessText ->
            stringResource(R.string.yentry_process_text)
        YEntrySurface.Open ->
            stringResource(
                R.string.yentry_open,
            )
        YEntrySurface.Browser ->
            stringResource(
                R.string.yentry_browser,
            )
        YEntrySurface.Tile ->
            stringResource(
                R.string.yentry_tiles,
            )
        YEntrySurface.Shortcut ->
            stringResource(
                R.string.yentry_shortcuts,
            )
        YEntrySurface.Widget ->
            stringResource(
                R.string.yentry_widgets,
            )
        YEntrySurface.Historical ->
            stringResource(
                R.string.yentry_historical,
            )
    }

@Composable
private fun filterLabel(
    value: YEntryAppFilter,
): String =
    when (value) {
        YEntryAppFilter.All ->
            stringResource(
                R.string.yentry_filter_all,
            )
        YEntryAppFilter.User ->
            stringResource(
                R.string.yentry_filter_user,
            )
        YEntryAppFilter.System ->
            stringResource(
                R.string.yentry_filter_system,
            )
    }

@Composable
private fun selectionFilterLabel(
    value: YEntrySelectionFilter,
): String =
    when (value) {
        YEntrySelectionFilter.All ->
            stringResource(R.string.yentry_filter_all)
        YEntrySelectionFilter.Selected ->
            stringResource(R.string.yentry_filter_hidden)
        YEntrySelectionFilter.Unselected ->
            stringResource(R.string.yentry_filter_unselected)
        YEntrySelectionFilter.Locked ->
            stringResource(R.string.yentry_filter_locked)
    }
