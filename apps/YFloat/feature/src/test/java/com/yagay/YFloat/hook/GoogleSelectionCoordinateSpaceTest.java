package com.yagay.YFloat.hook;

import static org.junit.Assert.*;
import org.junit.Test;

public class GoogleSelectionCoordinateSpaceTest {
    @Test public void fullScreenWordBoundsIgnoreAnimatedViewTranslation() {
        GoogleSelectionCoordinateSpace.Values a = GoogleSelectionCoordinateSpace.project(
                104, 1100, 1148, 2008, 1272, 2772, 0, -1369,
                true, 0, 0, 1272, 2772);
        GoogleSelectionCoordinateSpace.Values b = GoogleSelectionCoordinateSpace.project(
                104, 1100, 1148, 2008, 1272, 2772, 0, -296,
                true, 0, 0, 1272, 2772);
        assertEquals("stable_decor", a.space);
        assertEquals(1100, a.top);
        assertEquals(2008, a.bottom);
        assertEquals(a.top, b.top);
        assertEquals(a.bottom, b.bottom);
    }

    @Test public void nestedTextViewUsesActualViewOrigin() {
        GoogleSelectionCoordinateSpace.Values out = GoogleSelectionCoordinateSpace.project(
                20, 30, 80, 70, 400, 200, 100, 500, true, 0, 0, 1272, 2772);
        assertEquals("local_view", out.space);
        assertEquals(120, out.left);
        assertEquals(530, out.top);
        assertEquals(180, out.right);
        assertEquals(570, out.bottom);
    }

    @Test public void outsideWordRectCannotBecomeVisibleSelection() {
        GoogleSelectionCoordinateSpace.Values out = GoogleSelectionCoordinateSpace.project(
                1500, 1500, 1700, 1600, 1272, 2772, 0, -500,
                true, 0, 0, 1272, 2772);
        assertFalse(out.valid());
        assertEquals("outside", out.space);
    }
}
