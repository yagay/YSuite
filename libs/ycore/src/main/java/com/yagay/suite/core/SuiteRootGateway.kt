package com.yagay.suite.core

import android.content.Context
import com.topjohnwu.superuser.Shell
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
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
    private val streamingProcesses = ConcurrentHashMap<String, MutableSet<Process>>()

    data class Result(
        val code: Int,
        val stdout: String,
        val stderr: String,
        val timedOut: Boolean = false,
        val error: Throwable? = null,
    ) {
        val success: Boolean get() = !timedOut && error == null && code == 0

        fun failureMessage(defaultMessage: String = com.yagay.suite.api.YLocale.text(com.yagay.suite.core.R.string.ycore_dynamic_d74afaa590e9)): String = when {
            timedOut -> com.yagay.suite.api.YLocale.text(com.yagay.suite.core.R.string.ycore_dynamic_dfca1061baa0)
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
     * Reflection-friendly streaming entry for long-lived collectors. YSuite registers every stream
     * under its feature id so disabling that feature can terminate already-running root collectors.
     */
    @JvmStatic
    fun startFromPlugin(
        pluginId: String,
        operation: String,
        command: String,
    ): Process {
        val context = RootManager.contextOrNull()
            ?: throw IllegalStateException("YSuite root host is not initialized")
        checkFeatureEnabled(context, pluginId)
        val safePlugin = pluginId.ifBlank { "unknown" }
        val safeOperation = operation.ifBlank { "stream" }
        SuiteLog.i(
            context,
            SuiteContract.HOST_MODULE_ID,
            "root stream begin; plugin=$safePlugin operation=$safeOperation",
        )
        val process = ProcessBuilder("su", "-c", command)
            .redirectErrorStream(true)
            .start()
        registerStreamingProcess(context, pluginId, safeOperation, process)
        return process
    }

    /** Terminates all long-lived root streams that belong to one feature. */
    @JvmStatic
    fun stopPluginProcesses(pluginId: String): Int {
        val featureId = normalizedFeatureId(pluginId) ?: return 0
        val processes = streamingProcesses.remove(featureId)?.toList().orEmpty()
        processes.forEach { process ->
            runCatching {
                if (process.isAlive) {
                    process.destroy()
                    if (process.isAlive) process.destroyForcibly()
                }
            }
        }
        RootManager.contextOrNull()?.let { context ->
            if (processes.isNotEmpty()) {
                SuiteLog.i(
                    context,
                    SuiteContract.HOST_MODULE_ID,
                    "root streams stopped; feature=$featureId count=${processes.size}",
                )
            }
        }
        return processes.size
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
        val disabled = disabledFeatureError(app, pluginId)
        if (disabled != null) {
            SuiteLog.i(
                app,
                SuiteContract.HOST_MODULE_ID,
                "root blocked; plugin=${pluginId.ifBlank { "unknown" }} reason=${disabled.message}",
            )
            return Result(
                code = Shell.Result.JOB_NOT_EXECUTED,
                stdout = "",
                stderr = disabled.message.orEmpty(),
                error = disabled,
            )
        }

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
        val disabled = disabledFeatureError(app, pluginId)
        if (disabled != null) {
            SuiteLog.i(
                app,
                SuiteContract.HOST_MODULE_ID,
                "root binary blocked; plugin=${pluginId.ifBlank { "unknown" }} reason=${disabled.message}",
            )
            return BinaryResult(
                code = Shell.Result.JOB_NOT_EXECUTED,
                stdout = ByteArray(0),
                stderr = disabled.message.orEmpty(),
                errorMessage = disabled.message,
            )
        }

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

    private fun registerStreamingProcess(
        context: Context,
        pluginId: String,
        operation: String,
        process: Process,
    ) {
        val featureId = normalizedFeatureId(pluginId) ?: return
        val processes = streamingProcesses.computeIfAbsent(featureId) {
            ConcurrentHashMap.newKeySet<Process>()
        }
        processes.add(process)
        Thread({
            runCatching { process.waitFor() }
            processes.remove(process)
            if (processes.isEmpty()) streamingProcesses.remove(featureId, processes)
            SuiteLog.i(
                context,
                SuiteContract.HOST_MODULE_ID,
                "root stream end; feature=$featureId operation=$operation",
            )
        }, "YSuite-root-watch-$featureId").apply {
            isDaemon = true
            start()
        }
    }

    private fun checkFeatureEnabled(context: Context, pluginId: String) {
        disabledFeatureError(context.applicationContext, pluginId)?.let { throw it }
    }

    private fun normalizedFeatureId(pluginId: String): String? {
        val featureId = pluginId.trim().substringBefore('/')
        if (featureId.isEmpty() || featureId == SuiteContract.HOST_MODULE_ID || featureId == SuiteContract.CRASH_MODULE_ID) {
            return null
        }
        return featureId
    }

    private fun disabledFeatureError(context: Context, pluginId: String): SecurityException? {
        val featureId = normalizedFeatureId(pluginId) ?: return null
        val feature = FeatureRegistry.all.firstOrNull { it.id == featureId } ?: return null
        return if (FeatureStateStore(context).isEnabled(feature)) null
        else SecurityException("YSuite feature is disabled: $featureId")
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
