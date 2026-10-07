package com.yagay.YFloat.compat

import com.yagay.ysuite.common.Outcome
import com.yagay.ysuite.logging.api.YSuiteLogger
import com.yagay.ysuite.platform.api.RootGateway
import com.yagay.ysuite.platform.api.RootRequest
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

object YFloatRuntimeHost {
    data class CommandResult(
        val code: Int,
        val stdout: ByteArray,
        val stderr: String,
        val timedOut: Boolean = false,
        val errorMessage: String? = null,
    )

    @Volatile private var rootGateway: RootGateway? = null
    @Volatile private var logger: YSuiteLogger? = null

    @JvmStatic
    fun install(
        rootGateway: RootGateway,
        logger: YSuiteLogger,
    ) {
        this.rootGateway = rootGateway
        this.logger = logger
    }

    @JvmStatic
    fun isInstalled(): Boolean = rootGateway != null

    @JvmStatic
    fun run(
        command: String,
        timeoutSeconds: Long,
        maxStdoutBytes: Int,
        binary: Boolean,
        mergeError: Boolean,
    ): CommandResult {
        val gateway =
            rootGateway
                ?: return CommandResult(
                    code = -1,
                    stdout = ByteArray(0),
                    stderr = "Managed Root host is not attached",
                    errorMessage = "Managed Root host is not attached",
                )

        return runBlocking(Dispatchers.IO) {
            val wrapped =
                if (binary) {
                    val stderr =
                        if (mergeError) " 2>&1" else ""
                    "($command)$stderr | base64 -w0"
                } else {
                    command
                }
            when (
                val outcome =
                    gateway.execute(
                        RootRequest(
                            command = wrapped,
                            timeoutMillis =
                                timeoutSeconds.coerceAtLeast(1L) * 1_000L,
                        ),
                    )
            ) {
                is Outcome.Success -> {
                    val bytes =
                        if (binary) {
                            runCatching {
                                Base64.getMimeDecoder()
                                    .decode(outcome.value.stdout.trim())
                            }.getOrElse { ByteArray(0) }
                        } else {
                            outcome.value.stdout.toByteArray()
                        }
                    CommandResult(
                        code = outcome.value.exitCode,
                        stdout =
                            if (bytes.size > maxStdoutBytes) {
                                bytes.copyOf(maxStdoutBytes)
                            } else {
                                bytes
                            },
                        stderr = outcome.value.stderr,
                    )
                }
                is Outcome.Failure ->
                    CommandResult(
                        code = -1,
                        stdout = ByteArray(0),
                        stderr = outcome.error.message,
                        errorMessage =
                            outcome.error.code + ": " + outcome.error.message,
                    )
            }
        }
    }

    @JvmStatic
    fun info(tag: String, message: String) {
        logger?.debug(tag, message)
    }

    @JvmStatic
    fun error(
        tag: String,
        message: String,
        throwable: Throwable? = null,
    ) {
        logger?.error(tag, message, throwable)
    }
}
