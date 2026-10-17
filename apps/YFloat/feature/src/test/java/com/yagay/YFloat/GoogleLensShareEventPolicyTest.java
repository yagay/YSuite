package com.yagay.YFloat;

import static org.junit.Assert.*;
import org.junit.Test;

public class GoogleLensShareEventPolicyTest {
    @Test public void normalCtsRetainsItsOriginalRegionAndViewportRules() {
        assertTrue(GoogleLensShareEventPolicy.mayForwardRegion(false));
        assertTrue(GoogleLensShareEventPolicy.mayProtectViewport(false));
    }

    @Test public void sharedImageNeverBecomesFullScreenCtsRegion() {
        assertFalse(GoogleLensShareEventPolicy.mayForwardRegion(true));
        assertFalse(GoogleLensShareEventPolicy.mayProtectViewport(true));
    }
}
