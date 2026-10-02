package com.yagay.YEntryCleaner.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yagay.YEntryCleaner.R
import com.yagay.yui.YBottomActionBar
import com.yagay.yui.YDimens
import com.yagay.yui.YFeatureCard
import com.yagay.yui.YFeatureEmpty
import com.yagay.yui.YFeatureSectionHeader
import com.yagay.yui.YStatusRow
import com.yagay.yui.YStatusTone

@Composable
internal fun ScopeDialog(status: ModuleStatus, requestScope: () -> Unit, refresh: () -> Unit, dismiss: () -> Unit) {
    FullScreenDetails(
        onDismissRequest = dismiss,
        title = { Text(stringResource(R.string.scope_title)) },
        text = {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    stringResource(R.string.scope_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                YFeatureSectionHeader(
                    title = stringResource(R.string.scope_detected_hosts),
                    subtitle = stringResource(R.string.scope_advanced),
                )
                if (status.detection.hosts.isEmpty()) {
                    YFeatureEmpty(stringResource(R.string.scope_no_hosts))
                } else {
                    status.detection.hosts.forEach { host ->
                        YFeatureCard(
                            title = host.packageName,
                            subtitle = host.className,
                            detail = stringResource(
                                R.string.scope_host_details,
                                host.packageName,
                                host.className,
                                host.processName,
                                host.scenarios.joinToString(stringResource(R.string.yentry_list_separator)),
                            ),
                        ) {
                            when {
                                host.requiresManualScope -> YStatusRow(
                                    label = stringResource(R.string.view_filter),
                                    value = stringResource(R.string.scope_manual_host_warning),
                                    tone = YStatusTone.Warning,
                                )
                                host.packageName == "android" -> YStatusRow(
                                    label = "android",
                                    value = stringResource(R.string.scope_android_system_note),
                                    tone = YStatusTone.Neutral,
                                )
                            }
                        }
                    }
                }

                val unconfirmed = status.detection.installedCandidates - status.detection.hosts.map { it.packageName }.toSet()
                if (unconfirmed.isNotEmpty()) {
                    YStatusRow(
                        label = stringResource(R.string.app_type_filter),
                        value = stringResource(R.string.scope_unconfirmed_installed, unconfirmed.sorted().joinToString("\n")),
                        tone = YStatusTone.Warning,
                    )
                }

                YFeatureSectionHeader(stringResource(R.string.scope_granted))
                val grantedText = if (!status.scopeKnown) {
                    stringResource(R.string.scope_grant_unknown)
                } else {
                    status.grantedScope.sorted().joinToString("\n").ifEmpty { stringResource(R.string.common_none) }
                }
                YFeatureCard(
                    title = stringResource(R.string.scope_granted),
                    subtitle = grantedText,
                ) {
                    if (status.scopeKnown && status.extraScope.isNotEmpty()) {
                        YStatusRow(
                            label = stringResource(R.string.view_filter),
                            value = stringResource(R.string.scope_extra_grants, status.extraScope.sorted().joinToString("\n")),
                            tone = YStatusTone.Warning,
                        )
                    }
                }

                YFeatureSectionHeader(stringResource(R.string.scope_running_targets))
                if (status.runningTargets.isEmpty()) {
                    YFeatureEmpty(stringResource(R.string.scope_no_running_targets))
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
                        YStatusRow(
                            label = target.processName,
                            value = stringResource(R.string.scope_target_details, target.processName, stateLabel, target.version),
                            tone = tone,
                        )
                    }
                }

                YFeatureSectionHeader(stringResource(R.string.root_screen_title))
                YStatusRow(
                    label = stringResource(R.string.root_screen_title),
                    value = stringResource(R.string.scope_runtime_warning),
                    tone = YStatusTone.Warning,
                )
                status.detection.warnings.forEach { warning ->
                    YStatusRow("Warning", warning, YStatusTone.Error)
                }
                status.message?.let { YStatusRow("Status", it, YStatusTone.Neutral) }
                status.error?.let { YStatusRow("Error", it, YStatusTone.Error) }

                TextButton(onClick = refresh, enabled = !status.requesting) {
                    Text(stringResource(R.string.scope_recheck))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = requestScope,
                enabled = status.connected && status.scopeKnown && !status.requesting && status.missingScope.isNotEmpty(),
            ) {
                Text(
                    when {
                        status.requesting -> stringResource(R.string.scope_requesting)
                        status.detection.recommended.isEmpty() -> stringResource(R.string.scope_no_recommended)
                        status.scopeKnown && status.missingScope.isEmpty() -> stringResource(R.string.scope_recommended_granted)
                        else -> stringResource(R.string.scope_request_missing, status.missingScope.size)
                    },
                )
            }
        },
        dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.common_close)) } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FullScreenDetails(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable () -> Unit,
) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismissRequest,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = title,
                    navigationIcon = {
                        IconButton(onClick = onDismissRequest) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.common_back))
                        }
                    },
                )
            },
            bottomBar = {
                YBottomActionBar {
                    dismissButton()
                    confirmButton()
                }
            },
        ) { padding ->
            Box(
                Modifier
                    .padding(padding)
                    .padding(horizontal = YDimens.ScreenHorizontal),
            ) { text() }
        }
    }
}
