package com.yagay.suite.api

/**
 * The single Root invocation and error normalization boundary for feature code.
 * Execution is always delegated to the active FeatureHost (never launches a second su).
 * Feature-specific facades may retain their existing return types.
 */
object FeatureRootCommands {
    @JvmStatic
    @JvmOverloads
    fun execute(
        services: FeatureServices,
        operation: String,
        command: String,
        timeoutSeconds: Long = 15L,
    ): HostCommandResult {
        require(timeoutSeconds > 0L) { "timeoutSeconds must be positive" }
        return try {
            services.requireHost("Managed Root host is not attached")
                .rootExecute(operation, command, timeoutSeconds)
        } catch (error: Exception) {
            HostCommandResult(
                code = -1, stdout = "", stderr = "",
                errorMessage = "Managed Root error: ${error.javaClass.simpleName}: ${error.message}",
            )
        }
    }

    @JvmStatic
    fun errorText(result: HostCommandResult): String =
        result.stderr.ifBlank { result.errorMessage.orEmpty() }

    @JvmStatic
    fun splitLines(text: String?): List<String> =
        text.orEmpty().takeIf { it.isNotBlank() }?.split(Regex("\\R"))?.toList() ?: emptyList()
}
