package com.yagay.ysuite.feature.ypower

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class YPowerBootReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent?,
    ) {
        if (
            intent?.action !=
            Intent.ACTION_BOOT_COMPLETED
        ) return
        val pending = goAsync()
        EXECUTOR.execute {
            try {
                restore(context.applicationContext)
            } finally {
                pending.finish()
            }
        }
    }

    private fun restore(context: Context) {
        val prefs =
            context.getSharedPreferences(
                "ysuite_ypower_profiles",
                Context.MODE_PRIVATE,
            )
        prefs.all.forEach {
            (packageName, raw) ->
            val value =
                runCatching {
                    JSONObject(
                        raw as? String
                            ?: return@forEach,
                    )
                }.getOrNull()
                    ?: return@forEach
            if (
                !value.optBoolean(
                    "enabled",
                    false,
                )
            ) return@forEach
            val safe =
                "'" +
                    packageName.replace(
                        "'",
                        "'\\''",
                    ) +
                    "'"
            val uid =
                runCatching {
                    context.packageManager
                        .getApplicationInfo(
                            packageName,
                            0,
                        ).uid
                }.getOrNull()
                    ?: return@forEach
            shell(
                if (
                    value.optBoolean(
                        "dozeWhitelist",
                        true,
                    )
                ) {
                    "cmd deviceidle whitelist +" +
                        safe
                } else {
                    "cmd deviceidle whitelist -" +
                        safe
                },
            )
            if (
                value.optBoolean(
                    "backgroundOps",
                    true,
                )
            ) {
                shell(
                    "cmd appops set " +
                        safe +
                        " RUN_IN_BACKGROUND allow; " +
                        "cmd appops set " +
                        safe +
                        " RUN_ANY_IN_BACKGROUND allow; " +
                        "cmd appops set " +
                        safe +
                        " START_FOREGROUND allow",
                )
            }
            if (
                value.optBoolean(
                    "standbyActive",
                    true,
                )
            ) {
                shell(
                    "am set-inactive " +
                        safe +
                        " false; am set-standby-bucket " +
                        safe +
                        " active",
                )
            }
            if (
                value.optBoolean(
                    "backgroundData",
                    true,
                )
            ) {
                shell(
                    "cmd netpolicy add " +
                        "restrict-background-whitelist " +
                        uid,
                )
            }
            if (
                value.optBoolean(
                    "autoGrantDangerous",
                    false,
                )
            ) {
                grantDangerous(
                    context,
                    packageName,
                    safe,
                )
            }
        }
    }

    private fun grantDangerous(
        context: Context,
        packageName: String,
        safePackage: String,
    ) {
        val info =
            runCatching {
                context.packageManager
                    .getPackageInfo(
                        packageName,
                        PackageManager
                            .GET_PERMISSIONS,
                    )
            }.getOrNull()
                ?: return
        info.requestedPermissions
            ?.forEach {
                permission ->
                val permissionInfo =
                    runCatching {
                        context.packageManager
                            .getPermissionInfo(
                                permission,
                                0,
                            )
                    }.getOrNull()
                        ?: return@forEach
                if (
                    permissionInfo.protectionLevel and
                    PermissionInfo
                        .PROTECTION_MASK_BASE ==
                    PermissionInfo
                        .PROTECTION_DANGEROUS
                ) {
                    shell(
                        "pm grant --user current " +
                            safePackage +
                            " '" +
                            permission.replace(
                                "'",
                                "'\\''",
                            ) +
                            "' || true",
                    )
                }
            }
    }

    private fun shell(command: String) {
        runCatching {
            val process =
                ProcessBuilder(
                    "su",
                    "-c",
                    command,
                )
                    .redirectErrorStream(true)
                    .start()
            process.inputStream.close()
            process.waitFor(
                8,
                TimeUnit.SECONDS,
            )
            if (process.isAlive) {
                process.destroyForcibly()
            }
        }
    }

    companion object {
        private val EXECUTOR =
            Executors.newSingleThreadExecutor()
    }
}
