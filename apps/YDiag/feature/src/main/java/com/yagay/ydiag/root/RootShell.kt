package com.yagay.ydiag.root

import java.lang.reflect.Method
import java.util.concurrent.TimeUnit

data class ShellResult(val code: Int, val stdout: String, val stderr: String)

object RootShell {
    private const val MAX_CAPTURE_CHARS = 24 * 1024 * 1024
    private const val SUITE_ROOT_GATEWAY = "com.yagay.suite.core.SuiteRootGateway"
    private const val PLUGIN_ID = "ydiag"

    fun isAvailable(): Boolean = runCatching {
        val result = exec("id", 4)
        result.code == 0 && result.stdout.contains("uid=0")
    }.getOrDefault(false)

    fun exec(command: String, timeoutSeconds: Long = 15): ShellResult {
        val host = hostGateway()
        if (host != null) return execThroughHost(host, command, timeoutSeconds)

        val process = ProcessBuilder("su", "-c", command)
            .redirectErrorStream(true)
            .start()
        val output = StringBuilder()
        val readerThread = Thread({
            runCatching {
                process.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        if (output.length < MAX_CAPTURE_CHARS) {
                            output.appendLine(line)
                        }
                    }
                }
            }
        }, "YDiag-root-reader").apply {
            isDaemon = true
            start()
        }

        val completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
        if (!completed) process.destroyForcibly()
        readerThread.join(1500)
        return ShellResult(
            code = if (completed) process.exitValue() else -1,
            stdout = output.toString(),
            stderr = if (completed) "" else "timeout",
        )
    }

    fun start(command: String): Process {
        val host = hostGateway()
        if (host != null) {
            return try {
                val method: Method = host.getMethod(
                    "startFromPlugin",
                    String::class.java,
                    String::class.java,
                    String::class.java,
                )
                method.invoke(null, PLUGIN_ID, "root-stream", command) as Process
            } catch (error: Throwable) {
                // Host exists: never bypass YSuite with a second local su process.
                throw IllegalStateException("YSuite root stream gateway failed", error)
            }
        }
        return ProcessBuilder("su", "-c", command).redirectErrorStream(true).start()
    }

    private fun hostGateway(): Class<*>? = runCatching {
        Class.forName(SUITE_ROOT_GATEWAY, false, RootShell::class.java.classLoader)
    }.getOrNull()

    private fun execThroughHost(host: Class<*>, command: String, timeoutSeconds: Long): ShellResult =
        try {
            val method = host.getMethod(
                "executeFromPlugin",
                String::class.java,
                String::class.java,
                String::class.java,
                java.lang.Long.TYPE,
            )
            val raw = method.invoke(null, PLUGIN_ID, "root-shell", command, timeoutSeconds)
                ?: error("YSuite root gateway returned null")
            val type = raw.javaClass
            ShellResult(
                code = (type.getMethod("getCode").invoke(raw) as Number).toInt(),
                stdout = type.getMethod("getStdout").invoke(raw) as? String ?: "",
                stderr = type.getMethod("getStderr").invoke(raw) as? String ?: "",
            )
        } catch (error: Throwable) {
            ShellResult(
                code = -1,
                stdout = "",
                stderr = "YSuite root gateway error: ${error.javaClass.simpleName}: ${error.message}",
            )
        }
}
