package com.yagay.YMiniGuard;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class LockScreenPolicyTest {
    @Test public void backgroundOnlyNeverRetainsVisibleWindowOnKeyguard() {
        assertFalse(LockScreenPolicy.keepWindowVisible(
                false, true, false, false, false, false));
        assertFalse(LockScreenPolicy.keepWindowVisible(
                false, true, true, true, true, true));
    }

    @Test public void backgroundListTakesPriorityWhenBothListsContainApp() {
        assertFalse(LockScreenPolicy.keepWindowVisible(
                true, true, true, true, true, true));
    }

    @Test public void foregroundOplusWindowKeepsExistingProtection() {
        assertTrue(LockScreenPolicy.keepWindowVisible(
                true, false, true, false, false, false));
        assertTrue(LockScreenPolicy.keepWindowVisible(
                true, false, false, true, false, false));
        assertFalse(LockScreenPolicy.keepWindowVisible(
                true, false, false, false, false, false));
    }

    @Test public void sleepingUiAlwaysPassesThroughSystemPause() {
        assertTrue(LockScreenPolicy.allowSystemPause(true));
        assertFalse(LockScreenPolicy.allowSystemPause(false));
    }
}
