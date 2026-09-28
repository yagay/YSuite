package com.yagay.YSuite

import android.app.Application
import com.yagay.suite.core.FeatureRegistry
import com.yagay.suite.core.FeatureStateStore
import com.yagay.suite.core.RootManager
import com.yagay.suite.core.SuiteContract
import com.yagay.suite.core.SuiteCrashTracker
import com.yagay.suite.core.SuiteLog
import com.yagay.suite.core.SuiteXposedServiceBroker

class YSuiteApp : Application() {
    override fun onCreate() {
        super.onCreate()

        SuiteCrashTracker.install(this)
        SuiteCrashTracker.markActiveFeature(this, null)

        val states = FeatureStateStore(this)
        val included = FeatureRegistry.included()
        val packageInfo = packageManager.getPackageInfo(packageName, 0)
        val versionName = packageInfo.versionName ?: "unknown"
        val versionCode = packageInfo.longVersionCode

        SuiteLog.i(
            this,
            SuiteContract.HOST_MODULE_ID,
            "YSuite host starting; version=$versionName($versionCode); contract=${SuiteContract.REVISION}; " +
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
                .onSuccess { runtime ->
                    // Capture the listener immediately, before the next standalone feature runtime
                    // has a chance to replace XposedServiceHelper's process-global listener.
                    SuiteXposedServiceBroker.capture(this, runtime)
                    SuiteLog.i(this, feature.id, "host runtime initialized")
                }
                .onFailure { SuiteLog.e(this, feature.id, "host runtime initialization failed; class=$initializer", it) }
        }

        // These three resources are process-global. Reclaim them only after all independently
        // buildable feature initializers have had a chance to configure their standalone defaults.
        SuiteXposedServiceBroker.takeOwnership(this)
        RootManager.initialize(this)
        SuiteCrashTracker.reclaim(this)

        SuiteLog.i(
            this,
            SuiteContract.HOST_MODULE_ID,
            "YSuite host initialized; version=$versionName($versionCode); contract=${SuiteContract.REVISION}; " +
                "features=${included.size}; xposedListeners=${SuiteXposedServiceBroker.listenerCount()}",
        )
    }
}
