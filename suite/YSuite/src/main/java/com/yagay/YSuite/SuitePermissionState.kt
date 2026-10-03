package com.yagay.YSuite

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.yagay.YFloat.AccessibilityState
import com.yagay.YNotify.data.ListenerStateStore
import com.yagay.YNotify.util.ServiceGrantStatus
import com.yagay.YSuite.accessibility.SuiteAccessibilityService
import com.yagay.YSuite.notification.SuiteNotificationListenerService

/** Host-level permission snapshot used by the YSuite shell and shared capability users. */
object SuitePermissionState {
    private const val ACTION_ACCESSIBILITY_DETAILS_SETTINGS =
        "android.settings.ACCESSIBILITY_DETAILS_SETTINGS"

    data class Snapshot(
        val accessibilityEnabled: Boolean,
        val accessibilityConnected: Boolean,
        val accessibilityLabel: String,
        val legacyAccessibilityEnabled: Boolean,
        val otherAccessibilityHostEnabled: Boolean,
        val overlayGranted: Boolean,
        val notificationsGranted: Boolean,
        val notificationListenerGranted: Boolean,
        val notificationListenerConnected: Boolean,
        val legacyNotificationListenerEnabled: Boolean,
        val otherNotificationListenerHostEnabled: Boolean,
    )

    /**
     * Status inspection is UI-only and must never be able to crash the YSuite shell. OEM settings
     * providers, stale legacy components and feature-local state stores are all treated as optional.
     */
    fun snapshot(context: Context): Snapshot {
        val accessibility = runCatching { AccessibilityState.snapshot(context) }.getOrNull()
        val legacyAccessibility = runCatching {
            ServiceGrantStatus.legacySuiteAccessibilityEnabled(context)
        }.getOrDefault(false)
        val otherAccessibilityHost = runCatching {
            ServiceGrantStatus.otherHostAccessibilityEnabled(context)
        }.getOrDefault(false) || (accessibility?.otherYFloatEnabled == true)

        val notificationListenerGranted = runCatching {
            ServiceGrantStatus.notificationListenerEnabled(context)
        }.getOrDefault(false)
        val notificationListenerConnected = runCatching {
            SuiteNotificationListenerService.isConnected()
        }.getOrDefault(false) || runCatching {
            ListenerStateStore.isConnected(context)
        }.getOrDefault(false)

        return Snapshot(
            accessibilityEnabled = accessibility?.hostEnabled == true,
            accessibilityConnected = runCatching { SuiteAccessibilityService.isConnected() }.getOrDefault(false),
            accessibilityLabel = runCatching { accessibility?.statusLabel(context).orEmpty() }.getOrDefault(""),
            legacyAccessibilityEnabled = legacyAccessibility ||
                (accessibility?.sameHostOtherAccessibilityEnabled == true),
            otherAccessibilityHostEnabled = otherAccessibilityHost,
            overlayGranted = runCatching { Settings.canDrawOverlays(context) }.getOrDefault(false),
            notificationsGranted = Build.VERSION.SDK_INT < 33 || runCatching {
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
            }.getOrDefault(false),
            notificationListenerGranted = notificationListenerGranted,
            notificationListenerConnected = notificationListenerConnected,
            legacyNotificationListenerEnabled = runCatching {
                ServiceGrantStatus.legacySuiteNotificationListenerEnabled(context)
            }.getOrDefault(false),
            otherNotificationListenerHostEnabled = runCatching {
                ServiceGrantStatus.otherHostNotificationListenerEnabled(context)
            }.getOrDefault(false),
        )
    }

    fun openAccessibilitySettings(context: Context): Boolean {
        val component = ComponentName(context, SuiteAccessibilityService::class.java)
        val detail = Intent(ACTION_ACCESSIBILITY_DETAILS_SETTINGS)
            .putExtra(Intent.EXTRA_COMPONENT_NAME, component)
        if (context !is Activity) detail.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { context.startActivity(detail) }.isSuccess) return true

        val generic = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        if (context !is Activity) generic.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(generic) }.isSuccess
    }

    fun openOverlaySettings(context: Context): Boolean {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}"),
        )
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    fun openNotificationListenerSettings(context: Context): Boolean {
        val component = runCatching { ServiceGrantStatus.notificationListenerComponent(context) }
            .getOrNull()
        if (component != null) {
            val detail = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(
                    Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                    component.flattenToString(),
                )
            if (context !is Activity) detail.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (runCatching { context.startActivity(detail) }.isSuccess) return true
        }

        val generic = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        if (context !is Activity) generic.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(generic) }.isSuccess
    }
}
