package com.yagay.suite.core

import android.content.Context
import com.yagay.suite.api.ManagedFeatureRuntime

/**
 * Owns feature runtime lifecycle in the combined process.
 *
 * Legacy runtimes remain compatible: their existing static get(Context) initializer is invoked on
 * enable. Migrated runtimes additionally receive attach/enable/disable/destroy callbacks.
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
            else -> feature.initialize(app)
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
                "legacy runtime enabled through compatibility adapter; migrate to ManagedFeatureRuntime for deterministic disable",
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
                "legacy runtime has no disable callback; persisted feature state is disabled and runtime will stop after migration/process restart",
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
