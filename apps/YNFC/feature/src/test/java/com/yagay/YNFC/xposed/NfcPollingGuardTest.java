package com.yagay.YNFC.xposed;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.android.nfc.NfcDiscoveryParameters;

import org.junit.Test;

public class NfcPollingGuardTest {
    @Test public void android17FieldOnlyParametersBlockReaderPollingAndPreserveHce() throws Exception {
        com.example.nfc.Nfc17FieldOnlyParameters original =
                new com.example.nfc.Nfc17FieldOnlyParameters();
        assertTrue(NfcPollingGuard.supportsType(original.getClass()));
        assertEquals(0x2f, NfcPollingGuard.mask(original));
        assertTrue(NfcPollingGuard.hostRouting(original));
        com.example.nfc.Nfc17FieldOnlyParameters result =
                (com.example.nfc.Nfc17FieldOnlyParameters)
                        NfcPollingGuard.withoutPolling(original);
        assertNotSame(original, result);
        assertEquals(0x2f, original.mTechMask);
        assertEquals(0, result.mTechMask);
        assertEquals(original.mEnableHostRouting, result.mEnableHostRouting);
        assertTrue(result.mEnableHostRouting);
        assertTrue(result.shouldEnableDiscovery());
        assertEquals(original.mEnableReaderMode, result.mEnableReaderMode);
        assertEquals(original.mEnableLowPowerDiscovery, result.mEnableLowPowerDiscovery);
        assertSame(original.mTechAPollingLoopAnnotation, result.mTechAPollingLoopAnnotation);
        assertSame(original.mExtraAnnotation, result.mExtraAnnotation);
        assertSame(result, NfcPollingGuard.withoutPolling(result));
    }

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

    @Test public void builderCopiesParametersEvenWithoutKnownMaskFieldOrConstructor()
            throws Exception {
        com.example.nfc.RenamedDiscoveryParameters original =
                com.example.nfc.RenamedDiscoveryParameters.newBuilder().build();
        com.example.nfc.RenamedDiscoveryParameters result =
                (com.example.nfc.RenamedDiscoveryParameters)
                        NfcPollingGuard.withoutPolling(original);
        assertEquals(0x2f, original.getTechMask());
        assertEquals(0, result.getTechMask());
        assertTrue(result.shouldEnableHostRouting());
        assertEquals(original.shouldEnableReaderMode(), result.shouldEnableReaderMode());
        assertEquals(original.shouldEnableLowPowerDiscovery(),
                result.shouldEnableLowPowerDiscovery());
        assertSame(original.techAPollingLoopAnnotation(), result.techAPollingLoopAnnotation());
    }

    @Test(expected = IllegalArgumentException.class)
    public void unrelatedParameterObjectIsRejected() throws Exception {
        NfcPollingGuard.withoutPolling(new Object());
    }
}
