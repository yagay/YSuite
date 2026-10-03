package com.yagay.ydownload

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yagay.yui.YActionRow
import com.yagay.yui.YFeatureCard
import com.yagay.yui.YSettingSwitch
import com.yagay.yui.YStatusRow
import com.yagay.yui.YStatusTone

@Composable
fun YDownloadHookScopeCard(context: Context) {
    val candidates = remember(context) { YDownloadHookScopeAdvisor.recommendedCandidates(context) }
    var settings by remember(context) { mutableStateOf(YDownloadEnhancedSettings.load(context)) }
    var userAgentDraft by remember(context) { mutableStateOf(settings.userAgent) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        YFeatureCard(
            title = stringResource(R.string.ydownload_hook_scope_title),
            subtitle = stringResource(R.string.ydownload_hook_scope_summary),
        ) {
            YStatusRow(
                stringResource(R.string.hook_scope_system_provider),
                stringResource(R.string.hook_scope_do_not_hook),
                YStatusTone.Good,
            )
            Text(stringResource(R.string.ydownload_hook_scope_rule))
            if (candidates.isEmpty()) {
                Text(stringResource(R.string.ydownload_scope_no_candidates))
            } else {
                Text(stringResource(R.string.ydownload_scope_detected_candidates))
                candidates.take(12).forEach { candidate ->
                    val reason = when (candidate.signal) {
                        YDownloadHookScopeAdvisor.Signal.DOWNLOAD_RECEIVER -> stringResource(R.string.ydownload_scope_download_listener)
                        YDownloadHookScopeAdvisor.Signal.BROWSER -> stringResource(R.string.ydownload_scope_browser)
                        YDownloadHookScopeAdvisor.Signal.COMMON_CALLER -> stringResource(R.string.ydownload_scope_common_caller)
                    }
                    YStatusRow(
                        candidate.displayName,
                        reason,
                        if (candidate.signal == YDownloadHookScopeAdvisor.Signal.DOWNLOAD_RECEIVER) {
                            YStatusTone.Good
                        } else {
                            YStatusTone.Neutral
                        },
                    )
                }
                if (candidates.size > 12) {
                    Text(stringResource(R.string.hook_scope_more_candidates, candidates.size - 12))
                }
            }
            Text(stringResource(R.string.ydownload_scope_signal_note))
            Text(stringResource(R.string.ydownload_hook_scope_note))
        }

        YFeatureCard(
            title = stringResource(R.string.enhanced_network_tools),
            subtitle = stringResource(R.string.enhanced_network_tools_summary),
        ) {
            OutlinedTextField(
                value = userAgentDraft,
                onValueChange = { userAgentDraft = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.user_agent)) },
                singleLine = true,
            )
            YActionRow {
                Button(
                    onClick = {
                        settings = YDownloadEnhancedSettings.update(context) { copy(userAgent = userAgentDraft) }
                        userAgentDraft = settings.userAgent
                    },
                ) { Text(stringResource(R.string.apply_user_agent)) }
                OutlinedButton(
                    onClick = {
                        userAgentDraft = YDownloadEnhancedSettings.DEFAULT_USER_AGENT
                        settings = YDownloadEnhancedSettings.update(context) {
                            copy(userAgent = YDownloadEnhancedSettings.DEFAULT_USER_AGENT)
                        }
                    },
                ) { Text(stringResource(R.string.reset_user_agent)) }
            }

            YStatusRow(
                stringResource(R.string.speed_limit),
                if (settings.speedLimitKib == 0) stringResource(R.string.unlimited)
                else stringResource(R.string.speed_limit_value, settings.speedLimitKib),
                YStatusTone.Neutral,
            )
            YActionRow {
                listOf(0, 512, 1024, 2048).forEach { limit ->
                    OutlinedButton(
                        onClick = {
                            settings = YDownloadEnhancedSettings.update(context) { copy(speedLimitKib = limit) }
                        },
                    ) {
                        Text(
                            if (limit == 0) stringResource(R.string.unlimited)
                            else stringResource(R.string.speed_limit_value, limit),
                        )
                    }
                }
            }
            YActionRow {
                listOf(4096, 8192, 16384).forEach { limit ->
                    OutlinedButton(
                        onClick = {
                            settings = YDownloadEnhancedSettings.update(context) { copy(speedLimitKib = limit) }
                        },
                    ) { Text(stringResource(R.string.speed_limit_value, limit)) }
                }
            }
            YSettingSwitch(
                title = stringResource(R.string.calculate_sha256),
                subtitle = stringResource(R.string.calculate_sha256_summary),
                checked = settings.calculateSha256,
                onCheckedChange = { enabled ->
                    settings = YDownloadEnhancedSettings.update(context) { copy(calculateSha256 = enabled) }
                },
            )
        }
    }
}
