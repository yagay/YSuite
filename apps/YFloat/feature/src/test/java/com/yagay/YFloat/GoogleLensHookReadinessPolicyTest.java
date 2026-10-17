package com.yagay.YFloat;

import static org.junit.Assert.*;
import org.junit.Test;

public class GoogleLensHookReadinessPolicyTest {
    @Test public void blockAlreadyRunningOutdatedGoogleProcess() {
        assertTrue(GoogleLensHookReadinessPolicy.requiresRestart(true,true,true));
    }

    @Test public void permitCurrentGoogleProcess() {
        assertFalse(GoogleLensHookReadinessPolicy.requiresRestart(true,true,false));
    }

    @Test public void permitColdGoogleStartBeforeHookRegistration() {
        assertFalse(GoogleLensHookReadinessPolicy.requiresRestart(true,false,false));
    }

    @Test public void reportMissingScopeToExistingArmingPath() {
        assertFalse(GoogleLensHookReadinessPolicy.requiresRestart(false,false,false));
    }
}
