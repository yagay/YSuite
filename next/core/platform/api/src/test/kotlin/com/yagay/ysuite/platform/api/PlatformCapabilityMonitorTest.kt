package com.yagay.ysuite.platform.api

import com.yagay.ysuite.common.Outcome
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class PlatformCapabilityMonitorTest {
    @Test
    fun monitorReportsSharedCapabilityLayers() =
        runBlocking {
            val monitor =
                PlatformCapabilityMonitor(
                    PlatformServices(
                        root =
                            FakeRootGateway(
                                CapabilityStatus
                                    .Unavailable,
                            ),
                        shizuku =
                            FakeShizukuGateway(
                                CapabilityStatus
                                    .Available,
                            ),
                        hooks =
                            FakeHookGateway(
                                CapabilityStatus
                                    .PermissionRequired,
                            ),
                    ),
                )

            val snapshot = monitor.probe()

            assertEquals(
                CapabilityStatus.Available,
                snapshot[
                    CapabilityKind.Normal
                ],
            )
            assertEquals(
                CapabilityStatus.Available,
                snapshot[
                    CapabilityKind.Shizuku
                ],
            )
            assertEquals(
                CapabilityStatus.Unavailable,
                snapshot[
                    CapabilityKind.Root
                ],
            )
            assertEquals(
                CapabilityStatus.PermissionRequired,
                snapshot[
                    CapabilityKind.Hooks
                ],
            )
        }

    @Test
    fun privilegedRouterPrefersShizuku() =
        runBlocking {
            val root =
                FakeRootGateway(
                    CapabilityStatus.Available,
                    stdout = "root",
                )
            val shizuku =
                FakeShizukuGateway(
                    CapabilityStatus.Available,
                    stdout = "shizuku",
                )
            val router =
                PrivilegedCommandRouter(
                    PlatformServices(
                        root = root,
                        shizuku = shizuku,
                        hooks =
                            FakeHookGateway(
                                CapabilityStatus
                                    .Unavailable,
                            ),
                    ),
                )

            val result =
                router.execute(
                    RootRequest("id"),
                )

            assertEquals(
                "shizuku",
                (result as Outcome.Success)
                    .value.stdout,
            )
        }
}

private class FakeRootGateway(
    private val state: CapabilityStatus,
    private val stdout: String = "",
) : RootGateway {
    override suspend fun status():
        CapabilityStatus = state

    override suspend fun execute(
        request: RootRequest,
    ): Outcome<RootResult> =
        Outcome.Success(
            RootResult(
                exitCode = 0,
                stdout = stdout,
                stderr = "",
            ),
        )
}

private class FakeShizukuGateway(
    private val state: CapabilityStatus,
    private val stdout: String = "",
) : ShizukuGateway {
    override suspend fun status():
        CapabilityStatus = state

    override fun requestPermission(
        requestCode: Int,
    ): Boolean = true

    override suspend fun execute(
        request: RootRequest,
    ): Outcome<RootResult> =
        Outcome.Success(
            RootResult(
                exitCode = 0,
                stdout = stdout,
                stderr = "",
            ),
        )
}

private class FakeHookGateway(
    private val state: CapabilityStatus,
) : HookGateway {
    override suspend fun status():
        CapabilityStatus = state

    override suspend fun reload(
        scopePackages: Set<String>,
    ): Outcome<Unit> =
        Outcome.Success(Unit)
}
