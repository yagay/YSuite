package com.yagay.YSuite

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.yagay.suite.api.RuntimeOwnerGate
import com.yagay.suite.core.FeatureRegistry
import com.yagay.suite.core.SuiteContract
import com.yagay.suite.core.SuiteRootGateway

/** Host-side view and ownership controller for one separately installed feature APK. */
object StandaloneAppManager {
    private const val PREFS = "standalone_management"
    private const val KEY_PREFIX = "managed:"

    /**
     * Components that really must surrender Android component ownership while the corresponding
     * feature is managed by the combined host.
     *
     * Keep hook communication/state channels enabled. In particular ConfigProvider,
     * EngineStatusProvider and the YFloat/YNotify hook bridges are not duplicate Android owners;
     * disabling them while a previously loaded standalone hook is still retiring breaks its
     * control/status path. LSPosed ownership is handled separately by RuntimeOwnerGate.
     */
    private val managedRuntimeComponents = mapOf(
        "com.yagay.YEntryCleaner" to listOf(
            "com.yagay.YEntryCleaner.runtime.ComponentReconcileReceiver",
            "com.yagay.YEntryCleaner.runtime.ComponentReconcileJobService",
        ),
        "com.yagay.ydiag" to listOf(
            "com.yagay.ydiag.service.MonitorService",
        ),
        "com.yagay.YNotify" to listOf(
            "com.yagay.YNotify.collector.NotificationCaptureService",
            "com.yagay.YNotify.collector.UiAccessibilityService",
        ),
        "com.yagay.ypower" to listOf(
            "com.yagay.ypower.root.BootReceiver",
            "com.yagay.ypower.root.YPowerRootService",
        ),
        "com.yagay.YFloat" to listOf(
            "com.yagay.YFloat.FloatService",
            "com.yagay.YFloat.LensAccessibilityService",
            "com.yagay.YFloat.FloatServiceBootReceiver",
        ),
    )

    data class Snapshot(
        val packageName: String,
        val installed: Boolean,
        val versionName: String? = null,
        val versionCode: Long? = null,
        val launchable: Boolean = false,
        val launcherAliasSupported: Boolean = false,
        val launcherHidden: Boolean = false,
        val managed: Boolean = false,
    )

    data class ManagementResult(
        val success: Boolean,
        val message: String = "",
    )

