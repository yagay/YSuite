package com.yagay.yfiles

import android.content.Context
import com.yagay.suite.api.FeatureServices
import com.yagay.suite.api.ManagedFeatureRuntime

/**
 * Required YSuite feature lifecycle bridge. No legacy YFiles business logic is retained.
 * The standalone and combined hosts continue to own the LSPosed registration.
 */
class YFilesSuiteRuntime private constructor(context: Context) : ManagedFeatureRuntime {
    override fun enable() { services.info("rebuilt yfiles enabled") }
    override fun disable() { services.info("rebuilt yfiles disabled") }

    companion object {
        @Volatile private var instance: YFilesSuiteRuntime? = null
        private val services = FeatureServices.of("yfiles", "YFiles")

        @JvmStatic
        fun get(context: Context): YFilesSuiteRuntime = instance ?: synchronized(this) {
            instance ?: YFilesSuiteRuntime(context.applicationContext).also { instance = it }
        }
    }
}
