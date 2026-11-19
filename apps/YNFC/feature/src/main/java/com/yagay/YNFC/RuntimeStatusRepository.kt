package com.yagay.YNFC

import android.database.ContentObserver
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.yagay.YNFC.system.NfcSystemService
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

class RuntimeStatusRepository(context: Context, private val nfcSystemService: NfcSystemService) {
    private val resolver = context.applicationContext.contentResolver

    fun observeChanges(): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { trySend(Unit) }
            override fun onChange(selfChange: Boolean, uri: android.net.Uri?) { trySend(Unit) }
        }
        resolver.registerContentObserver(ConfigProvider.URI, true, observer)
        trySend(Unit)
        awaitClose { runCatching { resolver.unregisterContentObserver(observer) } }
    }.conflate()

    fun readProviderMap(): Map<String, String> {
        val map = mutableMapOf<String, String>()
        runCatching {
            resolver.query(ConfigProvider.URI, null, null, null, null)?.use { c ->
                while (c.moveToNext()) map[c.getString(0)] = c.getString(1)
            }
        }
        return map
    }

    fun read(includeRootPid: Boolean = false): RuntimeStatus {
        val map = readProviderMap()
        val rootPid = if (includeRootPid) currentNfcPid() else null
        return decode(map, rootPid)
    }

    /** Low-frequency Root verification; absence is unknown, never proof NFC stopped. */
    fun currentNfcPid(): Int? = nfcSystemService.currentNfcPid().toIntOrNull()?.takeIf { it > 0 }

    fun isCurrentCommandGeneration(generation: Long): Boolean =
        readProviderMap()[ConfigProvider.KEY_COMMAND_GENERATION]?.toLongOrNull() == generation

    fun simulationEnabled(): Boolean = readProviderMap()[ConfigProvider.KEY_SIMULATION_ENABLED].toBoolean()

    fun decode(map: Map<String, String>, rootPid: Int?): RuntimeStatus {
        val scopePid = map[ConfigProvider.KEY_SCOPE_PID]?.toIntOrNull() ?: 0
        val hookPid = map[ConfigProvider.KEY_HOOK_PID]?.toIntOrNull() ?: 0
        val runtimePid = map[ConfigProvider.KEY_RUNTIME_PID]?.toIntOrNull() ?: 0
        val rfPid = map[ConfigProvider.KEY_RF_PID]?.toIntOrNull() ?: 0
        val commandPid = map[ConfigProvider.KEY_COMMAND_PID]?.toIntOrNull() ?: 0
        val currentPid = rootPid ?: runtimePid.takeIf { it > 0 }
            ?: hookPid.takeIf { it > 0 } ?: commandPid.takeIf { it > 0 } ?: scopePid.takeIf { it > 0 } ?: rfPid
        val hookBuild = map[ConfigProvider.KEY_HOOK_BUILD]?.toIntOrNull() ?: 0
        val rawRfStatus = map[ConfigProvider.KEY_RF_STATUS] ?: "IDLE"
        val rawControllerEpoch = map[ConfigProvider.KEY_CONTROLLER_EPOCH]?.toLongOrNull() ?: 0L
        val rfControllerEpoch = map[ConfigProvider.KEY_RF_CONTROLLER_EPOCH]?.toLongOrNull() ?: Long.MIN_VALUE
        val providerAccepted = map[ConfigProvider.KEY_RF_ACCEPTED].toBoolean()
        val providerVerified = map[ConfigProvider.KEY_VERIFICATION_CONFIDENCE] == "VERIFIED"
        val sameRfProcess = currentPid > 0 && rfPid == currentPid && (runtimePid == 0 || runtimePid == currentPid)

        // Compatibility repair for early schema-7 snapshots: they could contain a verified RF
        // epoch without the matching controller_epoch. HookConfigStore now persists the missing
        // controller epoch, but the UI must not briefly downgrade an already verified native write
        // to STALE while that repair is happening.
        val controllerEpoch = if (
            rawControllerEpoch <= 0L && rfControllerEpoch > 0L && providerAccepted && providerVerified && sameRfProcess
        ) rfControllerEpoch else rawControllerEpoch

        val rfFresh = RfFreshness.isFresh(
            currentPid = currentPid,
            runtimePid = runtimePid,
            rfPid = rfPid,
            controllerEpoch = controllerEpoch,
            rfControllerEpoch = rfControllerEpoch
        )
        // Last manual RF evidence remains stale. A newer lifecycle request
        // can be displayed separately only while its generation, PID and
        // controller epoch are still current.
        val ownNciRecoveryRequested =
            RuntimePresentationPolicy.currentUidRestore(map, currentPid, controllerEpoch)
        val restartTransition = map[ConfigProvider.KEY_COMMAND_STATUS] == "RESTART_REQUIRED" &&
            map[ConfigProvider.KEY_COMMAND_GENERATION]?.toLongOrNull() == map[ConfigProvider.KEY_RF_GENERATION]?.toLongOrNull()
        // A current-generation terminal FAILED result is authoritative command
        // metadata, not RF proof. It must stay visible even when controller RF
        // evidence has a stale epoch, e.g. ColorOS Type-A permission denial.
        val currentCommandGeneration = map[ConfigProvider.KEY_COMMAND_GENERATION]?.toLongOrNull()
        val terminalFailureVisible = currentPid > 0 && commandPid == currentPid &&
            currentCommandGeneration != null && currentCommandGeneration > 0L &&
            map[ConfigProvider.KEY_COMMAND_STATUS] == "FAILED" &&
            map[ConfigProvider.KEY_COMMAND_HANDLED_GENERATION]?.toLongOrNull() == currentCommandGeneration &&
            map[ConfigProvider.KEY_RF_GENERATION]?.toLongOrNull() == currentCommandGeneration &&
            map[ConfigProvider.KEY_OPERATION_STATE] == "FAILED"
        // NO_OVERRIDE is terminal in-process command evidence, not a native
        // RF proof. Keep its explanatory UI visible across an unrelated
        // controller epoch update without claiming a verified UID/stock RF.
        val noOverrideVisible = currentPid > 0 && commandPid == currentPid &&
            currentCommandGeneration != null && currentCommandGeneration > 0L &&
            map[ConfigProvider.KEY_COMMAND_STATUS] == "NO_OVERRIDE" &&
            map[ConfigProvider.KEY_COMMAND_HANDLED_GENERATION]?.toLongOrNull() == currentCommandGeneration &&
            map[ConfigProvider.KEY_RF_GENERATION]?.toLongOrNull() == currentCommandGeneration
        // A terminal NO_OVERRIDE STOP is *command* evidence that no UID write
        // happened, not an RF readback. Old lifecycle refresh metadata must
        // never transform that terminal IDLE into REVERIFY_PENDING and cause
        // the upper layer to restart an unchanged NFC process.
        val lifecycleReverifyPending = !noOverrideVisible && rfFresh &&
            map[ConfigProvider.KEY_REFRESH_TRIGGER_STATUS] in setOf(
                "LIFECYCLE_INVALIDATED",
                "LIFECYCLE_REPLAY_WAIT",
                "LIFECYCLE_FAILED"
            )
        val visibleRfStatus = when {
            rawRfStatus == "IDLE" -> "IDLE"
            terminalFailureVisible || noOverrideVisible -> rawRfStatus
            ownNciRecoveryRequested -> "UID_RECOVERY_REQUESTED_UNVERIFIED"
            rfFresh -> rawRfStatus
            restartTransition -> "RESETTING($rawRfStatus)"
            rfPid == 0 && rawRfStatus in setOf("WAITING", "APPLYING", "STOPPING") -> rawRfStatus
            else -> "STALE($rawRfStatus)"
        }
        val semanticVisible = rfFresh || rfPid == 0 || restartTransition ||
            terminalFailureVisible || noOverrideVisible || ownNciRecoveryRequested
        val rawOperation = map[ConfigProvider.KEY_OPERATION_STATE] ?: "IDLE"
        return RuntimeStatus(
            appBuild = map[ConfigProvider.KEY_APP_BUILD]?.toIntOrNull() ?: 0,
            hookBuild = hookBuild,
            hookImplementationRevision = map["hook_impl_revision"]?.toIntOrNull() ?: 0,
            hookImplementationPid = map["hook_impl_revision_pid"]?.toIntOrNull() ?: 0,
            currentPid = currentPid,
            runtimePid = runtimePid,
            scopePid = scopePid,
            hookPid = hookPid,
            scopeOk = currentPid > 0 && scopePid == currentPid && map[ConfigProvider.KEY_SCOPE_OK].toBoolean(),
            hookInstalled = currentPid > 0 && hookPid == currentPid && map[ConfigProvider.KEY_HOOK_INSTALLED].toBoolean(),
            simulationEnabled = map[ConfigProvider.KEY_SIMULATION_ENABLED].toBoolean(),
            selectedUid = map[ConfigProvider.KEY_UID]?.takeIf { it.isNotBlank() },
            commandGeneration = map[ConfigProvider.KEY_COMMAND_GENERATION]?.toLongOrNull() ?: 0L,
            consumedGeneration = map[ConfigProvider.KEY_COMMAND_CONSUMED_GENERATION]?.toLongOrNull() ?: Long.MIN_VALUE,
            handledGeneration = map[ConfigProvider.KEY_COMMAND_HANDLED_GENERATION]?.toLongOrNull() ?: Long.MIN_VALUE,
            commandAction = map[ConfigProvider.KEY_COMMAND_ACTION].orEmpty(),
            commandStatus = map[ConfigProvider.KEY_COMMAND_STATUS] ?: "IDLE",
            commandDetail = map[ConfigProvider.KEY_COMMAND_DETAIL]?.takeIf { it.isNotBlank() },
            commandPid = commandPid,
            operationState = when {
                !semanticVisible -> "STALE"
                ownNciRecoveryRequested -> "RECOVERY_REQUESTED"
                lifecycleReverifyPending && rawOperation == "IDLE" -> "REVERIFY_PENDING"
                else -> rawOperation
            },
            effectiveState = if (semanticVisible) map[ConfigProvider.KEY_EFFECTIVE_STATE] ?: "UNKNOWN" else "UNKNOWN",
            verificationConfidence = if (semanticVisible) map[ConfigProvider.KEY_VERIFICATION_CONFIDENCE] ?: "NONE" else "NONE",
            rfAccepted = rfFresh && providerAccepted,
            rfStatus = visibleRfStatus,
            rfUid = if (rfFresh) map[ConfigProvider.KEY_RF_UID]?.takeIf { it.isNotBlank() } else null,
            rfSource = if (rfFresh || terminalFailureVisible || noOverrideVisible) map[ConfigProvider.KEY_RF_SOURCE]?.takeIf { it.isNotBlank() } else null,
            rfResult = if (rfFresh) map[ConfigProvider.KEY_RF_RESULT]?.takeIf { it.isNotBlank() } else null,
            rfNativeResult = if (rfFresh) map[ConfigProvider.KEY_RF_NATIVE_RESULT]?.takeIf { it.isNotBlank() } else null,
            rfNativeResultType = if (rfFresh) map[ConfigProvider.KEY_RF_NATIVE_RESULT_TYPE]?.takeIf { it.isNotBlank() } else null,
            rfError = if (rfFresh || terminalFailureVisible) map[ConfigProvider.KEY_RF_ERROR]?.takeIf { it.isNotBlank() } else null,
            rfPid = rfPid,
            rfGeneration = map[ConfigProvider.KEY_RF_GENERATION]?.toLongOrNull() ?: 0L,
            rfVerification = if (rfFresh || terminalFailureVisible || noOverrideVisible) map[ConfigProvider.KEY_RF_VERIFICATION]?.takeIf { it.isNotBlank() } else if (restartTransition) "LIFECYCLE_PENDING" else null,
            fullDiagStage = map[ConfigProvider.KEY_FULL_DIAG_STAGE]?.takeIf { it.isNotBlank() },
            fullDiagSummary = map[ConfigProvider.KEY_FULL_DIAG_SUMMARY]?.takeIf { it.isNotBlank() },
            pollingDisabled = map[ConfigProvider.KEY_POLLING_DISABLED].toBoolean(),
            pollingGuardStatus = run {
                val status = map[ConfigProvider.KEY_POLLING_GUARD_STATUS] ?: "UNKNOWN"
                val pid = map[ConfigProvider.KEY_POLLING_GUARD_PID]?.toIntOrNull() ?: 0
                val capabilitiesPid = map[ConfigProvider.KEY_POLLING_GUARD_PID]?.toIntOrNull() ?: 0
                val reported = if (currentPid <= 0 || pid <= 0 || pid != currentPid ||
                    capabilitiesPid != currentPid) "STALE($status)" else status
                RuntimePresentationPolicy.pollingStatus(
                    map[ConfigProvider.KEY_POLLING_DISABLED].toBoolean(),
                    reported, if (reported.startsWith("STALE(")) null
                    else map[ConfigProvider.KEY_POLLING_CAPABILITIES]
                )
            },
            pollingGuardPid = map[ConfigProvider.KEY_POLLING_GUARD_PID]?.toIntOrNull() ?: 0,
            pollingGuardDetail = map[ConfigProvider.KEY_POLLING_GUARD_DETAIL]
                ?.takeIf { it.isNotBlank() },
            pollingCapabilitySummary = map[ConfigProvider.KEY_POLLING_CAPABILITIES]
                ?.takeIf { it.isNotBlank() },
            ownNciRecoveryRequested = ownNciRecoveryRequested
        )
    }
}
