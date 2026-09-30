package com.yagay.suite.core

import android.content.Context
import com.yagay.suite.api.ManagedFeatureRuntime

/**
 * Owns the lifecycle of every feature runtime in the combined process.
 *
 * YSuite no longer supports a legacy runtime path: every configured runtime must implement
 * [ManagedFeatureRuntime]. This keeps enable/disable semantics deterministic across Runtime,
 * LSPosed callbacks, Hook gating and Root ownership.
 */
object FeatureRuntimeManager {
    private data class RuntimeRecord(
        var runtime: ManagedFeatureRuntime? = null,
        var attached: Boolean = false,
        var enabled: Boolean = false,
    )

    private val records = linkedMapOf<String, RuntimeRecord>()

    @Synchronized
    fun enable(context: Context, feature: FeatureSpec): Result<Any?> = runCatching {
        val app = context.applicationContext
        val record = records.getOrPut(feature.id) { RuntimeRecord() }

        if (record.enabled) return@runCatching record.runtime

        val runtime = record.runtime ?: feature.runtimeInitializerClassName?.let {
            val created = feature.instantiateRuntime(app)
            require(created is ManagedFeatureRuntime) {
                "${feature.id} runtime must implement ManagedFeatureRuntime: ${feature.runtimeInitializerClassName}"
            }
            created.also { record.runtime = it }
        }

        if (runtime != null) {
            if (!record.attached) {
                runtime.attach(SuiteFeatureHost(app, feature))
                record.attached = true
            }
            runtime.enable()
        }

        record.enabled = true
        runtime
    }

    @Synchronized
    fun disable(context: Context, feature: FeatureSpec): Result<Unit> = runCatching {
        val record = records[feature.id]
        if (record?.enabled == true) {
            record.runtime?.disable()
            record.enabled = false
        }
    }

    @Synchronized
    fun destroyAll(context: Context) {
        val app = context.applicationContext
        records.forEach { (featureId, record) ->
            val runtime = record.runtime ?: return@forEach
            runCatching {
                if (record.enabled) runtime.disable()
                runtime.destroy()
            }.onFailure {
                SuiteLog.e(app, featureId, "managed runtime destroy failed", it)
            }
        }
        records.clear()
    }

    @Synchronized
    fun isRuntimeEnabled(featureId: String): Boolean = records[featureId]?.enabled == true

    @Synchronized
    fun isManaged(featureId: String): Boolean = records[featureId]?.runtime != null
}
