package com.yagay.YFloat;

import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FlOverlayLayerPolicyTest {
    @Test public void screenshotAndResultSurfacesUseHighLayer() {
        String[] tags = {
            "region_selector", "editable_region", "fl_circle_inline",
            "circle_active_border", "native_circle_workspace",
            "float_action_menu", "probe", "pointer_hint", "gesture_trail",
            "view_hover", "view_candidate_frame", "region_frame",
            "float_icon", "float_icon_handle"
        };
        for (String tag : tags) {
            assertTrue("High-layer surface missing: " + tag,
                    FlOverlayLayerPolicy.elevate(tag, true));
            assertFalse("Opt-out must be honored: " + tag,
                    FlOverlayLayerPolicy.elevate(tag, false));
        }
    }

    @Test public void highLayerPreferenceDoesNotBlackListDeviceModels() {
        assertTrue(FlOverlayLayerPolicy.preferAccessibilityIconHost(true, true, true, false));
        assertFalse(FlOverlayLayerPolicy.preferAccessibilityIconHost(true, true, true, true));
        assertFalse(FlOverlayLayerPolicy.preferAccessibilityIconHost(true, true, false, false));
        assertFalse(FlOverlayLayerPolicy.preferAccessibilityIconHost(false, true, true, false));
        // If 2038 is not permitted, 2032 is still the only viable host.
        assertTrue(FlOverlayLayerPolicy.preferAccessibilityIconHost(true, false, true, true));
        assertTrue(FlOverlayLayerPolicy.preferAccessibilityIconHost(true, false, false, false));
    }

    @Test public void offset2032TouchFrameCausesAutomaticFallback() {
        assertTrue(FlOverlayLayerPolicy.touchOutsideIconBounds(
                719, 1520, 1222, 668, 131, 131));
        assertFalse(FlOverlayLayerPolicy.touchOutsideIconBounds(
                1250, 735, 1222, 668, 131, 131));
        assertFalse(FlOverlayLayerPolicy.touchOutsideIconBounds(
                1206, 735, 1222, 668, 131, 131));
    }

    @Test public void invisibleSystemEdgeWakeStripsNeverRiseAboveSystemUi() {
        assertFalse(FlOverlayLayerPolicy.elevate("edge_wake_left", true));
        assertFalse(FlOverlayLayerPolicy.elevate("edge_wake_right", true));
    }
}
