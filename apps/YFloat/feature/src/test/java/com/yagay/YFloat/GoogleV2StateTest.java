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
    @Test public void lateOcrCannotTurnRegionSelectionIntoTextSearch() {
        GoogleV2State s=new GoogleV2State();
        assertTrue(s.text("OCR before draw",100,200,320,260));
        assertTrue(s.region(40,80,400,580));
        assertFalse(s.text("late Google OCR",110,215,330,270));
        assertEquals(GoogleV2State.Phase.REGION,s.phase);
        assertEquals("",s.text);
        assertEquals(40,s.left);
        assertEquals(580,s.bottom);
        assertTrue(s.confirm());
    }
    @Test public void drawingNewRegionClearsOldDraggingMode() {
        GoogleV2State s=new GoogleV2State();
        assertTrue(s.text("word",100,200,300,250));
        s.drag(true);
        assertTrue(s.region(10,20,400,560));
        assertFalse(s.dragging);
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
