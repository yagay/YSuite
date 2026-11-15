package com.yagay.YMiniGuard;

import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class YMiniMediaLaunchPolicyTest {
    @Test public void oxygenOsSystemUiLauncherWithoutResetRoutes() {
        assertTrue(YMiniMediaLaunchPolicy.shouldRedirect(
                true, true, true, true, false, "com.android.systemui"));
    }

    @Test public void missingCrossUidIdentityUsesOnlyStrictFlags() {
        assertTrue(YMiniMediaLaunchPolicy.shouldRedirect(
                true, true, true, true, false, null));
    }

    @Test public void ordinaryHomeScreenLauncherNeverRedirects() {
        assertFalse(YMiniMediaLaunchPolicy.shouldRedirect(
                true, true, true, true, true, "com.android.launcher"));
        assertFalse(YMiniMediaLaunchPolicy.shouldRedirect(
                true, true, true, true, true, null));
    }

    @Test public void otherAppIntentNeverRedirects() {
        assertFalse(YMiniMediaLaunchPolicy.shouldRedirect(
                true, true, true, true, false, "com.example.other"));
        assertFalse(YMiniMediaLaunchPolicy.shouldRedirect(
                false, true, true, true, false, "com.android.systemui"));
        assertFalse(YMiniMediaLaunchPolicy.shouldRedirect(
                true, true, false, true, false, "com.android.systemui"));
    }
}
