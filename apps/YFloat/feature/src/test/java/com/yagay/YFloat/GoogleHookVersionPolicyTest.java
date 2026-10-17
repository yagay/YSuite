package com.yagay.YFloat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class GoogleHookVersionPolicyTest {
    @Test public void identityChangesWhenGoogleBuildOrUpdateChanges() {
        assertEquals("17.64/1764@123", GoogleHookVersionPolicy.identity("17.64", 1764, 123));
        assertTrue(GoogleHookVersionPolicy.changed("17.64/1764@123", "17.64/1764@456"));
        assertTrue(GoogleHookVersionPolicy.changed("17.64/1764@123", "17.65/1765@789"));
    }

    @Test public void initialObservationAndUnchangedVersionDoNotRequireReload() {
        assertFalse(GoogleHookVersionPolicy.changed("", "17.64/1764@123"));
        assertFalse(GoogleHookVersionPolicy.changed("17.64/1764@123", "17.64/1764@123"));
        assertFalse(GoogleHookVersionPolicy.changed(null, "17.64/1764@123"));
        assertFalse(GoogleHookVersionPolicy.changed("17.64/1764@123", ""));
    }
    @Test public void use1760OnlyAsFixedProfileAndScanAllOtherVersions() {
        assertEquals(GoogleHookVersionPolicy.Compatibility.BASELINE_1760,
                GoogleHookVersionPolicy.compatibility("17.60.16.ve.arm64"));
        assertEquals(GoogleHookVersionPolicy.Compatibility.EXPERIMENTAL,
                GoogleHookVersionPolicy.compatibility("17.64.15.ve.arm64"));
        assertEquals(GoogleHookVersionPolicy.Compatibility.EXPERIMENTAL,
                GoogleHookVersionPolicy.compatibility("17.58.16.ve"));
        assertEquals(GoogleHookVersionPolicy.Compatibility.EXPERIMENTAL,
                GoogleHookVersionPolicy.compatibility(null));
    }
    @org.junit.Test public void manualScanTimeoutIsBounded() {
        org.junit.Assert.assertFalse(GoogleHookVersionPolicy.scanTimedOut(100, 300, 201));
        org.junit.Assert.assertTrue(GoogleHookVersionPolicy.scanTimedOut(100, 301, 201));
        org.junit.Assert.assertTrue(GoogleHookVersionPolicy.scanTimedOut(100, 10, 201));
        org.junit.Assert.assertTrue(GoogleHookVersionPolicy.scanTimedOut(0, 100, 201));
    }

}
