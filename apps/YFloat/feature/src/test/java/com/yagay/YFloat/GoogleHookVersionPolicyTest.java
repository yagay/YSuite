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
}
