package com.yagay.suite.core

import android.content.Context
import com.topjohnwu.superuser.Shell

object RootManager {
    data class Result(val code: Int, val out: List<String>, val err: List<String>)

    @Volatile private var configured = false

    @Synchronized
    fun configure(context: Context) {
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

    fun exec(context: Context, module: String, command: String): Result {
        configure(context)
        SuiteLog.i(context, module, "ROOT start: $command")
        return runCatching {
            val result = Shell.cmd(command).exec()
            Result(result.code, result.out, result.err).also {
                SuiteLog.i(context, module, "ROOT end: code=${it.code}")
            }
        }.getOrElse { error ->
            SuiteLog.e(context, module, "ROOT failed: $command", error)
            Result(-1, emptyList(), listOf(error.toString()))
        }
    }
}
