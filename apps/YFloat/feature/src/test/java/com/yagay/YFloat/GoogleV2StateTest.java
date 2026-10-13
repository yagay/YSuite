package com.yagay.YFloat;

import static org.junit.Assert.*;
import org.junit.Test;

public class GoogleV2StateTest {
    @Test public void textAndRegionModesAreExclusive() {
        GoogleV2State s = new GoogleV2State();
        assertTrue(s.text("Hello", 10,20,70,40));
        assertEquals(GoogleV2State.Phase.TEXT,s.phase);
        assertTrue(s.region(10,20,90,100));
        assertEquals("",s.text);
        assertEquals(GoogleV2State.Phase.REGION,s.phase);
    }
    @Test public void screenshotWaitsForOriginalFrameAndCommitsOnce() {
        GoogleV2State s = new GoogleV2State();
        assertTrue(s.region(10,20,300,400));
        assertTrue(s.confirm());
        assertFalse(s.canDeliver());
        s.frameReady = true;
        assertTrue(s.canDeliver());
        assertTrue(s.delivered());
        assertFalse(s.confirm());
        assertFalse(s.delivered());
        assertFalse(s.region(0,0,100,100));
    }
    @Test public void oldAsyncWorkCannotOverrideLatestSelection() {
        GoogleV2State s = new GoogleV2State();
        s.text("a",1,2,10,20);
        int revision=s.revision;
        assertTrue(s.drag(true));
        s.text("ab",1,2,25,20);
        s.drag(false);
        assertTrue(s.revision>revision);
        assertEquals("ab",s.text);
    }
}
