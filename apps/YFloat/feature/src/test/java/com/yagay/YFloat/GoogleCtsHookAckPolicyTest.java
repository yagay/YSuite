package com.yagay.YFloat;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class GoogleCtsHookAckPolicyTest {
    @Test public void noHookAckReportsPendingForCurrentOwnedSession() {
        assertTrue(GoogleCtsHookAckPolicy.shouldReportPending(true,"token-b",""));
        assertTrue(GoogleCtsHookAckPolicy.shouldReportPending(true,"token-b","token-a"));
    }

    @Test public void validCurrentAckNeedsNoStartupWarning() {
        assertFalse(GoogleCtsHookAckPolicy.shouldReportPending(true,"token-b","token-b"));
    }

    @Test public void staleDiagnosticIsNeverAttributedToAnotherSession() {
        assertFalse(GoogleCtsHookAckPolicy.shouldReportPending(false,"token-b",""));
        assertFalse(GoogleCtsHookAckPolicy.shouldReportPending(true,"",""));
        assertFalse(GoogleCtsHookAckPolicy.shouldReportPending(true,null,""));
    }
}
