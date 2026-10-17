package com.yagay.YFloat;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class GoogleCtsHookAckPolicyTest {
    @Test public void noHookAckMustCloseCurrentOwnedSession() {
        assertTrue(GoogleCtsHookAckPolicy.shouldFailClosed(true,"token-b",""));
        assertTrue(GoogleCtsHookAckPolicy.shouldFailClosed(true,"token-b","token-a"));
    }

    @Test public void validCurrentAckStaysInInterceptedMode() {
        assertFalse(GoogleCtsHookAckPolicy.shouldFailClosed(true,"token-b","token-b"));
    }

    @Test public void staleTimeoutNeverClosesNewOrNormalGoogleSession() {
        assertFalse(GoogleCtsHookAckPolicy.shouldFailClosed(false,"token-b",""));
        assertFalse(GoogleCtsHookAckPolicy.shouldFailClosed(true,"",""));
        assertFalse(GoogleCtsHookAckPolicy.shouldFailClosed(true,null,""));
    }
}
