package com.yagay.ysuite.platform.android

import com.yagay.ysuite.common.Outcome
import com.yagay.ysuite.platform.api.CapabilityStatus
import com.yagay.ysuite.platform.api.HookGateway
import com.yagay.ysuite.platform.api.PlatformServices
import com.yagay.ysuite.platform.api.RootGateway
import com.yagay.ysuite.platform.api.RootRequest
import com.yagay.ysuite.platform.api.RootResult
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private object SuRootGateway : RootGateway {
    override suspend fun status(): CapabilityStatus =
        withContext(Dispatchers.IO) {
            val result = executeProcess(
                command = "id -u",
                timeoutMillis = STATUS_TIMEOUT_MILLIS,
            )
            when {
                result == null ->
                    CapabilityStatus.Unavailable
                result.exitCode != 0 ->
                    CapabilityStatus.PermissionRequired
                result.stdout.trim() == "0" ->
                    CapabilityStatus.Available
                else ->
                    CapabilityStatus.Error
            }
        }

    override suspend fun execute(
        request: RootRequest,
    ): Outcome<RootResult> =
        withContext(Dispatchers.IO) {
            try {
                val result = executeProcess(
                    command = request.command,
                    timeoutMillis = request.timeoutMillis,
                )
                if (result != null) {
                    Outcome.Success(result)
                } else {
                    Outcome.Failure(
                        code = "root_unavailable",
                        message = ROOT_UNAVAILABLE_MESSAGE,
                        retryable = true,
                    )
                }
            } catch (error: Throwable) {
                Outcome.Failure(
                    code = "root_execution_failed",
                    message = error.message
                        ?: ROOT_EXECUTION_FAILED_MESSAGE,
                    cause = error,
                    retryable = true,
                )
            }
        }

    private fun executeProcess(
        command: String,
        timeoutMillis: Long,
    ): RootResult? {
        val executable = findSu() ?: return null
        val process = ProcessBuilder(
            executable,
            "-c",
            command,
        ).start()

        val stdout = StringBuilder()
        val stderr = StringBuilder()
        val stdoutThread = thread(start = true) {
            process.inputStream.bufferedReader().use {
                stdout.append(it.readText())
            }
        }
        val stderrThread = thread(start = true) {
            process.errorStream.bufferedReader().use {
                stderr.append(it.readText())
            }
        }

        val completed = process.waitFor(
            timeoutMillis.coerceAtLeast(1L),
            TimeUnit.MILLISECONDS,
        )
        if (!completed) {
            process.destroyForcibly()
            stdoutThread.join(THREAD_JOIN_MILLIS)
            stderrThread.join(THREAD_JOIN_MILLIS)
            return RootResult(
                exitCode = TIMEOUT_EXIT_CODE,
                stdout = stdout.toString(),
                stderr = ROOT_TIMEOUT_MESSAGE,
            )
        }

        stdoutThread.join(THREAD_JOIN_MILLIS)
        stderrThread.join(THREAD_JOIN_MILLIS)
        return RootResult(
            exitCode = process.exitValue(),
            stdout = stdout.toString(),
            stderr = stderr.toString(),
        )
    }

    private fun findSu(): String? {
        val candidates = listOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "su",
        )
        return candidates.firstOrNull { candidate ->
            candidate == "su" || File(candidate).canExecute()
        }
    }

    private const val STATUS_TIMEOUT_MILLIS = 3_000L
    private const val THREAD_JOIN_MILLIS = 1_000L
    private const val TIMEOUT_EXIT_CODE = 124
    private const val ROOT_UNAVAILABLE_MESSAGE =
        "Root executable is unavailable"
    private const val ROOT_EXECUTION_FAILED_MESSAGE =
        "Root command failed"
    private const val ROOT_TIMEOUT_MESSAGE =
        "Root command timed out"
}

private object UnconfiguredHookGateway : HookGateway {
    override suspend fun status(): CapabilityStatus =
        CapabilityStatus.Unavailable

    override suspend fun reload(
        scopePackages: Set<String>,
    ): Outcome<Unit> =
        Outcome.Failure(
            code = "hook_unavailable",
            message = HOOK_UNAVAILABLE_MESSAGE,
        )

    private const val HOOK_UNAVAILABLE_MESSAGE =
        "Hook adapter is not configured"
}

object DefaultPlatformServices {
    fun create(): PlatformServices =
        PlatformServices(
            root = SuRootGateway,
            hooks = UnconfiguredHookGateway,
        )
}
