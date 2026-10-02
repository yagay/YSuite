package com.yagay.YSuite

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings

/** Host-side view of one separately installed standalone feature APK. */
object StandaloneAppManager {
    data class Snapshot(
        val packageName: String,
        val installed: Boolean,
        val versionName: String? = null,
        val versionCode: Long? = null,
        val launchable: Boolean = false,
    )

    fun snapshot(context: Context, packageName: String?): Snapshot? {
        if (packageName.isNullOrBlank()) return null
        val packageManager = context.packageManager
        val packageInfo = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0)
            }
        }.getOrNull()

        if (packageInfo == null) {
            return Snapshot(packageName = packageName, installed = false)
        }

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
            launchable = packageManager.getLaunchIntentForPackage(packageName) != null,
        )
    }

    fun open(context: Context, packageName: String): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    fun openSettings(context: Context, packageName: String): Boolean = runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            },
        )
    }.isSuccess
}
