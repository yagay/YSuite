package com.yagay.suite.core

import android.content.Context
import android.os.SystemClock
import com.topjohnwu.superuser.Shell

/**
 * Sole owner of the process-wide libsu configuration and authorization snapshot.
 * A command failure must never overwrite this authorization snapshot: root access
 * and an individual command's success are separate dimensions.
 */
object RootManager {
    private const val ROOT_VERIFIED_TTL_MS = 10_000L
    private const val ROOT_RETRY_TTL_MS = 1_500L

    @Volatile private var configured = false
    @Volatile private var hostContext: Context? = null
    @Volatile private var lastCheckedAtMs: Long = 0L
    @Volatile private var lastCheckWasRoot: Boolean = false

    @Synchronized
    private fun applyDefaults(context: Context) {
        val app = context.applicationContext
        hostContext = app
        Shell.enableVerboseLogging = false
        Shell.setDefaultBuilder(
            Shell.Builder.create()
                .setContext(app)
                .setFlags(Shell.FLAG_MOUNT_MASTER)
                .setTimeout(15)
        )
        lastCheckedAtMs = 0L
        configured = true
    }

    /** Configure libsu without opening a shell or requesting root. */
    fun initialize(context: Context) {
        hostContext = context.applicationContext
        if (!configured) synchronized(this) {
            if (!configured) applyDefaults(context)
        }
    }

    /** Restore host-owned libsu settings after a feature runtime is attached. */
    fun reclaim(context: Context) {
        synchronized(this) { applyDefaults(context) }
    }

    internal fun contextOrNull(): Context? = hostContext

    /** Retry on the next check after a known shell/permission change. */
    @Synchronized
    fun invalidate() {
        lastCheckedAtMs = 0L
    }

    /**
     * Single-flight, short-lived authorization evidence for the current libsu shell.
     * Failed probes are retried quickly, but never turn an unrelated command timeout
     * into evidence that KernelSU denied permission.
     */
    @Synchronized
    fun isAvailable(context: Context): Boolean {
        initialize(context)
        val now = SystemClock.elapsedRealtime()
        val ttl = if (lastCheckWasRoot) ROOT_VERIFIED_TTL_MS else ROOT_RETRY_TTL_MS
        if (lastCheckedAtMs > 0L && now - lastCheckedAtMs in 0 until ttl) {
            return lastCheckWasRoot
        }

        var granted = false
        repeat(2) {
            val current = runCatching {
                val shell = Shell.getShell()
                if (shell.isRoot && shell.isAlive) {
                    true
                } else {
                    runCatching { shell.close() }
                    false
                }
            }.getOrDefault(false)
            if (current) {
                granted = true
                return@repeat
            }
        }
        lastCheckWasRoot = granted
        lastCheckedAtMs = SystemClock.elapsedRealtime()
        return granted
    }
}
