package com.yagay.YTaskManager.root

import com.yagay.YTaskManager.AppLogger
import com.yagay.suite.api.FeatureServices
import com.yagay.suite.api.FeatureRootCommands
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
data class RootProbe(val granted: Boolean, val detail: String)

class RootShell {
    companion object {
        private val services = FeatureServices.of("ytaskmanager", "YTaskManager")
    }

    private val mutex = Mutex()

    @Volatile
    private var closed = false

    suspend fun probeRoot(): RootProbe {
        val host = services.hostOrNull()
            ?: return RootProbe(false, "YSuite feature Root host not attached; retrying connection")
        if (!host.supports(HostCapability.ROOT)) {
            return RootProbe(false, "TaskManager ROOT capability is not registered by its host")
        }
        return try {
            val result = execute("id -u", 8_000)
            val uid = result.stdout.lineSequence().map { it.trim() }
                .firstOrNull { it.matches(Regex("[0-9]+")) }
            val granted = result.code == 0 && uid == "0"
            val detail = when {
                granted -> "Root active (uid=0 via ${host.hostPackageName})"
                result.code != 0 -> "Root command failed (exit=${result.code}): ${result.stderr.ifBlank { result.stdout }.take(180)}"
                uid == null -> "Root command returned no valid UID via ${host.hostPackageName}"
                else -> "Root command ran with uid=$uid, not uid=0 (${host.hostPackageName})"
            }
            AppLogger.i("Root probe: $detail")
            RootProbe(granted, detail)
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (error: Throwable) {
            AppLogger.e("Root probe could not use the feature host", error)
            RootProbe(false, error.message ?: error.javaClass.simpleName)
        }
    }

    suspend fun isRootAvailable(): Boolean = probeRoot().granted

    suspend fun execute(command: String, timeoutMs: Long = 8_000): ShellResult =
        mutex.withLock {
            check(!closed) { "Root shell already closed" }
            withContext(Dispatchers.IO) {
                executeThroughHost(command, timeoutMs)
            }
        }

    private fun executeThroughHost(command: String, timeoutMs: Long): ShellResult =
        try {
            val timeoutSeconds = ((timeoutMs.coerceAtLeast(1L) + 999L) / 1000L).coerceAtLeast(1L)
            val raw = FeatureRootCommands.execute(services, "root-shell", command, timeoutSeconds)
            if (raw.timedOut) throw TimeoutException("Managed root command timed out after ${timeoutMs}ms")
            if (!raw.errorMessage.isNullOrBlank()) throw IllegalStateException(raw.errorMessage)
            if (raw.stderr.isNotBlank()) AppLogger.i("Managed root stderr: ${raw.stderr.take(240)}")
            ShellResult(raw.code, raw.stdout.trim(), raw.stderr.trim())
        } catch (e: CancellationException) {
            throw e
        } catch (e: TimeoutException) {
            throw e
        } catch (t: Throwable) {
            throw IllegalStateException("Managed root execution failed", t)
        }

    fun close() {
        closed = true
    }
}
