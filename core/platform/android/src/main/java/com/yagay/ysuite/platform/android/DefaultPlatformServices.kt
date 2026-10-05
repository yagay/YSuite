package com.yagay.ysuite.platform.android

import android.content.pm.PackageManager
import com.yagay.ysuite.common.Outcome
import com.yagay.ysuite.platform.api.CapabilityStatus
import com.yagay.ysuite.platform.api.HookGateway
import com.yagay.ysuite.platform.api.PlatformServices
import com.yagay.ysuite.platform.api.RootGateway
import com.yagay.ysuite.platform.api.RootRequest
import com.yagay.ysuite.platform.api.RootResult
import com.yagay.ysuite.platform.api.ShizukuGateway
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

private object SuRootGateway : RootGateway {
    override suspend fun status(): CapabilityStatus =
        withContext(Dispatchers.IO) {
            val result =
                executeProcess(
                    command = "id -u",
                    timeoutMillis =
                        STATUS_TIMEOUT_MILLIS,
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
                val result =
                    executeProcess(
                        command = request.command,
                        timeoutMillis =
                            request.timeoutMillis,
                    )
                if (result != null) {
                    Outcome.Success(result)
                } else {
                    Outcome.Failure(
                        code = "root_unavailable",
                        message =
                            ROOT_UNAVAILABLE_MESSAGE,
                        retryable = true,
                    )
                }
            } catch (error: Throwable) {
                Outcome.Failure(
                    code =
                        "root_execution_failed",
                    message =
                        error.message
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
        val process =
            ProcessBuilder(
                executable,
                "-c",
                command,
            ).start()
        return collectProcess(
            process = process,
            timeoutMillis = timeoutMillis,
            timeoutMessage = ROOT_TIMEOUT_MESSAGE,
        )
    }

    private fun findSu(): String? {
        val candidates =
            listOf(
                "/system/bin/su",
                "/system/xbin/su",
                "/sbin/su",
                "su",
            )
        return candidates.firstOrNull {
            candidate ->
            candidate == "su" ||
                File(candidate).canExecute()
        }
    }

    private const val STATUS_TIMEOUT_MILLIS =
        3_000L
    private const val ROOT_UNAVAILABLE_MESSAGE =
        "Root executable is unavailable"
    private const val ROOT_EXECUTION_FAILED_MESSAGE =
        "Root command failed"
    private const val ROOT_TIMEOUT_MESSAGE =
        "Root command timed out"
}

private object AndroidShizukuGateway :
    ShizukuGateway {
    override suspend fun status(): CapabilityStatus =
        withContext(Dispatchers.IO) {
            try {
                when {
                    Shizuku.isPreV11() ->
                        CapabilityStatus.Unavailable
                    !Shizuku.pingBinder() ->
                        CapabilityStatus.Unavailable
                    Shizuku.checkSelfPermission() ==
                        PackageManager.PERMISSION_GRANTED ->
                        CapabilityStatus.Available
                    else ->
                        CapabilityStatus.PermissionRequired
                }
            } catch (_: Throwable) {
                CapabilityStatus.Unavailable
            }
        }

    override fun requestPermission(
        requestCode: Int,
    ): Boolean =
        runCatching {
            if (!Shizuku.pingBinder()) {
                return@runCatching false
            }
            if (
                Shizuku.checkSelfPermission() ==
                PackageManager.PERMISSION_GRANTED
            ) {
                true
            } else {
                Shizuku.requestPermission(
                    requestCode,
                )
                true
            }
        }.getOrDefault(false)

    override suspend fun execute(
        request: RootRequest,
    ): Outcome<RootResult> =
        withContext(Dispatchers.IO) {
            if (
                status() !=
                CapabilityStatus.Available
            ) {
                return@withContext Outcome.Failure(
                    code =
                        "shizuku_permission_required",
                    message =
                        "Shizuku is not ready or permission is missing",
                    retryable = true,
                )
            }

            try {
                val method =
                    Shizuku::class.java.methods
                        .firstOrNull {
                            it.name == "newProcess" &&
                                it.parameterTypes.size ==
                                3
                        }
                        ?: return@withContext Outcome.Failure(
                            code =
                                "shizuku_process_unavailable",
                            message =
                                "This Shizuku version does not expose process execution",
                        )
                val process =
                    method.invoke(
                        null,
                        arrayOf(
                            "sh",
                            "-c",
                            request.command,
                        ),
                        null,
                        null,
                    ) as Process
                Outcome.Success(
                    collectProcess(
                        process = process,
                        timeoutMillis =
                            request.timeoutMillis,
                        timeoutMessage =
                            SHIZUKU_TIMEOUT_MESSAGE,
                    ),
                )
            } catch (error: Throwable) {
                Outcome.Failure(
                    code =
                        "shizuku_execution_failed",
                    message =
                        error.message
                            ?: SHIZUKU_EXECUTION_FAILED_MESSAGE,
                    cause = error,
                    retryable = true,
                )
            }
        }

    private const val SHIZUKU_TIMEOUT_MESSAGE =
        "Shizuku command timed out"
    private const val SHIZUKU_EXECUTION_FAILED_MESSAGE =
        "Shizuku command failed"
}

private object UnconfiguredHookGateway :
    HookGateway {
    override suspend fun status():
        CapabilityStatus =
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

private fun collectProcess(
    process: Process,
    timeoutMillis: Long,
    timeoutMessage: String,
): RootResult {
    val stdout = StringBuilder()
    val stderr = StringBuilder()
    val stdoutThread =
        thread(start = true) {
            process.inputStream
                .bufferedReader()
                .use {
                    stdout.append(it.readText())
                }
        }
    val stderrThread =
        thread(start = true) {
            process.errorStream
                .bufferedReader()
                .use {
                    stderr.append(it.readText())
                }
        }

    val completed =
        process.waitFor(
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
            stderr = timeoutMessage,
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

object DefaultPlatformServices {
    fun create(): PlatformServices =
        PlatformServices(
            root = SuRootGateway,
            shizuku = AndroidShizukuGateway,
            hooks = UnconfiguredHookGateway,
        )
}

private const val THREAD_JOIN_MILLIS = 1_000L
private const val TIMEOUT_EXIT_CODE = 124
