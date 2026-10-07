package com.yagay.ysuite.feature.ytaskmanager

import android.content.Context
import com.yagay.ysuite.feature.ytaskmanager.api.YTaskProcess
import com.yagay.ysuite.feature.ytaskmanager.api.YTaskSnapshot
import com.yagay.ysuite.logging.api.YSuiteLogger
import com.yagay.ysuite.platform.api.CapabilityStatus
import com.yagay.ysuite.platform.api.RootGateway

internal class YTaskManagerRepository(
    context: Context,
    private val root: RootGateway,
    logger: YSuiteLogger,
) {
    private val shell =
        YTaskRootRunner(
            root,
            logger,
        )
    private val system =
        YTaskSystemSampler(shell)
    private val network =
        YTaskNetworkSampler(
            context,
            shell,
        )
    private val processes =
        YTaskProcessSampler(
            context,
            shell,
        )
    private val gpu =
        YTaskGpuSampler(
            context,
            shell,
        )

    suspend fun rootStatus():
        CapabilityStatus =
        root.status()

    suspend fun snapshot():
        YTaskSnapshot {
        val networkSample =
            network.read()
        val processRows =
            processes.read(
                networkSample.rows
                    .associateBy {
                        it.uid
                    },
            )
        return YTaskSnapshot(
            system = system.read(),
            gpu = gpu.read(),
            processes = processRows,
            network =
                networkSample.rows,
            networkBackend =
                networkSample.backend,
        )
    }

    suspend fun loadDetails(
        process: YTaskProcess,
    ): YTaskProcess =
        processes.details(process)

    suspend fun kill(
        pid: Int,
    ): Boolean {
        if (pid <= 1) return false
        return shell.run(
            "kill -9 $pid",
        ).exitCode == 0
    }

    suspend fun forceStop(
        packageName: String,
    ): Boolean {
        val safe =
            "'" +
                packageName.replace(
                    "'",
                    "'\\''",
                ) +
                "'"
        return shell.run(
            "am force-stop $safe",
        ).exitCode == 0
    }
}
