package com.yagay.suite.api

import android.content.Context

/** Stable host contract shared by YSuite and independently buildable feature shells. */
interface FeatureHost {
    val applicationContext: Context
    val hostPackageName: String
    val featureId: String

    fun supports(capability: HostCapability): Boolean

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
