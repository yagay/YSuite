package com.yagay.ysuite.permissions

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

enum class PermissionStatus {
    Granted,
    Denied,
}

data class PermissionRequirement(
    val permission: String,
    val required: Boolean = true,
)

interface PermissionChecker {
    fun status(permission: String): PermissionStatus
}

class AndroidPermissionChecker(
    private val context: Context,
) : PermissionChecker {
    override fun status(permission: String): PermissionStatus =
        if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
            PermissionStatus.Granted
        } else {
            PermissionStatus.Denied
        }
}
