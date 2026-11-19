package com.yagay.ydiag.root

import com.yagay.suite.api.FeatureServices
import com.yagay.suite.api.FeatureRootCommands
import com.yagay.suite.api.HostRootProbe

data class ShellResult(val code: Int, val stdout: String, val stderr: String)

object RootShell {
    private val services = FeatureServices.of("ydiag", "YDiag")

    fun isAvailable(): Boolean = HostRootProbe.probe(services, 4L).granted

    fun exec(command: String, timeoutSeconds: Long = 15): ShellResult {
        val result = FeatureRootCommands.execute(services, "root-shell", command, timeoutSeconds)
        return ShellResult(result.code, result.stdout, FeatureRootCommands.errorText(result))
    }

    fun start(command: String): Process =
        services.rootStart("root-stream", command)
            ?: throw IllegalStateException("Managed host does not provide Root streaming")
}
