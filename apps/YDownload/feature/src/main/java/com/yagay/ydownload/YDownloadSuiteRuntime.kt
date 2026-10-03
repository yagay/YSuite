package com.yagay.ydownload

import android.content.Context
import com.yagay.suite.api.FeatureHost
import com.yagay.suite.api.FeatureHostBinding
import com.yagay.suite.api.HostLogLevel
import com.yagay.suite.api.ManagedFeatureRuntime

class YDownloadSuiteRuntime private constructor(context: Context) : ManagedFeatureRuntime {
    private val appContext = context.applicationContext
    override fun attach(host: FeatureHost) { hostBinding.attach(host) }
    override fun enable() { log(HostLogLevel.INFO, "ydownload runtime enabled") }
    override fun disable() { log(HostLogLevel.INFO, "ydownload runtime disabled") }
    override fun destroy() { hostBinding.clearIfOwnedBy(appContext) }

    companion object {
        @Volatile private var instance: YDownloadSuiteRuntime? = null
        private val hostBinding = FeatureHostBinding("YDownload")

        @JvmStatic fun get(context: Context): YDownloadSuiteRuntime = instance ?: synchronized(this) {
            instance ?: YDownloadSuiteRuntime(context).also { instance = it }
        }

        fun log(level: HostLogLevel, message: String, error: Throwable? = null) {
            hostBinding.log(level, message, error)
        }
    }
}
