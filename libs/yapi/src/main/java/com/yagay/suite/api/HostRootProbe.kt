package com.yagay.suite.api

/**
 * Shared Host Root probe. Every hosted feature uses the same real Host executor;
 * independently probing each feature creates a flood of redundant privileged
 * commands that can itself cause false timeouts.
 *
 * Missing Host, command timeout and I/O failures are UNKNOWN, never a revoked grant.
 */
object HostRootProbe {
    private const val READY_TTL_MS = 12_000L
    private const val RETRY_TTL_MS = 2_000L

    private data class Cached(val atMs: Long, val result: Result)
    private val cache = java.util.concurrent.ConcurrentHashMap<String, Cached>()
    private val lock = Any()

    @JvmStatic
    fun invalidate(featureId: String) {
        // All embedded features share the same privileged Host process. A known
        // permission change must invalidate the evidence for every feature.
        synchronized(lock) { cache.clear() }
    }

    enum class Status { GRANTED, UNPRIVILEGED, HOST_MISSING, NOT_DECLARED, TRANSIENT_FAILURE }

    data class Result(
        val status: Status,
        val detail: String,
        val uid: Int? = null,
    ) {
        val granted: Boolean get() = status == Status.GRANTED
        val transient: Boolean get() = status == Status.HOST_MISSING ||
            status == Status.TRANSIENT_FAILURE
    }

    @JvmStatic
    @JvmOverloads
    fun probe(services: FeatureServices, timeoutSeconds: Long = 6L): Result {
        val host = services.hostOrNull()
            ?: return Result(Status.HOST_MISSING, "Feature Host is not attached")
        if (!host.supports(HostCapability.ROOT)) {
            return Result(Status.NOT_DECLARED, "Feature does not declare Root capability")
        }

        // One check per actual Host APK, rather than one per UI feature. Do not
        // cache missing hosts: the FeatureHostRegistry may still be attaching.
        val key = host.hostPackageName.ifBlank { services.featureId }
        synchronized(lock) {
            val now = android.os.SystemClock.elapsedRealtime()
            val previous = cache[key]
            val ttl = if (previous?.result?.granted == true) READY_TTL_MS else RETRY_TTL_MS
            if (previous != null && now - previous.atMs in 0 until ttl) {
                return previous.result
            }
            val fresh = checkHost(services, timeoutSeconds)
            cache[key] = Cached(android.os.SystemClock.elapsedRealtime(), fresh)
            return fresh
        }
    }

    private fun checkHost(services: FeatureServices, timeoutSeconds: Long): Result {
        // A verified Host libsu shell already proves uid 0. Re-running "id -u" on
        // the shared command queue caused false permission failures under TaskManager load.
        // Do not translate UNKNOWN into revoked Root; preserve the transport distinction.
        when (services.capabilityState(HostCapability.ROOT)) {
            HostCapabilityState.GRANTED ->
                return Result(Status.GRANTED, "Root verified (uid=0) by managed Host libsu", 0)
            HostCapabilityState.UNKNOWN ->
                return Result(Status.TRANSIENT_FAILURE, "Host Root status pending or inconclusive")
            HostCapabilityState.NOT_DECLARED ->
                return Result(Status.NOT_DECLARED, "Feature does not declare Root capability")
            HostCapabilityState.NOT_SUPPORTED ->
                return Result(Status.TRANSIENT_FAILURE, "Host cannot currently verify Root")
            HostCapabilityState.NOT_GRANTED -> {
                // A custom/standalone Host may not implement capability-state probing.
                // Only numeric uid evidence from its executor can prove unprivileged execution.
            }
        }
        val response = FeatureRootCommands.execute(
            services, "root-capability-probe", "id -u", timeoutSeconds.coerceAtLeast(1L),
        )
        if (response.timedOut) return Result(Status.TRANSIENT_FAILURE, "Root probe timed out")
        if (!response.errorMessage.isNullOrBlank()) {
            return Result(Status.TRANSIENT_FAILURE, response.errorMessage.take(240))
        }
        if (response.code != 0) {
            return Result(
                Status.TRANSIENT_FAILURE,
                "Root probe failed (exit=${response.code}): " +
                    response.stderr.ifBlank { response.stdout }.take(200),
            )
        }
        val uid = response.stdout.lineSequence().map(String::trim)
            .firstOrNull { it.isNotEmpty() && it.all(Char::isDigit) }
            ?.toIntOrNull()
            ?: return Result(Status.TRANSIENT_FAILURE, "Root probe returned no numeric UID")
        return if (uid == 0) Result(Status.GRANTED, "Root verified (uid=0)", uid)
        else Result(Status.UNPRIVILEGED, "Root command ran as uid=$uid", uid)
    }
}
