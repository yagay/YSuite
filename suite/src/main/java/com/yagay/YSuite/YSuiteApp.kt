package com.yagay.YSuite

import android.app.Application
import com.yagay.suite.core.FeatureRegistry
import com.yagay.suite.core.FeatureStateStore
import com.yagay.suite.core.SuiteContract
import com.yagay.suite.core.SuiteCrashTracker
import com.yagay.suite.core.SuiteLog

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
                .onSuccess { SuiteLog.i(this, feature.id, "host runtime initialized") }
                .onFailure { SuiteLog.e(this, feature.id, "host runtime initialization failed; class=$initializer", it) }
        }

        SuiteLog.i(
            this,
            SuiteContract.HOST_MODULE_ID,
            "YSuite host initialized; version=$versionName($versionCode); contract=${SuiteContract.REVISION}; features=${included.size}",
        )
    }
}
