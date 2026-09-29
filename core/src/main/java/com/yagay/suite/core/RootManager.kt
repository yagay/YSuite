package com.yagay.suite.core

import android.content.Context
import com.topjohnwu.superuser.Shell

/** Process-wide libsu owner for the combined YSuite host. */
object RootManager {
    @Volatile private var configured = false
    @Volatile private var hostContext: Context? = null

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
        configured = true
    }

    /** Configure libsu without requesting root. */
    fun initialize(context: Context) {
        hostContext = context.applicationContext
        if (!configured) synchronized(this) {
            if (!configured) applyDefaults(context)
        }
    }

    /**
     * Re-apply the host defaults after enabling a standalone feature runtime that may configure
     * libsu's process-global builder for its own APK.
     */
    fun reclaim(context: Context) {
        synchronized(this) { applyDefaults(context) }
    }

    /** Context retained only by the YSuite host so embedded plugins can use the host gateway. */
    internal fun contextOrNull(): Context? = hostContext

    fun isAvailable(context: Context): Boolean {
        initialize(context)
        return runCatching {
            val shell = Shell.getShell()
            val granted = shell.isRoot
            // libsu caches the process-wide main shell. If the first request was denied, keeping
            // that NON_ROOT shell would make later checks stale even after the user grants YSuite
            // in KernelSU. Close non-root shells so the next resume creates a fresh su session.
            if (!granted) runCatching { shell.close() }
            granted
        }.getOrDefault(false)
    }
}
