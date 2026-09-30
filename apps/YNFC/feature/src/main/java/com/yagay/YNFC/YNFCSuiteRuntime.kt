package com.yagay.YNFC

import android.content.Context
import com.yagay.suite.api.FeatureHost
import com.yagay.suite.api.ManagedFeatureRuntime

/** Thin runtime entry used by YSuite without changing standalone behavior. */
object YNFCSuiteRuntime : ManagedFeatureRuntime {
    @Volatile private var appContext: Context? = null
    @Volatile private var host: FeatureHost? = null
    @Volatile private var enabled = false

    @JvmStatic
    fun get(context: Context): YNFCSuiteRuntime {
        appContext = context.applicationContext
        return this
    }

    override fun attach(host: FeatureHost) {
        this.host = host
    }

    override fun enable() {
        if (enabled) return
        enabled = true
        val context = appContext ?: host?.applicationContext ?: return
        AppLogger.attach(context)
        AppLogger.i("YSuite runtime attached")
    }

    override fun disable() {
        if (!enabled) return
        AppLogger.i("YSuite runtime detached")
        enabled = false
        AppLogger.detach()
    }

    override fun destroy() {
        disable()
        host = null
        appContext = null
    }
}
