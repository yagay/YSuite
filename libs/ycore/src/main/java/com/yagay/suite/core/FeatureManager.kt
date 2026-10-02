package com.yagay.suite.core

import android.content.Context
import android.content.Intent

/**
 * Minimal registration contract for one reusable feature.
 *
 * Keep feature-specific business logic out of this class. A feature must remain independently
 * buildable while exposing the same entry/runtime implementation to YSuite.
 */
data class FeatureSpec(
    val id: String,
    val name: String,
    val description: String,
    val entryActivityClassName: String,
    val runtimeInitializerClassName: String? = null,
    /** Package name of the optional standalone APK built from the same feature source. */
    val standalonePackageName: String? = null,
    /** Whether this feature is expected to remain independently buildable as an APK. */
    val standaloneEnabled: Boolean = false,
    /** Shared host capabilities consumed by this feature when embedded in YSuite. */
    val sharedCapabilities: Set<SuiteCapability> = emptySet(),
    /**
     * Optional standalone-safe bridge consumed by YSuite's one AccessibilityService.
     */
    val accessibilityBridgeClassName: String? = null,
    /** Optional standalone-safe bridge consumed by YSuite's one NotificationListenerService. */
    val notificationListenerBridgeClassName: String? = null,
    /** Optional standalone BroadcastReceiver implementation used as a logical lifecycle handler. */
    val bootReceiverClassName: String? = null,
    /** Exact exported IPC action -> logical BroadcastReceiver implementation. */
    val ipcRoutes: Map<String, String> = emptyMap(),
    val requiresRoot: Boolean = false,
    val requiresHook: Boolean = false,
    val defaultEnabled: Boolean = true,
) {
    fun isIncluded(): Boolean = runCatching { Class.forName(entryActivityClassName) }.isSuccess

    fun createIntent(context: Context): Intent = Intent(context, Class.forName(entryActivityClassName))

    /** All host-side runtime activation passes through the shared lifecycle owner. */
    fun initialize(context: Context): Any? = FeatureRuntimeManager.enable(context, this).getOrThrow()

    /** Reflective runtime factory used only by [FeatureRuntimeManager]. */
    internal fun instantiateRuntime(context: Context): Any? {
        val className = runtimeInitializerClassName ?: return null
        val runtimeClass = Class.forName(className)
        return runtimeClass.getMethod("get", Context::class.java).invoke(null, context.applicationContext)
    }
}

/** Runtime view of config/features.toml. */
object FeatureRegistry {
    val all: List<FeatureSpec> = GeneratedFeatureCatalog.all
    private val byId: Map<String, FeatureSpec> = all.associateBy(FeatureSpec::id)

    fun included(): List<FeatureSpec> = all.filter(FeatureSpec::isIncluded)

    fun find(id: String): FeatureSpec? = byId[id]

    /** Constant-time exact IPC routing generated from config/features.toml. */
    fun resolveIpcRoute(action: String): Pair<FeatureSpec, String>? {
        val (featureId, receiverClassName) =
            GeneratedFeatureCatalog.ipcActionOwners[action] ?: return null
        val feature = byId[featureId]?.takeIf(FeatureSpec::isIncluded) ?: return null
        return feature to receiverClassName
    }
}

class FeatureStateStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(
        SuiteContract.FEATURE_STATE_PREFS,
        Context.MODE_PRIVATE,
    )

    fun isEnabled(feature: FeatureSpec): Boolean =
        prefs.getBoolean(SuiteContract.FEATURE_STATE_KEY_PREFIX + feature.id, feature.defaultEnabled)

    /**
     * The persisted switch is the authoritative feature state for the combined host.
     * Enable prepares runtime before Hook replay; disable cuts Hook/Root access before cleanup.
     */
    fun setEnabled(feature: FeatureSpec, enabled: Boolean) {
        prefs.edit()
            .putBoolean(SuiteContract.FEATURE_STATE_KEY_PREFIX + feature.id, enabled)
            .apply()

        val lifecycle = if (enabled) {
            FeatureRuntimeManager.enable(appContext, feature).map { Unit }.also { result ->
                if (result.isSuccess) {
                    SuiteXposedServiceBroker.setPluginEnabled(feature.id, true)
                } else {
                    prefs.edit()
                        .putBoolean(SuiteContract.FEATURE_STATE_KEY_PREFIX + feature.id, false)
                        .apply()
                    SuiteXposedServiceBroker.setPluginEnabled(feature.id, false)
                }
            }
        } else {
            SuiteXposedServiceBroker.setPluginEnabled(feature.id, false)
            SuiteRootGateway.stopPluginProcesses(feature.id)
            FeatureRuntimeManager.disable(appContext, feature)
        }

        lifecycle.onFailure {
            SuiteLog.e(
                appContext,
                feature.id,
                if (enabled) "host enable failed" else "host disable failed",
                it,
            )
        }
    }
}
