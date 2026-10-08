package com.yagay.ysuite.feature.ytaskmanager

import com.yagay.ysuite.feature.ytaskmanager.api.YTaskProcess

/**
 * /proc processes can share an Android UID. Network counters are per UID,
 * not per PID. Show the UID rate on just one representative process so
 * summing the process list never multiplies the same traffic.
 */
internal fun attributeNetworkToPrimaryProcess(
    processes: List<YTaskProcess>,
): List<YTaskProcess> {
    val primaryPidByUid =
        processes.asSequence()
            .filter { it.uid >= 0 }
            .groupBy { it.uid }
            .mapValues { (_, group) ->
                group.firstOrNull { process ->
                    process.packageName != null &&
                        process.command == process.packageName
                }?.pid
                    ?: group.firstOrNull { process ->
                        process.packageName != null &&
                            process.command.startsWith(
                                process.packageName + ":",
                            )
                    }?.pid
                    ?: group.first().pid
            }

    return processes.map { process ->
        if (
            process.uid < 0 ||
            primaryPidByUid[process.uid] == process.pid
        ) {
            process
        } else {
            process.copy(
                rxBytesPerSecond = 0L,
                txBytesPerSecond = 0L,
            )
        }
    }
}
