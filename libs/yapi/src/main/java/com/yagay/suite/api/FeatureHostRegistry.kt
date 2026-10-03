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
        hosts[FeatureIds.normalize(host.featureId)] = host
    }

    @JvmStatic
    fun find(featureId: String): FeatureHost? = hosts[FeatureIds.normalize(featureId)]

    @JvmStatic
    fun detach(featureId: String, expectedHost: FeatureHost? = null) {
        val normalized = FeatureIds.normalize(featureId)
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
