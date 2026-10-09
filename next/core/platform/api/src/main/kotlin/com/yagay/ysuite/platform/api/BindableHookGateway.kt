package com.yagay.ysuite.platform.api

import com.yagay.ysuite.common.Outcome

/** Common dynamic bridge for the single host-owned Hook gateway. */
class BindableHookGateway : HookGateway {
    @Volatile private var delegate: HookGateway? = null

    fun bind(gateway: HookGateway) {
        delegate = gateway
    }

    override suspend fun status(): CapabilityStatus =
        delegate?.status() ?: CapabilityStatus.Unavailable

    override suspend fun reload(scopePackages: Set<String>): Outcome<Unit> =
        delegate?.reload(scopePackages) ?: unavailable()

    override suspend fun writeConfig(group: String, key: String, value: String?): Outcome<Unit> =
        delegate?.writeConfig(group, key, value) ?: unavailable()

    override suspend fun writeConfigBatch(group: String, values: Map<String, String?>): Outcome<Unit> =
        delegate?.writeConfigBatch(group, values) ?: unavailable()

    private fun unavailable(): Outcome.Failure = Outcome.Failure(
        code = "hook_unavailable",
        message = "Hook service is unavailable",
        retryable = true,
    )
}
