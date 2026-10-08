package com.yagay.ysuite.feature.ynotify

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
import androidx.compose.ui.platform.LocalContext
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
import com.yagay.ysuite.designsystem.theme.YSuiteSpacing
import com.yagay.ysuite.feature.ynotify.api.YNotifyEvent
import com.yagay.ysuite.feature.ynotify.api.YNotifyEventType
import com.yagay.ysuite.feature.ynotify.api.YNotifyNotificationKind
import com.yagay.ysuite.productui.featurelayout.YNotifyWorkspace
import com.yagay.ysuite.ui.YSuiteHostNavigationButton
import java.text.DateFormat
import java.util.Date

@Composable
fun YNotifyFeatureScreen() {
    val context =
        LocalContext.current.applicationContext
    val model: YNotifyViewModel =
        viewModel(
            factory =
                YNotifyViewModel.Factory(
                    context,
                ),
        )
    val state by
        model.state.collectAsStateWithLifecycle()
    val selected = model.selectedEvent()
    var showAdvanced by remember { mutableStateOf(false) }

    YNotifyWorkspace(
        title =
            stringResource(
                R.string.ynotify_title,
            ),
        navigationIcon = {
            YSuiteHostNavigationButton()
        },
        search = {
            if (state.viewMode != YNotifyViewMode.Settings) YSuiteSearchField(
                value = state.query,
                onValueChange =
                    model::setQuery,
                label =
                    stringResource(
                        R.string
                            .ynotify_search,
                    ),
                modifier =
                    Modifier.padding(
                        horizontal =
                            YSuiteSpacing.Medium,
                        vertical =
                            YSuiteSpacing.Small,
                    ),
            )
        },
        filters = {
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
                YSuiteFilterBar(
                    options =
                        YNotifyViewMode.entries
                            .map {
                                YSuiteFilterOption(
                                    it.name,
                                    viewModeLabel(it),
                                )
                            },
                    selectedId =
                        state.viewMode.name,
                    onSelected = {
                        runCatching {
                            YNotifyViewMode
                                .valueOf(it)
                        }.getOrNull()
                            ?.let(
                                model::setViewMode,
                            )
                    },
                )
                if (state.viewMode == YNotifyViewMode.History) {
                    YSuiteFilterBar(
                        options = YNotifyTimelineFilter.entries.map {
                            YSuiteFilterOption(it.name, timelineFilterLabel(it))
                        },
                        selectedId = state.timelineFilter.name,
                        onSelected = { key ->
                            runCatching { YNotifyTimelineFilter.valueOf(key) }
                                .getOrNull()?.let(model::setTimelineFilter)
                        },
                    )
                    YSuiteSecondaryButton(
                        text = stringResource(
                            if (showAdvanced) R.string.ynotify_hide_advanced_filters
                            else R.string.ynotify_advanced_filters,
                        ),
                        onClick = { showAdvanced = !showAdvanced },
                    )
                    if (showAdvanced) {
                    YSuiteFilterBar(
                    options =
                        YNotifyTypeFilter.entries
                            .map {
                                YSuiteFilterOption(
                                    it.name,
                                    filterLabel(it),
                                )
                            },
                    selectedId =
                        state.typeFilter.name,
                    onSelected = {
                        runCatching {
                            YNotifyTypeFilter
                                .valueOf(it)
                        }.getOrNull()
                            ?.let(
                                model::setTypeFilter,
                            )
                    },
                )
                    YSuiteFilterBar(
                        options =
                            YNotifyKindFilter.entries
                                .map {
                                    YSuiteFilterOption(
                                        it.name,
                                        kindFilterLabel(it),
                                    )
                                },
                        selectedId =
                            state.kindFilter.name,
                        onSelected = {
                            runCatching {
                                YNotifyKindFilter
                                    .valueOf(it)
                            }.getOrNull()
                                ?.let(
                                    model::setKindFilter,
                                )
                        },
                    )
                    }
                    state.selectedPackage?.let {
                        selectedPackage ->
                        YSuiteListItem(
                            title =
                                stringResource(
                                    R.string
                                        .ynotify_app_filter,
                                ),
                            subtitle =
                                selectedPackage,
                            modifier =
                                Modifier.clickable {
                                    model.selectPackage(
                                        null,
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
                                        if (
                                            model.isPaused(
                                                selectedPackage,
                                            )
                                        ) {
                                            R.string
                                                .ynotify_resume_capture
                                        } else {
                                            R.string
                                                .ynotify_pause_capture
                                        },
                                    ),
                                onClick = {
                                    model.togglePaused(
                                        selectedPackage,
                                    )
                                },
                            )
                            YSuiteSecondaryButton(
                                text =
                                    stringResource(
                                        if (
                                            model.isRedacted(
                                                selectedPackage,
                                            )
                                        ) {
                                            R.string
                                                .ynotify_show_content
                                        } else {
                                            R.string
                                                .ynotify_redact_content
                                        },
                                    ),
                                onClick = {
                                    model.toggleRedacted(
                                        selectedPackage,
                                    )
                                },
                            )
                        }
                    }
                }
            }
        },
        details = if (state.viewMode == YNotifyViewMode.History) {
            selected?.let { { EventDetail(selected) } }
        } else null,
    ) {
        when (state.viewMode) {
            YNotifyViewMode.Apps -> AppList(model)
            YNotifyViewMode.Settings -> NotifySettings(model)
            YNotifyViewMode.History -> EventList(model)
        }
    }
}

