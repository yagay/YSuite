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
import com.yagay.ysuite.productui.featurelayout.YEntryCleanerWorkspace
import com.yagay.ysuite.ui.YSuiteFeatureBackHandler
import com.yagay.ysuite.ui.YSuiteHostNavigationButton

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
                        YEntrySurface.entries
                            .map {
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
                        YEntryAppFilter.entries
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
                            YEntryAppFilter
                                .valueOf(it)
                        }.getOrNull()
                            ?.let(
                                model::setFilter,
                            )
                    },
                )
                if (
                    state.surface ==
                    YEntrySurface.Browser
                ) {
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
                }
            }
        },
        navigationPane = {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
            ) {
                item {
                    YSuiteListItem(
                        title =
                            stringResource(
                                R.string
                                    .yentry_mode,
                            ),
                        subtitle =
                            if (
                                state.displayMode ==
                                "HIDE_SELECTED"
                            ) {
                                stringResource(
                                    R.string
                                        .yentry_hide_selected,
                                )
                            } else {
                                stringResource(
                                    R.string
                                        .yentry_show_selected,
                                )
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
            if (
                state.surface !in
                setOf(
                    YEntrySurface.Tile,
                    YEntrySurface.Shortcut,
                    YEntrySurface.Widget,
                    YEntrySurface.Historical,
                )
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

@Composable
private fun CandidateList(
    model: YEntryCleanerViewModel,
) {
    val state by
        model.state.collectAsStateWithLifecycle()
    val items = model.visible()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
    ) {
        state.statusToken?.let {
            item {
                YSuiteStatusBadge(
                    text =
                        if (
                            it ==
                            "component_changed"
                        ) {
                            stringResource(
                                R.string
                                    .yentry_component_changed,
                            )
                        } else {
                            stringResource(
                                R.string
                                    .yentry_component_failed,
                            )
                        },
                    tone =
                        if (
                            it ==
                            "component_changed"
                        ) {
                            YSuiteStatusTone
                                .Positive
                        } else {
                            YSuiteStatusTone
                                .Error
                        },
                    modifier =
                        Modifier.padding(
                            YSuiteSpacing.Medium,
                        ),
                )
            }
        }
        if (items.isEmpty()) {
            item {
                YSuiteListItem(
                    title =
                        stringResource(
                            R.string
                                .yentry_empty,
                        ),
                    modifier =
                        Modifier.padding(
                            YSuiteSpacing.Medium,
                        ),
                )
            }
        }
        items(items, key = { it.id }) {
            candidate ->
            YSuiteListItem(
                title = candidate.label,
                subtitle =
                    candidate.packageName +
                        if (
                            candidate.state !=
                            YEntryCandidateState.Current
                        ) {
                            " · " +
                                candidate.state.name
                        } else {
                            ""
                        },
                modifier =
                    Modifier
                        .clickable {
                            model.select(
                                candidate.id,
                            )
                        }
                        .padding(
                            horizontal =
                                YSuiteSpacing.Medium,
                            vertical =
                                YSuiteSpacing.Small,
                        ),
                trailing = {
                    if (candidate.locked) {
                        YSuiteStatusBadge(
                            text =
                                stringResource(
                                    R.string
                                        .yentry_locked,
                                ),
                            tone =
                                YSuiteStatusTone
                                    .Warning,
                        )
                    } else if (
                        candidate.hidden
                    ) {
                        YSuiteStatusBadge(
                            text =
                                stringResource(
                                    R.string
                                        .yentry_hidden,
                                ),
                            tone =
                                YSuiteStatusTone
                                    .Neutral,
                        )
                    }
                },
            )
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
                YSuiteSwitchItem(
                    title =
                        stringResource(
                            R.string
                                .yentry_locked,
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
                    if (
                        candidate.rootBlocked
                    ) {
                        YSuiteStatusBadge(
                            text =
                                stringResource(
                                    R.string
                                        .yentry_component_protected,
                                ),
                            tone =
                                YSuiteStatusTone
                                    .Warning,
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
                    }
                }
            }
        }
    }
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
        YEntryAppFilter.Hidden ->
            stringResource(
                R.string.yentry_filter_hidden,
            )
        YEntryAppFilter.Locked ->
            stringResource(
                R.string.yentry_filter_locked,
            )
    }
