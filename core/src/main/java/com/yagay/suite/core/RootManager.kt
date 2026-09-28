package com.yagay.suite.core

import android.content.Context
import com.topjohnwu.superuser.Shell

/** Process-wide libsu owner for the combined YSuite host. */
object RootManager {
    @Volatile private var configured = false

    @Synchronized
    private fun applyDefaults(context: Context) {
        Shell.enableVerboseLogging = false
        Shell.setDefaultBuilder(
            Shell.Builder.create()
                .setContext(context.applicationContext)
                .setFlags(Shell.FLAG_MOUNT_MASTER)
                .setTimeout(15)
        )
        configured = true
    }

    /** Configure libsu without requesting root. */
    fun initialize(context: Context) {
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

    fun isAvailable(context: Context): Boolean {
        initialize(context)
        return runCatching { Shell.getShell().isRoot }.getOrDefault(false)
    }
}
