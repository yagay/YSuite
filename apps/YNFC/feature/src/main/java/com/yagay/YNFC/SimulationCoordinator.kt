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

    fun simulate(card: CardModel, onDone: (RuntimeStatus, String) -> Unit) {
        val generation = configClient.publishCommand(enabled = true, card = card)
        AppLogger.i("SIMULATION: COMMAND APPLY published generation=$generation uid=${card.uid} sak=${card.sak} atqa=${card.atqa}")
        executor.execute {
            var state = runtimeRepository.read(includeRootPid = true)
            if (!state.hookInstalled || state.hookBuild != expectedHookBuild) {
                val restart = nfcSystemService.restartNfcProcessKeepingEnabled("load_hook_build_$expectedHookBuild")
                AppLogger.i("SIMULATION: restarting stale/unavailable hook for command generation=$generation\n$restart")
                state = waitForHookOnly(12_000)
            }
            state = waitForCommandCompletion(generation, card.uid, apply = true, timeoutMs = 12_000)
            val message = when {
                SimulationResultPolicy.isApplySuccess(state, generation, card.uid) ->
                    YLocale.text(R.string.ynfc_sim_apply_success, card.uid)
                !state.hookInstalled -> YLocale.text(R.string.ynfc_sim_hook_not_ready)
                state.commandGeneration != generation -> YLocale.text(R.string.ynfc_sim_command_superseded)
                state.commandStatus == "FAILED" || state.commandStatus == "TRIGGER_FAILED" ->
                    YLocale.text(
                        R.string.ynfc_sim_apply_failure,
                        state.commandStatus,
                        state.commandDetail ?: state.rfError ?: "unknown"
                    )
                else -> YLocale.text(R.string.ynfc_sim_waiting_rf)
            }
            AppLogger.i("SIMULATION: COMMAND result generation=$generation message=$message\n${statusSummary(state)}")
            onDone(state, message)
        }
    }

    fun stop(onDone: (RuntimeStatus, String) -> Unit) {
        val generation = configClient.publishCommand(enabled = false, card = null)
        AppLogger.i("SIMULATION: COMMAND STOP published generation=$generation")
        executor.execute {
            var state = waitForCommandCompletion(generation, null, apply = false, timeoutMs = 6_000)
            if (SimulationResultPolicy.isStopSuccess(state, generation)) {
                AppLogger.i("SIMULATION: STOP success without restart generation=$generation\n${statusSummary(state)}")
                onDone(state, YLocale.text(R.string.ynfc_sim_stop_lifecycle_restored))
                return@execute
            }

            AppLogger.i("SIMULATION: STOP handoff snapshot before fallback generation=$generation\n${statusSummary(state)}\nPROVIDER=${runtimeRepository.readProviderMap().toSortedMap()}")
            if (!runtimeRepository.isCurrentCommandGeneration(generation)) {
                state = runtimeRepository.read(includeRootPid = true)
                AppLogger.i("SIMULATION: STOP fallback cancelled because generation=$generation is no longer current")
                onDone(state, YLocale.text(R.string.ynfc_sim_stop_superseded))
                return@execute
            }
            val restart = nfcSystemService.restartNfcProcessKeepingEnabled("stop_command_fallback_generation_$generation")
            AppLogger.i("SIMULATION: STOP fallback NFC restart generation=$generation\n$restart")
            waitForHookOnly(12_000)
            state = waitForCommandCompletion(generation, null, apply = false, timeoutMs = 6_000)

            if (!SimulationResultPolicy.isStopSuccess(state, generation) && runtimeRepository.isCurrentCommandGeneration(generation)) {
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
            if (state.commandGeneration == generation && state.handledGeneration == generation &&
                state.commandStatus == "FAILED") return state
            Thread.sleep(100)
        }
        return state
    }

    private fun waitForHookOnly(timeoutMs: Long): RuntimeStatus {
        val end = System.currentTimeMillis() + timeoutMs
        var state = RuntimeStatus()
        while (System.currentTimeMillis() < end) {
            state = runtimeRepository.read(includeRootPid = true)
            if (state.hookInstalled && state.hookBuild == expectedHookBuild) return state
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
