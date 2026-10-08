package com.yagay.ysuite.permissions.android

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.yagay.ysuite.permissions.api.PermissionChecker
import com.yagay.ysuite.permissions.api.PermissionStatus

class AndroidPermissionChecker(
    private val context: Context,
) : PermissionChecker {
    override fun status(permission: String): PermissionStatus {
        val granted = when (permission) {
            Manifest.permission.SYSTEM_ALERT_WINDOW ->
                Settings.canDrawOverlays(context)
            Manifest.permission.WRITE_SETTINGS ->
                Settings.System.canWrite(context)
            Manifest.permission.MANAGE_EXTERNAL_STORAGE ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    android.os.Environment.isExternalStorageManager()
                } else {
                    false
                }
            Manifest.permission.PACKAGE_USAGE_STATS -> {
                val ops = context.getSystemService(AppOpsManager::class.java)
                val mode = ops?.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName,
                )
                mode == AppOpsManager.MODE_ALLOWED ||
                    (mode == AppOpsManager.MODE_DEFAULT &&
                        ContextCompat.checkSelfPermission(
                            context, permission,
                        ) == PackageManager.PERMISSION_GRANTED)
            }
            Manifest.permission.REQUEST_INSTALL_PACKAGES ->
                context.packageManager.canRequestPackageInstalls()
            Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS ->
                context.getSystemService(PowerManager::class.java)
                    ?.isIgnoringBatteryOptimizations(context.packageName) == true
            Manifest.permission.QUERY_ALL_PACKAGES ->
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_PERMISSIONS,
                ).requestedPermissions?.contains(permission) == true
            else ->
                ContextCompat.checkSelfPermission(
                    context, permission,
                ) == PackageManager.PERMISSION_GRANTED
        }
        return if (granted) PermissionStatus.Granted else PermissionStatus.Denied
    }
}
