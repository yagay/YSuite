package com.yagay.ysuite.feature.ydownload

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.yagay.ysuite.permissions.api.PermissionRequirement
import com.yagay.ysuite.productui.settings.ComposeSettingsChoice
import com.yagay.ysuite.productui.settings.ComposeSettingsChoiceGroup
import com.yagay.ysuite.productui.settings.ComposeSettingsGroup
import com.yagay.ysuite.productui.settings.ComposeSettingsIntSlider
import com.yagay.ysuite.productui.settings.ComposeSettingsLink
import com.yagay.ysuite.productui.settings.ComposeSettingsSurface
import com.yagay.ysuite.productui.settings.ComposeSettingsSwitch
import com.yagay.ysuite.ui.YSuiteBackNavigationButton
import com.yagay.ysuite.ui.rememberYSuitePermissionRequester

@Composable
fun YDownloadSettingsScreen(
    settings: YDownloadSettings,
    systemPatch: YDownloadSystemPatchSettings,
    systemPatchScopeCount: Int,
    onBack: () -> Unit,
    onDefaultTreeUri: (String?) -> Unit,
    onMaxConcurrent: (Int) -> Unit,
    onDefaultThreadCount: (Int) -> Unit,
    onSpeedLimit: (Long) -> Unit,
    onWifiOnly: (Boolean) -> Unit,
    onAutoResumeNetwork: (Boolean) -> Unit,
    onNotifications: (Boolean) -> Unit,
    onUserAgent: (String) -> Unit,
    onSystemPatchEnabled: (Boolean) -> Unit,
    onSystemPatchAllowMetered: (Boolean) -> Unit,
    onSystemPatchAllowRoaming: (Boolean) -> Unit,
    onSystemPatchRequireCharging: (Boolean) -> Unit,
    onSystemPatchRequireIdle: (Boolean) -> Unit,
    onSystemPatchCompletionNotification:
        (Boolean) -> Unit,
    onSystemPatchScope: () -> Unit,
    onSystemPatchSync: () -> Unit,
) {
    val context = LocalContext.current
    val permissionRequester =
        rememberYSuitePermissionRequester { result ->
            onNotifications(result.allGranted)
        }
    val folderLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocumentTree(),
        ) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            val flags =
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching {
                context.contentResolver
                    .takePersistableUriPermission(
                        uri,
                        flags,
                    )
            }
            onDefaultTreeUri(uri.toString())
        }

    ComposeSettingsSurface(
        title =
            stringResource(
                R.string.ydownload_settings,
            ),
        navigationIcon = {
            YSuiteBackNavigationButton(onBack)
        },
    ) {
        ComposeSettingsGroup(
            title =
                stringResource(
                    R.string.ydownload_settings_downloads,
                ),
        ) {
            ComposeSettingsLink(
                title =
                    stringResource(
                        R.string.ydownload_default_folder,
                    ),
                subtitle =
                    settings.defaultTreeUri
                        ?: stringResource(
                            R.string.ydownload_default_folder_system,
                        ),
                onClick = {
                    folderLauncher.launch(null)
                },
            )
            if (settings.defaultTreeUri != null) {
                ComposeSettingsLink(
                    title =
                        stringResource(
                            R.string.ydownload_use_system_folder,
                        ),
                    onClick = {
                        onDefaultTreeUri(null)
                    },
                )
            }
        }

        ComposeSettingsIntSlider(
            title =
                stringResource(
                    R.string.ydownload_max_concurrent,
                ),
            value = settings.maxConcurrentDownloads,
            range = 1..10,
            onValueChange = onMaxConcurrent,
        )

        ComposeSettingsIntSlider(
            title =
                stringResource(
                    R.string.ydownload_default_threads,
                ),
            subtitle =
                stringResource(
                    R.string.ydownload_default_threads_desc,
                ),
            value = settings.defaultThreadCount,
            range = 1..16,
            onValueChange = onDefaultThreadCount,
        )

        ComposeSettingsChoiceGroup(
            title =
                stringResource(
                    R.string.ydownload_speed_limit,
                ),
            selectedId =
                settings.globalSpeedLimitBytesPerSecond
                    .speedChoiceId(),
            choices =
                listOf(
                    ComposeSettingsChoice(
                        "0",
                        stringResource(
                            R.string.ydownload_unlimited,
                        ),
                    ),
                    ComposeSettingsChoice(
                        "524288",
                        stringResource(
                            R.string.ydownload_speed_512k,
                        ),
                    ),
                    ComposeSettingsChoice(
                        "1048576",
                        stringResource(
                            R.string.ydownload_speed_1m,
                        ),
                    ),
                    ComposeSettingsChoice(
                        "5242880",
                        stringResource(
                            R.string.ydownload_speed_5m,
                        ),
                    ),
                    ComposeSettingsChoice(
                        "10485760",
                        stringResource(
                            R.string.ydownload_speed_10m,
                        ),
                    ),
                ),
            onSelected = {
                it.toLongOrNull()
                    ?.let(onSpeedLimit)
            },
        )

        ComposeSettingsGroup(
            title =
                stringResource(
                    R.string.ydownload_settings_network,
                ),
        ) {
            ComposeSettingsSwitch(
                title =
                    stringResource(
                        R.string.ydownload_wifi_only,
                    ),
                subtitle =
                    stringResource(
                        R.string.ydownload_wifi_only_desc,
                    ),
                checked = settings.wifiOnly,
                onCheckedChange = onWifiOnly,
            )
            ComposeSettingsSwitch(
                title =
                    stringResource(
                        R.string.ydownload_auto_resume_network,
                    ),
                subtitle =
                    stringResource(
                        R.string
                            .ydownload_auto_resume_network_desc,
                    ),
                checked = settings.autoResumeNetwork,
                onCheckedChange = onAutoResumeNetwork,
            )
        }

        ComposeSettingsGroup(
            title =
                stringResource(
                    R.string.ydownload_settings_notifications,
                ),
        ) {
            ComposeSettingsSwitch(
                title =
                    stringResource(
                        R.string.ydownload_notification_progress,
                    ),
                subtitle =
                    stringResource(
                        R.string.ydownload_notification_progress_desc,
                    ),
                checked =
                    settings.notificationsEnabled,
                onCheckedChange = { enabled ->
                    if (
                        enabled &&
                        Build.VERSION.SDK_INT >= 33
                    ) {
                        permissionRequester.launch(
                            listOf(
                                PermissionRequirement(
                                    permission =
                                        Manifest.permission
                                            .POST_NOTIFICATIONS,
                                    required = false,
                                ),
                            ),
                        )
                    } else {
                        onNotifications(enabled)
                    }
                },
            )
        }

        ComposeSettingsGroup(
            title =
                stringResource(
                    R.string
                        .ydownload_patch_title,
                ),
        ) {
            ComposeSettingsSwitch(
                title =
                    stringResource(
                        R.string
                            .ydownload_patch_enabled,
                    ),
                subtitle =
                    stringResource(
                        R.string
                            .ydownload_patch_enabled_desc,
                    ),
                checked = systemPatch.enabled,
                onCheckedChange =
                    onSystemPatchEnabled,
            )
            ComposeSettingsSwitch(
                title =
                    stringResource(
                        R.string
                            .ydownload_patch_metered,
                    ),
                checked =
                    systemPatch.allowMetered,
                enabled = systemPatch.enabled,
                onCheckedChange =
                    onSystemPatchAllowMetered,
            )
            ComposeSettingsSwitch(
                title =
                    stringResource(
                        R.string
                            .ydownload_patch_roaming,
                    ),
                checked =
                    systemPatch.allowRoaming,
                enabled = systemPatch.enabled,
                onCheckedChange =
                    onSystemPatchAllowRoaming,
            )
            ComposeSettingsSwitch(
                title =
                    stringResource(
                        R.string
                            .ydownload_patch_charging,
                    ),
                checked =
                    systemPatch.requireCharging,
                enabled = systemPatch.enabled,
                onCheckedChange =
                    onSystemPatchRequireCharging,
            )
            ComposeSettingsSwitch(
                title =
                    stringResource(
                        R.string
                            .ydownload_patch_idle,
                    ),
                checked =
                    systemPatch.requireDeviceIdle,
                enabled = systemPatch.enabled,
                onCheckedChange =
                    onSystemPatchRequireIdle,
            )
            ComposeSettingsSwitch(
                title =
                    stringResource(
                        R.string
                            .ydownload_patch_completion_notification,
                    ),
                checked =
                    systemPatch
                        .forceCompletionNotification,
                enabled = systemPatch.enabled,
                onCheckedChange =
                    onSystemPatchCompletionNotification,
            )
            ComposeSettingsLink(
                title =
                    stringResource(
                        R.string
                            .ydownload_patch_scope,
                    ),
                subtitle =
                    stringResource(
                        R.string
                            .ydownload_patch_scope_desc,
                        systemPatchScopeCount,
                    ),
                onClick = onSystemPatchScope,
            )
            ComposeSettingsLink(
                title =
                    stringResource(
                        R.string
                            .ydownload_patch_sync,
                    ),
                onClick = onSystemPatchSync,
            )
        }

        ComposeSettingsChoiceGroup(
            title =
                stringResource(
                    R.string.ydownload_default_user_agent,
                ),
            selectedId =
                if (
                    settings.defaultUserAgent ==
                    YDownloadSettings.DESKTOP_USER_AGENT
                ) {
                    "desktop"
                } else {
                    "mobile"
                },
            choices =
                listOf(
                    ComposeSettingsChoice(
                        "mobile",
                        stringResource(
                            R.string.ydownload_user_agent_mobile,
                        ),
                    ),
                    ComposeSettingsChoice(
                        "desktop",
                        stringResource(
                            R.string.ydownload_user_agent_desktop,
                        ),
                    ),
                ),
            onSelected = { id ->
                onUserAgent(
                    if (id == "desktop") {
                        YDownloadSettings.DESKTOP_USER_AGENT
                    } else {
                        YDownloadSettings.MOBILE_USER_AGENT
                    },
                )
            },
        )
    }
}

private fun Long.speedChoiceId(): String {
    val choices =
        longArrayOf(
            0L,
            524_288L,
            1_048_576L,
            5_242_880L,
            10_485_760L,
        )
    return choices.minByOrNull {
        kotlin.math.abs(this - it)
    }?.toString() ?: "0"
}
