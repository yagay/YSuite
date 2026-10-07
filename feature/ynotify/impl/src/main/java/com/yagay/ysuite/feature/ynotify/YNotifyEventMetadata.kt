package com.yagay.ysuite.feature.ynotify

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.yagay.ysuite.designsystem.component.YSuiteListItem
import com.yagay.ysuite.designsystem.component.YSuiteSection
import com.yagay.ysuite.designsystem.theme.YSuiteSpacing
import com.yagay.ysuite.feature.ynotify.api.YNotifyEvent
import androidx.compose.foundation.layout.padding

@Composable
internal fun YNotifyEventMetadata(
    event: YNotifyEvent,
) {
    if (
        event.eventType !=
        com.yagay.ysuite.feature.ynotify.api
            .YNotifyEventType.Notification
    ) {
        return
    }

    YSuiteSection(
        title =
            stringResource(
                R.string
                    .ynotify_detail_metadata,
            ),
        modifier =
            Modifier.padding(
                horizontal =
                    YSuiteSpacing.Medium,
            ),
    ) {
        event.subText
            ?.takeIf(String::isNotBlank)
            ?.let {
                YSuiteListItem(
                    title =
                        stringResource(
                            R.string
                                .ynotify_detail_subtext,
                        ),
                    subtitle = it,
                )
            }
        event.summaryText
            ?.takeIf(String::isNotBlank)
            ?.let {
                YSuiteListItem(
                    title =
                        stringResource(
                            R.string
                                .ynotify_detail_summary,
                        ),
                    subtitle = it,
                )
            }
        YSuiteListItem(
            title =
                stringResource(
                    R.string
                        .ynotify_detail_notification_id,
                ),
            subtitle =
                event.notificationId
                    .toString(),
        )
        event.notificationTag
            ?.takeIf(String::isNotBlank)
            ?.let {
                YSuiteListItem(
                    title =
                        stringResource(
                            R.string
                                .ynotify_detail_tag,
                        ),
                    subtitle = it,
                )
            }
        event.channelId
            ?.takeIf(String::isNotBlank)
            ?.let {
                YSuiteListItem(
                    title =
                        stringResource(
                            R.string
                                .ynotify_detail_channel,
                        ),
                    subtitle =
                        listOfNotNull(
                            event.channelName,
                            it,
                            event.channelDescription,
                        ).filter(
                            String::isNotBlank,
                        ).joinToString("\n"),
                )
            }
        event.groupKey
            ?.takeIf(String::isNotBlank)
            ?.let {
                YSuiteListItem(
                    title =
                        stringResource(
                            R.string
                                .ynotify_detail_group,
                        ),
                    subtitle =
                        stringResource(
                            R.string
                                .ynotify_detail_group_value,
                            it,
                            event.groupSummary,
                        ),
                )
            }
        event.category
            ?.takeIf(String::isNotBlank)
            ?.let {
                YSuiteListItem(
                    title =
                        stringResource(
                            R.string
                                .ynotify_detail_category,
                        ),
                    subtitle = it,
                )
            }
        event.template
            ?.takeIf(String::isNotBlank)
            ?.let {
                YSuiteListItem(
                    title =
                        stringResource(
                            R.string
                                .ynotify_detail_template,
                        ),
                    subtitle = it,
                )
            }
        YSuiteListItem(
            title =
                stringResource(
                    R.string
                        .ynotify_detail_ranking,
                ),
            subtitle =
                stringResource(
                    R.string
                        .ynotify_detail_ranking_value,
                    event.importance,
                    event.channelImportance,
                    event.conversation,
                    event.rankingCanBubble,
                    event.rankingAmbient,
                    event.rankingSuspended,
                ),
        )
        YSuiteListItem(
            title =
                stringResource(
                    R.string
                        .ynotify_detail_flags,
                ),
            subtitle =
                stringResource(
                    R.string
                        .ynotify_detail_flags_value,
                    event.flags,
                    event.clearable,
                    event.ongoing,
                    event.foregroundService,
                    event.bubble,
                    event.fullScreen,
                    event.silent,
                ),
        )
        if (
            event.progressMax > 0 ||
            event.progress > 0 ||
            event.progressIndeterminate
        ) {
            YSuiteListItem(
                title =
                    stringResource(
                        R.string
                            .ynotify_detail_progress,
                    ),
                subtitle =
                    stringResource(
                        R.string
                            .ynotify_detail_progress_value,
                        event.progress,
                        event.progressMax,
                        event.progressIndeterminate,
                    ),
            )
        }
        event.messagesJson
            ?.takeIf {
                it.isNotBlank() &&
                    it != "[]"
            }
            ?.let {
                YSuiteListItem(
                    title =
                        stringResource(
                            R.string
                                .ynotify_detail_messages,
                        ),
                    subtitle = it,
                )
            }
        event.actionsJson
            ?.takeIf {
                it.isNotBlank() &&
                    it != "[]"
            }
            ?.let {
                YSuiteListItem(
                    title =
                        stringResource(
                            R.string
                                .ynotify_detail_actions,
                        ),
                    subtitle = it,
                )
            }
        event.rawExtras
            ?.takeIf(String::isNotBlank)
            ?.let {
                YSuiteListItem(
                    title =
                        stringResource(
                            R.string
                                .ynotify_detail_raw_extras,
                        ),
                    subtitle = it,
                )
            }
        if (event.removalReason != 0) {
            YSuiteListItem(
                title =
                    stringResource(
                        R.string
                            .ynotify_detail_removal_reason,
                    ),
                subtitle =
                    event.removalReason
                        .toString(),
            )
        }
    }
}
