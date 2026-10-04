package com.yagay.YEntryCleaner.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.yagay.YEntryCleaner.R
import com.yagay.yui.YActionSpec
import com.yagay.yui.YActionStyle
import com.yagay.yui.YDimens
import com.yagay.yui.YFullScreenDialog
import com.yagay.yui.YListItem
import com.yagay.yui.YNotice
import com.yagay.yui.YNoticeTone
import com.yagay.yui.YPageRole
import com.yagay.yui.YSectionHeader
import com.yagay.yui.YSecondaryButton
import com.yagay.yui.YStatusItem
import com.yagay.yui.YStatusTone

@Composable
internal fun ScopeDialog(status: ModuleStatus, requestScope: () -> Unit, refresh: () -> Unit, dismiss: () -> Unit) {
    val requestLabel = when {
        status.requesting -> stringResource(R.string.scope_requesting)
        status.detection.recommended.isEmpty() -> stringResource(R.string.scope_no_recommended)
        status.scopeKnown && status.missingScope.isEmpty() -> stringResource(R.string.scope_recommended_granted)
        else -> stringResource(R.string.scope_request_missing, status.missingScope.size)
    }
    YFullScreenDialog(
        title = stringResource(R.string.scope_title),
        backContentDescription = stringResource(R.string.common_back),
        onDismissRequest = dismiss,
        role = YPageRole.DETAIL,
        actions = listOf(
            YActionSpec(
                label = stringResource(R.string.common_close),
                onClick = dismiss,
            ),
            YActionSpec(
                label = requestLabel,
                enabled = status.connected && status.scopeKnown && !status.requesting && status.missingScope.isNotEmpty(),
                style = YActionStyle.PRIMARY,
                onClick = requestScope,
            ),
        ),
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = YDimens.ScreenHorizontal)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(YDimens.ControlGap),
        ) {
            Text(
                stringResource(R.string.scope_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            YSectionHeader(
                title = stringResource(R.string.scope_detected_hosts),
                subtitle = stringResource(R.string.scope_advanced),
            )
            if (status.detection.hosts.isEmpty()) {
                YNotice(stringResource(R.string.scope_no_hosts))
            } else {
                status.detection.hosts.forEach { host ->
                    YListItem(
                        title = host.packageName,
                        subtitle = host.className,
                        detail = stringResource(
                            R.string.scope_host_details,
                            host.packageName,
                            host.className,
                            host.processName,
                            host.scenarios.joinToString(stringResource(R.string.yentry_list_separator)),
                        ),
                    )
                    when {
                        host.requiresManualScope -> YStatusItem(
                            title = stringResource(R.string.view_filter),
                            value = stringResource(R.string.scope_manual_host_warning),
                            tone = YStatusTone.Warning,
                        )
                        host.packageName == "android" -> YStatusItem(
                            title = "android",
                            value = stringResource(R.string.scope_android_system_note),
                        )
                    }
                }
            }

            val unconfirmed = status.detection.installedCandidates - status.detection.hosts.map { it.packageName }.toSet()
            if (unconfirmed.isNotEmpty()) {
                YNotice(
                    text = stringResource(R.string.scope_unconfirmed_installed, unconfirmed.sorted().joinToString("\n")),
                    tone = YNoticeTone.WARNING,
                )
            }

            YSectionHeader(stringResource(R.string.scope_granted))
            val grantedText = if (!status.scopeKnown) {
                stringResource(R.string.scope_grant_unknown)
            } else {
                status.grantedScope.sorted().joinToString("\n").ifEmpty { stringResource(R.string.common_none) }
            }
            YListItem(
                title = stringResource(R.string.scope_granted),
                subtitle = grantedText,
            )
            if (status.scopeKnown && status.extraScope.isNotEmpty()) {
                YStatusItem(
                    title = stringResource(R.string.view_filter),
                    value = stringResource(R.string.scope_extra_grants, status.extraScope.sorted().joinToString("\n")),
                    tone = YStatusTone.Warning,
                )
            }

            YSectionHeader(stringResource(R.string.scope_running_targets))
            if (status.runningTargets.isEmpty()) {
                YNotice(stringResource(R.string.scope_no_running_targets))
            } else {
                status.runningTargets.forEach { target ->
                    val stateLabel = when (target.state) {
                        "UP_TO_DATE" -> stringResource(R.string.scope_target_up_to_date)
                        "STALE" -> stringResource(R.string.scope_target_stale)
                        "RELOADING" -> stringResource(R.string.scope_target_reloading)
                        "FAILED" -> stringResource(R.string.scope_target_failed)
                        else -> target.state
                    }
                    val tone = when (target.state) {
                        "UP_TO_DATE" -> YStatusTone.Good
                        "FAILED" -> YStatusTone.Error
                        "STALE", "RELOADING" -> YStatusTone.Warning
                        else -> YStatusTone.Neutral
                    }
                    YStatusItem(
                        title = target.processName,
                        value = stringResource(R.string.scope_target_details, target.processName, stateLabel, target.version),
                        tone = tone,
                    )
                }
            }

            YSectionHeader(stringResource(R.string.root_screen_title))
            YNotice(
                text = stringResource(R.string.scope_runtime_warning),
                tone = YNoticeTone.WARNING,
            )
            status.detection.warnings.forEach { warning ->
                YStatusItem(stringResource(R.string.yentry_status_warning), warning, tone = YStatusTone.Error)
            }
            status.message?.let {
                YStatusItem(stringResource(R.string.yentry_status_status), it)
            }
            status.error?.let {
                YStatusItem(stringResource(R.string.yentry_status_error), it, tone = YStatusTone.Error)
            }
            YSecondaryButton(
                text = stringResource(R.string.scope_recheck),
                onClick = refresh,
                enabled = !status.requesting,
            )
        }
    }
}
