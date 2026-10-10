package com.yagay.YSuite

import android.app.Application
import android.content.Context
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.UserManager
import android.util.Log
import com.yagay.suite.core.FeatureRegistry
import com.yagay.suite.core.FeatureRuntimeManager
import com.yagay.suite.core.FeatureStateStore
import com.yagay.suite.core.RootManager
import com.yagay.suite.core.SuiteContract
import com.yagay.suite.core.SuiteCrashTracker
import com.yagay.suite.core.SuiteHookReloadCoordinator
import com.yagay.suite.core.SuiteLog
import com.yagay.suite.core.SuiteXposedServiceBroker

class YSuiteApp : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(SuiteLocaleController.localizedContext(base))
    }

    @Volatile
    private var initialized = false
    private var unlockReceiver: BroadcastReceiver? = null

    override fun onCreate() {
        super.onCreate()
        SuiteLocaleController.initializeLegacyDelegates(this)

        runCatching { SuiteCrashTracker.install(this) }
            .onFailure { Log.e(TAG, "Crash tracker initialization failed safely", it) }

        if (!isUserUnlockedSafely()) {
            runCatching { registerUnlockReceiver() }
                .onFailure { Log.e(TAG, "Unlock receiver registration failed safely", it) }
            Log.i(TAG, "User locked; deferring YSuite host initialization until ACTION_USER_UNLOCKED")
            return
        }

        runCatching { initializeUnlockedHost() }
            .onFailure { Log.e(TAG, "YSuite host bootstrap failed safely", it) }
    }

    @Synchronized
    private fun initializeUnlockedHost() {
        if (initialized || !isUserUnlockedSafely()) return
        initialized = true

        unlockReceiver?.let { receiver -> runCatching { unregisterReceiver(receiver) } }
        unlockReceiver = null

        safeHostStep("crash-attribution") {
            SuiteCrashTracker.markActiveFeature(this, null)
        }

        // Each process-global subsystem is isolated. A missing/broken LSPosed service, Root backend,
        // OEM package service or post-update reload path must never prevent the shell from opening.
        safeHostStep("xposed-broker") {
            SuiteXposedServiceBroker.takeOwnership(this)
        }
        safeHostStep("root-manager") {
            RootManager.initialize(this)
        }
        safeHostStep("hook-reload") {
            SuiteHookReloadCoordinator.resumePending(this)
        }

        val states = runCatching { FeatureStateStore(this) }
            .getOrElse { error ->
                Log.e(TAG, "Feature state store unavailable; skipping feature bootstrap", error)
                return
            }
        val included = runCatching { FeatureRegistry.included() }
            .getOrElse { error ->
                Log.e(TAG, "Feature registry scan failed safely", error)
                emptyList()
            }
        val packageInfo = runCatching { packageManager.getPackageInfo(packageName, 0) }.getOrNull()
        val versionName = packageInfo?.versionName ?: "unknown"
        val versionCode = packageInfo?.longVersionCode ?: -1L

        safeSuiteLog(
            SuiteContract.HOST_MODULE_ID,
            "YSuite host starting; version=$versionName($versionCode); contract=${SuiteContract.REVISION}; " +
                "features=${included.size}; ids=${included.joinToString(",") { it.id }}",
        )

        included.forEach { feature ->
            val enabled = runCatching { states.isEnabled(feature) }
                .getOrElse { error ->
                    safeSuiteError(feature.id, "host state read failed; feature skipped", error)
                    return@forEach
                }
            if (!enabled) {
                safeSuiteLog(feature.id, "host disabled")
                return@forEach
            }

            safeSuiteLog(
                feature.id,
                "host runtime enable requested; class=${feature.runtimeInitializerClassName ?: "none"}",
            )
            runCatching { FeatureRuntimeManager.enable(this, feature) }
                .getOrElse { Result.failure(it) }
                .onSuccess {
                    safeSuiteLog(
                        feature.id,
                        "host runtime enabled; managed=${FeatureRuntimeManager.isManaged(feature.id)}; capabilities owned by YSuite",
                    )
                }
                .onFailure {
                    safeSuiteError(
                        feature.id,
                        "host runtime enable failed; class=${feature.runtimeInitializerClassName ?: "none"}",
                        it,
                    )
                }
        }

        safeSuiteLog(
            SuiteContract.HOST_MODULE_ID,
            "YSuite host initialized; version=$versionName($versionCode); contract=${SuiteContract.REVISION}; " +
                "features=${included.size}; xposedListeners=${runCatching { SuiteXposedServiceBroker.listenerCount() }.getOrDefault(0)}",
        )
    }

    override fun onTerminate() {
        runCatching { FeatureRuntimeManager.destroyAll(this) }
            .onFailure { Log.e(TAG, "Feature runtime shutdown failed safely", it) }
        super.onTerminate()
    }

    private fun registerUnlockReceiver() {
        if (unlockReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_USER_UNLOCKED) {
                    runCatching { initializeUnlockedHost() }
                        .onFailure { Log.e(TAG, "Deferred host bootstrap failed safely", it) }
                }
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

    private fun isUserUnlockedSafely(): Boolean = runCatching {
        getSystemService(UserManager::class.java)?.isUserUnlocked != false
    }.getOrElse {
        Log.w(TAG, "Cannot resolve user-unlocked state; assuming unlocked", it)
        true
    }

    private inline fun safeHostStep(name: String, block: () -> Unit) {
        runCatching(block).onFailure { error ->
            Log.e(TAG, "Host subsystem '$name' failed safely", error)
            safeSuiteError(SuiteContract.HOST_MODULE_ID, "host subsystem failed: $name", error)
        }
    }

    private fun safeSuiteLog(moduleId: String, message: String) {
        runCatching { SuiteLog.i(this, moduleId, message) }
            .onFailure { Log.w(TAG, "SuiteLog write failed: $message", it) }
    }

    private fun safeSuiteError(moduleId: String, message: String, error: Throwable) {
        runCatching { SuiteLog.e(this, moduleId, message, error) }
            .onFailure { Log.e(TAG, "$message (SuiteLog unavailable)", error) }
    }

    private companion object {
        const val TAG = "YSuite.App"
    }
}
