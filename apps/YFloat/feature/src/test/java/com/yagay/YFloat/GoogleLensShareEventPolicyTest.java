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

    @Test public void onlyProjectedLiveTextMayAnchorShareToolbar() {
        String verified="profile=17.66 source=state.g selectedWords=3"
                +" space=STABLE_DECOR local=Rect(1,2 - 9,20)"
                +" screen=Rect(350,680 - 490,710)"
                +" selectedChars=3 liveFilter=accepted";
        assertTrue(GoogleLensShareEventPolicy.mayAnchorTextMenu(true,verified));
        assertFalse(GoogleLensShareEventPolicy.mayAnchorTextMenu(true,
                "stage=before metadata=dwpz"));
        assertFalse(GoogleLensShareEventPolicy.mayAnchorTextMenu(true,
                "liveFilter=accepted region=Rect(1,2 - 9,20)"));
        assertTrue(GoogleLensShareEventPolicy.mayAnchorTextMenu(false,null));
    }

    @Test public void nativeSelectedTextFinishesWorkflowEvenAfterGoogleExit() {
        assertTrue(GoogleLensShareEventPolicy.workflowSucceeded(false,true));
        assertTrue(GoogleLensShareEventPolicy.workflowSucceeded(true,false));
        assertFalse(GoogleLensShareEventPolicy.workflowSucceeded(false,false));
    }
}
