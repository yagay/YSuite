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
import com.yagay.suite.core.SuiteHookReloadCoordinator
import com.yagay.suite.core.SuiteLog
import com.yagay.suite.core.SuiteXposedServiceBroker

class YSuiteApp : Application() {
    @Volatile
    private var initialized = false
    private var unlockReceiver: BroadcastReceiver? = null

    override fun onCreate() {
        super.onCreate()

        // YUI is initialized automatically by AndroidX Startup before Application.onCreate().
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

        SuiteCrashTracker.markActiveFeature(this, null)

        // Process-global capabilities always belong to the host and are established before any
        // plugin runtime is initialized. Embedded plugins may only attach/consume these services.
        SuiteXposedServiceBroker.takeOwnership(this)
        RootManager.initialize(this)
        // A package replacement can arrive while credential storage is locked or before the LSPosed
        // service is ready. Resume the persisted host-owned reload request after bootstrap.
        SuiteHookReloadCoordinator.resumePending(this)

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
                .onSuccess {
                    SuiteLog.i(this, feature.id, "host runtime initialized; capabilities owned by YSuite")
                }
                .onFailure { SuiteLog.e(this, feature.id, "host runtime initialization failed; class=$initializer", it) }
        }

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