@Composable
private fun NotifySettings(model: YNotifyViewModel) {
    val state by model.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(YSuiteSpacing.Medium),
    ) {
        item {
            YSuiteSection(
                title = stringResource(R.string.ynotify_settings_access),
                modifier = Modifier.padding(YSuiteSpacing.Medium),
            ) {
                RuntimeControls(state, model)
                YSuiteSecondaryButton(
                    text = stringResource(R.string.ynotify_rebind_listener),
                    onClick = model::requestReconnect,
                )
                state.captureMessage?.let { message ->
                    YSuiteStatusBadge(
                        text = when (message) {
                            "rebind_requested" -> stringResource(R.string.ynotify_rebind_requested)
                            "rebind_failed" -> stringResource(R.string.ynotify_rebind_failed)
                            else -> stringResource(R.string.ynotify_settings_unavailable)
                        },
                        tone = YSuiteStatusTone.Warning,
                    )
                }
                YSuiteListItem(
                    title = stringResource(R.string.ynotify_captured_count, state.runtimeStatus.storedEventCount),
                    subtitle = stringResource(R.string.ynotify_capture_scope_explanation),
                )
            }
        }
        item {
            YSuiteSection(
                title = stringResource(R.string.ynotify_settings_data),
                modifier = Modifier.padding(horizontal = YSuiteSpacing.Medium),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(YSuiteSpacing.Small)) {
                    YSuiteSecondaryButton(
                        text = stringResource(R.string.ynotify_reclassify),
                        onClick = model::reclassify,
                    )
                    YSuiteSecondaryButton(
                        text = stringResource(R.string.ynotify_export),
                        onClick = model::export,
                    )
                }
                state.exportUri?.let {
                    YSuiteStatusBadge(
                        text = stringResource(R.string.ynotify_exported),
                        tone = YSuiteStatusTone.Positive,
                    )
                }
                YSuiteSecondaryButton(
                    text = stringResource(R.string.ynotify_clear),
                    onClick = {
                        android.app.AlertDialog.Builder(context)
                            .setTitle(R.string.ynotify_clear_confirm_title)
                            .setMessage(R.string.ynotify_clear_confirm_message)
                            .setNegativeButton(android.R.string.cancel, null)
                            .setPositiveButton(R.string.ynotify_clear) { _, _ -> model.clear() }
                            .show()
                    },
                )
            }
        }
    }
}

