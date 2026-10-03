package com.yagay.suite.api

import java.util.concurrent.ConcurrentHashMap

/**
 * Process-local registry for the FeatureHost currently owned by each managed feature.
 *
 * Deep feature helpers (Root facades, exporters, diagnostics, etc.) can resolve the host by the
 * stable feature id instead of depending on Core implementation class names or duplicating
 * runtime-to-helper forwarding code.
 */
object FeatureHostRegistry {
    private val hosts = ConcurrentHashMap<String, FeatureHost>()

    @JvmStatic
    fun attach(host: FeatureHost) {
        val featureId = host.featureId.trim()
        require(featureId.isNotEmpty()) { "FeatureHost.featureId must not be blank" }
        hosts[featureId] = host
    }

    @JvmStatic
    fun find(featureId: String): FeatureHost? = hosts[featureId.trim()]

    @JvmStatic
    fun detach(featureId: String, expectedHost: FeatureHost? = null) {
        val normalized = featureId.trim()
        if (normalized.isEmpty()) return
        if (expectedHost == null) {
            hosts.remove(normalized)
        } else {
            hosts.remove(normalized, expectedHost)
        }
    }

    @JvmStatic
    fun clear() {
        hosts.clear()
    }
}
