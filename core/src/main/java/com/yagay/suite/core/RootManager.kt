package com.yagay.suite.core

import android.content.Context
import com.topjohnwu.superuser.Shell

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

    fun isAvailable(context: Context): Boolean {
        configure(context)
        return runCatching { Shell.getShell().isRoot }.getOrDefault(false)
    }
}
