package com.yagay.ysuite.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.yagay.ysuite.permissions.api.PermissionRequirement
import com.yagay.ysuite.permissions.api.PermissionResult
import com.yagay.ysuite.permissions.api.PermissionStatus

class YSuitePermissionRequester internal constructor(
    private val launchBlock: (List<PermissionRequirement>) -> Unit,
) {
    fun launch(requirements: List<PermissionRequirement>) {
        launchBlock(requirements)
    }
}

@Composable
fun rememberYSuitePermissionRequester(
    onResult: (PermissionResult) -> Unit,
): YSuitePermissionRequester {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        onResult(
            PermissionResult(
                statuses = results.mapValues { (_, granted) ->
                    if (granted) PermissionStatus.Granted else PermissionStatus.Denied
                },
            ),
        )
    }

    return remember(launcher, context) {
        YSuitePermissionRequester { requirements ->
            val permissions = requirements.map(PermissionRequirement::permission).distinct()
            val runtime = permissions.filter { permission ->
                runCatching {
                    val info = context.packageManager.getPermissionInfo(permission, 0)
                    (info.protectionLevel and PermissionInfo.PROTECTION_MASK_BASE) ==
                        PermissionInfo.PROTECTION_DANGEROUS
                }.getOrDefault(false)
            }
            // Android special accesses are managed by Settings, not a runtime
            // permission dialog. Open one appropriate page per user action.
            val special = permissions.firstOrNull { it !in runtime }
            if (special != null) {
                val action = when (special) {
                    Manifest.permission.SYSTEM_ALERT_WINDOW ->
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION
                    Manifest.permission.WRITE_SETTINGS ->
                        Settings.ACTION_MANAGE_WRITE_SETTINGS
                    Manifest.permission.MANAGE_EXTERNAL_STORAGE ->
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION
                    Manifest.permission.PACKAGE_USAGE_STATS ->
                        Settings.ACTION_USAGE_ACCESS_SETTINGS
                    else -> null
                }
                if (action != null) {
                    val intent = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    if (special != Manifest.permission.PACKAGE_USAGE_STATS) {
                        intent.data = Uri.parse("package:${context.packageName}")
                    }
                    val opened = runCatching {
                        context.startActivity(intent)
                    }.isSuccess
                    if (!opened) {
                        runCatching {
                            context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                    .setData(Uri.parse("package:${context.packageName}"))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                    }
                }
            }
            if (runtime.isNotEmpty()) {
                launcher.launch(runtime.toTypedArray())
            }
        }
    }
}
