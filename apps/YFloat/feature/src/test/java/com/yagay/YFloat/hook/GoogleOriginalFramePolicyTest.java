package com.yagay.YFloat.hook;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class GoogleOriginalFramePolicyTest {
    @Test public void acceptsGoogleScreenSizedSources() {
        assertTrue(GoogleOriginalFramePolicy.matchesFrozenView(1272, 2772, 1272, 2772));
        assertTrue(GoogleOriginalFramePolicy.matchesFrozenView(636, 1386, 1272, 2772));
    }

    @Test public void rejectsIconsThumbnailAndOversizedSources() {
        assertFalse(GoogleOriginalFramePolicy.matchesFrozenView(100, 100, 1272, 2772));
        assertFalse(GoogleOriginalFramePolicy.matchesFrozenView(1272, 1000, 1272, 2772));
        assertFalse(GoogleOriginalFramePolicy.validDecodedSize(12000, 8000));
    }
}
