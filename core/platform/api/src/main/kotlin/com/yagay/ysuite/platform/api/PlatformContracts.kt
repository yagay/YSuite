package com.yagay.ysuite.platform.api

import com.yagay.ysuite.common.Outcome

enum class CapabilityStatus {
    Available,
    Unavailable,
    PermissionRequired,
    Error,
}

data class RootRequest(
    val command: String,
    val timeoutMillis: Long = 10_000L,
)

data class RootResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
)

interface RootGateway {
    suspend fun status(): CapabilityStatus

    suspend fun execute(request: RootRequest): Outcome<RootResult>
}

interface HookGateway {
    suspend fun status(): CapabilityStatus

    suspend fun reload(scopePackages: Set<String> = emptySet()): Outcome<Unit>
}

data class PlatformServices(
    val root: RootGateway,
    val hooks: HookGateway,
)
