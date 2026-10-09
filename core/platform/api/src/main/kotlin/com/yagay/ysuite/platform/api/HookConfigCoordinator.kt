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
) {
    val runtimeVerified: Boolean get() = false
}

/**
 * Publishes a coherent revision marker only after all individual remote
 * preference writes succeed. Target runtimes can use this marker to
 * reject partial generations as they gain protocol support.
 *
 * Existing target hooks keep reading their original keys: the marker is
 * additive and cannot itself claim runtime activation.
 */
class HookConfigCoordinator(private val gateway: HookGateway) {
    suspend fun publish(
        group: String,
        values: Map<String, String?>,
        scopePackages: Set<String> = emptySet(),
    ): Outcome<HookDeliveryReceipt> {
        if (group.isBlank() || values.isEmpty() ||
            values.keys.any { it.isBlank() || it == REVISION_KEY }) {
            return Outcome.Failure(
                code = "hook_publication_invalid",
                message = "Hook group and feature keys must be nonblank; revision is reserved",
            )
        }
        val revision = revisionFor(values)
        for ((key, value) in values) {
            when (val written = gateway.writeConfig(group, key, value)) {
                is Outcome.Failure -> return written
                is Outcome.Success -> Unit
            }
        }
        // Never advance revision on a partially failed write.
        when (val marked = gateway.writeConfig(group, REVISION_KEY, revision)) {
            is Outcome.Failure -> return marked
            is Outcome.Success -> Unit
        }
        if (scopePackages.isNotEmpty()) {
            when (val scoped = gateway.reload(scopePackages)) {
                is Outcome.Failure -> return scoped
                is Outcome.Success -> Unit
            }
        }
        return Outcome.Success(
            HookDeliveryReceipt(group, revision, values.keys.toSet(), scopePackages),
        )
    }

    companion object {
        const val REVISION_KEY: String = "__config_revision"

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
