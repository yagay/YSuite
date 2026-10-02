package com.yagay.ydownload

import android.content.Context
import android.util.Log
import com.yagay.suite.api.FeatureHost
import com.yagay.suite.api.HostLogLevel
import com.yagay.suite.api.ManagedFeatureRuntime

class YDownloadSuiteRuntime private constructor(context: Context) : ManagedFeatureRuntime {
    private val appContext = context.applicationContext
    override fun attach(host: FeatureHost) { Companion.host = host }
    override fun enable() { log(HostLogLevel.INFO, "ydownload runtime enabled") }
    override fun disable() { log(HostLogLevel.INFO, "ydownload runtime disabled") }
    override fun destroy() { if (host?.applicationContext === appContext) host = null }

    companion object {
        @Volatile private var instance: YDownloadSuiteRuntime? = null
        @Volatile private var host: FeatureHost? = null
        @JvmStatic fun get(context: Context): YDownloadSuiteRuntime = instance ?: synchronized(this) {
            instance ?: YDownloadSuiteRuntime(context).also { instance = it }
        }
        fun log(level: HostLogLevel, message: String, error: Throwable? = null) {
            host?.log(level, message, error) ?: when (level) {
                HostLogLevel.DEBUG -> Log.d("YDownload", message, error)
                HostLogLevel.INFO -> Log.i("YDownload", message, error)
                HostLogLevel.WARN -> Log.w("YDownload", message, error)
                HostLogLevel.ERROR -> Log.e("YDownload", message, error)
            }
        }
    }
}
