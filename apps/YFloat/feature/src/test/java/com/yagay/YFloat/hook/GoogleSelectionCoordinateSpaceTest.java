package com.yagay.YFloat.hook;

import static org.junit.Assert.*;
import android.graphics.Rect;
import org.junit.Test;

public class GoogleSelectionCoordinateSpaceTest {
    @Test public void fullScreenTextWordsIgnoreAnimatedViewTranslation() {
        Rect stable = new Rect(0, 0, 1272, 2772);
        Rect word = new Rect(104, 1100, 1148, 2008);
        GoogleSelectionCoordinateSpace.Result one =
                GoogleSelectionCoordinateSpace.map(word,1272,2772,0,-1369,stable);
        assertEquals("stable_decor",one.space);
        assertEquals(word,one.bounds);
        GoogleSelectionCoordinateSpace.Result two =
                GoogleSelectionCoordinateSpace.map(word,1272,2772,0,-296,stable);
        assertEquals(one.bounds,two.bounds);
    }
    @Test public void embeddedTextViewUsesActualOrigin() {
        Rect stable = new Rect(0,0,1272,2772);
        GoogleSelectionCoordinateSpace.Result out = GoogleSelectionCoordinateSpace.map(
                new Rect(20,30,80,70),400,200,100,500,stable);
        assertEquals("local_view",out.space);
        assertEquals(new Rect(120,530,180,570),out.bounds);
    }
    @Test public void impossibleAndOutOfBoundsCoordinatesDoNotPropagate() {
        Rect stable = new Rect(0,0,1272,2772);
        assertNull(GoogleSelectionCoordinateSpace.map(
                new Rect(1500,1500,1700,1600),1272,2772,0,-500,stable).bounds);
    }
}
