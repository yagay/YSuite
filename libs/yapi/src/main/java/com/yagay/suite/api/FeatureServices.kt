package com.yagay.suite.api

import android.app.Activity
import android.net.Uri
import android.util.Log
import java.io.File

/**
 * Single public infrastructure entry for reusable feature code.
 *
 * Feature business code should depend on this facade (or the typed FeatureHost passed to a runtime)
 * instead of knowing Core implementation classes. The registry is populated by the host before a
 * managed runtime is attached, so the same code works in the combined YSuite APK and generic
 * standalone host.
 */
class FeatureServices @JvmOverloads constructor(
    featureId: String,
    private val logTag: String = featureId,
) {
    val featureId: String = FeatureIds.normalize(featureId)

    fun hostOrNull(): FeatureHost? = FeatureHostRegistry.find(featureId)

    fun requireHost(message: String = "Feature host is not attached: $featureId"): FeatureHost =
        hostOrNull() ?: throw IllegalStateException(message)

    fun capabilityState(capability: HostCapability): HostCapabilityState =
        hostOrNull()?.capabilityState(capability) ?: HostCapabilityState.NOT_DECLARED

    fun requestCapability(
        activity: Activity,
        capability: HostCapability,
        requestCode: Int = 0x5953,
    ): HostCapabilityRequestResult =
        hostOrNull()?.requestCapability(activity, capability, requestCode)
            ?: HostCapabilityRequestResult.NOT_DECLARED

    fun rootText(
        operation: String,
        command: String,
        timeoutSeconds: Long = 15L,
    ): Result<String> {
        val host = hostOrNull()
            ?: return Result.failure(IllegalStateException("Root host is not attached: $featureId"))
        return host.rootExecute(operation, command, timeoutSeconds).toTextResult(operation)
    }

    fun rootBinary(
        operation: String,
        command: String,
        timeoutSeconds: Long = 15L,
        maxStdoutBytes: Int = 1024 * 1024,
        mergeError: Boolean = false,
    ): HostBinaryCommandResult {
        val host = hostOrNull()
            ?: return HostBinaryCommandResult(
                code = -1,
                stdout = ByteArray(0),
                stderr = "Feature host is not attached: $featureId",
                errorMessage = "Root host unavailable",
            )
        return host.rootExecuteBinary(
            operation = operation,
            command = command,
            timeoutSeconds = timeoutSeconds,
            maxStdoutBytes = maxStdoutBytes,
            mergeError = mergeError,
        )
    }

    fun rootStart(operation: String, command: String): Process? =
        hostOrNull()?.rootStart(operation, command)

    fun reloadPackage(packageName: String, timeoutSeconds: Long = 12L): HostProcessReloadResult =
        hostOrNull()?.reloadPackage(packageName, timeoutSeconds)
            ?: HostProcessReloadResult(
                packageName = packageName,
                success = false,
                killedCount = 0,
                detail = "Feature host is not attached: $featureId",
            )

    fun frameworkStatus(): String = hostOrNull()?.frameworkStatus().orEmpty()

    fun sharedFileUri(file: File): Uri? = hostOrNull()?.sharedFileUri(file)

    fun settingBoolean(key: String, defaultValue: Boolean = false): Boolean =
        hostOrNull()?.settingBoolean(key, defaultValue) ?: defaultValue

    fun putSettingBoolean(key: String, value: Boolean) {
        hostOrNull()?.putSettingBoolean(key, value)
    }

    fun settingString(key: String, defaultValue: String? = null): String? =
        hostOrNull()?.settingString(key, defaultValue) ?: defaultValue

    fun putSettingString(key: String, value: String?) {
        hostOrNull()?.putSettingString(key, value)
    }

    fun settingInt(key: String, defaultValue: Int = 0): Int =
        hostOrNull()?.settingInt(key, defaultValue) ?: defaultValue

    fun putSettingInt(key: String, value: Int) {
        hostOrNull()?.putSettingInt(key, value)
    }

    fun settingLong(key: String, defaultValue: Long = 0L): Long =
        hostOrNull()?.settingLong(key, defaultValue) ?: defaultValue

    fun putSettingLong(key: String, value: Long) {
        hostOrNull()?.putSettingLong(key, value)
    }

    fun settingStringSet(key: String, defaultValue: Set<String> = emptySet()): Set<String> =
        hostOrNull()?.settingStringSet(key, defaultValue) ?: defaultValue

    fun putSettingStringSet(key: String, value: Set<String>) {
        hostOrNull()?.putSettingStringSet(key, value)
    }

    fun removeSetting(key: String) {
        hostOrNull()?.removeSetting(key)
    }

    fun log(level: HostLogLevel, message: String, error: Throwable? = null) {
        val host = hostOrNull()
        if (host != null) {
            host.log(level, message, error)
            return
        }
        when (level) {
            HostLogLevel.DEBUG -> Log.d(logTag, message, error)
            HostLogLevel.INFO -> Log.i(logTag, message, error)
            HostLogLevel.WARN -> Log.w(logTag, message, error)
            HostLogLevel.ERROR -> Log.e(logTag, message, error)
        }
    }

    fun debug(message: String) = log(HostLogLevel.DEBUG, message)
    fun info(message: String) = log(HostLogLevel.INFO, message)
    fun warn(message: String, error: Throwable? = null) = log(HostLogLevel.WARN, message, error)
    fun error(message: String, error: Throwable? = null) = log(HostLogLevel.ERROR, message, error)

    companion object {
        @JvmStatic
        @JvmOverloads
        fun of(featureId: String, logTag: String = featureId): FeatureServices =
            FeatureServices(featureId, logTag)
    }
}
