package com.yagay.YMiniGuard;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BackgroundPlaybackPolicyTest {
    @Test public void oxygenOsRecentsThenMakeInvisibleRetainsProtection() {
        assertTrue(BackgroundPlaybackPolicy.shouldRetainArmedProtection(
                true, "makeInvisible"));
        assertTrue(BackgroundPlaybackPolicy.shouldRetainArmedProtection(
                true, "finishTransition"));
        assertTrue(BackgroundPlaybackPolicy.shouldRetainArmedProtection(
                true, "pauseInRecentsAnim"));
        assertTrue(BackgroundPlaybackPolicy.shouldRetainArmedProtection(
                true, "pauseBackTasks"));
        assertTrue(BackgroundPlaybackPolicy.shouldRetainArmedProtection(
                true, "setVisibility"));
    }

    @Test public void unarmedOrNormalSamePackageNavigationIsNotSuppressed() {
        assertFalse(BackgroundPlaybackPolicy.shouldRetainArmedProtection(
                false, "makeInvisible"));
        assertFalse(BackgroundPlaybackPolicy.shouldRetainArmedProtection(
                true, "navigateInApp"));
        assertFalse(BackgroundPlaybackPolicy.shouldRetainArmedProtection(
                true, null));
        assertFalse(BackgroundPlaybackPolicy.isBackgroundTransition(null));
    }

    @Test public void visibilityHideReasonDoesNotConfuseRecentsEvents() {
        assertTrue(BackgroundPlaybackPolicy.isVisibilityHide("makeInvisible"));
        assertTrue(BackgroundPlaybackPolicy.isVisibilityHide("setVisibility"));
        assertFalse(BackgroundPlaybackPolicy.isVisibilityHide("finishTransition"));
        assertFalse(BackgroundPlaybackPolicy.isVisibilityHide("pauseInRecentsAnim"));
    }
}
