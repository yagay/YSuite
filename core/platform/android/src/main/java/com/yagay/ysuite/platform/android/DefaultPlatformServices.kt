package com.yagay.ysuite.platform.android

import com.yagay.ysuite.common.Outcome
import com.yagay.ysuite.platform.api.CapabilityStatus
import com.yagay.ysuite.platform.api.HookGateway
import com.yagay.ysuite.platform.api.PlatformServices
import com.yagay.ysuite.platform.api.RootGateway
import com.yagay.ysuite.platform.api.RootRequest
import com.yagay.ysuite.platform.api.RootResult

private object UnconfiguredRootGateway : RootGateway {
    override suspend fun status(): CapabilityStatus = CapabilityStatus.Unavailable

    override suspend fun execute(request: RootRequest): Outcome<RootResult> =
        Outcome.Failure("Root adapter is not configured")
}

private object UnconfiguredHookGateway : HookGateway {
    override suspend fun status(): CapabilityStatus = CapabilityStatus.Unavailable

    override suspend fun reload(scopePackages: Set<String>): Outcome<Unit> =
        Outcome.Failure("Hook adapter is not configured")
}

object DefaultPlatformServices {
    fun create(): PlatformServices =
        PlatformServices(
            root = UnconfiguredRootGateway,
            hooks = UnconfiguredHookGateway,
        )
}
