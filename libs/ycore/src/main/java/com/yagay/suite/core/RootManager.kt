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
        // libsu may cache a non-root fallback shell while the manager's authorization dialog
        // is pending. Closing it and checking again is necessary after KernelSU grants access.
        // A positive result only means the *current* host shell is actually privileged.
        repeat(2) {
            val granted = runCatching {
                val shell = Shell.getShell()
                if (shell.isRoot) true
                else {
                    runCatching { shell.close() }
                    false
                }
            }.getOrDefault(false)
            if (granted) return true
        }
        return false
    }
}
