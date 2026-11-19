package com.yagay.YFloat;
import org.junit.Test;
import static org.junit.Assert.*;

public final class OcrV4RegionTest {
    @Test public void firstPassUsesGenuineMlLinePlusMargins(){
        var r=OcrV4Region.line(10,30,150,50,500,500,false);
        assertNotNull(r);assertTrue(r.left<10);assertTrue(r.top<30);
        assertTrue(r.right>150);assertTrue(r.bottom>50);
    }
    @Test public void secondPassWidensOnlyHorizontalMargin(){
        var a=OcrV4Region.line(100,30,150,50,500,500,false);
        var b=OcrV4Region.line(100,30,150,50,500,500,true);
        assertTrue(b.left<a.left);assertTrue(b.right>a.right);
        assertEquals(a.top,b.top);assertEquals(a.bottom,b.bottom);
    }
    @Test public void clipAtScreenBoundary(){
        var r=OcrV4Region.line(0,0,25,10,30,20,true);
        assertNotNull(r);assertEquals(0,r.left);assertEquals(0,r.top);
        assertTrue(r.right<=30&&r.bottom<=20);
    }
    @Test public void wordRoiIsDistinctFromLineRoi(){
        var w=OcrV4Region.word(30,50,100,70,300,300);
        var line=OcrV4Region.line(30,50,100,70,300,300,false);
        assertNotNull(w);
        assertTrue(w.left>line.left);
        assertTrue(w.right<line.right);
    }
    @Test public void wordRegionClipsToRealImageBounds(){
        var w=OcrV4Region.word(0,0,21,14,24,17);
        assertNotNull(w);assertEquals(0,w.left);assertEquals(0,w.top);
        assertTrue(w.right<=24&&w.bottom<=17);
    }
    @Test public void invalidLineCannotBecomeRandomScreenshot(){
        assertNull(OcrV4Region.line(40,40,20,50,100,100,false));
    }
}