package com.yagay.YNFC

import android.content.Context
import com.yagay.suite.api.FeatureServices
import com.yagay.suite.api.ManagedFeatureRuntime

/** Thin runtime entry used by YSuite without changing standalone behavior. */
object YNFCSuiteRuntime : ManagedFeatureRuntime {
    @Volatile private var appContext: Context? = null
    private val services = FeatureServices.of("ynfc", "YNFC")
    @Volatile private var enabled = false

    @JvmStatic
    fun get(context: Context): YNFCSuiteRuntime {
        appContext = context.applicationContext
        return this
    }

    override fun enable() {
        if (enabled) return
        enabled = true
        val context = appContext ?: services.hostOrNull()?.applicationContext ?: return
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
        appContext = null
    }
}