@Composable
private fun RuntimeControls(
    state: YNotifyUiState,
    model: YNotifyViewModel,
) {
    Row(
        horizontalArrangement =
            Arrangement.spacedBy(
                YSuiteSpacing.Small,
            ),
    ) {
        YSuiteStatusBadge(
            text =
                if (
                    state.runtimeStatus
                        .notificationListenerConnected
                ) {
                    stringResource(
                        R.string
                            .ynotify_listener_ready,
                    )
                } else {
                    stringResource(
                        R.string
                            .ynotify_listener_off,
                    )
                },
            tone =
                if (
                    state.runtimeStatus
                        .notificationListenerConnected
                ) {
                    YSuiteStatusTone.Positive
                } else {
                    YSuiteStatusTone.Warning
                },
        )
        YSuiteStatusBadge(
            text =
                if (
                    state.runtimeStatus
                        .accessibilityConnected
                ) {
                    stringResource(
                        R.string
                            .ynotify_accessibility_ready,
                    )
                } else {
                    stringResource(
                        R.string
                            .ynotify_accessibility_off,
                    )
                },
            tone =
                if (
                    state.runtimeStatus
                        .accessibilityConnected
                ) {
                    YSuiteStatusTone.Positive
                } else {
                    YSuiteStatusTone.Warning
                },
        )
    }
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
                        .ynotify_notification_access,
                ),
            onClick =
                model::openNotificationAccess,
        )
        YSuiteSecondaryButton(
            text =
                stringResource(
                    R.string
                        .ynotify_accessibility_access,
                ),
            onClick =
                model::openAccessibility,
        )
    }
    YSuiteListItem(
        title = stringResource(R.string.ynotify_listener_grant_state),
        subtitle = stringResource(
            if (state.notificationAuthorized) R.string.ynotify_grant_yes
            else R.string.ynotify_grant_no,
        ),
    )
    YSuiteListItem(
        title = stringResource(R.string.ynotify_accessibility_grant_state),
        subtitle = stringResource(
            if (state.accessibilityAuthorized) R.string.ynotify_grant_yes
            else R.string.ynotify_grant_no,
        ),
    )
    YSuiteFilterBar(
        options = listOf(7, 30, 90, 0).map { days ->
            YSuiteFilterOption(
                days.toString(),
                if (days == 0) stringResource(R.string.ynotify_retention_forever)
                else stringResource(R.string.ynotify_retention_days, days),
            )
        },
        selectedId = state.retentionDays.toString(),
        onSelected = { days -> days.toIntOrNull()?.let(model::setRetentionDays) },
    )
}

