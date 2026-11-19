package com.yagay.YNFC

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SimulationResultPolicyTest {
    private fun verifiedState(effective: String, uid: String? = null) = RuntimeStatus(
        currentPid = 1027,
        commandGeneration = 10,
        handledGeneration = 10,
        commandStatus = "SUCCESS",
        commandPid = 1027,
        operationState = "IDLE",
        effectiveState = effective,
        verificationConfidence = "VERIFIED",
        rfAccepted = true,
        rfUid = uid,
        rfPid = 1027,
        rfGeneration = 10
    )

    @Test fun applyRequiresMatchingUidAndCurrentPidProof() {
        val state = verifiedState("ACTIVE", "04A1B2C3")
        assertTrue(SimulationResultPolicy.isApplySuccess(state, 10, "04a1b2c3"))
        assertFalse(SimulationResultPolicy.isApplySuccess(state.copy(rfPid = 999), 10, "04A1B2C3"))
        assertFalse(SimulationResultPolicy.isApplySuccess(state, 10, "DEADBEEF"))
    }

    @Test fun stopWithoutNativeUidWriteDoesNotClaimRfStockVerification() {
        val state = RuntimeStatus(
            currentPid = 1234,
            commandGeneration = 25,
            handledGeneration = 25,
            commandPid = 1234,
            commandAction = "STOP",
            commandStatus = "NO_OVERRIDE",
            operationState = "IDLE",
            verificationConfidence = "NO_MODIFICATION",
            rfStatus = "NO_OVERRIDE",
            rfSource = "no-uid-write",
            rfVerification = "NO_UID_WRITE",
            rfGeneration = 25,
            rfPid = 1234,
            rfAccepted = false,
            simulationEnabled = false
        )
        assertTrue(SimulationResultPolicy.isStopNoOverride(state, 25))
        // Regression: controller lifecycle markers must never restart NFC
        // after the in-process Hook has already acknowledged no UID write.
        assertTrue(SimulationResultPolicy.isStopNoOverride(
            state.copy(operationState = "REVERIFY_PENDING"), 25))
        assertFalse(SimulationResultPolicy.isStopSuccess(state, 25))
        // Replaced process means NFC has already restarted; don't restart again.
        assertTrue(SimulationResultPolicy.isStopNoOverride(state.copy(currentPid = 1235), 25))
        assertFalse(SimulationResultPolicy.isStopNoOverride(state.copy(commandPid = 0), 25))
        assertFalse(SimulationResultPolicy.isStopNoOverride(state.copy(simulationEnabled = true), 25))
        assertFalse(SimulationResultPolicy.isStopNoOverride(state.copy(rfAccepted = true), 25))
        assertFalse(SimulationResultPolicy.isStopNoOverride(state.copy(commandStatus = "FAILED"), 25))
        assertFalse(SimulationResultPolicy.isStopNoOverride(state.copy(rfGeneration = 24), 25))
        assertFalse(SimulationResultPolicy.isStopNoOverride(state.copy(rfPid = 4321), 25))
        assertFalse(SimulationResultPolicy.isStopNoOverride(state.copy(rfSource = null), 25))
        assertFalse(SimulationResultPolicy.isStopNoOverride(state.copy(rfVerification = null), 25))
    }

    @Test fun stopRequiresVerifiedStockProof() {
        val state = verifiedState("STOCK")
        assertTrue(SimulationResultPolicy.isStopSuccess(state, 10))
        assertFalse(SimulationResultPolicy.isStopSuccess(state.copy(verificationConfidence = "PENDING"), 10))
        assertFalse(SimulationResultPolicy.isStopSuccess(state.copy(effectiveState = "ACTIVE"), 10))
    }
}
