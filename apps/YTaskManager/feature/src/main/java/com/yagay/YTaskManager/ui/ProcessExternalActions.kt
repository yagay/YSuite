package com.yagay.YTaskManager.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/**
 * Ordinary, user-initiated Android intents: no Root / LSPosed Hook is needed.
 * These deliberately do not invoke hidden OEM Settings activities.
 */
internal object ProcessExternalActions {
    fun canLaunch(context: Context, packageName: String): Boolean =
        runCatching {
            context.packageManager.getLaunchIntentForPackage(packageName) != null
        }.getOrDefault(false)

    fun launch(context: Context, packageName: String): Boolean {
        val intent = runCatching {
            context.packageManager.getLaunchIntentForPackage(packageName)
        }.getOrNull() ?: return false
        return start(context, intent)
    }

    fun openSystemAppInfo(context: Context, packageName: String): Boolean =
        start(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        })

    fun openNotificationSettings(context: Context, packageName: String): Boolean =
        start(context, Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        })

    private fun start(context: Context, intent: Intent): Boolean = runCatching {
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    }.getOrDefault(false)
}
