package com.yagay.suite.api

import android.app.Activity
import android.content.Context
import android.net.Uri
import java.io.File

/** Stable host contract shared by YSuite and independently buildable feature shells. */
interface FeatureHost {
    val applicationContext: Context
    val hostPackageName: String
    val featureId: String

    /** Whether this feature declared the capability in the central feature catalog. */
    fun supports(capability: HostCapability): Boolean

    /** Current host-owned state for a declared system capability. */
    fun capabilityState(capability: HostCapability): HostCapabilityState =
        if (supports(capability)) HostCapabilityState.NOT_GRANTED else HostCapabilityState.NOT_DECLARED

    /** Route runtime/special-access requests through the host instead of feature-local Settings code. */
    fun requestCapability(
        activity: Activity,
        capability: HostCapability,
        requestCode: Int = 0x5953,
    ): HostCapabilityRequestResult =
        if (supports(capability)) HostCapabilityRequestResult.MANUAL_ACTION_REQUIRED
        else HostCapabilityRequestResult.NOT_DECLARED

    fun rootExecute(
        operation: String,
        command: String,
        timeoutSeconds: Long = 15L,
    ): HostCommandResult

    fun reloadPackage(
        packageName: String,
        timeoutSeconds: Long = 12L,
    ): HostProcessReloadResult

    fun frameworkStatus(): String

    /** Convert a feature-owned file into a host-owned content URI. */
    fun sharedFileUri(file: File): Uri? = null

    /** Feature-scoped settings. The implementation owns the physical preference name. */
    fun settingBoolean(key: String, defaultValue: Boolean = false): Boolean = defaultValue
    fun putSettingBoolean(key: String, value: Boolean) {}
    fun settingString(key: String, defaultValue: String? = null): String? = defaultValue
    fun putSettingString(key: String, value: String?) {}
    fun removeSetting(key: String) {}

    fun log(
        level: HostLogLevel,
        message: String,
        error: Throwable? = null,
    )
}

enum class HostCapability {
    ROOT,
    LSPOSED,
    ACCESSIBILITY,
    NOTIFICATION_LISTENER,
    OVERLAY,
    NOTIFICATIONS,
    ALL_FILES,
    FILE_SHARE,
    NFC,
}

enum class HostCapabilityState {
    GRANTED,
    NOT_GRANTED,
    NOT_SUPPORTED,
    NOT_DECLARED,
}

enum class HostCapabilityRequestResult {
    ALREADY_GRANTED,
    RUNTIME_PERMISSION_REQUESTED,
    SETTINGS_OPENED,
    MANUAL_ACTION_REQUIRED,
    NOT_SUPPORTED,
    NOT_DECLARED,
    FAILED,
}

enum class HostLogLevel {
    DEBUG,
    INFO,
    WARN,
    ERROR,
}

data class HostCommandResult(
    val code: Int,
    val stdout: String,
    val stderr: String,
    val timedOut: Boolean = false,
    val errorMessage: String? = null,
) {
    val success: Boolean get() = !timedOut && errorMessage == null && code == 0
}

data class HostProcessReloadResult(
    val packageName: String,
    val success: Boolean,
    val killedCount: Int,
    val detail: String,
)

/** Required lifecycle contract for every YSuite feature runtime. */
interface ManagedFeatureRuntime {
    fun attach(host: FeatureHost) {}
    fun enable() {}
    fun disable() {}
    fun destroy() {}
}
