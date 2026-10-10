package com.yagay.YFloat;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class GoogleOverlayAppearanceTest {
    @Test public void defaultsMatchPriorGoogleCircleAppearance() {
        GoogleOverlayAppearance defaults = GoogleOverlayAppearance.defaults();
        assertEquals(8, defaults.radiusDp);
        assertEquals(1, defaults.outlineDp);
        assertEquals(1, defaults.glowWidthDp);
        assertEquals(20, defaults.glowBlurDp);
        assertEquals(235, defaults.glowAlpha);
        assertEquals(102, defaults.scrimAlpha);
        assertEquals(3, defaults.trailWidthDp);
        assertFalse(defaults.yuiColor);
    }

    @Test public void absentRemotePreferencesRemainSafe() {
        GoogleOverlayAppearance missing = GoogleOverlayAppearance.fromPreferences(null);
        assertEquals(GoogleOverlayAppearance.defaults().radiusDp, missing.radiusDp);
        assertEquals(0xFF4285F4, missing.accentColor);
    }
}
