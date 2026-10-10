package com.yagay.YSuite

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import com.yagay.yui.YUiText as Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yagay.suite.core.SuiteCommonSetting
import com.yagay.suite.core.SuiteCommonSettings
import com.yagay.yui.YAppearanceStore
import com.yagay.yui.YDialogConfirmButton
import com.yagay.yui.YDialogDismissButton
import com.yagay.yui.YHorizontalActions
import com.yagay.yui.YListItem
import com.yagay.yui.YPageList
import com.yagay.yui.YSecondaryButton
import com.yagay.yui.YSection
import com.yagay.yui.YSettingsScaffold
import com.yagay.yui.YStatusLine
import com.yagay.yui.YStatusTone
import com.yagay.yui.YSwitchItem
import com.yagay.yui.YUiAlertDialog
import com.yagay.yui.YUiRadioButton
import com.yagay.yui.YUiSlider
import kotlin.math.roundToInt

/**
 * Settings shared by multiple real host consumers, not a catch-all editor for feature internals.
 * Feature switches use FeatureStateStore and keep its Root/Hook/runtime gating contract.
 * Shared diagnostic options are read by SuiteLog for every module.
 */
@Composable
internal fun SuiteCommonSettingsScreen(
    modules: List<Pair<String, String>>,
    moduleId: String?,
    moduleStates: Map<String, Boolean>,
    permissions: SuitePermissionState.Snapshot,
    rootAvailable: Boolean?,
    xposedConnected: Boolean,
    onBack: () -> Unit,
    onSelectModule: (String) -> Unit,
    onToggleModule: (String, Boolean) -> Unit,
    onExportDiagnostic: (String?) -> Unit,
) {
    val context = LocalContext.current
    val store = remember(context.applicationContext) { SuiteCommonSettings(context) }
    val appearance = remember(context.applicationContext) { YAppearanceStore(context) }
    var revision by remember { mutableIntStateOf(0) }
    DisposableEffect(store) {
        val close = store.observe { revision++ }
        onDispose(close)
    }
    var confirmReset by remember(moduleId) { mutableStateOf(false) }
    val name = modules.firstOrNull { it.first == moduleId }?.second
    val logLevel = remember(store, moduleId, revision) {
        store.value(SuiteCommonSetting.LOG_LEVEL, moduleId)
    }
    val maxMb = remember(store, moduleId, revision) {
        store.value(SuiteCommonSetting.LOG_MAX_FILE_MB, moduleId).toInt()
    }
    val keepPrevious = remember(store, moduleId, revision) {
        store.value(SuiteCommonSetting.LOG_KEEP_PREVIOUS, moduleId) == "true"
    }
    val inherit: @Composable (SuiteCommonSetting) -> Unit = { key ->
        if (moduleId != null && store.isOverridden(key, moduleId)) {
            YSecondaryButton(
                text = stringResource(R.string.settings_inherit_global),
                onClick = { store.inherit(key, moduleId); revision++ },
            )
        }
    }

    YSettingsScaffold(
        title = name ?: stringResource(R.string.common_title),
        subtitle = stringResource(if (moduleId == null) R.string.common_global_desc
            else R.string.common_module_desc),
    ) { padding ->
        YPageList(padding = padding) {
            item(key = "back") {
                YSecondaryButton(stringResource(R.string.settings_back), onClick = onBack)
            }

            item(key = "logging") {
                YSection(
                    title = stringResource(R.string.common_logging_title),
                    subtitle = stringResource(R.string.common_logging_desc),
                ) {
                    Text(stringResource(R.string.common_logging_level), style = MaterialTheme.typography.titleSmall)
                    listOf(
                        "debug" to R.string.common_log_debug,
                        "info" to R.string.common_log_info,
                        "warning" to R.string.common_log_warning,
                        "error" to R.string.common_log_error,
                    ).forEach { (level, labelRes) ->
                        Row(
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                .clickable { store.set(SuiteCommonSetting.LOG_LEVEL, level, moduleId); revision++ },
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            YUiRadioButton(
                                selected = logLevel == level,
                                onClick = { store.set(SuiteCommonSetting.LOG_LEVEL, level, moduleId); revision++ },
                            )
                            Text(stringResource(labelRes), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    inherit(SuiteCommonSetting.LOG_LEVEL)

                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.common_log_size),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text("$maxMb MB", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    YUiSlider(
                        value = maxMb.toFloat(),
                        onValueChange = {
                            val size = it.roundToInt().coerceIn(1, 10)
                            store.set(SuiteCommonSetting.LOG_MAX_FILE_MB, size.toString(), moduleId)
                            revision++
                        },
                        valueRange = 1f..10f,
                        steps = 8,
                    )
                    inherit(SuiteCommonSetting.LOG_MAX_FILE_MB)
                    YSwitchItem(
                        title = stringResource(R.string.common_log_keep_previous),
                        subtitle = stringResource(R.string.common_log_keep_previous_desc),
                        checked = keepPrevious,
                        onCheckedChange = {
                            store.set(SuiteCommonSetting.LOG_KEEP_PREVIOUS, it.toString(), moduleId)
                            revision++
                        },
                    )
                    inherit(SuiteCommonSetting.LOG_KEEP_PREVIOUS)
                }
            }

            if (moduleId == null) {
                item(key = "home") {
                    YSection(
                        title = stringResource(R.string.settings_home_title),
                        subtitle = stringResource(R.string.settings_home_subtitle),
                    ) {
                        val homeAppearance = appearance.appearance()
                        // Existing homepage reads these same two YUI keys. No parallel settings.
                        YSwitchItem(
                            title = stringResource(R.string.settings_swipe_pin),
                            checked = homeAppearance.homeSwipePin,
                            onCheckedChange = {
                                appearance.set(com.yagay.yui.YSettingKey.HOME_SWIPE_PIN, it.toString())
                                revision++
                            },
                        )
                        YSwitchItem(
                            title = stringResource(R.string.settings_home_status),
                            checked = homeAppearance.homeStatusVisible,
                            onCheckedChange = {
                                appearance.set(com.yagay.yui.YSettingKey.HOME_STATUS, it.toString())
                                revision++
                            },
                        )
                    }
                }

                item(key = "permissions") {
                    YSection(
                        title = stringResource(R.string.common_permissions_title),
                        subtitle = stringResource(R.string.common_permissions_desc),
                    ) {
                        YStatusLine(
                            label = stringResource(R.string.capability_root),
                            value = if (rootAvailable == true) stringResource(R.string.status_authorized)
                                else stringResource(R.string.status_unavailable_or_unauthorized),
                            tone = if (rootAvailable == true) YStatusTone.Good else YStatusTone.Warning,
                        )
                        YStatusLine(
                            label = stringResource(R.string.capability_lsposed),
                            value = stringResource(if (xposedConnected) R.string.status_connected
                                else R.string.status_not_connected),
                            tone = if (xposedConnected) YStatusTone.Good else YStatusTone.Warning,
                        )
                        YStatusLine(
                            label = stringResource(R.string.capability_accessibility),
                            value = stringResource(if (permissions.accessibilityConnected) R.string.status_connected
                                else if (permissions.accessibilityEnabled) R.string.status_authorized
                                else R.string.status_not_authorized),
                            tone = if (permissions.accessibilityConnected) YStatusTone.Good
                                else YStatusTone.Warning,
                        )
                        YStatusLine(
                            label = stringResource(R.string.capability_overlay),
                            value = stringResource(if (permissions.overlayGranted) R.string.status_authorized
                                else R.string.status_not_authorized),
                            tone = if (permissions.overlayGranted) YStatusTone.Good else YStatusTone.Warning,
                        )
                        YStatusLine(
                            label = stringResource(R.string.capability_notification_listener),
                            value = stringResource(if (permissions.notificationListenerConnected) R.string.status_connected
                                else if (permissions.notificationListenerGranted) R.string.status_authorized
                                else R.string.status_not_authorized),
                            tone = if (permissions.notificationListenerConnected) YStatusTone.Good
                                else YStatusTone.Warning,
                        )
                        YHorizontalActions {
                            YSecondaryButton(
                                text = stringResource(R.string.accessibility_settings),
                                onClick = {
                                    if (!SuitePermissionState.openAccessibilitySettings(context)) {
                                        Toast.makeText(context, R.string.error_open_accessibility_settings, Toast.LENGTH_SHORT).show()
                                    }
                                },
                            )
                            YSecondaryButton(
                                text = stringResource(R.string.overlay_settings),
                                onClick = {
                                    if (!SuitePermissionState.openOverlaySettings(context)) {
                                        Toast.makeText(context, R.string.error_open_overlay_settings, Toast.LENGTH_SHORT).show()
                                    }
                                },
                            )
                            YSecondaryButton(
                                text = stringResource(R.string.notification_listener_settings),
                                onClick = {
                                    if (!SuitePermissionState.openNotificationListenerSettings(context)) {
                                        Toast.makeText(context, R.string.error_open_notification_listener_settings, Toast.LENGTH_SHORT).show()
                                    }
                                },
                            )
                        }
                        Text(
                            stringResource(R.string.common_permission_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                item(key = "module-list-title") {
                    YSection(
                        title = stringResource(R.string.common_modules_title),
                        subtitle = stringResource(R.string.common_modules_desc),
                    ) {
                        modules.forEach { (id, label) ->
                            YSwitchItem(
                                title = label,
                                subtitle = stringResource(R.string.common_module_enable_desc),
                                checked = moduleStates[id] == true,
                                onCheckedChange = { onToggleModule(id, it) },
                            )
                            YSecondaryButton(
                                text = stringResource(R.string.common_module_customize),
                                onClick = { onSelectModule(id) },
                            )
                        }
                    }
                }
            } else {
                item(key = "module-enabled") {
                    YSection(title = stringResource(R.string.common_module_control_title)) {
                        YSwitchItem(
                            title = name ?: moduleId,
                            subtitle = stringResource(R.string.common_module_enable_desc),
                            checked = moduleStates[moduleId] == true,
                            onCheckedChange = { onToggleModule(moduleId, it) },
                        )
                    }
                }
            }

            item(key = "diagnostics") {
                YSection(
                    title = stringResource(R.string.common_diagnostics_title),
                    subtitle = stringResource(R.string.common_diagnostics_desc),
                ) {
                    YSecondaryButton(
                        text = stringResource(if (moduleId == null) R.string.export_full_diagnostic
                            else R.string.common_export_module),
                        onClick = { onExportDiagnostic(moduleId) },
                    )
                }
            }

            item(key = "reset") {
                YSection(
                    title = stringResource(R.string.common_reset_title),
                    subtitle = stringResource(R.string.common_reset_desc),
                ) {
                    YSecondaryButton(
                        text = stringResource(R.string.common_reset_action),
                        onClick = { confirmReset = true },
                    )
                }
            }
        }
    }

    if (confirmReset) {
        YUiAlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(stringResource(R.string.common_reset_title)) },
            text = { Text(stringResource(R.string.common_reset_confirm)) },
            confirmButton = {
                YDialogConfirmButton(
                    label = stringResource(R.string.settings_reset_action),
                    onClick = {
                        store.reset(moduleId)
                        revision++
                        confirmReset = false
                    },
                    dangerous = true,
                )
            },
            dismissButton = {
                YDialogDismissButton(stringResource(R.string.settings_cancel)) {
                    confirmReset = false
                }
            },
        )
    }
}
