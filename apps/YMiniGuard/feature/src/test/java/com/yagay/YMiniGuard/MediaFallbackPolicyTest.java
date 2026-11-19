package com.yagay.YMiniGuard;

import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class MediaFallbackPolicyTest {
    @Test public void selectedOos17NullStateSessionPlayingAudioCreatesBasicCard() {
        assertTrue(MediaFallbackPolicy.shouldActivate(1, true));
    }

    @Test public void inactiveAudioMustNotDisplayPlayingCard() {
        assertFalse(MediaFallbackPolicy.shouldActivate(1, false));
    }

    @Test public void multipleEligibleAppsDoNotBorrowEachOthersPlayback() {
        assertFalse(MediaFallbackPolicy.shouldActivate(2, true));
    }

    @Test public void noEligibleAppMustNotCreateMediaCard() {
        assertFalse(MediaFallbackPolicy.shouldActivate(0, true));
    }
}
