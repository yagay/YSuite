package com.yagay.YTaskManager.root

import com.yagay.YTaskManager.AppLogger
import com.yagay.suite.api.FeatureServices
import com.yagay.suite.api.FeatureRootCommands
import com.yagay.suite.api.HostRootProbe
import com.yagay.suite.api.HostCapability
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeoutException

data class ShellResult(
    val code: Int,
    val stdout: String,
    val stderr: String = "",
)

/** Report the actual failure layer instead of equating a missing host with denied Root. */
data class RootProbe(
    val granted: Boolean,
    val detail: String,
    /** Transport/host failure does not mean Root permission was denied. */
    val transient: Boolean = false,
)

class RootShell {
    companion object {
        private val services = FeatureServices.of("ytaskmanager", "YTaskManager")
    }

    private val mutex = Mutex()

    @Volatile
    private var closed = false

    suspend fun probeRoot(): RootProbe {
        // A shared short-lived Root probe is not queued behind this module's
        // long-running process scans and preserves temporary failure reasons.
        return withContext(Dispatchers.IO) {
            val result = HostRootProbe.probe(services, 8L)
            val hostPackage = services.hostOrNull()?.hostPackageName ?: "unattached"
            val detail = "${result.status}: ${result.detail} (host=$hostPackage)"
            AppLogger.i("Root probe: $detail")
            RootProbe(result.granted, detail, transient = result.transient)
        }
    }

    suspend fun isRootAvailable(): Boolean = probeRoot().granted

    suspend fun execute(
        command: String,
        timeoutMs: Long = 8_000,
        operation: String = "root-shell",
    ): ShellResult =
        mutex.withLock {
            check(!closed) { "Root shell already closed" }
            withContext(Dispatchers.IO) {
                executeThroughHost(command, timeoutMs, operation)
            }
        }

    private fun executeThroughHost(command: String, timeoutMs: Long, operation: String): ShellResult =
        try {
            val timeoutSeconds = ((timeoutMs.coerceAtLeast(1L) + 999L) / 1000L).coerceAtLeast(1L)
            val raw = FeatureRootCommands.execute(services, operation, command, timeoutSeconds)
            if (raw.timedOut) throw TimeoutException("Managed root command timed out after ${timeoutMs}ms")
            if (!raw.errorMessage.isNullOrBlank()) throw IllegalStateException(raw.errorMessage)
            if (raw.stderr.isNotBlank()) AppLogger.i("Managed root stderr: ${raw.stderr.take(240)}")
            ShellResult(raw.code, raw.stdout.trim(), raw.stderr.trim())
        } catch (e: CancellationException) {
            throw e
        } catch (e: TimeoutException) {
            throw e
        } catch (t: Throwable) {
            throw IllegalStateException(
                "Managed Root execution failed: ${t.message ?: t.javaClass.simpleName}", t
            )
        }

    fun close() {
        closed = true
    }
}
