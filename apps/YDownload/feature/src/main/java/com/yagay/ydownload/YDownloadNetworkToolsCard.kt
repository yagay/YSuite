package com.yagay.ydownload

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.yagay.yui.YActionRow
import com.yagay.yui.YFeatureCard
import com.yagay.yui.YSettingSwitch
import com.yagay.yui.YStatusRow
import com.yagay.yui.YStatusTone

@Composable
fun YDownloadNetworkToolsCard(
    settings: YDownloadEnhancedSettings,
    onSettingsChanged: (YDownloadEnhancedSettings) -> Unit,
) {
    var userAgentDraft by remember { mutableStateOf(settings.userAgent) }

    LaunchedEffect(settings.userAgent) {
        if (userAgentDraft != settings.userAgent) userAgentDraft = settings.userAgent
    }

    YFeatureCard(
        title = stringResource(R.string.enhanced_network_tools),
        subtitle = stringResource(R.string.enhanced_network_tools_summary),
    ) {
        OutlinedTextField(
            value = userAgentDraft,
            onValueChange = { userAgentDraft = it.take(256) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.user_agent)) },
            singleLine = true,
        )
        YActionRow {
            OutlinedButton(
                onClick = {
                    onSettingsChanged(settings.copy(userAgent = userAgentDraft))
                },
                enabled = userAgentDraft.trim().isNotEmpty() && userAgentDraft.trim() != settings.userAgent,
            ) { Text(stringResource(R.string.apply_user_agent)) }
            OutlinedButton(
                onClick = {
                    userAgentDraft = YDownloadEnhancedSettings.DEFAULT_USER_AGENT
                    onSettingsChanged(settings.copy(userAgent = YDownloadEnhancedSettings.DEFAULT_USER_AGENT))
                },
                enabled = settings.userAgent != YDownloadEnhancedSettings.DEFAULT_USER_AGENT,
            ) { Text(stringResource(R.string.reset_user_agent)) }
        }

        YStatusRow(
            stringResource(R.string.speed_limit),
            if (settings.speedLimitKib <= 0) {
                stringResource(R.string.unlimited)
            } else {
                stringResource(R.string.speed_limit_value, settings.speedLimitKib)
            },
            YStatusTone.Neutral,
        )
        YActionRow {
            SPEED_LIMITS.take(4).forEach { kib ->
                OutlinedButton(onClick = { onSettingsChanged(settings.copy(speedLimitKib = kib)) }) {
                    Text(if (kib == 0) stringResource(R.string.unlimited) else stringResource(R.string.speed_limit_short, kib))
                }
            }
        }
        YActionRow {
            SPEED_LIMITS.drop(4).forEach { kib ->
                OutlinedButton(onClick = { onSettingsChanged(settings.copy(speedLimitKib = kib)) }) {
                    Text(stringResource(R.string.speed_limit_short, kib))
                }
            }
        }

        YSettingSwitch(
            title = stringResource(R.string.calculate_sha256),
            subtitle = stringResource(R.string.calculate_sha256_summary),
            checked = settings.calculateSha256,
            onCheckedChange = { onSettingsChanged(settings.copy(calculateSha256 = it)) },
        )
    }
}

private val SPEED_LIMITS = listOf(0, 512, 1024, 2048, 4096, 8192, 16384)
