package com.yagay.YNFC.system

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.Toast
import com.yagay.suite.api.FeatureServices
import java.util.concurrent.TimeUnit

class RootShell(context: Context) {
    companion object {
        private const val ROOT_OK_CACHE_MS = 60_000L
        private const val ROOT_FAILURE_CACHE_MS = 5_000L
        private val services = FeatureServices.of("ynfc", "YNFC")
    }

    private val appContext = context.applicationContext
    @Volatile private var rootAvailableCache: Boolean? = null
    @Volatile private var rootCacheCheckedAt: Long = 0L
    @Volatile private var lastRootToastAt: Long = 0L

    fun run(command: String, timeoutSeconds: Long = 20, maxChars: Int = 1_000_000, showToast: Boolean = true): String {
        if (services.hostOrNull() != null) {
            val result = hostRun(command, timeoutSeconds)
            if (!result.success && showToast && result.code == -1) notifyRootUnavailable()
            val output = buildString {
                append(result.stdout.take(maxChars))
                if (result.stderr.isNotBlank() && length < maxChars) {
                    if (isNotEmpty() && last() != '\n') append('\n')
                    append(result.stderr.take(maxChars - length))
                }
                if (length < maxChars) {
                    if (isNotEmpty() && last() != '\n') append('\n')
                    appendLine(if (result.timedOut) "[timeout=${timeoutSeconds}s]" else "[exit=${result.code}]")
                }
            }
            return output
        }

        if (!ensureRootAccess(showToast)) return "ROOT_UNAVAILABLE"
        return try {
            val process = ProcessBuilder("su", "-c", command).redirectErrorStream(true).start()
            val output = StringBuffer()
            val reader = Thread({
                runCatching {
                    process.inputStream.bufferedReader().useLines { lines ->
                        lines.forEach { line ->
                            if (output.length < maxChars) {
                                val remaining = maxChars - output.length
                                val piece = if (line.length + 1 <= remaining) line + "\n" else line.take(remaining)
                                output.append(piece)
                            }
                        }
                    }
                }
            }, "YNFC-RootReader").apply { isDaemon = true; start() }

            val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                process.waitFor(2, TimeUnit.SECONDS)
            }
            reader.join(1500)
            if (!finished) output.appendLine("[timeout=${timeoutSeconds}s]")
            else output.appendLine("[exit=${process.exitValue()}]")
            output.toString()
        } catch (t: Throwable) {
            invalidateRootCache()
            if (showToast) notifyRootUnavailable()
            "ERROR ${t.javaClass.simpleName}: ${t.message}"
        }
    }

    fun invalidateRootCache() {
        rootAvailableCache = null
        rootCacheCheckedAt = 0L
    }

    @Synchronized
    private fun ensureRootAccess(showToast: Boolean): Boolean {
        val now = SystemClock.elapsedRealtime()
        rootAvailableCache?.let { cached ->
            val ttl = if (cached) ROOT_OK_CACHE_MS else ROOT_FAILURE_CACHE_MS
            if (now - rootCacheCheckedAt in 0 until ttl) {
                if (!cached && showToast) notifyRootUnavailable()
                return cached
            }
        }

        val ok = if (services.hostOrNull() != null) {
            val result = hostRun("id -u", 4)
            result.success && result.stdout.lineSequence().any { it.trim() == "0" }
        } else {
            try {
                val process = ProcessBuilder("su", "-c", "id -u").redirectErrorStream(true).start()
                val finished = process.waitFor(4, TimeUnit.SECONDS)
                val output = if (finished) process.inputStream.bufferedReader().readText().trim() else ""
                if (!finished) process.destroyForcibly()
                finished && process.exitValue() == 0 && output.lineSequence().any { it.trim() == "0" }
            } catch (_: Throwable) {
                false
            }
        }
        rootAvailableCache = ok
        rootCacheCheckedAt = now
        if (!ok && showToast) notifyRootUnavailable()
        return ok
    }

    private fun hostRun(command: String, timeoutSeconds: Long): HostRootResult =
        runCatching {
            val raw = services.requireHost("Managed Root host is not attached")
                .rootExecute("root-shell", command, timeoutSeconds)
            HostRootResult(
                code = raw.code,
                stdout = raw.stdout,
                stderr = raw.stderr.ifBlank { raw.errorMessage.orEmpty() },
                timedOut = raw.timedOut,
                success = raw.success,
            )
        }.getOrElse { error ->
            HostRootResult(
                code = -1,
                stdout = "",
                stderr = "Managed root error: ${error.javaClass.simpleName}: ${error.message}",
                timedOut = false,
                success = false,
            )
        }

    private data class HostRootResult(
        val code: Int,
        val stdout: String,
        val stderr: String,
        val timedOut: Boolean,
        val success: Boolean,
    )

    private fun notifyRootUnavailable() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastRootToastAt < 3000L) return
        lastRootToastAt = now
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(appContext, com.yagay.suite.api.YLocale.text(com.yagay.YNFC.R.string.ynfc_generated_d592225d23cd), Toast.LENGTH_LONG).show()
        }
    }
}
