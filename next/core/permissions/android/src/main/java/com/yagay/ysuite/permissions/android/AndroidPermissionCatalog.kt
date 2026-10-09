package com.yagay.ysuite.permissions.android

import android.content.Context
import android.content.pm.PackageManager
import com.yagay.ysuite.permissions.api.PermissionCatalog
import com.yagay.ysuite.permissions.api.PermissionRequirement

class AndroidPermissionCatalog(
    private val context: Context,
) : PermissionCatalog {
    @Suppress("DEPRECATION")
    override fun requirements(): List<PermissionRequirement> {
        val packageInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS,
        )

        return packageInfo.requestedPermissions
            .orEmpty()
            .distinct()
            .sorted()
            .map(::PermissionRequirement)
    }
}
