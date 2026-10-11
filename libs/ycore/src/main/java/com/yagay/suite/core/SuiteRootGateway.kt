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
    // Prevent continuous process scans from starving Root health and other features.
    private val scanSlots = java.util.concurrent.Semaphore(2, true)

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
        check(RootManager.isAvailable(context)) { "YSuite Root host is not privileged" }
        val safePlugin = pluginId.ifBlank { "unknown" }
        val safeOperation = operation.ifBlank { "stream" }
        SuiteLog.i(
            context,
            SuiteContract.HOST_MODULE_ID,
            "root stream begin; plugin=$safePlugin operation=$safeOperation",
        )
        val process = ProcessBuilder("su", "-c", requireRootUid(command))
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

        // Never execute a feature command in libsu's unprivileged fallback shell.
        // Re-open a previously cached non-root shell if authorization has since changed.
        if (!RootManager.isAvailable(app)) {
            return Result(
                code = Shell.Result.JOB_NOT_EXECUTED,
                stdout = "",
                stderr = "Root shell is not privileged (host: ${app.packageName})",
                error = IllegalStateException(
                    "Root shell could not open as uid 0 for ${app.packageName}"
                ),
            )
        }
        val safePlugin = pluginId.ifBlank { "unknown" }
        val safeOperation = operation.ifBlank { "command" }

        SuiteLog.i(
            app,
            SuiteContract.HOST_MODULE_ID,
            "root begin; plugin=$safePlugin operation=$safeOperation",
        )

        val result = try {
            val isolated = safePlugin == "ytaskmanager" && safeOperation != "root-capability-probe"
            if (isolated && !scanSlots.tryAcquire()) {
                Result(
                    code = Shell.Result.JOB_NOT_EXECUTED,
                    stdout = "",
                    stderr = "Root process scan busy; a previous scan is still active",
                    error = IllegalStateException("Root scan busy; retry after the previous scan"),
                )
            } else {
                var isolatedShell: Shell? = null
                try {
                    val isolatedOut = mutableListOf<String>()
                    val isolatedErr = mutableListOf<String>()
                    val job = if (isolated) {
                        isolatedShell = RootManager.openIsolatedShell(app)
                            ?: throw IllegalStateException("Isolated libsu shell could not acquire uid 0")
                        // Shell.newJob() does not collect output unless to(...) is set.
                        isolatedShell!!.newJob().add(requireRootUid(command))
                            .to(isolatedOut, isolatedErr)
                    } else {
                        Shell.cmd(requireRootUid(command))
                    }
                    val future = job.enqueue()
                    try {
                        val shellResult = future.get(timeoutSeconds.coerceAtLeast(1L), TimeUnit.SECONDS)
                        Result(
                            code = shellResult.code,
                            stdout = (if (isolated) isolatedOut else shellResult.out).joinToString("\n"),
                            stderr = (if (isolated) isolatedErr else shellResult.err).joinToString("\n"),
                        )
                    } catch (timeout: TimeoutException) {
                        future.cancel(true)
                        Result(
                            code = Shell.Result.JOB_NOT_EXECUTED,
                            stdout = "",
                            stderr = "",
                            timedOut = true,
                            error = timeout,
                        )
                    }
                } finally {
                    if (isolated) {
                        runCatching { isolatedShell?.close() }
                        scanSlots.release()
                    }
                }
            }
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

    /**
     * Binary commands use the same libsu Root shell as normal commands.
     * The app may have uid=0 through libsu while ProcessBuilder("su") is
     * unavailable in its sandbox, especially with KernelSU.
     */
    private fun executeBinary(
        context: Context, pluginId: String, operation: String, command: String,
        timeoutSeconds: Long, maxStdoutBytes: Int, mergeError: Boolean,
    ): BinaryResult {
        val app = context.applicationContext
        val disabled = disabledFeatureError(app, pluginId)
        if (disabled != null) return BinaryResult(
            code = Shell.Result.JOB_NOT_EXECUTED, stdout = ByteArray(0),
            stderr = disabled.message.orEmpty(), errorMessage = disabled.message,
        )
        if (!RootManager.isAvailable(app)) return BinaryResult(
            code = Shell.Result.JOB_NOT_EXECUTED, stdout = ByteArray(0),
            stderr = "YSuite Root host not privileged",
            errorMessage = "Root authorization could not be verified",
        )
        val stdoutLimit = maxStdoutBytes.coerceAtLeast(1024)
        val stderrLimit = 16 * 1024
        var script: java.io.File? = null
        var output: java.io.File? = null
        var errors: java.io.File? = null
        SuiteLog.i(app, SuiteContract.HOST_MODULE_ID,
            "root binary begin; plugin=$pluginId operation=$operation maxBytes=$stdoutLimit")
        val result = try {
            val source = java.io.File.createTempFile("ysuite-command-", ".sh", app.cacheDir)
                .also { script = it }
            val stdout = java.io.File.createTempFile("ysuite-stdout-", ".bin", app.cacheDir)
                .also { output = it }
            val stderr = java.io.File.createTempFile("ysuite-stderr-", ".txt", app.cacheDir)
                .also { errors = it }
            source.writeText(command, Charsets.UTF_8)
            // Run in a child shell: 'exit' within a feature script must not kill libsu.
            val sh = "sh ${quotePath(source.absolutePath)}"
            val bounded = "if command -v timeout >/dev/null 2>&1; " +
                "then timeout ${timeoutSeconds.coerceAtLeast(1L)}s $sh; else $sh; fi"
            val capture = "($bounded) > ${quotePath(stdout.absolutePath)} " +
                if (mergeError) "2>&1" else "2> ${quotePath(stderr.absolutePath)}"
            val completed = Shell.cmd(capture).enqueue()
                .get(timeoutSeconds.coerceAtLeast(1L) + 3L, TimeUnit.SECONDS)
            val outputOverflow = stdout.length() > stdoutLimit
            val errorOverflow = !mergeError && stderr.length() > stderrLimit
            BinaryResult(
                code = completed.code,
                stdout = readAtMost(stdout, stdoutLimit),
                stderr = if (mergeError) "" else
                    String(readAtMost(stderr, stderrLimit), Charsets.UTF_8),
                timedOut = completed.code == 124,
                errorMessage = if (outputOverflow || errorOverflow)
                    "Root command output exceeds limit" else null,
            )
        } catch (_: TimeoutException) {
            BinaryResult(
                code = Shell.Result.JOB_NOT_EXECUTED, stdout = ByteArray(0),
                stderr = "", timedOut = true,
            )
        } catch (error: Throwable) {
            BinaryResult(
                code = Shell.Result.JOB_NOT_EXECUTED, stdout = ByteArray(0),
                stderr = "", errorMessage = "${error.javaClass.simpleName}: ${error.message}",
            )
        } finally {
            runCatching { script?.delete() }
            runCatching { output?.delete() }
            runCatching { errors?.delete() }
        }
        SuiteLog.i(app, SuiteContract.HOST_MODULE_ID,
            "root binary end; plugin=$pluginId operation=$operation " +
                "exit=${result.code} timeout=${result.timedOut} bytes=${result.stdout.size}")
        return result
    }

    private fun quotePath(path: String): String =
        "'" + path.replace("'", "'\\''") + "'"

    private fun readAtMost(file: java.io.File, maxBytes: Int): ByteArray {
        val output = ByteArrayOutputStream(minOf(maxBytes, 2 * 1024 * 1024))
        file.inputStream().use { stream ->
            val buffer = ByteArray(8192)
            var remaining = maxBytes
            while (remaining > 0) {
                val count = stream.read(buffer, 0, minOf(buffer.size, remaining))
                if (count < 0) break
                if (count == 0) continue
                output.write(buffer, 0, count)
                remaining -= count
            }
        }
        return output.toByteArray()
    }

    /** Never run a privileged feature command under a non-root su fallback. */
    private fun requireRootUid(command: String): String =
        "test \"$(id -u)\" = 0 || exit 126; " + command

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
