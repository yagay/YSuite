package com.yagay.YSuite

import android.widget.Toast
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.lifecycleScope
import com.yagay.suite.core.FeatureRegistry
import com.yagay.suite.core.FeatureSpec
import com.yagay.suite.core.FeatureStateStore
import com.yagay.suite.core.RootManager
import com.yagay.suite.core.SuiteCapability
import com.yagay.suite.core.SuiteCrashTracker
import com.yagay.suite.core.SuiteLog
import com.yagay.suite.core.SuiteXposedServiceBroker
import com.yagay.yui.YActionRow
import com.yagay.yui.YComposeActivity
import com.yagay.yui.YFeatureCard
import com.yagay.yui.YFeatureList
import com.yagay.yui.YFeatureScaffold
import com.yagay.yui.YPrimaryButton
import com.yagay.yui.YSecondaryButton
import com.yagay.yui.YSettingSwitch
import com.yagay.yui.YStatusRow
import com.yagay.yui.YStatusTone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : YComposeActivity() {
    @Composable
    override fun YContent() {
        FeatureManagerScreen()
    }

    override fun onResume() {
        super.onResume()
        SuiteCrashTracker.markActiveFeature(this, null)
    }

    private fun exportDiagnostic(modules: Set<String>?, label: String) {
        Toast.makeText(
            this,
            getString(R.string.diagnostic_collecting, label),
            Toast.LENGTH_SHORT,
        ).show()
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { SuiteLog.export(this@MainActivity, modules) }
            }
            result.onSuccess { path ->
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.diagnostic_saved, label, path),
                    Toast.LENGTH_LONG,
                ).show()
            }.onFailure {
                Toast.makeText(
                    this@MainActivity,
                    getString(
                        R.string.diagnostic_save_failed,
                        label,
                        it.javaClass.simpleName,
                    ),
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    @Composable
    private fun FeatureManagerScreen() {
        val features = remember { FeatureRegistry.included() }
        val store = remember { FeatureStateStore(this) }
        val enabled = remember {
            mutableStateMapOf<String, Boolean>().apply {
                features.forEach { put(it.id, store.isEnabled(it)) }
            }
        }
        var rootAvailable by remember { mutableStateOf<Boolean?>(null) }
        var xposedConnected by remember { mutableStateOf(SuiteXposedServiceBroker.isConnected()) }
        var xposedStatus by remember { mutableStateOf(SuiteXposedServiceBroker.statusLabel()) }
        var permissions by remember { mutableStateOf(SuitePermissionState.snapshot(this)) }
        var resumeTick by remember { mutableIntStateOf(0) }
        val fullDiagnosticLabel = stringResource(R.string.diagnostic_full_label)

        LifecycleResumeEffect(Unit) {
            resumeTick++
            onPauseOrDispose { }
        }

        LaunchedEffect(resumeTick) {
            rootAvailable = withContext(Dispatchers.IO) {
                RootManager.isAvailable(this@MainActivity)
            }
            permissions = SuitePermissionState.snapshot(this@MainActivity)
        }

        LaunchedEffect(Unit) {
            while (true) {
                xposedConnected = SuiteXposedServiceBroker.isConnected()
                xposedStatus = SuiteXposedServiceBroker.statusLabel()
                permissions = SuitePermissionState.snapshot(this@MainActivity)
                delay(1_000L)
            }
        }

        YFeatureScaffold(
            title = stringResource(R.string.app_name),
            subtitle = stringResource(R.string.suite_subtitle),
        ) { scaffoldPadding ->
            YFeatureList(padding = scaffoldPadding) {
                item {
                    RuntimeEnvironmentCard(
                        featureCount = features.size,
                        rootAvailable = rootAvailable,
                        xposedConnected = xposedConnected,
                        xposedStatus = xposedStatus,
                        permissions = permissions,
                        onAccessibility = {
                            if (!SuitePermissionState.openAccessibilitySettings(this@MainActivity)) {
                                Toast.makeText(
                                    this@MainActivity,
                                    getString(R.string.error_open_accessibility_settings),
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                        },
                        onOverlay = {
                            if (!SuitePermissionState.openOverlaySettings(this@MainActivity)) {
                                Toast.makeText(
                                    this@MainActivity,
                                    getString(R.string.error_open_overlay_settings),
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                        },
                        onNotificationListener = {
                            if (!SuitePermissionState.openNotificationListenerSettings(this@MainActivity)) {
                                Toast.makeText(
                                    this@MainActivity,
                                    getString(R.string.error_open_notification_listener_settings),
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                        },
                        onExport = { exportDiagnostic(null, fullDiagnosticLabel) },
                    )
                }

                items(features.size, key = { features[it].id }) { index ->
                    val feature = features[index]
                    val localizedName = localizedFeatureName(feature)
                    val localizedDescription = localizedFeatureDescription(feature)
                    FeatureCard(
                        feature = feature,
                        localizedName = localizedName,
                        localizedDescription = localizedDescription,
                        isEnabled = enabled[feature.id] == true,
                        onEnabledChange = { next ->
                            store.setEnabled(feature, next)
                            enabled[feature.id] = next
                            if (next) {
                                runCatching { feature.initialize(this@MainActivity) }
                                    .onSuccess {
                                        SuiteLog.i(
                                            this@MainActivity,
                                            feature.id,
                                            "host enabled; plugin attached to existing YSuite capabilities",
                                        )
                                    }
                                    .onFailure {
                                        SuiteLog.e(this@MainActivity, feature.id, "host enable failed", it)
                                        Toast.makeText(
                                            this@MainActivity,
                                            getString(
                                                R.string.feature_enable_failed,
                                                localizedName,
                                                it.javaClass.simpleName,
                                            ),
                                            Toast.LENGTH_LONG,
                                        ).show()
                                    }
                            } else {
                                SuiteLog.i(
                                    this@MainActivity,
                                    feature.id,
                                    "host disabled; shared capabilities remain owned by YSuite",
                                )
                            }
                        },
                        onOpen = {
                            SuiteCrashTracker.markActiveFeature(this@MainActivity, feature.id)
                            SuiteLog.i(
                                this@MainActivity,
                                feature.id,
                                "open requested; activity=${feature.entryActivityClassName}",
                            )
                            runCatching { startActivity(feature.createIntent(this@MainActivity)) }
                                .onFailure {
                                    SuiteLog.e(this@MainActivity, feature.id, "open failed", it)
                                    SuiteCrashTracker.markActiveFeature(this@MainActivity, null)
                                    Toast.makeText(
                                        this@MainActivity,
                                        getString(
                                            R.string.feature_open_failed,
                                            localizedName,
                                            it.javaClass.simpleName,
                                        ),
                                        Toast.LENGTH_LONG,
                                    ).show()
                                }
                        },
                        onExportLog = { exportDiagnostic(setOf(feature.id), localizedName) },
                    )
                }
            }
        }
    }
}

@Composable
private fun RuntimeEnvironmentCard(
    featureCount: Int,
    rootAvailable: Boolean?,
    xposedConnected: Boolean,
    xposedStatus: String,
    permissions: SuitePermissionSnapshot,
    onAccessibility: () -> Unit,
    onOverlay: () -> Unit,
    onNotificationListener: () -> Unit,
    onExport: () -> Unit,
) {
    YFeatureCard(
        title = stringResource(R.string.runtime_environment_title),
        subtitle = stringResource(R.string.runtime_environment_subtitle),
        detail = stringResource(
            R.string.runtime_environment_detail,
            featureCount,
            SuiteXposedServiceBroker.listenerCount(),
        ),
    ) {
        YStatusRow(
            label = stringResource(R.string.capability_root),
            value = when (rootAvailable) {
                true -> stringResource(R.string.status_authorized)
                false -> stringResource(R.string.status_unavailable_or_unauthorized)
                null -> stringResource(R.string.status_checking)
            },
            tone = when (rootAvailable) {
                true -> YStatusTone.Good
                false -> YStatusTone.Error
                null -> YStatusTone.Neutral
            },
        )
        YStatusRow(
            stringResource(R.string.capability_lsposed),
            if (xposedConnected) xposedStatus else stringResource(R.string.status_not_connected),
        )
        YStatusRow(
            stringResource(R.string.capability_accessibility),
            permissions.accessibilityLabel,
            if (permissions.accessibilityEnabled) YStatusTone.Good else YStatusTone.Warning,
        )
        YStatusRow(
            stringResource(R.string.capability_overlay),
            if (permissions.overlayGranted) {
                stringResource(R.string.status_authorized)
            } else {
                stringResource(R.string.status_not_authorized)
            },
            if (permissions.overlayGranted) YStatusTone.Good else YStatusTone.Warning,
        )
        YStatusRow(
            stringResource(R.string.capability_notifications),
            if (permissions.notificationsGranted) {
                stringResource(R.string.status_authorized)
            } else {
                stringResource(R.string.status_not_authorized)
            },
            if (permissions.notificationsGranted) YStatusTone.Good else YStatusTone.Warning,
        )
        YStatusRow(
            stringResource(R.string.capability_notification_listener),
            when {
                permissions.notificationListenerConnected -> stringResource(R.string.status_connected)
                permissions.notificationListenerGranted -> stringResource(R.string.status_authorized_waiting_connection)
                else -> stringResource(R.string.status_not_authorized)
            },
            when {
                permissions.notificationListenerConnected -> YStatusTone.Good
                permissions.notificationListenerGranted -> YStatusTone.Warning
                else -> YStatusTone.Error
            },
        )

        if (permissions.legacyAccessibilityEnabled && !permissions.accessibilityEnabled) {
            HostWarning(stringResource(R.string.warning_legacy_accessibility))
        }
        if (permissions.legacyNotificationListenerEnabled && !permissions.notificationListenerGranted) {
            HostWarning(stringResource(R.string.warning_legacy_notification_listener))
        }
        if (permissions.otherAccessibilityHostEnabled) {
            HostWarning(stringResource(R.string.warning_other_accessibility_host))
        }
        if (permissions.otherNotificationListenerHostEnabled) {
            HostWarning(stringResource(R.string.warning_other_notification_host))
        }

        YActionRow {
            YSecondaryButton(
                text = if (permissions.accessibilityEnabled) {
                    stringResource(R.string.accessibility_settings)
                } else {
                    stringResource(R.string.enable_accessibility)
                },
                onClick = onAccessibility,
            )
            YSecondaryButton(text = stringResource(R.string.overlay_settings), onClick = onOverlay)
        }
        YActionRow {
            YSecondaryButton(
                text = if (permissions.notificationListenerGranted) {
                    stringResource(R.string.notification_listener_settings)
                } else {
                    stringResource(R.string.enable_notification_listener)
                },
                onClick = onNotificationListener,
            )
            YSecondaryButton(text = stringResource(R.string.export_full_diagnostic), onClick = onExport)
        }
    }
}

@Composable
private fun HostWarning(message: String) {
    Text(
        message,
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun FeatureCard(
    feature: FeatureSpec,
    localizedName: String,
    localizedDescription: String,
    isEnabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onOpen: () -> Unit,
    onExportLog: () -> Unit,
) {
    val capabilities = feature.sharedCapabilities
        .map { localizedCapabilityName(it) }
        .takeIf { it.isNotEmpty() }
        ?.joinToString(" + ")

    YFeatureCard(
        title = localizedName,
        subtitle = localizedDescription,
        detail = capabilities?.let { stringResource(R.string.shared_capabilities, it) },
    ) {
        YSettingSwitch(
            title = stringResource(R.string.enable_feature),
            subtitle = stringResource(R.string.enable_feature_summary),
            checked = isEnabled,
            onCheckedChange = onEnabledChange,
        )
        YActionRow {
            YPrimaryButton(
                text = stringResource(R.string.open_feature),
                onClick = onOpen,
                enabled = isEnabled,
            )
            YSecondaryButton(
                text = stringResource(R.string.diagnostic_package),
                onClick = onExportLog,
            )
        }
    }
}

@Composable
private fun localizedFeatureName(feature: FeatureSpec): String = when (feature.id) {
    "yentrycleaner" -> stringResource(R.string.feature_yentrycleaner_name)
    "ydiag" -> stringResource(R.string.feature_ydiag_name)
    "ynotify" -> stringResource(R.string.feature_ynotify_name)
    "ypower" -> stringResource(R.string.feature_ypower_name)
    "yminiguard" -> stringResource(R.string.feature_yminiguard_name)
    "ynfc" -> stringResource(R.string.feature_ynfc_name)
    "ytaskmanager" -> stringResource(R.string.feature_ytaskmanager_name)
    "yparam" -> stringResource(R.string.feature_yparam_name)
    "yfloat" -> stringResource(R.string.feature_yfloat_name)
    else -> feature.name
}

@Composable
private fun localizedFeatureDescription(feature: FeatureSpec): String = when (feature.id) {
    "yentrycleaner" -> stringResource(R.string.feature_yentrycleaner_description)
    "ydiag" -> stringResource(R.string.feature_ydiag_description)
    "ynotify" -> stringResource(R.string.feature_ynotify_description)
    "ypower" -> stringResource(R.string.feature_ypower_description)
    "yminiguard" -> stringResource(R.string.feature_yminiguard_description)
    "ynfc" -> stringResource(R.string.feature_ynfc_description)
    "ytaskmanager" -> stringResource(R.string.feature_ytaskmanager_description)
    "yparam" -> stringResource(R.string.feature_yparam_description)
    "yfloat" -> stringResource(R.string.feature_yfloat_description)
    else -> feature.description
}

@Composable
private fun localizedCapabilityName(capability: SuiteCapability): String = when (capability) {
    SuiteCapability.ROOT -> stringResource(R.string.capability_root)
    SuiteCapability.LSPOSED -> stringResource(R.string.capability_lsposed)
    SuiteCapability.ACCESSIBILITY -> stringResource(R.string.capability_accessibility)
    SuiteCapability.OVERLAY -> stringResource(R.string.capability_overlay)
    SuiteCapability.NOTIFICATIONS -> stringResource(R.string.capability_notifications)
    SuiteCapability.NOTIFICATION_LISTENER -> stringResource(R.string.capability_notification_listener)
}
