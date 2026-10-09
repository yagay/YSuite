package com.yagay.ysuite.permissions.api

enum class PermissionStatus {
    Granted,
    Denied,
}

data class PermissionRequirement(
    val permission: String,
    val required: Boolean = true,
)

data class PermissionResult(
    val statuses: Map<String, PermissionStatus>,
) {
    val allGranted: Boolean
        get() = statuses.values.all {
            it == PermissionStatus.Granted
        }
}

interface PermissionChecker {
    fun status(permission: String): PermissionStatus

    fun snapshot(
        requirements: List<PermissionRequirement>,
    ): PermissionResult =
        PermissionResult(
            statuses = requirements.associate { requirement ->
                requirement.permission to
                    status(requirement.permission)
            },
        )
}

fun interface PermissionCatalog {
    fun requirements(): List<PermissionRequirement>
}
