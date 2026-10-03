package com.yagay.suite.core

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.yagay.suite.api.HostCapability
import com.yagay.suite.api.HostCapabilityRequestResult
import com.yagay.suite.api.HostCapabilityState

/**
 * Single Android permission/special-access router used by both YSuite and generic standalone hosts.
 * Features declare capabilities; they never own the Settings/permission flow themselves.
 */
internal object SuiteCapabilityBroker {
    fun state(context: Context, capability: HostCapability): HostCapabilityState {
        val app = context.applicationContext
        return when (capability) {
            HostCapability.ROOT -> granted(RootManager.isAvailable(app))
            HostCapability.LSPOSED -> granted(SuiteXposedServiceBroker.isConnected())
            HostCapability.ACCESSIBILITY -> accessibilityState(app)
            HostCapability.NOTIFICATION_LISTENER -> notificationListenerState(app)
            HostCapability.OVERLAY -> granted(Settings.canDrawOverlays(app))
            HostCapability.NOTIFICATIONS -> notificationState(app)
            HostCapability.ALL_FILES -> allFilesState()
            HostCapability.FILE_SHARE -> HostCapabilityState.GRANTED
            HostCapability.NFC -> if (app.packageManager.hasSystemFeature(PackageManager.FEATURE_NFC)) {
                HostCapabilityState.GRANTED
            } else {
                HostCapabilityState.NOT_SUPPORTED
            }
        }
    }

    fun request(
        activity: Activity,
        capability: HostCapability,
        requestCode: Int,
    ): HostCapabilityRequestResult {
        val current = state(activity, capability)
        if (current == HostCapabilityState.GRANTED) return HostCapabilityRequestResult.ALREADY_GRANTED
        if (current == HostCapabilityState.NOT_SUPPORTED) return HostCapabilityRequestResult.NOT_SUPPORTED

        return when (capability) {
            HostCapability.NOTIFICATIONS -> requestNotifications(activity, requestCode)
            HostCapability.OVERLAY -> openSettings(
                activity,
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${activity.packageName}")),
            )
            HostCapability.ALL_FILES -> openSettings(
                activity,
                Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:${activity.packageName}"),
                ),
            )
            HostCapability.ACCESSIBILITY -> openSettings(activity, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            HostCapability.NOTIFICATION_LISTENER ->
                openSettings(activity, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            HostCapability.ROOT -> {
                // RootManager owns the one process-wide libsu session. isAvailable() may trigger the
                // root manager's normal grant flow, but the Feature never initializes libsu itself.
                if (RootManager.isAvailable(activity)) HostCapabilityRequestResult.ALREADY_GRANTED
                else HostCapabilityRequestResult.MANUAL_ACTION_REQUIRED
            }
            HostCapability.LSPOSED -> HostCapabilityRequestResult.MANUAL_ACTION_REQUIRED
            HostCapability.NFC -> HostCapabilityRequestResult.NOT_SUPPORTED
            HostCapability.FILE_SHARE -> HostCapabilityRequestResult.ALREADY_GRANTED
        }
    }

    private fun notificationState(context: Context): HostCapabilityState {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return HostCapabilityState.GRANTED
        return granted(
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }

    private fun allFilesState(): HostCapabilityState {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return HostCapabilityState.GRANTED
        return granted(Environment.isExternalStorageManager())
    }

    private fun accessibilityState(context: Context): HostCapabilityState {
        val manager = context.getSystemService(AccessibilityManager::class.java)
            ?: return HostCapabilityState.NOT_SUPPORTED
        val enabled = manager
            .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { info -> info.resolveInfo?.serviceInfo?.packageName == context.packageName }
        return granted(enabled)
    }

    private fun notificationListenerState(context: Context): HostCapabilityState =
        granted(context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context))

    private fun requestNotifications(
        activity: Activity,
        requestCode: Int,
    ): HostCapabilityRequestResult {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return HostCapabilityRequestResult.ALREADY_GRANTED
        }
        return runCatching {
            activity.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), requestCode)
            HostCapabilityRequestResult.RUNTIME_PERMISSION_REQUESTED
        }.getOrDefault(HostCapabilityRequestResult.FAILED)
    }

    private fun openSettings(activity: Activity, intent: Intent): HostCapabilityRequestResult =
        runCatching {
            activity.startActivity(intent)
            HostCapabilityRequestResult.SETTINGS_OPENED
        }.getOrDefault(HostCapabilityRequestResult.FAILED)

    private fun granted(value: Boolean): HostCapabilityState =
        if (value) HostCapabilityState.GRANTED else HostCapabilityState.NOT_GRANTED
}
