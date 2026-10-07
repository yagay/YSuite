package com.yagay.ysuite.feature.ytaskmanager

import com.yagay.ysuite.common.Outcome
import com.yagay.ysuite.logging.api.YSuiteLogger
import com.yagay.ysuite.platform.api.RootGateway
import com.yagay.ysuite.platform.api.RootRequest
import com.yagay.ysuite.platform.api.RootResult

internal class YTaskRootRunner(
    private val root: RootGateway,
    private val logger: YSuiteLogger,
) {
    suspend fun run(
        command: String,
        timeoutMillis: Long = 10_000L,
    ): RootResult =
        when (
            val outcome =
                root.execute(
                    RootRequest(
                        command = command,
                        timeoutMillis = timeoutMillis,
                    ),
                )
        ) {
            is Outcome.Success ->
                outcome.value
            is Outcome.Failure -> {
                logger.error(
                    TAG,
                    "Root command failed: " +
                        outcome.error.code,
                    outcome.error.cause,
                )
                error(
                    outcome.error.message,
                )
            }
        }

    suspend fun text(
        command: String,
        timeoutMillis: Long = 10_000L,
    ): String {
        val result =
            run(
                command,
                timeoutMillis,
            )
        if (
            result.exitCode != 0 &&
            result.stdout.isBlank()
        ) {
            error(
                result.stderr.ifBlank {
                    "Command failed"
                },
            )
        }
        return result.stdout
    }

    companion object {
        private const val TAG =
            "YSuite/YTaskManager"
    }
}
