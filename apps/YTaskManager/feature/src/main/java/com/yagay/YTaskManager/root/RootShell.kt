package com.yagay.YTaskManager.root

import com.yagay.YTaskManager.AppLogger
import com.yagay.suite.api.FeatureServices
import com.yagay.suite.api.FeatureRootCommands
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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
    private var closed = false

    suspend fun isRootAvailable(): Boolean {
        val result = runCatching {
            execute("id -u", 4_000).stdout.trim().lineSequence().lastOrNull() == "0"
        }
        result.onSuccess { AppLogger.i("Root availability checked: granted=$it") }
            .onFailure {
                if (it !is CancellationException) AppLogger.e("Root availability check failed", it)
            }
        return result.getOrDefault(false)
    }

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
            ShellResult(raw.code, raw.stdout.trim())
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
