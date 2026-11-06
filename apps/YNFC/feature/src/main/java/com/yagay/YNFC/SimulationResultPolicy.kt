package com.yagay.YNFC

/** Pure terminal proof rules shared by command waiting and completion messages. */
internal object SimulationResultPolicy {
    fun isApplySuccess(state: RuntimeStatus, generation: Long, uid: String): Boolean =
        state.commandGeneration == generation && state.handledGeneration == generation &&
            state.commandStatus == "SUCCESS" && state.currentPid > 0 && state.commandPid == state.currentPid &&
            state.rfGeneration == generation && state.rfPid == state.currentPid && state.operationState == "IDLE" &&
            state.effectiveState == "ACTIVE" && state.verificationConfidence == "VERIFIED" &&
            state.rfAccepted && state.rfUid.equals(uid, ignoreCase = true)

    /** STOP completed without writing a UID in this NFC process. This is NOT RF stock proof. */
    fun isStopNoOverride(state: RuntimeStatus, generation: Long): Boolean =
        !state.simulationEnabled && state.commandGeneration == generation &&
            state.handledGeneration == generation && state.commandStatus == "NO_OVERRIDE" &&
            state.commandAction == "STOP" && state.currentPid > 0 &&
            state.commandPid == state.currentPid &&
            state.operationState == "IDLE" && !state.rfAccepted &&
            state.verificationConfidence == "NO_MODIFICATION"

    fun isStopSuccess(state: RuntimeStatus, generation: Long): Boolean {
        val common = state.commandGeneration == generation && state.handledGeneration == generation &&
            state.commandStatus == "SUCCESS" && state.currentPid > 0 && state.commandPid == state.currentPid &&
            state.rfGeneration == generation && state.rfPid == state.currentPid
        return common && state.operationState == "IDLE" && state.effectiveState == "STOCK" &&
            state.verificationConfidence == "VERIFIED" && state.rfAccepted
    }
}
