package com.yagay.YTaskManager

import android.content.Context
import com.yagay.suite.api.FeatureHost
import com.yagay.suite.api.ManagedFeatureRuntime

/** Managed runtime adapter shared by YSuite while preserving standalone behavior. */
object YTaskManagerSuiteRuntime : ManagedFeatureRuntime {
    @Volatile private var delegate: YTaskManagerRuntime? = null
    @Volatile private var host: FeatureHost? = null

    @JvmStatic
    fun get(context: Context): Any {
        val appContext = context.applicationContext
        AppLogger.attach(appContext)
        delegate = YTaskManagerRuntime.get(appContext)
        AppLogger.i("YSuite runtime attached")
        return this
    }

    override fun attach(host: FeatureHost) {
        this.host = host
    }

    override fun enable() {
        delegate?.setManagedEnabled(true)
    }

    override fun disable() {
        delegate?.setManagedEnabled(false)
    }

    override fun destroy() {
        disable()
        delegate = null
        host = null
    }
}
