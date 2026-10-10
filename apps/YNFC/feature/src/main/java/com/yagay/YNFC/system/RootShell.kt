package com.yagay.YNFC.system

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.Toast
import com.yagay.suite.api.FeatureServices

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
        val result = hostRun(command, timeoutSeconds)
        if (!result.success && showToast && result.code == -1) notifyRootUnavailable()
        return buildString {
            append(result.stdout.take(maxChars))
            if (FeatureRootCommands.errorText(result).isNotBlank() && length < maxChars) {
                if (isNotEmpty() && last() != '\n') append('\n')
                append(result.stderr.take(maxChars - length))
            }
            if (length < maxChars) {
                if (isNotEmpty() && last() != '\n') append('\n')
                appendLine(if (result.timedOut) "[timeout=${timeoutSeconds}s]" else "[exit=${result.code}]")
            }
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

        val result = hostRun("id -u", 4)
        val ok = result.success && result.stdout.lineSequence().any { it.trim() == "0" }
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
