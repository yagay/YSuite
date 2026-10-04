package com.yagay.ysuite.permissions.android

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.yagay.ysuite.permissions.api.PermissionChecker
import com.yagay.ysuite.permissions.api.PermissionStatus

class AndroidPermissionChecker(
    private val context: Context,
) : PermissionChecker {
    override fun status(permission: String): PermissionStatus =
        if (
            ContextCompat.checkSelfPermission(
                context,
                permission,
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            PermissionStatus.Granted
        } else {
            PermissionStatus.Denied
        }
}
