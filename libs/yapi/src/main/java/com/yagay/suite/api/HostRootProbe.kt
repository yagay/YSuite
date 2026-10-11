package com.yagay.suite.api

/**
 * One Root authorization probe for all hosted features.
 *
 * A missing Host, failed command, or timeout is UNKNOWN, not a revoked grant.
 * A successful numeric non-zero uid is the only definite unprivileged result.
 * This checks the caller's actual Host shell, never su binaries or files.
 */
object HostRootProbe {
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
        val host = services.hostOrNull()
            ?: return Result(Status.HOST_MISSING, "Feature Host is not attached")
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
