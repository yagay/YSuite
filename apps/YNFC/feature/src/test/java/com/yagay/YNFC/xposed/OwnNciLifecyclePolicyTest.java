package com.yagay.YNFC.xposed;

import org.junit.Test;
import static org.junit.Assert.*;

public class OwnNciLifecyclePolicyTest {
    private static SimConfig state(boolean active, String action, String status,
                                   long generation, long handled, long epoch) {
        return new SimConfig(true, active, "C1B0BC1B", "08", "0004", "RF", true,
                generation, generation, handled, action, status, 100, epoch);
    }
    private static HookConfigStore.OwnNciRfEvidence evidence(
            String source, long rfEpoch, int pid) {
        return new HookConfigStore.OwnNciRfEvidence(source, rfEpoch, pid);
    }

    @Test public void reapplyOnceAfterOffOnWithSameNfcPid() {
        SimConfig s = state(true, "APPLY", "UNVERIFIED", 10, 10, 202);
        assertTrue(OwnNciLifecyclePolicy.shouldRecover(s,
                evidence("own-nci-0x33", 201, 33), 33, 1000, 900, 10));
        assertFalse(OwnNciLifecyclePolicy.shouldRecover(s,
                evidence("own-nci-0x33", 201, 33), 33, 1000, 1000, 10));
    }

    @Test public void rebootWithNewPidNeedsOneRestore() {
        SimConfig s = state(true, "APPLY", "UNVERIFIED", 10, 10, 202);
        assertTrue(OwnNciLifecyclePolicy.shouldRecover(s,
                evidence("own-nci-0x33", 201, 19), 33, 1000, 0, -1));
    }

    @Test public void discoveryAndStopCannotTriggerWrite() {
        SimConfig active = state(true, "APPLY", "UNVERIFIED", 10, 10, 202);
        assertFalse(OwnNciLifecyclePolicy.shouldRecover(active,
                evidence("own-nci-0x33", 202, 33), 33, 1000, 900, 10));
        assertFalse(OwnNciLifecyclePolicy.shouldRecover(
                state(false, "STOP", "SUCCESS", 11, 11, 202),
                evidence("own-nci-0x33", 201, 33), 33, 1000, 0, -1));
    }

    @Test public void failedAndOtherSourcesDoNotRecover() {
        assertFalse(OwnNciLifecyclePolicy.shouldRecover(
                state(true, "APPLY", "FAILED", 10, 10, 202),
                evidence("own-nci-0x33", 201, 33), 33, 1000, 0, -1));
        assertFalse(OwnNciLifecyclePolicy.shouldRecover(
                state(true, "APPLY", "UNVERIFIED", 10, 10, 202),
                evidence("other", 201, 33), 33, 1000, 0, -1));
    }

    @Test public void stopAndNewUidSupersedeScheduledRecovery() {
        SimConfig prior = state(true, "APPLY", "UNVERIFIED", 10, 10, 202);
        assertTrue(OwnNciLifecyclePolicy.sameDesiredCommand(prior, prior));
        assertFalse(OwnNciLifecyclePolicy.sameDesiredCommand(prior,
                state(false, "STOP", "SUCCESS", 11, 11, 202)));
        assertFalse(OwnNciLifecyclePolicy.sameDesiredCommand(prior,
                state(true, "APPLY", "UNVERIFIED", 12, 12, 202)));
        assertFalse(OwnNciLifecyclePolicy.sameDesiredCommand(prior,
                state(true, "APPLY", "UNVERIFIED", 10, 10, 203)));
    }
}
