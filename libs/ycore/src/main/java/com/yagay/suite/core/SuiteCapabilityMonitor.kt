package com.yagay.suite.core

import android.content.Context
import android.os.SystemClock
import com.yagay.suite.api.HostRootProbe
import com.yagay.suite.api.FeatureServices

/**
 * One evidence contract for YSuite's actual permissions and injected targets.
 * A framework connection does not prove target loading; target loading does
 * not prove that a feature-specific Hook or an RF operation succeeded.
 */
object SuiteCapabilityMonitor {
    enum class State { CHECKING, VERIFIED, UNKNOWN, STALE, NOT_GRANTED, MISSING_SCOPE, TARGET_NOT_LOADED, OUTDATED }
    data class Evidence(
        val state: State,
        val detail: String,
        val sampledAtElapsedMs: Long,
        val processName: String? = null,
        val loadedVersion: Long? = null,
    ) {
        val verified: Boolean get() = state == State.VERIFIED
    }

    private const val ROOT_POSITIVE_TTL_MS = 10_000L
    private const val ROOT_FAILURE_TTL_MS = 2_000L
    private const val TRANSIENT_GRACE_MS = 20_000L
    private var lastRoot: Evidence? = null
    private var lastSuccessfulRootAt: Long = 0

    @Synchronized
    fun root(context: Context, refresh: Boolean = false): Evidence {
        val now = SystemClock.elapsedRealtime()
        val cached = lastRoot
        val ttl = if (cached?.verified == true) ROOT_POSITIVE_TTL_MS else ROOT_FAILURE_TTL_MS
        if (!refresh && cached != null && now - cached.sampledAtElapsedMs in 0 until ttl) return cached
        val granted = runCatching { RootManager.isAvailable(context.applicationContext) }.getOrNull()
        val state = when (granted) {
            true -> State.VERIFIED
            false -> if (lastSuccessfulRootAt > 0 &&
                now - lastSuccessfulRootAt in 0 until TRANSIENT_GRACE_MS) State.STALE else State.UNKNOWN
            null -> State.UNKNOWN
        }
        if (granted == true) lastSuccessfulRootAt = now
        return Evidence(state, when (state) {
            State.VERIFIED -> "Host libsu shell verified privileged"
            State.STALE -> "Root probe failed temporarily; previous success is stale"
            else -> "Root not verified; retry or inspect Host Root shell"
        }, now).also { lastRoot = it }
    }

    fun featureRoot(services: FeatureServices): Evidence {
        val now = SystemClock.elapsedRealtime()
        val probe = HostRootProbe.probe(services)
        val state = when (probe.status) {
            HostRootProbe.Status.GRANTED -> State.VERIFIED
            HostRootProbe.Status.UNPRIVILEGED -> State.NOT_GRANTED
            else -> State.UNKNOWN
        }
        return Evidence(state, "${probe.status}: ${probe.detail}", now)
    }

    fun framework(): Evidence {
        val now = SystemClock.elapsedRealtime()
        return if (SuiteXposedServiceBroker.isConnected()) {
            Evidence(State.VERIFIED, "LSPosed service connected (target Hook status not implied)", now)
        } else Evidence(State.UNKNOWN, "LSPosed service unavailable or connecting", now)
    }

    fun target(packageName: String, expectedVersionCode: Long? = null): Evidence =
        SuiteXposedServiceBroker.targetEvidence(packageName, expectedVersionCode)

    /** Atomic export for troubleshooting shared state, without changing any Hook's behavior. */
    fun diagnosticSnapshot(context: Context): String = buildString {
        appendLine("ROOT=${root(context)}")
        appendLine("FRAMEWORK=${framework()}")
        appendLine("TARGETS (these prove framework loading only, not feature Hook installation):")
        append(SuiteXposedServiceBroker.targetDiagnosticSnapshot())
    }
}
