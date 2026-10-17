package com.yagay.YFloat;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class GoogleV2VisualExitPolicyTest {
    @Test public void backHidesTextAndScreenshotConfirmWithoutWaitingForRelease() {
        assertTrue(GoogleV2VisualExitPolicy.canSuspend(GoogleV2State.Phase.TEXT));
        assertTrue(GoogleV2VisualExitPolicy.canSuspend(GoogleV2State.Phase.REGION));
    }

    @Test public void pendingOrDeliveredScreenshotNeverLosesItsFrameOnNavigation() {
        assertFalse(GoogleV2VisualExitPolicy.canSuspend(
                GoogleV2State.Phase.AWAITING_FRAME));
        assertFalse(GoogleV2VisualExitPolicy.canSuspend(
                GoogleV2State.Phase.DELIVERED));
    }

    @Test public void emptyAndClosedSessionsCannotDisplayAnOverlay() {
        assertFalse(GoogleV2VisualExitPolicy.canSuspend(
                GoogleV2State.Phase.SELECTING));
        assertFalse(GoogleV2VisualExitPolicy.canSuspend(
                GoogleV2State.Phase.CLOSED));
    }
}
