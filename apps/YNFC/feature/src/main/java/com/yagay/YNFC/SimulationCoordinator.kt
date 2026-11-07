package com.yagay.YNFC

import com.yagay.YNFC.system.NfcSystemService
import com.yagay.suite.api.YLocale
import java.util.concurrent.Executors

/** Serializes APPLY/STOP commands and preserves their generation/PID verification contract. */
internal class SimulationCoordinator(
    private val configClient: ConfigClient,
    private val runtimeRepository: RuntimeStatusRepository,
    private val nfcSystemService: NfcSystemService,
    private val expectedHookBuild: Int
) : AutoCloseable {
    private val executor = Executors.newSingleThreadExecutor()

    /**
     * All publishes happen inside the same executor as their completion.
     * Otherwise a new APPLY could supersede a STOP while the earlier STOP
     * was already restarting com.android.nfc, causing a second restart.
     */
    fun simulate(card: CardModel, onDone: (RuntimeStatus, String) -> Unit) {
        executor.execute {
            var ready = runtimeRepository.read(includeRootPid = true)
            // Never publish APPLY to a stale/incompatible in-process Hook.
            // Wait briefly for an already-restarting NFC service, but never
            // restart it automatically as a side effect of an APK upgrade.
            if (!hookReady(ready)) ready = waitForHookOnly(2_000)
            if (!hookReady(ready)) {
                AppLogger.i("SIMULATION: APPLY not published; NFC Hook mismatch or missing. " +
                    "hook=${ready.hookBuild} expected=$expectedHookBuild pid=${ready.currentPid}")
                onDone(ready, YLocale.text(R.string.ynfc_sim_hook_not_ready))
                return@execute
            }
            val generation = configClient.publishCommand(enabled = true, card = card)
            if (generation <= 0L) {
                onDone(runtimeRepository.read(includeRootPid = true),
                    YLocale.text(R.string.ynfc_sim_command_superseded))
                return@execute
            }
            AppLogger.i("SIMULATION: serial COMMAND APPLY generation=$generation uid=${card.uid} backend=RF")
            var state = runtimeRepository.read(includeRootPid = true)
            if (!hookReady(state)) {
                // APK installation must never restart NFC implicitly. An already
                // loaded LSPosed hook cannot be hot-swapped; if its code version
                // differs, offer an explicit NFC-service-only reload in the UI.
                // A missing scope/injection is not solved by repeated restarts.
                AppLogger.i("SIMULATION: NFC Hook not ready; no automatic restart " +
                    "generation=$generation currentPid=${state.currentPid} " +
                    "hookPid=${state.hookPid} hookBuild=${state.hookBuild} " +
                    "expectedHook=$expectedHookBuild")
            }

            if (hookReady(state)) {
                state = waitForCommandCompletion(generation, card.uid, apply = true, timeoutMs = 12_000)
            }
            val message = when {
                state.commandGeneration != generation -> YLocale.text(R.string.ynfc_sim_command_superseded)
                !hookReady(state) -> YLocale.text(R.string.ynfc_sim_hook_not_ready)
                SimulationResultPolicy.isApplySuccess(state, generation, card.uid) ->
                    YLocale.text(R.string.ynfc_sim_apply_success, card.uid)
                state.commandStatus == "FAILED" &&
                    state.commandDetail?.contains("RF_NATIVE_WRITE_NOT_OBSERVED") == true ->
                    YLocale.text(R.string.ynfc_rf_uid_writer_not_observed)
                state.commandStatus == "FAILED" || state.commandStatus == "TRIGGER_FAILED" ->
                    YLocale.text(
                        R.string.ynfc_sim_apply_failure,
                        state.commandStatus,
                        state.commandDetail ?: state.rfError ?: "unknown"
                    )
                else -> YLocale.text(R.string.ynfc_sim_waiting_rf)
            }
            AppLogger.i("SIMULATION: APPLY result generation=$generation message=$message\\n${statusSummary(state)}")
            onDone(state, message)
        }
    }

    fun stop(onDone: (RuntimeStatus, String) -> Unit) {
        executor.execute {
            val generation = configClient.publishCommand(enabled = false, card = null)
            if (generation <= 0L) {
                onDone(runtimeRepository.read(includeRootPid = true),
                    YLocale.text(R.string.ynfc_sim_command_superseded))
                return@execute
            }
            AppLogger.i("SIMULATION: serial COMMAND STOP generation=$generation")
            var state = waitForCommandCompletion(generation, null, apply = false, timeoutMs = 6_000)

            // STOP_NO_OVERRIDE is an explicit terminal acknowledgement from
            // the injected Hook: nothing was written to the controller. This
            // must never escalate to a process restart because of a stale
            // lifecycle *display* marker (REVERIFY_PENDING) or delayed RF
            // telemetry. The strict policy still checks full proof where
            // available; neither branch claims stock RF was independently read.
            val confirmedNoWrite = SimulationResultPolicy.isStopNoOverride(state, generation)
            val terminalNoOverride = state.commandGeneration == generation &&
                state.handledGeneration == generation &&
                state.commandAction == "STOP" && state.commandStatus == "NO_OVERRIDE"
            if (confirmedNoWrite || terminalNoOverride) {
                AppLogger.i("SIMULATION: STOP no UID write; skip NFC restart generation=$generation " +
                    "proofComplete=$confirmedNoWrite operation=${state.operationState}")
                onDone(state, YLocale.text(R.string.ynfc_sim_stop_no_override))
                return@execute
            }
            if (SimulationResultPolicy.isStopSuccess(state, generation)) {
                AppLogger.i("SIMULATION: STOP verified stock without restart generation=$generation")
                onDone(state, YLocale.text(R.string.ynfc_sim_stop_lifecycle_restored))
                return@execute
            }

            AppLogger.i("SIMULATION: STOP fallback snapshot generation=$generation\\n${statusSummary(state)}")
            if (!runtimeRepository.isCurrentCommandGeneration(generation)) {
                state = runtimeRepository.read(includeRootPid = true)
                AppLogger.i("SIMULATION: STOP fallback cancelled, a newer command owns NFC")
                onDone(state, YLocale.text(R.string.ynfc_sim_stop_superseded))
                return@execute
            }

            // Only a potentially modified controller is allowed to reach
            // this fallback. Never reboot the entire phone.
            val restart = nfcSystemService.restartNfcProcessKeepingEnabled(
                "stock_recovery_required_generation_$generation"
            )
            AppLogger.i("SIMULATION: STOP fallback NFC service reload generation=$generation\\n$restart")
            waitForHookOnly(12_000)
            if (!runtimeRepository.isCurrentCommandGeneration(generation)) {
                state = runtimeRepository.read(includeRootPid = true)
                onDone(state, YLocale.text(R.string.ynfc_sim_stop_superseded))
                return@execute
            }
            state = waitForCommandCompletion(generation, null, apply = false, timeoutMs = 6_000)
            if (!SimulationResultPolicy.isStopSuccess(state, generation) &&
                runtimeRepository.isCurrentCommandGeneration(generation)
            ) {
                val currentPid = nfcSystemService.currentNfcPid().toIntOrNull() ?: state.currentPid
                configClient.confirmStockRestart(generation, currentPid)
                state = runtimeRepository.read(includeRootPid = true)
            }

            val message = if (SimulationResultPolicy.isStopSuccess(state, generation)) {
                YLocale.text(R.string.ynfc_sim_stop_restored)
            } else {
                YLocale.text(R.string.ynfc_sim_stop_restart_unconfirmed)
            }
            onDone(state, message)
        }
    }

    /**
     * Explicit user-initiated reload for a stale in-process Hook. This does
     * not restart Android, only com.android.nfc. Serialized with APPLY/STOP.
     * A missing scope is not a valid reason to kill the NFC service.
     */
    fun reloadNfcHook(onDone: (RuntimeStatus, String) -> Unit) {
        executor.execute {
            var state = runtimeRepository.read(includeRootPid = true)
            if (runtimeRepository.simulationEnabled()) {
                onDone(state, YLocale.text(R.string.ynfc_hook_reload_stop_first))
                return@execute
            }
            // Explicit refresh also supports recompiling Hook internals while
            // retaining a stable protocol/HOOK_BUILD=46. The user, not APK
            // installation, decides when to reload the target NFC service.
            val injectableHook = state.currentPid > 0 && state.scopeOk &&
                state.hookInstalled && state.hookPid == state.currentPid &&
                state.hookBuild > 0
            if (!injectableHook) {
                onDone(state, YLocale.text(R.string.ynfc_sim_hook_not_ready))
                return@execute
            }
            val previousPid = state.currentPid
            val result = nfcSystemService.restartNfcProcessKeepingEnabled(
                "user_requested_hook_reload_$expectedHookBuild"
            )
            AppLogger.i("SIMULATION: user requested NFC service Hook reload " +
                "previousPid=$previousPid\\n$result")
            state = waitForHookOnly(12_000)
            onDone(state, if (hookReady(state) && state.currentPid != previousPid) {
                YLocale.text(R.string.ynfc_hook_reload_success)
            } else {
                YLocale.text(R.string.ynfc_hook_reload_failed)
            })
        }
    }

    private fun hookReady(state: RuntimeStatus): Boolean =
        state.hookInstalled && state.scopeOk && state.currentPid > 0 &&
            state.hookPid == state.currentPid && state.hookBuild == expectedHookBuild

    private fun waitForCommandCompletion(
        generation: Long,
        uid: String?,
        apply: Boolean,
        timeoutMs: Long
    ): RuntimeStatus {
        val end = System.currentTimeMillis() + timeoutMs
        var state = RuntimeStatus()
        while (System.currentTimeMillis() < end) {
            state = runtimeRepository.read(includeRootPid = true)
            if (if (apply) SimulationResultPolicy.isApplySuccess(state, generation, uid.orEmpty())
                else SimulationResultPolicy.isStopSuccess(state, generation)) return state
            if (state.commandGeneration == generation && state.commandStatus == "RESTART_REQUIRED" &&
                state.consumedGeneration == generation) return state
            if (state.commandGeneration > generation) return state
            if (state.commandGeneration == generation && state.handledGeneration == generation &&
                state.commandStatus in setOf("FAILED", "NO_OVERRIDE")) return state
            Thread.sleep(100)
        }
        return state
    }

    private fun waitForHookOnly(timeoutMs: Long): RuntimeStatus {
        val end = System.currentTimeMillis() + timeoutMs
        var state = RuntimeStatus()
        while (System.currentTimeMillis() < end) {
            state = runtimeRepository.read(includeRootPid = true)
            if (hookReady(state)) return state
            Thread.sleep(200)
        }
        return state
    }

    private fun statusSummary(state: RuntimeStatus): String =
        RuntimeText.statusSummary(state, expectedHookBuild, readModeEnabled = false)

    override fun close() {
        executor.shutdownNow()
    }
}
