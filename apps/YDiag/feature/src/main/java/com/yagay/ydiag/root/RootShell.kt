package com.yagay.ydiag.root

import com.yagay.suite.api.FeatureServices
import com.yagay.suite.api.FeatureRootCommands

data class ShellResult(val code: Int, val stdout: String, val stderr: String)

object RootShell {
    private val services = FeatureServices.of("ydiag", "YDiag")

    fun isAvailable(): Boolean = runCatching {
        val result = exec("id", 4)
        result.code == 0 && result.stdout.contains("uid=0")
    }.getOrDefault(false)

    fun exec(command: String, timeoutSeconds: Long = 15): ShellResult {
        val result = FeatureRootCommands.execute(services, "root-shell", command, timeoutSeconds)
        return ShellResult(result.code, result.stdout, FeatureRootCommands.errorText(result))
    }

    fun start(command: String): Process =
        services.rootStart("root-stream", command)
            ?: throw IllegalStateException("Managed host does not provide Root streaming")
}
