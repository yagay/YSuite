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
import java.io.InputStream
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
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

private object AndroidLibXposedHookGateway :
    HookGateway,
    XposedServiceHelper.OnServiceListener {
    @Volatile
    private var service: XposedService? = null

    @Volatile
    private var registered = false

    @Synchronized
    fun ensureRegistered() {
        if (registered) return
        XposedServiceHelper.registerListener(this)
        registered = true
    }

    override fun onServiceBind(
        service: XposedService,
    ) {
        this.service = service
    }

    override fun onServiceDied(
        service: XposedService,
    ) {
        if (this.service === service) {
            this.service = null
        }
    }

    /**
     * LSPosed service binding arrives asynchronously, often after the first
     * settings screen has already tried writing its configuration.
     */
    private suspend fun connectedService(): XposedService? {
        ensureRegistered()
        repeat(15) {
            service?.let { return it }
            delay(100L)
        }
        return service
    }

    override suspend fun status():
        CapabilityStatus {
        val current = connectedService()
            ?: return CapabilityStatus.Unavailable
        return runCatching {
            if (current.apiVersion >= MIN_HOOK_API) {
                CapabilityStatus.Available
            } else {
                CapabilityStatus.Error
            }
        }.getOrDefault(
            CapabilityStatus.Error,
        )
    }

    override suspend fun reload(
        scopePackages: Set<String>,
    ): Outcome<Unit> {
        val current =
            connectedService()
                ?: return Outcome.Failure(
                    code =
                        "hook_service_unavailable",
                    message =
                        "LSPosed service is not connected",
                    retryable = true,
                )
        val requested =
            scopePackages
                .asSequence()
                .map(String::trim)
                .filter(String::isNotEmpty)
                .distinct()
                .toList()
        if (requested.isEmpty()) {
            return Outcome.Success(Unit)
        }

        val missing =
            runCatching {
                val existing =
                    current.scope
                        .map(String::trim)
                        .toSet()
                requested.filterNot {
                    it in existing
                }
            }.getOrDefault(requested)

        if (missing.isEmpty()) {
            return Outcome.Success(Unit)
        }

        return withTimeoutOrNull(60_000L) {
            suspendCancellableCoroutine {
                continuation ->
            try {
                current.requestScope(
                    missing,
                    object :
                        XposedService
                            .OnScopeEventListener {
                        override fun onScopeRequestApproved(
                            approved: List<String>,
                        ) {
                            if (!continuation.isActive) return
                            val accepted = approved.map(String::trim).toSet()
                            val omitted = missing.filterNot { it in accepted }
                            continuation.resume(
                                if (omitted.isEmpty()) {
                                    Outcome.Success(Unit)
                                } else {
                                    Outcome.Failure(
                                        code = "hook_scope_partial",
                                        message = "LSPosed did not approve: " +
                                            omitted.joinToString(", "),
                                        retryable = true,
                                    )
                                },
                            )
                        }

                        override fun onScopeRequestFailed(
                            message: String,
                        ) {
                            if (
                                continuation.isActive
                            ) {
                                continuation.resume(
                                    Outcome.Failure(
                                        code =
                                            "hook_scope_denied",
                                        message =
                                            message.ifBlank {
                                                "LSPosed scope request was denied"
                                            },
                                        retryable =
                                            true,
                                    ),
                                )
                            }
                        }
                    },
                )
            } catch (error: Throwable) {
                if (continuation.isActive) {
                    continuation.resume(
                        Outcome.Failure(
                            code =
                                "hook_scope_request_failed",
                            message =
                                error.message
                                    ?: "LSPosed scope request failed",
                            cause = error,
                            retryable = true,
                        ),
                    )
                }
            }
            }
        } ?: Outcome.Failure(
            code = "hook_scope_request_timeout",
            message = "LSPosed scope approval did not finish. Check the module scope in LSPosed.",
            retryable = true,
        )
    }

    override suspend fun writeConfig(
        group: String,
        key: String,
        value: String?,
    ): Outcome<Unit> = writeConfigBatch(group, mapOf(key to value))

    override suspend fun writeConfigBatch(
        group: String,
        values: Map<String, String?>,
    ): Outcome<Unit> {
        val safeGroup = group.trim()
        if (safeGroup.isBlank() || values.isEmpty() ||
            values.keys.any { it.isBlank() || it != it.trim() }) {
            return Outcome.Failure(
                code = "hook_config_invalid_key",
                message = "Hook preference group and keys must be nonblank",
            )
        }
        val current = connectedService()
            ?: return Outcome.Failure(
                code = "hook_service_unavailable",
                message = "LSPosed service is not connected",
                retryable = true,
            )
        return withContext(Dispatchers.IO) {
            runCatching {
                // One edit/commit guarantees all payload values and their
                // revision marker become visible together.
                val editor = current.getRemotePreferences(safeGroup).edit()
                values.forEach { (key, value) ->
                    if (value == null) editor.remove(key)
                    else editor.putString(key, value)
                }
                check(editor.commit()) {
                    "LSPosed remote preferences transaction failed"
                }
                Outcome.Success(Unit)
            }.getOrElse { error ->
                Outcome.Failure(
                    code = "hook_config_write_failed",
                    message = error.message
                        ?: "Unable to commit LSPosed remote preferences",
                    cause = error,
                    retryable = true,
                )
            }
        }
    }

    private const val MIN_HOOK_API = 102
}

/**
 * Drains stdout and stderr concurrently, with a strict memory cap for each
 * stream. After hitting the cap, the stream is still drained to prevent a
 * noisy Root command from blocking on a full OS pipe.
 */
private fun drainProcessStream(stream: InputStream, sink: StringBuilder) {
    try {
        stream.bufferedReader().use { reader ->
            val buffer = CharArray(8_192)
            var truncated = false
            while (true) {
                val count = reader.read(buffer)
                if (count <= 0) break
                val room = (PROCESS_OUTPUT_LIMIT - sink.length).coerceAtLeast(0)
                val kept = minOf(count, room)
                if (kept > 0) sink.append(buffer, 0, kept)
                if (kept < count) truncated = true
            }
            if (truncated) sink.append("\n[output truncated]")
        }
    } catch (_: java.io.IOException) {
        // Closing the pipes while a command is cancelled is expected.
    }
}

private fun collectProcess(
    process: Process,
    timeoutMillis: Long,
    timeoutMessage: String,
): RootResult {
    val stdout = StringBuilder()
    val stderr = StringBuilder()
    val stdoutThread = thread(start = true, isDaemon = true) {
        drainProcessStream(process.inputStream, stdout)
    }
    val stderrThread = thread(start = true, isDaemon = true) {
        drainProcessStream(process.errorStream, stderr)
    }
    try {
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
    } catch (interrupted: InterruptedException) {
        process.destroyForcibly()
        Thread.currentThread().interrupt()
        return RootResult(
            exitCode = TIMEOUT_EXIT_CODE,
            stdout = stdout.toString(),
            stderr = "Root command interrupted",
        )
    } finally {
        if (process.isAlive) process.destroyForcibly()
    }
}

object DefaultPlatformServices {
    fun create(): PlatformServices =
        PlatformServices(
            root = SuRootGateway,
            shizuku = AndroidShizukuGateway,
            hooks =
                AndroidLibXposedHookGateway,
        )
}

private const val THREAD_JOIN_MILLIS = 1_000L
private const val TIMEOUT_EXIT_CODE = 124
private const val PROCESS_OUTPUT_LIMIT = 512 * 1024
