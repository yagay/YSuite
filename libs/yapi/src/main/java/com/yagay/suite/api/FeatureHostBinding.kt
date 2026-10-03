package com.yagay.suite.api

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.File

/**
 * Small process-local bridge between a managed Feature runtime and its current YSuite host.
 *
 * Feature modules should keep their business state locally, while host lifecycle, capability
 * lookup, Root result normalization, shared-file routing and log fallback stay consistent here.
 */
class FeatureHostBinding(private val logTag: String) {
    @Volatile
    private var host: FeatureHost? = null

    fun attach(host: FeatureHost) {
        this.host = host
    }

    fun hostOrNull(): FeatureHost? = host

    fun requireHost(message: String = "YSuite host is not attached"): FeatureHost =
        host ?: throw IllegalStateException(message)

    fun clear() {
        host = null
    }

    /** Only detach the host owned by the same application process/context. */
    fun clearIfOwnedBy(context: Context) {
        val appContext = context.applicationContext
        if (host?.applicationContext === appContext) host = null
    }

    fun capabilityState(capability: HostCapability): HostCapabilityState =
        host?.capabilityState(capability) ?: HostCapabilityState.NOT_DECLARED

    fun requestCapability(
        activity: Activity,
        capability: HostCapability,
        requestCode: Int = 0x5953,
    ): HostCapabilityRequestResult =
        host?.requestCapability(activity, capability, requestCode)
            ?: HostCapabilityRequestResult.NOT_DECLARED

    fun sharedFileUri(file: File): Uri? = host?.sharedFileUri(file)

    /** Execute a Root command and expose the common Kotlin Result<String> shape to Features. */
    fun rootText(
        operation: String,
        command: String,
        timeoutSeconds: Long = 15L,
    ): Result<String> {
        val current = host
            ?: return Result.failure(IllegalStateException("YSuite Root host is not attached"))
        return current.rootExecute(operation, command, timeoutSeconds).toTextResult(operation)
    }

    /** Route logs through YSuite when attached, otherwise preserve standalone Logcat behavior. */
    fun log(level: HostLogLevel, message: String, error: Throwable? = null) {
        val current = host
        if (current != null) {
            current.log(level, message, error)
            return
        }
        when (level) {
            HostLogLevel.DEBUG -> Log.d(logTag, message, error)
            HostLogLevel.INFO -> Log.i(logTag, message, error)
            HostLogLevel.WARN -> Log.w(logTag, message, error)
            HostLogLevel.ERROR -> Log.e(logTag, message, error)
        }
    }
}

/** Normalize host Root failures once so every Feature reports timeout/error/stderr/exit consistently. */
fun HostCommandResult.toTextResult(operation: String): Result<String> =
    if (success) Result.success(stdout)
    else Result.failure(IllegalStateException(rootFailureMessage(operation)))

fun HostCommandResult.rootFailureMessage(operation: String): String = when {
    timedOut -> "Root operation timed out: $operation"
    !errorMessage.isNullOrBlank() -> errorMessage
    stderr.isNotBlank() -> stderr
    code != 0 -> "Root operation failed: $operation (exit=$code)"
    else -> "Root operation failed: $operation"
}
