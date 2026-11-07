package com.yagay.YNFC;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class NfcControlPolicyTest {
    @Test public void hceOnlyDisablesPollingButNeverEnablesReader() {
        assertEquals(NfcControlPolicy.Mode.HCE_ONLY, NfcControlPolicy.mode(true));
        assertTrue(NfcControlPolicy.shouldSuppressPolling(
                NfcControlPolicy.Mode.HCE_ONLY, 0x2f));
        assertFalse(NfcControlPolicy.shouldSuppressPolling(
                NfcControlPolicy.Mode.HCE_ONLY, 0));
        assertFalse(NfcControlPolicy.canRead(NfcControlPolicy.Mode.HCE_ONLY, false));
        assertTrue(NfcControlPolicy.canRead(NfcControlPolicy.Mode.NORMAL, false));
        assertFalse(NfcControlPolicy.canRead(NfcControlPolicy.Mode.NORMAL, true));
    }

    @Test public void automaticRoutingRefreshNeverLoopsDiscovery() {
        // The OEM may invoke controller-ready multiple times while NFC is ON.
        assertTrue(NfcControlPolicy.shouldSkipRoutingRefresh(true, 0, true, false));
        assertTrue(NfcControlPolicy.shouldSkipRoutingRefresh(true, -1, true, false));
        assertTrue(NfcControlPolicy.shouldSkipRoutingRefresh(true, 0x2f, true, true));
        assertTrue(NfcControlPolicy.shouldSkipRoutingRefresh(false, 0x2f, true, false));
        assertFalse(NfcControlPolicy.shouldSkipRoutingRefresh(true, 0x2f, true, false));
        // User explicitly toggled the guard: one real routing request remains.
        assertFalse(NfcControlPolicy.shouldSkipRoutingRefresh(false, 0, false, true));
        assertFalse(NfcControlPolicy.shouldSkipRoutingRefresh(true, -1, false, true));
        assertTrue(NfcControlPolicy.shouldSkipRoutingRefresh(true, 0, false, true));
    }

    @Test public void liveChangesCancelStaleRefreshRevisions() {
        NfcControlPolicy policy = new NfcControlPolicy();
        assertTrue(policy.setDesired(true));
        long initial = policy.nextRevision();
        assertTrue(policy.isCurrent(initial));
        assertFalse(policy.setDesired(true));
        assertTrue(policy.setDesired(false));
        policy.invalidateRefresh();
        assertFalse(policy.isCurrent(initial));
        long latest = policy.nextRevision();
        assertTrue(policy.isCurrent(latest));
    }
}
