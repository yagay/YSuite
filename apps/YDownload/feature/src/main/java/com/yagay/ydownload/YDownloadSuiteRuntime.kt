package com.yagay.ydownload

import android.content.Context
import com.yagay.suite.api.FeatureServices
import com.yagay.suite.api.HostLogLevel
import com.yagay.suite.api.ManagedFeatureRuntime

class YDownloadSuiteRuntime private constructor(context: Context) : ManagedFeatureRuntime {
    override fun enable() { services.info("ydownload runtime enabled") }
    override fun disable() { services.info("ydownload runtime disabled") }

    companion object {
        @Volatile private var instance: YDownloadSuiteRuntime? = null
        private val services = FeatureServices.of("ydownload", "YDownload")

        @JvmStatic fun get(context: Context): YDownloadSuiteRuntime = instance ?: synchronized(this) {
            instance ?: YDownloadSuiteRuntime(context.applicationContext).also { instance = it }
        }

        fun log(level: HostLogLevel, message: String, error: Throwable? = null) {
            services.log(level, message, error)
        }
    }
}
