package com.yagay.suite.core

import android.content.Context
import com.yagay.suite.api.ManagedFeatureRuntime

/**
 * Owns feature runtime lifecycle in the combined process.
 *
 * Legacy runtimes remain compatible: their existing static get(Context) initializer is invoked on
 * enable. Migrated runtimes additionally receive attach/enable/disable/destroy callbacks. Legacy
 * runtimes are still deterministically gated at host boundaries by FeatureStateStore,
 * SuiteXposedServiceBroker and SuiteRootGateway.
 */
object FeatureRuntimeManager {
    private data class RuntimeRecord(
        var runtime: Any? = null,
        var attached: Boolean = false,
        var enabled: Boolean = false,
        var managed: Boolean = false,
    )

    private val records = linkedMapOf<String, RuntimeRecord>()

    @Synchronized
    fun enable(context: Context, feature: FeatureSpec): Result<Any?> = runCatching {
        val app = context.applicationContext
        val record = records.getOrPut(feature.id) { RuntimeRecord() }

        if (record.enabled && record.runtime != null) return@runCatching record.runtime

        val runtime = when {
            record.runtime is ManagedFeatureRuntime -> record.runtime
            feature.runtimeInitializerClassName == null -> null
            else -> feature.instantiateRuntime(app)
        }
        record.runtime = runtime
        record.managed = runtime is ManagedFeatureRuntime

        if (runtime is ManagedFeatureRuntime) {
            if (!record.attached) {
                runtime.attach(SuiteFeatureHost(app, feature))
                record.attached = true
            }
            runtime.enable()
        } else if (feature.runtimeInitializerClassName != null) {
            SuiteLog.i(
                app,
                feature.id,
                "legacy runtime enabled through host compatibility gate; migrate to ManagedFeatureRuntime only when feature-local cleanup is needed",
            )
        }

        record.enabled = true
        runtime
    }

    @Synchronized
    fun disable(context: Context, feature: FeatureSpec): Result<Unit> = runCatching {
        val app = context.applicationContext
        val record = records[feature.id]
        val runtime = record?.runtime
        if (runtime is ManagedFeatureRuntime && record.enabled) {
            runtime.disable()
        } else if (record?.enabled == true && runtime != null) {
            SuiteLog.i(
                app,
                feature.id,
                "legacy runtime disabled at host boundaries; LSPosed callbacks and unified Root access are gated immediately",
            )
        }
        if (record != null) record.enabled = false
    }

    @Synchronized
    fun destroyAll(context: Context) {
        val app = context.applicationContext
        records.forEach { (featureId, record) ->
            val runtime = record.runtime
            if (runtime is ManagedFeatureRuntime) {
                runCatching {
                    if (record.enabled) runtime.disable()
                    runtime.destroy()
                }.onFailure {
                    SuiteLog.e(app, featureId, "managed runtime destroy failed", it)
                }
            }
        }
        records.clear()
    }

    @Synchronized
    fun isRuntimeEnabled(featureId: String): Boolean = records[featureId]?.enabled == true

    @Synchronized
    fun isManaged(featureId: String): Boolean = records[featureId]?.managed == true
}
