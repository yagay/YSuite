package com.yagay.ydiag.root

import com.yagay.suite.api.FeatureHost
import com.yagay.suite.api.FeatureHostBinding
import java.util.concurrent.TimeUnit

data class ShellResult(val code: Int, val stdout: String, val stderr: String)

object RootShell {
    private const val MAX_CAPTURE_CHARS = 24 * 1024 * 1024
    private val hostBinding = FeatureHostBinding("YDiag")

    fun attachHost(host: FeatureHost) {
        hostBinding.attach(host)
    }

    fun detachHost() {
        hostBinding.clear()
    }

    fun isAvailable(): Boolean = runCatching {
        val result = exec("id", 4)
        result.code == 0 && result.stdout.contains("uid=0")
    }.getOrDefault(false)

    fun exec(command: String, timeoutSeconds: Long = 15): ShellResult {
        val host = hostBinding.hostOrNull()
        if (host != null) {
            return try {
                val result = host.rootExecute("root-shell", command, timeoutSeconds)
                ShellResult(
                    code = result.code,
                    stdout = result.stdout,
                    stderr = result.stderr.ifBlank { result.errorMessage.orEmpty() },
                )
            } catch (error: Throwable) {
                ShellResult(
                    code = -1,
                    stdout = "",
                    stderr = "YSuite root host error: ${error.javaClass.simpleName}: ${error.message}",
                )
            }
        }

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
        val host = hostBinding.hostOrNull()
        if (host != null) {
            return host.rootStart("root-stream", command)
                ?: throw IllegalStateException("YSuite host does not provide Root streaming")
        }
        return ProcessBuilder("su", "-c", command).redirectErrorStream(true).start()
    }
}
