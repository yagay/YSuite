package com.yagay.YFloat.hook;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class GoogleTextViewportPolicyTest {
    @Test public void locksObservedTextZoomAndTranslation() {
        assertTrue(GoogleTextViewportPolicy.shouldNormalize(
                true, 0.9150236f, 0.9150236f, 0f, -708.131f));
        assertTrue(GoogleTextViewportPolicy.shouldNormalize(
                true, 1f, 1f, 0f, -96.8f));
    }

    @Test public void doesNotTouchRegionOrUnmarkedSessions() {
        assertFalse(GoogleTextViewportPolicy.shouldNormalize(
                false, 0.915f, 0.915f, 0f, -708.131f));
        assertFalse(GoogleTextViewportPolicy.shouldNormalize(
                true, 1f, 1f, 0f, 0f));
        assertFalse(GoogleTextViewportPolicy.shouldNormalize(
                true, 1.0001f, 1f, 0.1f, -0.2f));
    }
}
