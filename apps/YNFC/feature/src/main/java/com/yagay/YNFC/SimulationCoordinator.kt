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
    fun simulate(card: CardModel, backend: String, onDone: (RuntimeStatus, String) -> Unit) {
        executor.execute {
            val generation = configClient.publishCommand(enabled = true, card = card, backend = backend)
            if (generation <= 0L) {
                onDone(runtimeRepository.read(includeRootPid = true),
                    YLocale.text(R.string.ynfc_sim_command_superseded))
                return@execute
            }
            AppLogger.i("SIMULATION: serial COMMAND APPLY generation=$generation uid=${card.uid} backend=$backend")
            var state = runtimeRepository.read(includeRootPid = true)
            if (!hookReady(state)) {
                // An NFC process may be restarting independently. Let the
                // current process report its Hook before considering a reload.
                state = waitForHookOnly(3_000)
                if (!hookReady(state) && runtimeRepository.isCurrentCommandGeneration(generation)) {
                    // Only an installed but old Hook needs a one-time NFC
                    // service reload after an APK upgrade. Missing injection
                    // must not repeatedly kill NFC on every Apply.
                    val oldHookInCurrentProcess = state.currentPid > 0 &&
                        state.hookPid == state.currentPid &&
                        state.hookInstalled && state.hookBuild != expectedHookBuild
                    if (oldHookInCurrentProcess) {
                        val restart = nfcSystemService.restartNfcProcessKeepingEnabled(
                            "upgrade_hook_build_$expectedHookBuild"
                        )
                        AppLogger.i("SIMULATION: reload outdated NFC Hook once generation=$generation\\n$restart")
                        state = waitForHookOnly(12_000)
                    } else {
                        AppLogger.i("SIMULATION: no automatic NFC restart: Hook absent/transitional " +
                            "generation=$generation pid=${state.currentPid} hookPid=${state.hookPid}")
                    }
                }
            }

            if (hookReady(state)) {
                state = waitForCommandCompletion(generation, card.uid, apply = true, timeoutMs = 12_000)
            }
            val message = when {
                state.commandGeneration != generation -> YLocale.text(R.string.ynfc_sim_command_superseded)
                !hookReady(state) -> YLocale.text(R.string.ynfc_sim_hook_not_ready)
                SimulationResultPolicy.isApplySuccess(state, generation, card.uid) ->
                    YLocale.text(R.string.ynfc_sim_apply_success, card.uid)
                state.commandStatus == "VENDOR_API_ACCEPTED" ->
                    YLocale.text(R.string.ynfc_vendor_api_accepted)
                state.commandStatus == "FAILED" &&
                    state.verificationConfidence == "TYPE_A_PERMISSION_DENIED" ->
                    YLocale.text(R.string.ynfc_vendor_permission_denied)
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

            if (SimulationResultPolicy.isStopNoOverride(state, generation)) {
                AppLogger.i("SIMULATION: STOP no UID override; NFC process kept alive generation=$generation")
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
            if (apply && state.commandGeneration == generation &&
                state.commandStatus == "VENDOR_API_ACCEPTED") return state
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
