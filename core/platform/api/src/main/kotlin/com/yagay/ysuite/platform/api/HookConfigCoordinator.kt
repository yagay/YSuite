package com.yagay.ysuite.platform.api

import com.yagay.ysuite.common.Outcome
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * Transport publication and target activation are different operations.
 * A successful write and LSPosed scope approval never proves that the
 * target process loaded or executed the new hooks.
 */
enum class HookDeliveryPhase {
    AwaitingTargetRuntime,
}

data class HookDeliveryReceipt(
    val group: String,
    val revision: String,
    val keys: Set<String>,
    val scopePackages: Set<String>,
    val phase: HookDeliveryPhase = HookDeliveryPhase.AwaitingTargetRuntime,
    val revisionKey: String = HookConfigCoordinator.REVISION_KEY,
) {
    val runtimeVerified: Boolean get() = false
}

/**
 * Publishes payload and revision together in one remote-preference
 * transaction where the HookGateway supports batching (the production
 * Android gateway does). Custom gateways using the default fallback may
 * still write keys individually.
 *
 * Existing target hooks keep reading their original keys: the marker is
 * additive and does not itself confirm runtime activation.
 */
class HookConfigCoordinator(private val gateway: HookGateway) {
    suspend fun publish(
        group: String,
        values: Map<String, String?>,
        scopePackages: Set<String> = emptySet(),
        revisionKey: String = REVISION_KEY,
    ): Outcome<HookDeliveryReceipt> {
        if (group.isBlank() || values.isEmpty() ||
            !revisionKey.startsWith(REVISION_KEY) ||
            values.keys.any { it.isBlank() || it.startsWith(REVISION_KEY) }) {
            return Outcome.Failure(
                code = "hook_publication_invalid",
                message = "Hook group and feature keys must be nonblank; revision is reserved",
            )
        }
        val revision = revisionFor(values)
        // Production HookGateway commits payload + revision together.
        // Scope approval remains separate and does not prove Hook activation.
        when (
            val written = gateway.writeConfigBatch(
                group,
                LinkedHashMap(values).apply { put(revisionKey, revision) },
            )
        ) {
            is Outcome.Failure -> return written
            is Outcome.Success -> Unit
        }
        if (scopePackages.isNotEmpty()) {
            when (val scoped = gateway.reload(scopePackages)) {
                is Outcome.Failure -> return scoped
                is Outcome.Success -> Unit
            }
        }
        return Outcome.Success(
            HookDeliveryReceipt(
                group, revision, values.keys.toSet(), scopePackages,
                revisionKey = revisionKey,
            ),
        )
    }

    companion object {
        const val REVISION_KEY: String = "__config_revision"

        /** Keep revisions independent for per-app Hook payloads in one group. */
        fun revisionKeyFor(target: String): String {
            require(target.isNotBlank()) { "target cannot be blank" }
            return REVISION_KEY + ":" + target.trim()
        }

        fun revisionFor(values: Map<String, String?>): String {
            val digest = MessageDigest.getInstance("SHA-256")
            values.toSortedMap().forEach { (key, value) ->
                val entry = key.length.toString() + ":" + key +
                    ":" + (value?.length ?: -1) + ":" + (value ?: "")
                digest.update(entry.toByteArray(StandardCharsets.UTF_8))
            }
            return digest.digest().joinToString("") { byte ->
                "%02x".format(byte.toInt() and 0xff)
            }
        }
    }
}
