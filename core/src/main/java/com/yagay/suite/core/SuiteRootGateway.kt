package com.yagay.suite.core

import android.content.Context
import com.topjohnwu.superuser.Shell
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * The only Root execution gateway for the combined YSuite host.
 *
 * Plugins identify themselves and submit an operation; YSuite owns the actual root shell,
 * timeout policy, logging and result normalization. Standalone feature APKs may keep their local
 * root implementation, but code running through the combined host should call this gateway.
 */
object SuiteRootGateway {
    data class Result(
        val code: Int,
        val stdout: String,
        val stderr: String,
        val timedOut: Boolean = false,
        val error: Throwable? = null,
    ) {
        val success: Boolean get() = !timedOut && error == null && code == 0

        fun failureMessage(defaultMessage: String = "Root 操作失败"): String = when {
            timedOut -> "Root 操作超时"
            error != null -> error.message?.takeIf(String::isNotBlank) ?: error.javaClass.simpleName
            stderr.isNotBlank() -> stderr
            code != 0 -> "su exit=$code"
            else -> defaultMessage
        }
    }

    data class BinaryResult(
        val code: Int,
        val stdout: ByteArray,
        val stderr: String,
        val timedOut: Boolean = false,
        val errorMessage: String? = null,
    ) {
        val success: Boolean get() = !timedOut && errorMessage == null && code == 0
    }

    /**
     * Reflection-friendly entry used by independently buildable feature modules when embedded in
     * YSuite. Absence of this class means the feature is running standalone and may use its own
     * shell implementation.
     */
    @JvmStatic
    fun executeFromPlugin(
        pluginId: String,
        operation: String,
        command: String,
        timeoutSeconds: Long,
    ): Result {
        val context = RootManager.contextOrNull()
            ?: return Result(
                code = Shell.Result.JOB_NOT_EXECUTED,
                stdout = "",
                stderr = "YSuite root host is not initialized",
                error = IllegalStateException("YSuite root host is not initialized"),
            )
        return execute(context, pluginId, operation, command, timeoutSeconds)
    }

    /**
     * Reflection-friendly bounded binary command entry. This is used for screenshots/dumps where
     * stdout must remain bytes instead of being converted through line-oriented libsu output.
     */
    @JvmStatic
    fun executeBinaryFromPlugin(
        pluginId: String,
        operation: String,
        command: String,
        timeoutSeconds: Long,
        maxStdoutBytes: Int,
        mergeError: Boolean,
    ): BinaryResult {
        val context = RootManager.contextOrNull()
            ?: return BinaryResult(
                code = Shell.Result.JOB_NOT_EXECUTED,
                stdout = ByteArray(0),
                stderr = "YSuite root host is not initialized",
                errorMessage = "YSuite root host is not initialized",
            )
        return executeBinary(
            context = context,
            pluginId = pluginId,
            operation = operation,
            command = command,
            timeoutSeconds = timeoutSeconds,
            maxStdoutBytes = maxStdoutBytes,
            mergeError = mergeError,
        )
    }

    /**
     * Reflection-friendly streaming entry for long-lived collectors. The Process itself is created
     * by YSuite; the plugin only consumes its streams/lifecycle. In standalone builds this class is
     * absent and the feature may create its own local root process.
     */
    @JvmStatic
    fun startFromPlugin(
        pluginId: String,
        operation: String,
        command: String,
    ): Process {
        val context = RootManager.contextOrNull()
            ?: throw IllegalStateException("YSuite root host is not initialized")
        val safePlugin = pluginId.ifBlank { "unknown" }
        val safeOperation = operation.ifBlank { "stream" }
        SuiteLog.i(
            context,
            SuiteContract.HOST_MODULE_ID,
            "root stream begin; plugin=$safePlugin operation=$safeOperation",
        )
        return ProcessBuilder("su", "-c", command)
            .redirectErrorStream(true)
            .start()
    }

    @JvmStatic
    fun execute(
        context: Context,
        pluginId: String,
        operation: String,
        command: String,
        timeoutSeconds: Long = 15L,
    ): Result {
        val app = context.applicationContext
        RootManager.initialize(app)
        val safePlugin = pluginId.ifBlank { "unknown" }
        val safeOperation = operation.ifBlank { "command" }

        SuiteLog.i(
            app,
            SuiteContract.HOST_MODULE_ID,
            "root begin; plugin=$safePlugin operation=$safeOperation",
        )

        val result = try {
            val future = Shell.cmd(command).enqueue()
            val shellResult = future.get(timeoutSeconds.coerceAtLeast(1L), TimeUnit.SECONDS)
            Result(
                code = shellResult.code,
                stdout = shellResult.out.joinToString("\n"),
                stderr = shellResult.err.joinToString("\n"),
            )
        } catch (timeout: TimeoutException) {
            Result(
                code = Shell.Result.JOB_NOT_EXECUTED,
                stdout = "",
                stderr = "",
                timedOut = true,
                error = timeout,
            )
        } catch (error: Throwable) {
            Result(
                code = Shell.Result.JOB_NOT_EXECUTED,
                stdout = "",
                stderr = "",
                error = error,
            )
        }

        val detail = if (result.success) {
            "success"
        } else {
            result.failureMessage().replace('\n', ' ').take(240)
        }
        SuiteLog.i(
            app,
            SuiteContract.HOST_MODULE_ID,
            "root end; plugin=$safePlugin operation=$safeOperation result=$detail",
        )
        return result
    }

