package com.yagay.YNFC.xposed;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.android.nfc.NfcDiscoveryParameters;

import org.junit.Test;

public class NfcPollingGuardTest {
    @Test public void onlyNonzeroPollingMaskNeedsChange() {
        assertTrue(NfcPollingGuard.shouldReplace(true, 0x2f));
        assertFalse(NfcPollingGuard.shouldReplace(false, 0x2f));
        assertFalse(NfcPollingGuard.shouldReplace(true, 0));
    }

    @Test public void cloneDisablesPollingWithoutMutatingHceOrOemObject() throws Exception {
        NfcDiscoveryParameters original = new NfcDiscoveryParameters();
        NfcDiscoveryParameters result =
                (NfcDiscoveryParameters) NfcPollingGuard.withoutPolling(original);
        assertNotSame(original, result);
        assertEquals(0x2f, original.getTechMask());
        assertEquals(0, result.getTechMask());
        assertEquals(original.shouldEnableHostRouting(), result.shouldEnableHostRouting());
        assertTrue(result.shouldEnableHostRouting());
        assertEquals(original.shouldEnableReaderMode(), result.shouldEnableReaderMode());
        assertEquals(original.shouldEnableLowPowerDiscovery(), result.shouldEnableLowPowerDiscovery());
        assertSame(original.techAPollingLoopAnnotation(), result.techAPollingLoopAnnotation());
    }

    @Test public void alreadyBlockedParametersAreReusedWithoutCopies() throws Exception {
        NfcDiscoveryParameters first = new NfcDiscoveryParameters();
        NfcDiscoveryParameters blocked = (NfcDiscoveryParameters) NfcPollingGuard.withoutPolling(first);
        assertSame(blocked, NfcPollingGuard.withoutPolling(blocked));
    }

    @Test(expected = IllegalArgumentException.class)
    public void unrelatedParameterObjectIsRejected() throws Exception {
        NfcPollingGuard.withoutPolling(new Object());
    }
}
