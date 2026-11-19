package com.yagay.YFloat;

import org.junit.Test;
import static org.junit.Assert.*;

public final class OcrV3LocalRegionTest {
    @Test public void expandsRealMlWordOnAllFourSides(){
        OcrV3LocalRegion r=OcrV3LocalRegion.plan(10,20,90,40,300,300);
        assertNotNull(r);
        assertTrue(r.left<10);assertTrue(r.top<20);
        assertTrue(r.right>90);assertTrue(r.bottom>40);
    }
    @Test public void keepsWordAtScreenshotEdgesInsideBitmap(){
        OcrV3LocalRegion r=OcrV3LocalRegion.plan(0,0,30,16,40,25);
        assertNotNull(r);
        assertEquals(0,r.left);assertEquals(0,r.top);
        assertTrue(r.right<=40);assertTrue(r.bottom<=25);
    }
    @Test public void detectsInvalidCoordinates(){
        assertNull(OcrV3LocalRegion.plan(10,5,8,20,40,40));
        assertNull(OcrV3LocalRegion.plan(0,0,20,20,1,1));
    }
    @Test public void cropIsBoundedWithoutStretchingTheOriginalScreenshot(){
        OcrV3LocalRegion r=OcrV3LocalRegion.plan(470,950,510,980,512,1024);
        assertNotNull(r);
        assertTrue(r.left>=0&&r.top>=0);
        assertTrue(r.right<=512&&r.bottom<=1024);
    }
}
