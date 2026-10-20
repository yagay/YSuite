package com.yagay.ysuite.feature.yfiles

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.yagay.ysuite.designsystem.component.YSuiteTextInputDialog
import com.yagay.ysuite.productui.settings.ComposeSettingsGroup
import com.yagay.ysuite.productui.settings.ComposeSettingsLink
import com.yagay.ysuite.productui.settings.ComposeSettingsSwitch

@Composable
internal fun YFilesSystemPatchSettingsContent(
    state: YFilesAdvancedUiState,
    advanced: YFilesAdvancedViewModel,
) {
    val context = LocalContext.current
    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            advanced.setSystemPatchInitialUri(uri.toString())
        }
    }
    var initialUriDialog by
        remember {
            mutableStateOf(false)
        }
    var initialUri by
        remember(
            state.systemPatch.initialUri,
        ) {
            mutableStateOf(
                state.systemPatch.initialUri,
            )
        }

    ComposeSettingsGroup(
        title =
            stringResource(
                R.string.yfiles_patch_title,
            ),
    ) {
        ComposeSettingsSwitch(
            title =
                stringResource(
                    R.string.yfiles_patch_enabled,
                ),
            subtitle =
                stringResource(
                    R.string
                        .yfiles_patch_enabled_subtitle,
                ),
            checked =
                state.systemPatch.enabled,
            onCheckedChange =
                advanced::setSystemPatchEnabled,
        )
        ComposeSettingsSwitch(
            title =
                stringResource(
                    R.string.yfiles_patch_local_only,
                ),
            checked =
                state.systemPatch.localOnly,
            enabled =
                state.systemPatch.enabled,
            onCheckedChange =
                advanced::setSystemPatchLocalOnly,
        )
        ComposeSettingsSwitch(
            title =
                stringResource(
                    R.string
                        .yfiles_patch_allow_multiple,
                ),
            checked =
                state.systemPatch.allowMultiple,
            enabled =
                state.systemPatch.enabled,
            onCheckedChange =
                advanced::setSystemPatchAllowMultiple,
        )
        ComposeSettingsLink(
            title =
                stringResource(
                    R.string
                        .yfiles_patch_default_sort,
                ),
            subtitle =
                systemPatchSortLabel(
                    state.systemPatch.defaultSort,
                ),
            enabled =
                state.systemPatch.enabled,
            onClick =
                advanced::cycleSystemPatchSort,
        )
        ComposeSettingsLink(
            title = stringResource(R.string.yfiles_patch_sort_direction),
            subtitle = systemPatchDirectionLabel(state.systemPatch.defaultDirection),
            enabled = state.systemPatch.enabled &&
                state.systemPatch.defaultSort != YFilesSystemPatchSettings.SORT_SYSTEM,
            onClick = advanced::cycleSystemPatchDirection,
        )
        ComposeSettingsLink(
            title = stringResource(R.string.yfiles_patch_choose_folder),
            subtitle =
                state.systemPatch.initialUri.ifBlank {
                    stringResource(R.string.yfiles_patch_system_default)
                },
            enabled = state.systemPatch.enabled,
            onClick = { folderPicker.launch(null) },
        )
        if (state.systemPatch.initialUri.isNotBlank()) {
            ComposeSettingsLink(
                title = stringResource(R.string.yfiles_patch_clear_folder),
                enabled = state.systemPatch.enabled,
                onClick = { advanced.setSystemPatchInitialUri("") },
            )
        }
        ComposeSettingsLink(
            title =
                stringResource(
                    R.string
                        .yfiles_patch_initial_uri,
                ),
            subtitle =
                state.systemPatch.initialUri
                    .ifBlank {
                        stringResource(
                            R.string
                                .yfiles_patch_system_default,
                        )
                    },
            enabled =
                state.systemPatch.enabled,
            onClick = {
                initialUri =
                    state.systemPatch.initialUri
                initialUriDialog = true
            },
        )
        ComposeSettingsLink(
            title =
                stringResource(
                    R.string.yfiles_patch_scope,
                ),
            subtitle =
                stringResource(
                    R.string
                        .yfiles_patch_scope_subtitle,
                    advanced
                        .systemPatchRecommendedScopeCount(),
                ),
            onClick =
                advanced::requestSystemPatchScope,
        )
        ComposeSettingsLink(
            title =
                stringResource(
                    R.string.yfiles_patch_sync,
                ),
            onClick =
                advanced::syncSystemPatch,
        )
    }

    if (initialUriDialog) {
        YSuiteTextInputDialog(
            title =
                stringResource(
                    R.string
                        .yfiles_patch_initial_uri,
                ),
            label =
                stringResource(
                    R.string
                        .yfiles_patch_initial_uri_label,
                ),
            value = initialUri,
            confirmText =
                stringResource(
                    R.string.yfiles_adv_save,
                ),
            dismissText =
                stringResource(
                    R.string.yfiles_adv_cancel,
                ),
            onValueChange = {
                initialUri = it
            },
            onConfirm = {
                advanced
                    .setSystemPatchInitialUri(
                        initialUri,
                    )
                initialUriDialog = false
            },
            onDismiss = {
                initialUriDialog = false
            },
        )
    }
}

@Composable
private fun systemPatchSortLabel(
    value: String,
): String =
    stringResource(
        when (value) {
            YFilesSystemPatchSettings
                .SORT_NAME ->
                R.string.yfiles_patch_sort_name
            YFilesSystemPatchSettings
                .SORT_DATE ->
                R.string.yfiles_patch_sort_date
            YFilesSystemPatchSettings
                .SORT_SIZE ->
                R.string.yfiles_patch_sort_size
            YFilesSystemPatchSettings
                .SORT_TYPE ->
                R.string.yfiles_patch_sort_type
            else ->
                R.string.yfiles_patch_sort_system
        },
    )

@Composable
private fun systemPatchDirectionLabel(value: String): String =
    stringResource(
        when (value) {
            YFilesSystemPatchSettings.DIRECTION_ASCENDING ->
                R.string.yfiles_patch_direction_ascending
            YFilesSystemPatchSettings.DIRECTION_DESCENDING ->
                R.string.yfiles_patch_direction_descending
            else -> R.string.yfiles_patch_sort_system
        },
    )
