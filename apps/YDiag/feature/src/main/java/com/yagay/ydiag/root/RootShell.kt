package com.yagay.ydiag.root

import com.yagay.suite.api.FeatureServices

data class ShellResult(val code: Int, val stdout: String, val stderr: String)

object RootShell {
    private val services = FeatureServices.of("ydiag", "YDiag")

    fun isAvailable(): Boolean = runCatching {
        val result = exec("id", 4)
        result.code == 0 && result.stdout.contains("uid=0")
    }.getOrDefault(false)

    fun exec(command: String, timeoutSeconds: Long = 15): ShellResult = try {
        val result = services.requireHost("Managed Root host is not attached")
            .rootExecute("root-shell", command, timeoutSeconds)
        ShellResult(
            code = result.code,
            stdout = result.stdout,
            stderr = result.stderr.ifBlank { result.errorMessage.orEmpty() },
        )
    } catch (error: Throwable) {
        ShellResult(
            code = -1,
            stdout = "",
            stderr = "Managed Root error: ${error.javaClass.simpleName}: ${error.message}",
        )
    }

    fun start(command: String): Process =
        services.rootStart("root-stream", command)
            ?: throw IllegalStateException("Managed host does not provide Root streaming")
}
