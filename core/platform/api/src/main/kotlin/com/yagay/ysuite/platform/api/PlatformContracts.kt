package com.yagay.ysuite.platform.api

import com.yagay.ysuite.common.Outcome

enum class CapabilityKind {
    Normal,
    Shizuku,
    Root,
    Hooks,
}

enum class CapabilityStatus {
    Available,
    Unavailable,
    PermissionRequired,
    Error,
}

data class CapabilitySnapshot(
    val statuses: Map<CapabilityKind, CapabilityStatus>,
) {
    operator fun get(kind: CapabilityKind): CapabilityStatus =
        statuses[kind] ?: CapabilityStatus.Unavailable

    val available: Set<CapabilityKind>
        get() =
            statuses
                .filterValues { it == CapabilityStatus.Available }
                .keys
}

data class RootRequest(
    val command: String,
    val timeoutMillis: Long = 10_000L,
)

data class RootResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
) {
    val completion: CommandCompletion
        get() = when (exitCode) {
            0 -> CommandCompletion.Succeeded
            124 -> CommandCompletion.TimedOut
            else -> CommandCompletion.NonZeroExit
        }
}

enum class CommandCompletion {
    Succeeded,
    NonZeroExit,
    TimedOut,
}

interface CommandGateway {
    suspend fun status(): CapabilityStatus

    suspend fun execute(request: RootRequest): Outcome<RootResult>
}

interface RootGateway : CommandGateway

interface ShizukuGateway : CommandGateway {
    fun requestPermission(
        requestCode: Int = DEFAULT_PERMISSION_REQUEST_CODE,
    ): Boolean

    companion object {
        const val DEFAULT_PERMISSION_REQUEST_CODE = 9013
    }
}

interface HookGateway {
    suspend fun status(): CapabilityStatus

    /**
     * Ensures the supplied packages are in the module scope.
     *
     * The modern libxposed service does not hot-reload target processes from the app side. Feature
     * runtimes are expected to observe remote preferences for live configuration where possible.
     */
    suspend fun reload(scopePackages: Set<String> = emptySet()): Outcome<Unit>

    /**
     * Writes one feature-owned string payload into libxposed remote preferences.
     *
     * A null value removes the key. Keeping the payload string-based leaves feature serialization
     * inside the feature while the platform layer only owns LSPosed transport.
     */
    suspend fun writeConfig(
        group: String,
        key: String,
        value: String?,
    ): Outcome<Unit>
}

data class PlatformServices(
    val root: RootGateway,
    val shizuku: ShizukuGateway,
    val hooks: HookGateway,
)

enum class PrivilegedBackend {
    Shizuku,
    Root,
}

data class PrivilegedRoute(
    val backend: PrivilegedBackend,
    val status: CapabilityStatus,
)

class PlatformCapabilityMonitor(
    private val services: PlatformServices,
) {
    suspend fun probe(): CapabilitySnapshot =
        CapabilitySnapshot(
            statuses =
                linkedMapOf(
                    CapabilityKind.Normal to
                        CapabilityStatus.Available,
                    CapabilityKind.Shizuku to
                        runCatching {
                            services.shizuku.status()
                        }.getOrDefault(
                            CapabilityStatus.Error,
                        ),
                    CapabilityKind.Root to
                        runCatching {
                            services.root.status()
                        }.getOrDefault(
                            CapabilityStatus.Error,
                        ),
                    CapabilityKind.Hooks to
                        runCatching {
                            services.hooks.status()
                        }.getOrDefault(
                            CapabilityStatus.Error,
                        ),
                ),
        )
}

class PrivilegedCommandRouter(
    private val services: PlatformServices,
) {
    suspend fun routes(): List<PrivilegedRoute> =
        listOf(
            PrivilegedRoute(
                backend = PrivilegedBackend.Shizuku,
                status =
                    runCatching {
                        services.shizuku.status()
                    }.getOrDefault(
                        CapabilityStatus.Error,
                    ),
            ),
            PrivilegedRoute(
                backend = PrivilegedBackend.Root,
                status =
                    runCatching {
                        services.root.status()
                    }.getOrDefault(
                        CapabilityStatus.Error,
                    ),
            ),
        )

    suspend fun execute(
        request: RootRequest,
        preferShizuku: Boolean = true,
    ): Outcome<RootResult> {
        val candidates =
            if (preferShizuku) {
                listOf(
                    PrivilegedBackend.Shizuku,
                    PrivilegedBackend.Root,
                )
            } else {
                listOf(
                    PrivilegedBackend.Root,
                    PrivilegedBackend.Shizuku,
                )
            }

        for (backend in candidates) {
            val gateway: CommandGateway =
                when (backend) {
                    PrivilegedBackend.Shizuku ->
                        services.shizuku
                    PrivilegedBackend.Root ->
                        services.root
                }
            if (
                runCatching { gateway.status() }
                    .getOrDefault(
                        CapabilityStatus.Error,
                    ) ==
                CapabilityStatus.Available
            ) {
                return gateway.execute(request)
            }
        }

        return Outcome.Failure(
            code = "privileged_backend_unavailable",
            message =
                "Neither Shizuku nor Root is available",
            retryable = true,
        )
    }
}
