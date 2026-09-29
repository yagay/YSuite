package com.yagay.YSuite

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.UserManager
import android.util.Log
import com.yagay.suite.core.FeatureRegistry
import com.yagay.suite.core.FeatureStateStore
import com.yagay.suite.core.RootManager
import com.yagay.suite.core.SuiteContract
import com.yagay.suite.core.SuiteCrashTracker
import com.yagay.suite.core.SuiteLog
import com.yagay.suite.core.SuiteXposedServiceBroker

class YSuiteApp : Application() {
    @Volatile
    private var initialized = false
    private var unlockReceiver: BroadcastReceiver? = null

    override fun onCreate() {
        super.onCreate()

        // Crash attribution itself is Direct-Boot safe. Everything else can depend on credential
        // encrypted storage, so defer the normal host bootstrap until the user is unlocked.
        SuiteCrashTracker.install(this)
        if (!isUserUnlocked()) {
            registerUnlockReceiver()
            Log.i(TAG, "User locked; deferring YSuite host initialization until ACTION_USER_UNLOCKED")
            return
        }
        initializeUnlockedHost()
    }

    @Synchronized
    private fun initializeUnlockedHost() {
        if (initialized || !isUserUnlocked()) return
        initialized = true
        unlockReceiver?.let { receiver -> runCatching { unregisterReceiver(receiver) } }
        unlockReceiver = null

        // Own the combined APK's window/system-bar contract before any feature Activity opens.
        // Standalone feature APKs never load this class, so their UI remains independent.
        SuiteUiCoordinator.install(this)

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

    private fun registerUnlockReceiver() {
        if (unlockReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_USER_UNLOCKED) initializeUnlockedHost()
            }
        }
        unlockReceiver = receiver
        val filter = IntentFilter(Intent.ACTION_USER_UNLOCKED)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(receiver, filter)
        }
    }

    private fun isUserUnlocked(): Boolean =
        getSystemService(UserManager::class.java)?.isUserUnlocked != false

    private companion object {
        const val TAG = "YSuite.App"
    }
}