@Composable
private fun AppList(
    model: YNotifyViewModel,
) {
    val apps =
        model.appSummaries()
    LazyColumn(
        modifier =
            Modifier.fillMaxSize(),
    ) {
        if (apps.isEmpty()) {
            item {
                YSuiteListItem(
                    title =
                        stringResource(
                            R.string
                                .ynotify_no_apps,
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
            key = {
                it.packageName
            },
        ) { app ->
            YSuiteListItem(
                title = app.label,
                subtitle =
                    stringResource(
                        R.string
                            .ynotify_app_summary,
                        app.count,
                        DateFormat
                            .getDateTimeInstance()
                            .format(
                                Date(
                                    app.latestAt,
                                ),
                            ),
                    ),
                modifier =
                    Modifier
                        .clickable {
                            model.selectPackage(
                                app.packageName,
                            )
                        }
                        .padding(
                            horizontal =
                                YSuiteSpacing.Medium,
                            vertical =
                                YSuiteSpacing.Small,
                        ),
            )
        }
    }
}

@Composable
private fun EventList(
    model: YNotifyViewModel,
) {
    val state by
        model.state.collectAsStateWithLifecycle()
    val events = model.visibleEvents()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
    ) {
        if (events.isEmpty()) {
            item {
                YSuiteListItem(
                    title =
                        stringResource(
                            R.string
                                .ynotify_empty,
                        ),
                    subtitle = stringResource(R.string.ynotify_capture_scope_explanation),
                    modifier =
                        Modifier.padding(
                            YSuiteSpacing.Medium,
                        ),
                )
            }
        }
        items(
            items = events,
            key = { it.id },
        ) { event ->
            YSuiteListItem(
                title =
                    event.title
                        ?.takeIf {
                            it.isNotBlank()
                        }
                        ?: event.appLabel,
                subtitle =
                    eventSummary(event),
                modifier =
                    Modifier
                        .clickable {
                            model.select(event)
                        }
                        .padding(
                            horizontal =
                                YSuiteSpacing.Medium,
                            vertical =
                                YSuiteSpacing.Small,
                        ),
                trailing = {
                    YSuiteStatusBadge(
                        text =
                            eventTypeLabel(
                                event.eventType,
                            ),
                        tone =
                            if (
                                event.headsUp ||
                                event.bubbleShown ||
                                event
                                    .fullScreenShown
                            ) {
                                YSuiteStatusTone
                                    .Positive
                            } else {
                                YSuiteStatusTone
                                    .Neutral
                            },
                    )
                },
            )
        }
    }
}

@Composable
private fun EventDetail(
    event: YNotifyEvent,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement =
            Arrangement.spacedBy(
                YSuiteSpacing.Medium,
            ),
    ) {
        item {
            YSuiteSection(
                title = event.appLabel,
                modifier =
                    Modifier.padding(
                        YSuiteSpacing.Medium,
                    ),
            ) {
                YSuiteListItem(
                    title =
                        event.packageName,
                    subtitle =
                        DateFormat
                            .getDateTimeInstance()
                            .format(
                                Date(
                                    event.postedAt,
                                ),
                            ),
                )
                YSuiteListItem(
                    title =
                        stringResource(
                            R.string
                                .ynotify_type,
                        ),
                    subtitle =
                        eventTypeLabel(
                            event.eventType,
                        ),
                )
                if (
                    event.eventType ==
                    YNotifyEventType.Notification
                ) {
                    YSuiteListItem(
                        title =
                            stringResource(
                                R.string
                                    .ynotify_kind,
                            ),
                        subtitle =
                            notificationKindLabel(
                                event
                                    .notificationKind,
                            ),
                    )
                }
                YSuiteListItem(
                    title =
                        stringResource(
                            R.string
                                .ynotify_content,
                        ),
                    subtitle =
                        event.fullText
                            ?: event.text
                            ?: stringResource(
                                R.string
                                    .ynotify_no_content,
                            ),
                )
                if (event.headsUp) {
                    YSuiteStatusBadge(
                        text =
                            stringResource(
                                R.string
                                    .ynotify_heads_up,
                            ),
                        tone =
                            YSuiteStatusTone
                                .Positive,
                    )
                }
                if (event.bubbleShown) {
                    YSuiteStatusBadge(
                        text =
                            stringResource(
                                R.string
                                    .ynotify_bubble,
                            ),
                        tone =
                            YSuiteStatusTone
                                .Positive,
                    )
                }
                if (
                    event.fullScreenShown
                ) {
                    YSuiteStatusBadge(
                        text =
                            stringResource(
                                R.string
                                    .ynotify_fullscreen,
                            ),
                        tone =
                            YSuiteStatusTone
                                .Positive,
                    )
                }
                YSuiteListItem(
                    title =
                        stringResource(
                            R.string
                                .ynotify_source,
                        ),
                    subtitle = event.source,
                )
            }
        }
        item {
            YNotifyEventMetadata(event)
        }
    }
}

@Composable
private fun eventSummary(
    event: YNotifyEvent,
): String {
    val content =
        event.fullText
            ?.takeIf { it.isNotBlank() }
            ?: event.text
            ?.takeIf { it.isNotBlank() }
            ?: stringResource(
                R.string.ynotify_no_content,
            )
    return event.appLabel +
        " · " +
        DateFormat.getTimeInstance()
            .format(Date(event.postedAt)) +
        "\n" +
        content
}

@Composable
private fun viewModeLabel(
    value: YNotifyViewMode,
): String =
    when (value) {
        YNotifyViewMode.History ->
            stringResource(
                R.string
                    .ynotify_view_history,
            )
        YNotifyViewMode.Apps ->
            stringResource(
                R.string
                    .ynotify_view_apps,
            )
        YNotifyViewMode.Settings -> stringResource(R.string.ynotify_view_settings)
    }

@Composable
private fun timelineFilterLabel(value: YNotifyTimelineFilter): String =
    stringResource(
        when (value) {
            YNotifyTimelineFilter.All -> R.string.ynotify_filter_all
            YNotifyTimelineFilter.Notifications -> R.string.ynotify_filter_notifications
            YNotifyTimelineFilter.HeadsUp -> R.string.ynotify_heads_up
            YNotifyTimelineFilter.Bubble -> R.string.ynotify_bubble
            YNotifyTimelineFilter.FullScreen -> R.string.ynotify_fullscreen
            YNotifyTimelineFilter.Toast -> R.string.ynotify_filter_toast
            YNotifyTimelineFilter.Dialog -> R.string.ynotify_filter_dialog
            YNotifyTimelineFilter.Popup -> R.string.ynotify_filter_popup
            YNotifyTimelineFilter.Snackbar -> R.string.ynotify_filter_snackbar
        },
    )

@Composable
private fun kindFilterLabel(
    value: YNotifyKindFilter,
): String =
    when (value) {
        YNotifyKindFilter.All ->
            stringResource(
                R.string
                    .ynotify_kind_all,
            )
        YNotifyKindFilter.FullScreen ->
            stringResource(
                R.string
                    .ynotify_kind_fullscreen,
            )
        YNotifyKindFilter.Bubble ->
            stringResource(
                R.string
                    .ynotify_kind_bubble,
            )
        YNotifyKindFilter.Call ->
            stringResource(
                R.string
                    .ynotify_kind_call,
            )
        YNotifyKindFilter.Alarm ->
            stringResource(
                R.string
                    .ynotify_kind_alarm,
            )
        YNotifyKindFilter.Media ->
            stringResource(
                R.string
                    .ynotify_kind_media,
            )
        YNotifyKindFilter.Progress ->
            stringResource(
                R.string
                    .ynotify_kind_progress,
            )
        YNotifyKindFilter.ForegroundService ->
            stringResource(
                R.string
                    .ynotify_kind_foreground,
            )
        YNotifyKindFilter.Message ->
            stringResource(
                R.string
                    .ynotify_kind_message,
            )
        YNotifyKindFilter.System ->
            stringResource(
                R.string
                    .ynotify_kind_system,
            )
        YNotifyKindFilter.Ongoing ->
            stringResource(
                R.string
                    .ynotify_kind_ongoing,
            )
        YNotifyKindFilter.Silent ->
            stringResource(
                R.string
                    .ynotify_kind_silent,
            )
        YNotifyKindFilter.Standard ->
            stringResource(
                R.string
                    .ynotify_kind_standard,
            )
        YNotifyKindFilter.Unknown ->
            stringResource(
                R.string
                    .ynotify_kind_unknown,
            )
    }

@Composable
private fun filterLabel(
    value: YNotifyTypeFilter,
): String =
    when (value) {
        YNotifyTypeFilter.All ->
            stringResource(
                R.string.ynotify_filter_all,
            )
        YNotifyTypeFilter.Notifications ->
            stringResource(
                R.string
                    .ynotify_filter_notifications,
            )
        YNotifyTypeFilter.Toast ->
            stringResource(
                R.string.ynotify_filter_toast,
            )
        YNotifyTypeFilter.Dialog ->
            stringResource(
                R.string.ynotify_filter_dialog,
            )
        YNotifyTypeFilter.Snackbar ->
            stringResource(
                R.string
                    .ynotify_filter_snackbar,
            )
        YNotifyTypeFilter.Popup ->
            stringResource(
                R.string.ynotify_filter_popup,
            )
        YNotifyTypeFilter.SystemUi ->
            stringResource(
                R.string
                    .ynotify_filter_systemui,
            )
        YNotifyTypeFilter.OtherUi ->
            stringResource(
                R.string
                    .ynotify_filter_other,
            )
    }

@Composable
private fun eventTypeLabel(
    value: YNotifyEventType,
): String =
    when (value) {
        YNotifyEventType.Notification ->
            stringResource(
                R.string.ynotify_type_notification,
            )
        YNotifyEventType.Toast ->
            stringResource(
                R.string.ynotify_type_toast,
            )
        YNotifyEventType.Dialog ->
            stringResource(
                R.string.ynotify_type_dialog,
            )
        YNotifyEventType.Snackbar ->
            stringResource(
                R.string.ynotify_type_snackbar,
            )
        YNotifyEventType.Popup ->
            stringResource(
                R.string.ynotify_type_popup,
            )
        YNotifyEventType.SystemUi ->
            stringResource(
                R.string.ynotify_type_systemui,
            )
        YNotifyEventType.OtherUi ->
            stringResource(
                R.string.ynotify_type_other,
            )
    }

@Composable
private fun notificationKindLabel(
    value: YNotifyNotificationKind,
): String =
    when (value) {
        YNotifyNotificationKind.FullScreen ->
            stringResource(
                R.string.ynotify_kind_fullscreen,
            )
        YNotifyNotificationKind.Bubble ->
            stringResource(
                R.string.ynotify_kind_bubble,
            )
        YNotifyNotificationKind.Call ->
            stringResource(
                R.string.ynotify_kind_call,
            )
        YNotifyNotificationKind.Alarm ->
            stringResource(
                R.string.ynotify_kind_alarm,
            )
        YNotifyNotificationKind.Media ->
            stringResource(
                R.string.ynotify_kind_media,
            )
        YNotifyNotificationKind.Progress ->
            stringResource(
                R.string.ynotify_kind_progress,
            )
        YNotifyNotificationKind.ForegroundService ->
            stringResource(
                R.string.ynotify_kind_foreground,
            )
        YNotifyNotificationKind.Message ->
            stringResource(
                R.string.ynotify_kind_message,
            )
        YNotifyNotificationKind.System ->
            stringResource(
                R.string.ynotify_kind_system,
            )
        YNotifyNotificationKind.Ongoing ->
            stringResource(
                R.string.ynotify_kind_ongoing,
            )
        YNotifyNotificationKind.Silent ->
            stringResource(
                R.string.ynotify_kind_silent,
            )
        YNotifyNotificationKind.Standard ->
            stringResource(
                R.string.ynotify_kind_standard,
            )
        YNotifyNotificationKind.Unknown ->
            stringResource(
                R.string.ynotify_kind_unknown,
            )
    }