    private fun executeBinary(
        context: Context,
        pluginId: String,
        operation: String,
        command: String,
        timeoutSeconds: Long,
        maxStdoutBytes: Int,
        mergeError: Boolean,
    ): BinaryResult {
        val app = context.applicationContext
        RootManager.initialize(app)
        val safePlugin = pluginId.ifBlank { "unknown" }
        val safeOperation = operation.ifBlank { "binary" }
        val stdoutLimit = maxStdoutBytes.coerceAtLeast(1024)
        val stderrLimit = 16 * 1024

        SuiteLog.i(
            app,
            SuiteContract.HOST_MODULE_ID,
            "root binary begin; plugin=$safePlugin operation=$safeOperation maxBytes=$stdoutLimit",
        )

        var process: Process? = null
        var stdoutReader: Thread? = null
        var stderrReader: Thread? = null
        val stdout = ByteArrayOutputStream(minOf(stdoutLimit, 2 * 1024 * 1024))
        val stderr = ByteArrayOutputStream(stderrLimit)
        val readFailure = AtomicReference<Throwable?>(null)
        val stdoutOverflow = AtomicBoolean(false)
        val stderrOverflow = AtomicBoolean(false)

        val result = try {
            process = ProcessBuilder("su", "-c", command)
                .redirectErrorStream(mergeError)
                .start()
            val active = process

            stdoutReader = readerThread(
                name = "YSuite-root-stdout",
                input = active.inputStream,
                output = stdout,
                maxBytes = stdoutLimit,
                failure = readFailure,
                overflow = stdoutOverflow,
            ).also(Thread::start)

            if (!mergeError) {
                stderrReader = readerThread(
                    name = "YSuite-root-stderr",
                    input = active.errorStream,
                    output = stderr,
                    maxBytes = stderrLimit,
                    failure = readFailure,
                    overflow = stderrOverflow,
                ).also(Thread::start)
            }

            val finished = active.waitFor(timeoutSeconds.coerceAtLeast(1L), TimeUnit.SECONDS)
            if (!finished) {
                active.destroy()
                if (active.isAlive) active.destroyForcibly()
                joinQuietly(stdoutReader, 500L)
                joinQuietly(stderrReader, 500L)
                BinaryResult(
                    code = Shell.Result.JOB_NOT_EXECUTED,
                    stdout = stdout.toByteArray(),
                    stderr = stderr.toString(Charsets.UTF_8.name()),
                    timedOut = true,
                )
            } else {
                joinQuietly(stdoutReader, 1500L)
                joinQuietly(stderrReader, 1500L)
                val streamError = readFailure.get()
                val overflowed = stdoutOverflow.get() || stderrOverflow.get()
                BinaryResult(
                    code = active.exitValue(),
                    stdout = stdout.toByteArray(),
                    stderr = stderr.toString(Charsets.UTF_8.name()),
                    errorMessage = when {
                        streamError != null -> "${streamError.javaClass.simpleName}: ${streamError.message}"
                        overflowed -> "Root command output exceeds limit"
                        else -> null
                    },
                )
            }
        } catch (error: Throwable) {
            BinaryResult(
                code = Shell.Result.JOB_NOT_EXECUTED,
                stdout = stdout.toByteArray(),
                stderr = stderr.toString(Charsets.UTF_8.name()),
                errorMessage = "${error.javaClass.simpleName}: ${error.message}",
            )
        } finally {
            process?.let { active ->
                runCatching {
                    if (active.isAlive) active.destroyForcibly() else active.destroy()
                }
            }
            stdoutReader?.takeIf(Thread::isAlive)?.interrupt()
            stderrReader?.takeIf(Thread::isAlive)?.interrupt()
        }

        val detail = when {
            result.success -> "success bytes=${result.stdout.size}"
            result.timedOut -> "timeout bytes=${result.stdout.size}"
            else -> (result.errorMessage ?: result.stderr.ifBlank { "exit=${result.code}" })
                .replace('\n', ' ')
                .take(240)
        }
        SuiteLog.i(
            app,
            SuiteContract.HOST_MODULE_ID,
            "root binary end; plugin=$safePlugin operation=$safeOperation result=$detail",
        )
        return result
    }

    private fun readerThread(
        name: String,
        input: InputStream,
        output: ByteArrayOutputStream,
        maxBytes: Int,
        failure: AtomicReference<Throwable?>,
        overflow: AtomicBoolean,
    ): Thread = Thread({
        try {
            input.use { stream ->
                val buffer = ByteArray(64 * 1024)
                var stored = 0
                while (true) {
                    val count = stream.read(buffer)
                    if (count < 0) break
                    if (count == 0) continue
                    val remaining = (maxBytes - stored).coerceAtLeast(0)
                    val keep = minOf(remaining, count)
                    if (keep > 0) {
                        output.write(buffer, 0, keep)
                        stored += keep
                    }
                    if (keep < count) overflow.set(true)
                    // Keep draining even after the limit so the child cannot block on a full pipe.
                }
            }
        } catch (error: Throwable) {
            failure.compareAndSet(null, error)
        }
    }, name).apply { isDaemon = true }

    private fun joinQuietly(thread: Thread?, millis: Long) {
        if (thread == null) return
        try {
            thread.join(millis)
            if (thread.isAlive) thread.interrupt()
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }
}
