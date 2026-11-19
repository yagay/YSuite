package com.yagay.YFloat;

import static org.junit.Assert.assertArrayEquals;
import org.junit.Test;

/** Geometry must stay in one physical-screen coordinate system across windows. */
public class SelectionCoordinateMathTest {
    @Test public void centeredResultDialogLocalVisibleRectConvertsToAbsoluteScreen() {
        assertArrayEquals(new int[]{170,330,450,510},
                SelectionCoordinateMath.toScreen(10,20,290,200,160,310));
    }

    @Test public void scrollingAndClippedTextKeepsThePhysicalDialogOffset() {
        assertArrayEquals(new int[]{100,520,340,660},
                SelectionCoordinateMath.toScreen(0,120,240,260,100,400));
    }

    @Test public void originZeroDoesNotChangeAnAlreadyScreenAlignedRect() {
        assertArrayEquals(new int[]{10,24,40,78},
                SelectionCoordinateMath.toScreen(10,24,40,78,0,0));
    }

    @Test public void negativeLocalClipCoordinatesStillTranslateCorrectly() {
        assertArrayEquals(new int[]{230,350,320,410},
                SelectionCoordinateMath.toScreen(-20,-10,70,50,250,360));
    }
}
