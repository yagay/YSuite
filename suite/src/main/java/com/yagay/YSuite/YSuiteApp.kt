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
        included
            .filter(states::isEnabled)
            .filter { it.runtimeInitializerClassName != null }
            .forEach { feature ->
                runCatching { feature.initialize(this) }
                    .onSuccess { SuiteLog.i(this, feature.id, "host runtime initialized") }
                    .onFailure { SuiteLog.e(this, feature.id, "host runtime initialization failed", it) }
            }
        SuiteLog.i(this, "suite", "YSuite host initialized; features=${included.size}")
    }
}
