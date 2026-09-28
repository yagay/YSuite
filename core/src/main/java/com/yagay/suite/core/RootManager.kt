package com.yagay.suite.core

import android.content.Context
import com.topjohnwu.superuser.Shell

/** Process-wide libsu owner for the combined YSuite host. */
object RootManager {
    @Volatile private var configured = false

    @Synchronized
    private fun configure(context: Context) {
        if (configured) return
        Shell.enableVerboseLogging = false
        Shell.setDefaultBuilder(
            Shell.Builder.create()
                .setContext(context.applicationContext)
                .setFlags(Shell.FLAG_MOUNT_MASTER)
                .setTimeout(15)
        )
        configured = true
    }

    /**
     * Configure libsu without requesting root. Call after feature initialization so any standalone
     * feature default builder cannot remain the process-wide owner inside YSuite.
     */
    fun initialize(context: Context) {
        configure(context)
    }

    fun isAvailable(context: Context): Boolean {
        configure(context)
        return runCatching { Shell.getShell().isRoot }.getOrDefault(false)
    }
}
