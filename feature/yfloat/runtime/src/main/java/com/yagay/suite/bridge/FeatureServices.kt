package com.yagay.suite.api

import com.yagay.YFloat.compat.YFloatRuntimeHost

class FeatureServices private constructor(
    private val featureId: String,
    private val logTag: String,
) {
    fun hostOrNull(): Any? =
        if (YFloatRuntimeHost.isInstalled()) this else null

    @JvmOverloads
    fun rootBinary(
        operation: String,
        command: String,
        timeoutSeconds: Long = 15L,
        maxStdoutBytes: Int = 1024 * 1024,
        mergeError: Boolean = false,
    ): HostBinaryCommandResult {
        val result =
            YFloatRuntimeHost.run(
                command = command,
                timeoutSeconds = timeoutSeconds,
                maxStdoutBytes = maxStdoutBytes,
                binary = true,
                mergeError = mergeError,
            )
        return HostBinaryCommandResult(
            result.code,
            result.stdout,
            result.stderr,
            result.timedOut,
            result.errorMessage,
        )
    }

    fun info(message: String) =
        YFloatRuntimeHost.info(logTag, message)

    @JvmOverloads
    fun warn(message: String, error: Throwable? = null) {
        if (error == null) {
            YFloatRuntimeHost.info(logTag, message)
        } else {
            YFloatRuntimeHost.error(logTag, message, error)
        }
    }

    @JvmOverloads
    fun error(message: String, error: Throwable? = null) =
        YFloatRuntimeHost.error(logTag, message, error)

    companion object {
        @JvmStatic
        @JvmOverloads
        fun of(
            featureId: String,
            logTag: String = featureId,
        ): FeatureServices =
            FeatureServices(featureId, logTag)
    }
}