    fun snapshot(context: Context, packageName: String?): Snapshot? {
        if (packageName.isNullOrBlank()) return null
        val packageManager = context.packageManager
        val packageInfo = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(PackageManager.MATCH_DISABLED_COMPONENTS.toLong()))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, PackageManager.MATCH_DISABLED_COMPONENTS)
            }
        }.getOrNull()

        if (packageInfo == null) {
            return Snapshot(
                packageName = packageName,
                installed = false,
                managed = isManaged(context, packageName),
            )
        }

        val aliasInfo = launcherAliasInfo(packageManager, packageName)
        val launcherHidden = aliasInfo != null && isLauncherAliasDisabled(packageManager, packageName)
        return Snapshot(
            packageName = packageName,
            installed = true,
            versionName = packageInfo.versionName,
            versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            },
            launchable = resolveStandaloneActivity(packageManager, packageName) != null,
            launcherAliasSupported = aliasInfo != null,
            launcherHidden = launcherHidden,
            managed = isManaged(context, packageName),
        )
    }

    fun isManaged(context: Context, packageName: String): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_PREFIX + packageName, false)

    /**
     * Managed standalone APKs stay installed and their real activities/IPC bridges stay enabled.
     * YSuite owns duplicate services and marks itself as the LSPosed runtime owner before hiding the
     * standalone launcher. Existing hook callbacks can retire safely while future hook installs see
     * the owner marker and remain passive.
     */
    fun setManaged(context: Context, packageName: String, managed: Boolean): ManagementResult {
        if (!isSafePackageName(packageName)) return ManagementResult(false, "invalid package")
        val snapshot = snapshot(context, packageName)
            ?: return ManagementResult(false, "package unavailable")
        if (!snapshot.installed) return ManagementResult(false, "package not installed")
        if (!snapshot.launcherAliasSupported) {
            return ManagementResult(false, "standalone APK must be updated before launcher management")
        }
        val featureId = featureIdForPackage(packageName)
            ?: return ManagementResult(false, "feature not registered")

        if (managed) {
            val ownerResult = setRuntimeOwner(context, featureId, suiteOwned = true)
            if (!ownerResult.success) return ownerResult

            val componentsResult = setRuntimeComponentsEnabled(context, packageName, enabled = false)
            if (!componentsResult.success) {
                setRuntimeOwner(context, featureId, suiteOwned = false)
                return componentsResult
            }

            val launcherResult = setLauncherHidden(context, packageName, hidden = true)
            if (!launcherResult.success) {
                setRuntimeComponentsEnabled(context, packageName, enabled = true)
                setRuntimeOwner(context, featureId, suiteOwned = false)
                return launcherResult
            }
        } else {
            // Restore Android owners and launcher before allowing the standalone hook to become
            // active again. If any restore fails, keep the YSuite owner marker to avoid split-brain.
            val componentsResult = setRuntimeComponentsEnabled(context, packageName, enabled = true)
            if (!componentsResult.success) return componentsResult

            val launcherResult = setLauncherHidden(context, packageName, hidden = false)
            if (!launcherResult.success) {
                setRuntimeComponentsEnabled(context, packageName, enabled = false)
                return launcherResult
            }

            val ownerResult = setRuntimeOwner(context, featureId, suiteOwned = false)
            if (!ownerResult.success) {
                setLauncherHidden(context, packageName, hidden = true)
                setRuntimeComponentsEnabled(context, packageName, enabled = false)
                return ownerResult
            }
        }

        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_PREFIX + packageName, managed)
            .apply()
        return ManagementResult(true)
    }

    /** Re-applies ownership after the standalone APK is updated/reinstalled. */
    fun reconcileManaged(context: Context, packageName: String): ManagementResult {
        if (!isManaged(context, packageName)) return ManagementResult(true)
        val state = snapshot(context, packageName) ?: return ManagementResult(false, "package unavailable")
        if (!state.installed) return ManagementResult(true)
        if (!state.launcherAliasSupported) {
            return ManagementResult(false, "standalone APK does not expose LauncherAlias")
        }
        val featureId = featureIdForPackage(packageName)
            ?: return ManagementResult(false, "feature not registered")
        val ownerResult = setRuntimeOwner(context, featureId, suiteOwned = true)
        if (!ownerResult.success) return ownerResult
        val componentsResult = setRuntimeComponentsEnabled(context, packageName, enabled = false)
        if (!componentsResult.success) return componentsResult
        return if (state.launcherHidden) ManagementResult(true)
        else setLauncherHidden(context, packageName, true)
    }

    fun setLauncherHidden(context: Context, packageName: String, hidden: Boolean): ManagementResult {
        if (!isSafePackageName(packageName)) return ManagementResult(false, "invalid package")
        if (launcherAliasInfo(context.packageManager, packageName) == null) {
            return ManagementResult(false, "LauncherAlias not found")
        }
        val component = "$packageName/$packageName.LauncherAlias"
        return runPackageCommand(
            context = context,
            operation = if (hidden) "hide-standalone-launcher" else "restore-standalone-launcher",
            command = if (hidden) {
                "cmd package disable-user --user 0 $component"
            } else {
                "cmd package enable $component"
            },
        )
    }

    private fun setRuntimeOwner(
        context: Context,
        featureId: String,
        suiteOwned: Boolean,
    ): ManagementResult {
        val key = RuntimeOwnerGate.settingsKey(featureId)
        if (!key.matches(Regex("[a-z0-9_]+"))) {
            return ManagementResult(false, "invalid runtime owner key")
        }
        return runPackageCommand(
            context = context,
            operation = if (suiteOwned) "claim-runtime-owner" else "release-runtime-owner",
            command = if (suiteOwned) {
                "settings put global $key ${RuntimeOwnerGate.OWNER_SUITE}"
            } else {
                "settings delete global $key"
            },
        )
    }

    private fun featureIdForPackage(packageName: String): String? =
        FeatureRegistry.all.firstOrNull { it.standalonePackageName == packageName }?.id

    private fun setRuntimeComponentsEnabled(
        context: Context,
        packageName: String,
        enabled: Boolean,
    ): ManagementResult {
        val components = managedRuntimeComponents[packageName].orEmpty()
        for (className in components) {
            if (!isSafeComponentName(className)) {
                return ManagementResult(false, "invalid component: $className")
            }
            val flattened = "$packageName/$className"
            val result = runPackageCommand(
                context = context,
                operation = if (enabled) "restore-standalone-component" else "suppress-standalone-component",
                command = if (enabled) {
                    "cmd package enable $flattened"
                } else {
                    "cmd package disable-user --user 0 $flattened"
                },
            )
            if (!result.success) return result
        }
        return ManagementResult(true)
    }

    private fun runPackageCommand(
        context: Context,
        operation: String,
        command: String,
    ): ManagementResult {
        val result = SuiteRootGateway.execute(
            context = context,
            pluginId = SuiteContract.HOST_MODULE_ID,
            operation = operation,
            command = command,
            timeoutSeconds = 10L,
        )
        return ManagementResult(
            success = result.success,
            message = if (result.success) "" else result.failureMessage(),
        )
    }

    fun open(context: Context, packageName: String): Boolean {
        val component = resolveStandaloneActivity(context.packageManager, packageName) ?: return false
        val intent = Intent(Intent.ACTION_MAIN).apply {
            setComponent(component)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    fun openSettings(context: Context, packageName: String): Boolean = runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            },
        )
    }.isSuccess

    private fun resolveStandaloneActivity(packageManager: PackageManager, packageName: String): ComponentName? {
        launcherAliasInfo(packageManager, packageName)?.targetActivity?.takeIf(String::isNotBlank)?.let { target ->
            return ComponentName(packageName, target)
        }
        return packageManager.getLaunchIntentForPackage(packageName)?.component
    }

    private fun launcherAliasInfo(packageManager: PackageManager, packageName: String): ActivityInfo? {
        val component = ComponentName(packageName, "$packageName.LauncherAlias")
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getActivityInfo(
                    component,
                    PackageManager.ComponentInfoFlags.of(PackageManager.MATCH_DISABLED_COMPONENTS.toLong()),
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getActivityInfo(component, PackageManager.MATCH_DISABLED_COMPONENTS)
            }
        }.getOrNull()
    }

    private fun isLauncherAliasDisabled(packageManager: PackageManager, packageName: String): Boolean {
        val component = ComponentName(packageName, "$packageName.LauncherAlias")
        return runCatching {
            when (packageManager.getComponentEnabledSetting(component)) {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED -> true
                else -> false
            }
        }.getOrDefault(false)
    }

    private fun isSafePackageName(packageName: String): Boolean =
        packageName.matches(Regex("[A-Za-z0-9_.]+")) && '.' in packageName

    private fun isSafeComponentName(className: String): Boolean =
        className.matches(Regex("[A-Za-z0-9_.$]+")) && '.' in className
}
