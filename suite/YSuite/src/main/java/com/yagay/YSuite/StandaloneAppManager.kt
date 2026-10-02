package com.yagay.YSuite

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.yagay.suite.core.SuiteContract
import com.yagay.suite.core.SuiteRootGateway

/** Host-side view and ownership controller for one separately installed feature APK. */
object StandaloneAppManager {
    private const val PREFS = "standalone_management"
    private const val KEY_PREFIX = "managed:"

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
     * Managed standalone APKs remain fully enabled; only their launcher alias is hidden. This keeps
     * services/providers/hooks available for diagnostics while making YSuite the visible entry point.
     */
    fun setManaged(context: Context, packageName: String, managed: Boolean): ManagementResult {
        if (!isSafePackageName(packageName)) return ManagementResult(false, "invalid package")
        val snapshot = snapshot(context, packageName)
            ?: return ManagementResult(false, "package unavailable")
        if (!snapshot.installed) return ManagementResult(false, "package not installed")
        if (!snapshot.launcherAliasSupported) {
            return ManagementResult(false, "standalone APK must be updated before launcher management")
        }

        val launcherResult = setLauncherHidden(context, packageName, managed)
        if (!launcherResult.success) return launcherResult

        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_PREFIX + packageName, managed)
            .apply()
        return ManagementResult(true)
    }

    /** Re-applies the launcher state after the standalone APK is updated/reinstalled. */
    fun reconcileManaged(context: Context, packageName: String): ManagementResult {
        if (!isManaged(context, packageName)) return ManagementResult(true)
        val state = snapshot(context, packageName) ?: return ManagementResult(false, "package unavailable")
        if (!state.installed) return ManagementResult(true)
        if (!state.launcherAliasSupported) {
            return ManagementResult(false, "standalone APK does not expose LauncherAlias")
        }
        if (state.launcherHidden) return ManagementResult(true)
        return setLauncherHidden(context, packageName, true)
    }

    fun setLauncherHidden(context: Context, packageName: String, hidden: Boolean): ManagementResult {
        if (!isSafePackageName(packageName)) return ManagementResult(false, "invalid package")
        if (launcherAliasInfo(context.packageManager, packageName) == null) {
            return ManagementResult(false, "LauncherAlias not found")
        }
        val component = "$packageName/$packageName.LauncherAlias"
        val command = if (hidden) {
            "cmd package disable-user --user 0 $component"
        } else {
            "cmd package enable $component"
        }
        val result = SuiteRootGateway.execute(
            context = context,
            pluginId = SuiteContract.HOST_MODULE_ID,
            operation = if (hidden) "hide-standalone-launcher" else "restore-standalone-launcher",
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
}
