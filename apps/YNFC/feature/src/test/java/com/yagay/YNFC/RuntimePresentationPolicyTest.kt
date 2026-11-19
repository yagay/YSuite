package com.yagay.YNFC

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimePresentationPolicyTest {
    private fun active(): Map<String, String> = mapOf(
        "simulation_enabled" to "true", "simulation_backend" to "RF",
        "command_action" to "APPLY", "command_status" to "UNVERIFIED",
        "command_generation" to "100", "command_handled_generation" to "100",
        "rf_source" to "own-nci-0x33", "uid" to "C1B0BC1B",
        "own_nci_lifecycle_stage" to "WRITE_REQUESTED_RF_UNVERIFIED",
        "own_nci_lifecycle_generation" to "100",
        "own_nci_lifecycle_pid" to "5810",
        "own_nci_lifecycle_epoch" to "456",
        "own_nci_lifecycle_at" to "10000",
        "rf_pid" to "23591", "rf_controller_epoch" to "400",
    )
    @Test fun currentLifecycleRequestCanBeShownWithoutRevivingOldProof() {
        assertTrue(RuntimePresentationPolicy.currentUidRestore(active(), 5810, 456L))
        assertFalse(RuntimePresentationPolicy.currentUidRestore(active(), 5810, 457L))
        assertFalse(RuntimePresentationPolicy.currentUidRestore(active(), 1000, 456L))
    }
    @Test fun stopChangeOrFailedRequestCannotAppearRecovered() {
        assertFalse(RuntimePresentationPolicy.currentUidRestore(
            active() + ("simulation_enabled" to "false"), 5810, 456L))
        assertFalse(RuntimePresentationPolicy.currentUidRestore(
            active() + ("command_generation" to "101"), 5810, 456L))
        assertFalse(RuntimePresentationPolicy.currentUidRestore(
            active() + ("command_status" to "FAILED"), 5810, 456L))
        assertFalse(RuntimePresentationPolicy.currentUidRestore(
            active() + ("own_nci_lifecycle_stage" to "WAITING_FOR_NFC_ON"), 5810, 456L))
        assertFalse(RuntimePresentationPolicy.currentUidRestore(
            active() + ("rf_source" to "other"), 5810, 456L))
    }
    @Test fun absentPollingHookIsNotMisreportedAsEffective() {
        assertTrue(RuntimePresentationPolicy.hasPollingHook(
            "compute=true;driverHooks=0;routeRefresh=true"))
        assertTrue(RuntimePresentationPolicy.hasPollingHook(
            "compute=false;driverHooks=1;routeRefresh=true"))
        assertFalse(RuntimePresentationPolicy.hasPollingHook(
            "compute=false;driverHooks=0;routeRefresh=true"))
        assertEquals("UNSUPPORTED_NO_DISCOVERY_HOOKS",
            RuntimePresentationPolicy.pollingStatus(true, "WAITING_FOR_NFC_ON",
                "compute=false;driverHooks=0;routeRefresh=true"))
        assertEquals("WAITING_FOR_NFC_ON",
            RuntimePresentationPolicy.pollingStatus(false, "WAITING_FOR_NFC_ON",
                "compute=false;driverHooks=0;routeRefresh=true"))
        // Controller reset is not proof that the old 0x0 mask remains active.
        assertEquals("AWAITING_DISCOVERY",
            RuntimePresentationPolicy.pollingStatus(true, "AWAITING_DISCOVERY",
                "compute=true;driverHooks=1;routeRefresh=true"))
        assertEquals("STALE(PARAMETERS_INTERCEPTED)",
            RuntimePresentationPolicy.pollingStatus(true, "STALE(PARAMETERS_INTERCEPTED)",
                null))
    }
}
