package com.yagay.suite.core

import android.content.Context
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/** Host-owned target process operations used by Hook reloads and diagnostics. */
object SuiteProcessManager {
    private val activeReloads = ConcurrentHashMap<String, AtomicBoolean>()

    data class ReloadResult(
        val packageName: String,
        val success: Boolean,
        val killedCount: Int,
        val detail: String,
    )

    /** Reflection-friendly plugin entry so embedded features never kill targets directly. */
    @JvmStatic
    fun reloadFromPlugin(
        pluginId: String,
        packageName: String,
        timeoutSeconds: Long,
    ): ReloadResult {
        val context = RootManager.contextOrNull()
            ?: return ReloadResult(
                packageName = packageName,
                success = false,
                killedCount = 0,
                detail = "YSuite process host is not initialized",
            )
        return reloadPackageProcesses(context, pluginId, packageName, timeoutSeconds)
    }

    /**
     * Restarts all processes belonging to one package exactly once at a time.
     * Multiple plugins requesting the same reload are coalesced by package name.
     */
    @JvmStatic
    fun reloadPackageProcesses(
        context: Context,
        pluginId: String,
        packageName: String,
        timeoutSeconds: Long = 12L,
    ): ReloadResult {
        require(isSafePackageName(packageName)) { "Invalid package name: $packageName" }
        val gate = activeReloads.computeIfAbsent(packageName) { AtomicBoolean(false) }
        if (!gate.compareAndSet(false, true)) {
            return ReloadResult(
                packageName = packageName,
                success = true,
                killedCount = 0,
                detail = "reload already in progress; request coalesced",
            )
        }

        return try {
            val escaped = packageName.replace(".", "\\.")
            val command = buildString {
                append("COUNT=0; ")
                append("for PID in $(ps -A -o PID,NAME 2>/dev/null ")
                append("| awk '$2 ~ /^$escaped(:|$)/ {print $1}'); ")
                append("do kill -9 \"\$PID\" >/dev/null 2>&1 || true; ")
                append("COUNT=\$((COUNT+1)); done; ")
                append("sleep 0.12; echo \"YSUITE_KILLED=\$COUNT\"")
            }
            val result = SuiteRootGateway.execute(
                context = context,
                pluginId = pluginId,
                operation = "reload:$packageName",
                command = command,
                timeoutSeconds = timeoutSeconds,
            )
            val count = Regex("YSUITE_KILLED=(\\d+)")
                .find(result.stdout)
                ?.groupValues
                ?.getOrNull(1)
                ?.toIntOrNull()
                ?: 0
            ReloadResult(
                packageName = packageName,
                success = result.success,
                killedCount = count,
                detail = if (result.success) result.stdout else result.failureMessage(),
            )
        } finally {
            gate.set(false)
        }
    }

    private fun isSafePackageName(value: String): Boolean =
        value.length in 3..255 &&
            value.contains('.') &&
            value.all { it.isLetterOrDigit() || it == '.' || it == '_' }
}
