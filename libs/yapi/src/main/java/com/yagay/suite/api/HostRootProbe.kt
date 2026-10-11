package com.yagay.suite.api

/**
 * One Root authorization probe for all hosted features.
 *
 * A missing Host, failed command, or timeout is UNKNOWN, not a revoked grant.
 * A successful numeric non-zero uid is the only definite unprivileged result.
 * This checks the caller's actual Host shell, never su binaries or files.
 */
object HostRootProbe {
    private const val READY_TTL_MS = 5_000L
    private const val RETRY_TTL_MS = 1_000L
    private data class Cached(val host: FeatureHost, val atMs: Long, val result: Result)
    private val cache = java.util.concurrent.ConcurrentHashMap<String, Cached>()
    private val locks = java.util.concurrent.ConcurrentHashMap<String, Any>()

    @JvmStatic
    fun invalidate(featureId: String) {
        cache.remove(featureId)
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
    fun probe(
        services: FeatureServices,
        timeoutSeconds: Long = 6L,
    ): Result {
        val featureId = services.featureId
        val host = services.hostOrNull()
            ?: return Result(Status.HOST_MISSING, "Feature Host is not attached")
        val lock = locks.computeIfAbsent(featureId) { Any() }
        synchronized(lock) {
        val now = android.os.SystemClock.elapsedRealtime()
        val previous = cache[featureId]
        val ttl = if (previous?.result?.granted == true) READY_TTL_MS else RETRY_TTL_MS
        if (previous?.host === host && now - previous.atMs in 0 until ttl) {
            return previous.result
        }
        val fresh = checkHost(services, host, timeoutSeconds)
        cache[featureId] = Cached(host, now, fresh)
        return fresh
        }
    }

    private fun checkHost(services: FeatureServices, host: FeatureHost, timeoutSeconds: Long): Result {
        if (!host.supports(HostCapability.ROOT)) {
            return Result(Status.NOT_DECLARED, "Feature does not declare Root capability")
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
