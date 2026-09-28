package com.yagay.YSuite

import android.app.Application
import com.yagay.suite.core.FeatureRegistry
import com.yagay.suite.core.FeatureStateStore
import com.yagay.suite.core.SuiteLog

class YSuiteApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val states = FeatureStateStore(this)
        val included = FeatureRegistry.included()

        SuiteLog.i(
            this,
            "suite",
            "YSuite host starting; version=${BuildConfig.VERSION_NAME}(${BuildConfig.VERSION_CODE}); " +
                "features=${included.size}; ids=${included.joinToString(",") { it.id }}",
        )

        included.forEach { feature ->
            val enabled = states.isEnabled(feature)
            val initializer = feature.runtimeInitializerClassName

            if (!enabled) {
                SuiteLog.i(this, feature.id, "host disabled")
                return@forEach
            }

            if (initializer == null) {
                SuiteLog.i(this, feature.id, "host enabled; no runtime initializer")
                return@forEach
            }

            SuiteLog.i(this, feature.id, "host runtime init requested; class=$initializer")
            runCatching { feature.initialize(this) }
                .onSuccess { SuiteLog.i(this, feature.id, "host runtime initialized") }
                .onFailure { SuiteLog.e(this, feature.id, "host runtime initialization failed; class=$initializer", it) }
        }

        SuiteLog.i(
            this,
            "suite",
            "YSuite host initialized; version=${BuildConfig.VERSION_NAME}(${BuildConfig.VERSION_CODE}); features=${included.size}",
        )
    }
}
