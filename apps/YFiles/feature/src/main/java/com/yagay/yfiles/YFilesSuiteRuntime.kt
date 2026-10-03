package com.yagay.yfiles

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.util.Log
import com.yagay.suite.api.FeatureHost
import com.yagay.suite.api.HostCapability
import com.yagay.suite.api.HostCapabilityRequestResult
import com.yagay.suite.api.HostCapabilityState
import com.yagay.suite.api.HostLogLevel
import com.yagay.suite.api.ManagedFeatureRuntime
import java.io.File

class YFilesSuiteRuntime private constructor(context: Context) : ManagedFeatureRuntime {
    private val appContext = context.applicationContext
    override fun attach(host: FeatureHost) { Companion.host = host }
    override fun enable() { log(HostLogLevel.INFO, "yfiles runtime enabled") }
    override fun disable() { log(HostLogLevel.INFO, "yfiles runtime disabled") }
    override fun destroy() { if (host?.applicationContext === appContext) host = null }

    companion object {
        @Volatile private var instance: YFilesSuiteRuntime? = null
        @Volatile private var host: FeatureHost? = null

        @JvmStatic fun get(context: Context): YFilesSuiteRuntime = instance ?: synchronized(this) {
            instance ?: YFilesSuiteRuntime(context).also { instance = it }
        }

        fun capabilityState(capability: HostCapability): HostCapabilityState =
            host?.capabilityState(capability) ?: HostCapabilityState.NOT_DECLARED

        fun requestCapability(
            activity: Activity,
            capability: HostCapability,
        ): HostCapabilityRequestResult =
            host?.requestCapability(activity, capability) ?: HostCapabilityRequestResult.NOT_DECLARED

        fun rootAvailable(): Boolean =
            capabilityState(HostCapability.ROOT) == HostCapabilityState.GRANTED

        fun sharedFileUri(file: File): Uri? = host?.sharedFileUri(file)

        fun rootList(path: String): Result<List<String>> {
            val current = host ?: return Result.failure(IllegalStateException("YSuite Root host is not attached"))
            val quoted = "'" + path.replace("'", "'\\''") + "'"
            val result = current.rootExecute("list", "ls -A1 -- $quoted", 12L)
            return if (result.success) Result.success(result.stdout.lineSequence().filter(String::isNotBlank).toList())
            else Result.failure(IllegalStateException(result.errorMessage ?: result.stderr.ifBlank { "Root list failed" }))
        }

        fun log(level: HostLogLevel, message: String, error: Throwable? = null) {
            host?.log(level, message, error) ?: when (level) {
                HostLogLevel.DEBUG -> Log.d("YFiles", message, error)
                HostLogLevel.INFO -> Log.i("YFiles", message, error)
                HostLogLevel.WARN -> Log.w("YFiles", message, error)
                HostLogLevel.ERROR -> Log.e("YFiles", message, error)
            }
        }
    }
}
