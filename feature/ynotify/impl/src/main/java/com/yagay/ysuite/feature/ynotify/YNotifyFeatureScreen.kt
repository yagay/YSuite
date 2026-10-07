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

    YNotifyWorkspace(
        title =
            stringResource(
                R.string.ynotify_title,
            ),
        navigationIcon = {
            YSuiteHostNavigationButton()
        },
        search = {
            YSuiteSearchField(
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
                RuntimeControls(
                    state = state,
                    model = model,
                )
            }
        },
        details =
            selected?.let {
                {
                    EventDetail(selected)
                }
            },
    ) {
        EventList(model)
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
        item {
            Row(
                modifier =
                    Modifier.padding(
                        YSuiteSpacing.Medium,
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
                                .ynotify_reclassify,
                        ),
                    onClick =
                        model::reclassify,
                )
                YSuiteSecondaryButton(
                    text =
                        stringResource(
                            R.string
                                .ynotify_export,
                        ),
                    onClick = model::export,
                )
                YSuiteSecondaryButton(
                    text =
                        stringResource(
                            R.string
                                .ynotify_clear,
                        ),
                    onClick = model::clear,
                )
            }
        }
        state.exportUri?.let {
            item {
                YSuiteStatusBadge(
                    text =
                        stringResource(
                            R.string
                                .ynotify_exported,
                        ),
                    tone =
                        YSuiteStatusTone
                            .Positive,
                    modifier =
                        Modifier.padding(
                            horizontal =
                                YSuiteSpacing.Medium,
                        ),
                )
            }
        }
        if (events.isEmpty()) {
            item {
                YSuiteListItem(
                    title =
                        stringResource(
                            R.string
                                .ynotify_empty,
                        ),
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
