package com.yagay.YTaskManager.root

import com.yagay.YTaskManager.AppLogger
import com.yagay.suite.api.FeatureServices
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.UUID
import java.util.concurrent.TimeoutException

data class ShellResult(
    val code: Int,
    val stdout: String,
)

class RootShell {
    companion object {
        private val services = FeatureServices.of("ytaskmanager", "YTaskManager")
    }

    private val mutex = Mutex()

    @Volatile
    private var process: Process? = null

    @Volatile
    private var writer: BufferedWriter? = null

    @Volatile
    private var reader: BufferedReader? = null

    @Volatile
    private var closed = false

    suspend fun isRootAvailable(): Boolean {
        val result = runCatching {
            execute("id -u", 4_000).stdout.trim().lineSequence().lastOrNull() == "0"
        }
        result.onSuccess { AppLogger.i("Root availability checked: granted=$it") }
            .onFailure {
                if (it !is CancellationException) AppLogger.e("Root availability check failed", it)
            }
        val granted = result.getOrDefault(false)
        // A denied/non-root standalone su process must never be kept as the cached shell. KernelSU
        // grants can change while the app is open. In managed-host mode there is no feature shell.
        if (!granted) mutex.withLock { reset() }
        return granted
    }

    suspend fun execute(command: String, timeoutMs: Long = 8_000): ShellResult =
        mutex.withLock {
            withContext(Dispatchers.IO) {
                if (services.hostOrNull() != null) {
                    check(!closed) { "Root shell already closed" }
                    return@withContext executeThroughHost(command, timeoutMs)
                }

                try {
                    ensureShell()
                    val marker = "__TM_${UUID.randomUUID().toString().replace("-", "")}__"
                    val w = requireNotNull(writer)
                    val r = requireNotNull(reader)

                    w.write(command)
                    w.newLine()
                    w.write("printf '\\n$marker:%s\\n' \"$?\"")
                    w.newLine()
                    w.flush()

                    val lines = mutableListOf<String>()
                    var exitCode = -1

                    withTimeout(timeoutMs) {
                        while (true) {
                            val line = runInterruptible { r.readLine() }
                                ?: throw IllegalStateException("Root shell closed")
                            if (line.startsWith("$marker:")) {
                                exitCode = line.substringAfter(':').trim().toIntOrNull() ?: -1
                                break
                            }
                            lines += line
                        }
                    }

                    ShellResult(exitCode, lines.joinToString("\n").trim())
                } catch (e: TimeoutCancellationException) {
                    if (closed || !currentCoroutineContext().isActive) {
                        reset()
                        throw CancellationException("Root shell closed by lifecycle", e)
                    }
                    AppLogger.e("Root shell command timed out after ${timeoutMs}ms", e)
                    reset()
                    throw e
                } catch (e: CancellationException) {
                    reset()
                    throw e
                } catch (t: Throwable) {
                    if (closed || !currentCoroutineContext().isActive) {
                        reset()
                        throw CancellationException("Root shell closed by lifecycle", t)
                    }
                    AppLogger.e("Root shell command failed: ${t.javaClass.simpleName}", t)
                    reset()
                    throw t
                }
            }
        }

    private fun executeThroughHost(command: String, timeoutMs: Long): ShellResult =
        try {
            val timeoutSeconds = ((timeoutMs.coerceAtLeast(1L) + 999L) / 1000L).coerceAtLeast(1L)
            val host = services.requireHost("Managed Root host is not attached")
            val raw = host.rootExecute("root-shell", command, timeoutSeconds)
            if (raw.timedOut) throw TimeoutException("Managed root command timed out after ${timeoutMs}ms")
            if (!raw.errorMessage.isNullOrBlank()) throw IllegalStateException(raw.errorMessage)
            if (raw.stderr.isNotBlank()) AppLogger.i("Managed root stderr: ${raw.stderr.take(240)}")
            ShellResult(raw.code, raw.stdout.trim())
        } catch (e: TimeoutException) {
            throw e
        } catch (t: Throwable) {
            // A managed host exists: never bypass it by opening a second local su process.
            throw IllegalStateException("Managed root execution failed", t)
        }

    private fun ensureShell() {
        check(!closed) { "Root shell already closed" }
        val p = process
        if (p != null && p.isAlive && writer != null && reader != null) return

        reset()
        val newProcess = ProcessBuilder("su")
            .redirectErrorStream(true)
            .start()

        process = newProcess
        writer = BufferedWriter(OutputStreamWriter(newProcess.outputStream))
        reader = BufferedReader(InputStreamReader(newProcess.inputStream))
    }

    fun close() {
        closed = true
        reset()
    }

    private fun reset() {
        runCatching { writer?.write("exit\n") }
        runCatching { writer?.flush() }
        runCatching { process?.destroy() }
        writer = null
        reader = null
        process = null
    }
}
