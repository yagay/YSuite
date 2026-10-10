package com.yagay.YSuite

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.yagay.yui.YHorizontalActions
import com.yagay.yui.YComposeActivity
import com.yagay.yui.YAppearanceStore
import com.yagay.yui.YSection
import com.yagay.yui.YDashboardScaffold
import com.yagay.yui.YPageList
import com.yagay.yui.YPrimaryButton
import com.yagay.yui.YSecondaryButton
import com.yagay.yui.YSwitchItem
import com.yagay.yui.YStatusLine
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
        Toast.makeText(this, getString(R.string.diagnostic_collecting, label), Toast.LENGTH_SHORT).show()
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
                    getString(R.string.diagnostic_save_failed, label, it.javaClass.simpleName),
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
        val standaloneApps = remember {
            mutableStateMapOf<String, StandaloneAppManager.Snapshot?>()
        }
        var rootAvailable by remember { mutableStateOf<Boolean?>(null) }
        var xposedConnected by remember { mutableStateOf(SuiteXposedServiceBroker.isConnected()) }
        var permissions by remember { mutableStateOf(SuitePermissionState.snapshot(this)) }
        var resumeTick by remember { mutableIntStateOf(0) }
        val fullDiagnosticLabel = stringResource(R.string.diagnostic_full_label)
        var managementMode by rememberSaveable { mutableStateOf(false) }
        var managingFeatureId by rememberSaveable { mutableStateOf<String?>(null) }
        var settingsOpen by rememberSaveable { mutableStateOf(false) }
        var settingsModuleId by rememberSaveable { mutableStateOf<String?>(null) }
        val managementFeatures =
            if (managingFeatureId == null) features
            else features.filter { it.id == managingFeatureId }
        val homeModules = features.map { feature ->
            HomeModuleEntry(
                feature = feature,
                label = localizedFeatureName(feature),
                description = localizedFeatureDescription(feature),
                enabled = enabled[feature.id] == true,
            )
        }

        BackHandler(enabled = managementMode) {
            managementMode = false
            managingFeatureId = null
        }

        BackHandler(enabled = settingsOpen) {
            if (settingsModuleId != null) settingsModuleId = null else settingsOpen = false
        }

        val openModule: (FeatureSpec, String) -> Unit = { feature, localizedName ->
            SuiteCrashTracker.markActiveFeature(this@MainActivity, feature.id)
            runCatching {
                startActivity(feature.createIntent(this@MainActivity).apply {
                    putExtra(YAppearanceStore.MODULE_EXTRA, feature.id)
                })
            }.onFailure {
                SuiteLog.e(this@MainActivity, feature.id, "open failed", it)
                SuiteCrashTracker.markActiveFeature(this@MainActivity, null)
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.feature_open_failed, localizedName, it.javaClass.simpleName),
                    Toast.LENGTH_LONG,
                ).show()
            }
        }

        val updateFeatureEnabled: (FeatureSpec, Boolean) -> Unit = { feature, next ->
            store.setEnabled(feature, next)
            enabled[feature.id] = store.isEnabled(feature)
            if (next && enabled[feature.id] == true) {
                SuiteLog.i(this@MainActivity, feature.id, "host enabled")
            }
        }

        LifecycleResumeEffect(Unit) {
            resumeTick++
            onPauseOrDispose { }
        }

        LaunchedEffect(resumeTick) {
            rootAvailable = withContext(Dispatchers.IO) {
                RootManager.isAvailable(this@MainActivity)
            }
            permissions = SuitePermissionState.snapshot(this@MainActivity)

            val refreshedStandaloneApps = withContext(Dispatchers.IO) {
                features.associate { feature ->
                    val packageName = feature.standalonePackageName
                    feature.id to if (feature.standaloneEnabled && !packageName.isNullOrBlank()) {
                        if (rootAvailable == true && StandaloneAppManager.isManaged(this@MainActivity, packageName)) {
                            StandaloneAppManager.reconcileManaged(this@MainActivity, packageName)
                        }
                        StandaloneAppManager.snapshot(this@MainActivity, packageName)
                    } else {
                        null
                    }
                }
            }
            standaloneApps.clear()
            standaloneApps.putAll(refreshedStandaloneApps)
        }

        LaunchedEffect(Unit) {
            while (true) {
                xposedConnected = SuiteXposedServiceBroker.isConnected()
                permissions = SuitePermissionState.snapshot(this@MainActivity)
                delay(1_000L)
            }
        }

        if (settingsOpen) {
            SuiteSettingsScreen(
                modules = homeModules.map { it.feature.id to it.label },
                moduleId = settingsModuleId,
                onBack = {
                    if (settingsModuleId != null) settingsModuleId = null else settingsOpen = false
                },
                onSelectModule = { settingsModuleId = it },
                onOpenManagement = {
                    settingsOpen = false
                    managingFeatureId = null
                    managementMode = true
                },
            )
        } else {
        YDashboardScaffold(
            title = stringResource(R.string.app_name),
            subtitle = if (managementMode) stringResource(R.string.home_manage) else "",
        ) { scaffoldPadding ->
            if (!managementMode) {
                CompactSuiteHome(
                    modules = homeModules,
                    rootAvailable = rootAvailable,
                    xposedConnected = xposedConnected,
                    padding = scaffoldPadding,
                    onOpen = openModule,
                    onManage = { featureId ->
                        managingFeatureId = featureId
                        managementMode = true
                    },
                    onToggleEnabled = updateFeatureEnabled,
                    onExportModule = { feature, name ->
                        exportDiagnostic(setOf(feature.id), name)
                    },
                    onExportAll = { exportDiagnostic(null, fullDiagnosticLabel) },
                    onSettings = {
                        settingsModuleId = null
                        settingsOpen = true
                    },
                    onCustomizeModule = { id ->
                        settingsModuleId = id
                        settingsOpen = true
                    },
                )
            } else {
                YPageList(padding = scaffoldPadding) {
                    item {
                        YSecondaryButton(
                            text = stringResource(R.string.home_back),
                            onClick = {
                                managementMode = false
                                managingFeatureId = null
                            },
                        )
                    }
                    item {
                        YSecondaryButton(
                            text = stringResource(R.string.settings_title),
                            onClick = {
                                settingsModuleId = managingFeatureId
                                settingsOpen = true
                            },
                        )
                    }
                    if (managingFeatureId == null) {
                        item {
                            RuntimeEnvironmentCard(
                        featureCount = features.size,
                        rootAvailable = rootAvailable,
                        xposedConnected = xposedConnected,
                        permissions = permissions,
                        onAccessibility = {
                            if (!SuitePermissionState.openAccessibilitySettings(this@MainActivity)) {
                                Toast.makeText(this@MainActivity, R.string.error_open_accessibility_settings, Toast.LENGTH_LONG).show()
                            }
                        },
                        onOverlay = {
                            if (!SuitePermissionState.openOverlaySettings(this@MainActivity)) {
                                Toast.makeText(this@MainActivity, R.string.error_open_overlay_settings, Toast.LENGTH_LONG).show()
                            }
                        },
                        onNotificationListener = {
                            if (!SuitePermissionState.openNotificationListenerSettings(this@MainActivity)) {
                                Toast.makeText(this@MainActivity, R.string.error_open_notification_listener_settings, Toast.LENGTH_LONG).show()
                            }
                        },
                        onExport = { exportDiagnostic(null, fullDiagnosticLabel) },
                            )
                        }
                    }

                    items(managementFeatures.size, key = { managementFeatures[it].id }) { index ->
                    val feature = managementFeatures[index]
                    val localizedName = localizedFeatureName(feature)
                    val standalone = standaloneApps[feature.id]
                    FeatureCard(
                        feature = feature,
                        localizedName = localizedName,
                        localizedDescription = localizedFeatureDescription(feature),
                        isEnabled = enabled[feature.id] == true,
                        rootAvailable = rootAvailable,
                        xposedConnected = xposedConnected,
                        standalone = standalone,
                        onEnabledChange = { next -> updateFeatureEnabled(feature, next) },
                        onOpen = { openModule(feature, localizedName) },
                        onOpenStandalone = {
                            val packageName = feature.standalonePackageName ?: return@FeatureCard
                            if (!StandaloneAppManager.open(this@MainActivity, packageName)) {
                                Toast.makeText(
                                    this@MainActivity,
                                    getString(R.string.error_open_standalone, localizedName),
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                        },
                        onStandaloneSettings = {
                            val packageName = feature.standalonePackageName ?: return@FeatureCard
                            if (!StandaloneAppManager.openSettings(this@MainActivity, packageName)) {
                                Toast.makeText(
                                    this@MainActivity,
                                    getString(R.string.error_open_standalone_settings, localizedName),
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                        },
                        onStandaloneManagedChange = { next ->
                            val packageName = feature.standalonePackageName ?: return@FeatureCard
                            lifecycleScope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    StandaloneAppManager.setManaged(this@MainActivity, packageName, next)
                                }
                                standaloneApps[feature.id] = withContext(Dispatchers.IO) {
                                    StandaloneAppManager.snapshot(this@MainActivity, packageName)
                                }
                                if (!result.success) {
                                    Toast.makeText(
                                        this@MainActivity,
                                        getString(R.string.error_manage_standalone, localizedName, result.message),
                                        Toast.LENGTH_LONG,
                                    ).show()
                                }
                            }
                        },
                        onExportLog = { exportDiagnostic(setOf(feature.id), localizedName) },
                    )
                    }
                }
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
    permissions: SuitePermissionState.Snapshot,
    onAccessibility: () -> Unit,
    onOverlay: () -> Unit,
    onNotificationListener: () -> Unit,
    onExport: () -> Unit,
) {
    YSection(
        title = stringResource(R.string.runtime_environment_title),
        subtitle = stringResource(R.string.runtime_environment_subtitle),
        detail = stringResource(
            R.string.runtime_environment_detail,
            featureCount,
            SuiteXposedServiceBroker.listenerCount(),
        ),
    ) {
        YStatusLine(
            stringResource(R.string.capability_root),
            when (rootAvailable) {
                true -> stringResource(R.string.status_authorized)
                false -> stringResource(R.string.status_unavailable_or_unauthorized)
                null -> stringResource(R.string.status_checking)
            },
            when (rootAvailable) {
                true -> YStatusTone.Good
                false -> YStatusTone.Error
                null -> YStatusTone.Neutral
            },
        )
        YStatusLine(
            stringResource(R.string.capability_lsposed),
            if (xposedConnected) stringResource(R.string.status_connected) else stringResource(R.string.status_not_connected),
            if (xposedConnected) YStatusTone.Good else YStatusTone.Warning,
        )
        YStatusLine(
            stringResource(R.string.capability_accessibility),
            when {
                permissions.accessibilityConnected -> stringResource(R.string.status_connected)
                permissions.accessibilityEnabled -> stringResource(R.string.status_authorized_waiting_connection)
                else -> stringResource(R.string.status_not_authorized)
            },
            when {
                permissions.accessibilityConnected -> YStatusTone.Good
                permissions.accessibilityEnabled -> YStatusTone.Warning
                else -> YStatusTone.Error
            },
        )
        YStatusLine(
            stringResource(R.string.capability_overlay),
            if (permissions.overlayGranted) stringResource(R.string.status_authorized) else stringResource(R.string.status_not_authorized),
            if (permissions.overlayGranted) YStatusTone.Good else YStatusTone.Warning,
        )
        YStatusLine(
            stringResource(R.string.capability_notifications),
            if (permissions.notificationsGranted) stringResource(R.string.status_authorized) else stringResource(R.string.status_not_authorized),
            if (permissions.notificationsGranted) YStatusTone.Good else YStatusTone.Warning,
        )
        YStatusLine(
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
        YHorizontalActions {
            YSecondaryButton(
                text = if (permissions.accessibilityEnabled) stringResource(R.string.accessibility_settings) else stringResource(R.string.enable_accessibility),
                onClick = onAccessibility,
            )
            YSecondaryButton(text = stringResource(R.string.overlay_settings), onClick = onOverlay)
        }
        YHorizontalActions {
            YSecondaryButton(
                text = if (permissions.notificationListenerGranted) stringResource(R.string.notification_listener_settings) else stringResource(R.string.enable_notification_listener),
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
    rootAvailable: Boolean?,
    xposedConnected: Boolean,
    standalone: StandaloneAppManager.Snapshot?,
    onEnabledChange: (Boolean) -> Unit,
    onOpen: () -> Unit,
    onOpenStandalone: () -> Unit,
    onStandaloneSettings: () -> Unit,
    onStandaloneManagedChange: (Boolean) -> Unit,
    onExportLog: () -> Unit,
) {
    val capabilities = feature.sharedCapabilities
        .map { localizedCapabilityName(it) }
        .takeIf { it.isNotEmpty() }
        ?.joinToString(" + ")

    YSection(
        title = localizedName,
        subtitle = localizedDescription,
        detail = capabilities?.let { stringResource(R.string.shared_capabilities, it) },
    ) {
        if (feature.standaloneEnabled) {
            val standaloneValue = when {
                standalone == null -> stringResource(R.string.status_checking)
                !standalone.installed -> stringResource(R.string.status_not_installed)
                standalone.versionName != null && standalone.versionCode != null -> stringResource(
                    R.string.standalone_installed_version,
                    standalone.versionName,
                    standalone.versionCode,
                )
                else -> stringResource(R.string.standalone_installed_unknown_version)
            }
            val standaloneTone = when {
                standalone == null -> YStatusTone.Neutral
                standalone.installed -> YStatusTone.Good
                else -> YStatusTone.Warning
            }
            YStatusLine(
                label = stringResource(R.string.standalone_app),
                value = standaloneValue,
                tone = standaloneTone,
            )
            feature.standalonePackageName?.let { packageName ->
                Text(
                    text = stringResource(R.string.standalone_package, packageName),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (standalone?.installed == true) {
                val launcherValue = when {
                    !standalone.launcherAliasSupported -> stringResource(R.string.launcher_alias_update_required)
                    standalone.launcherHidden -> stringResource(R.string.status_hidden)
                    else -> stringResource(R.string.status_visible)
                }
                YStatusLine(
                    label = stringResource(R.string.launcher_entry),
                    value = launcherValue,
                    tone = when {
                        !standalone.launcherAliasSupported -> YStatusTone.Warning
                        standalone.launcherHidden -> YStatusTone.Good
                        else -> YStatusTone.Neutral
                    },
                )
                YSwitchItem(
                    title = stringResource(R.string.manage_standalone),
                    subtitle = if (standalone.launcherAliasSupported) {
                        stringResource(R.string.manage_standalone_summary)
                    } else {
                        stringResource(R.string.manage_standalone_update_summary)
                    },
                    checked = standalone.managed,
                    enabled = standalone.launcherAliasSupported && rootAvailable == true,
                    onCheckedChange = onStandaloneManagedChange,
                )
                if (feature.requiresHook && standalone.managed) {
                    HostWarning(stringResource(R.string.warning_standalone_hook_owner))
                }
            }
        }

        if (feature.requiresRoot && rootAvailable != true) {
            YStatusLine(
                stringResource(R.string.capability_root),
                when (rootAvailable) {
                    false -> stringResource(R.string.status_unavailable_or_unauthorized)
                    null -> stringResource(R.string.status_checking)
                    else -> stringResource(R.string.status_authorized)
                },
                if (rootAvailable == false) YStatusTone.Error else YStatusTone.Neutral,
            )
        }
        if (feature.requiresHook && !xposedConnected) {
            YStatusLine(
                stringResource(R.string.capability_lsposed),
                stringResource(R.string.status_not_connected),
                YStatusTone.Warning,
            )
        }

        YSwitchItem(
            title = stringResource(R.string.enable_feature),
            subtitle = stringResource(R.string.enable_feature_summary),
            checked = isEnabled,
            onCheckedChange = onEnabledChange,
        )
        YHorizontalActions {
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
        if (feature.standaloneEnabled && standalone?.installed == true) {
            YHorizontalActions {
                if (standalone.launchable) {
                    YSecondaryButton(
                        text = stringResource(R.string.open_standalone),
                        onClick = onOpenStandalone,
                    )
                }
                YSecondaryButton(
                    text = stringResource(R.string.standalone_settings),
                    onClick = onStandaloneSettings,
                )
            }
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
    SuiteCapability.ALL_FILES -> stringResource(R.string.capability_all_files)
    SuiteCapability.FILE_SHARE -> stringResource(R.string.capability_file_share)
    SuiteCapability.NFC -> stringResource(R.string.capability_nfc)
}
