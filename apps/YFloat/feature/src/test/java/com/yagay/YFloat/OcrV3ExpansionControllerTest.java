package com.yagay.YFloat;

import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Android-free tests exercise the production glyph-range controller, not a
 * mirror of the runtime behavior. No android.graphics.Rect construction.
 */
public final class OcrV3ExpansionControllerTest {
    private static OcrV3ExpansionController sample(){
        return new OcrV3ExpansionController(List.of(
                // Deliberately scramble input: Paddle source order must win.
                new OcrV3ExpansionController.Unit(3,1,1,12,28,22,48,3),
                new OcrV3ExpansionController.Unit(1,0,1,12,0,22,20,1),
                new OcrV3ExpansionController.Unit(4,3,0,300,300,320,320,4),
                new OcrV3ExpansionController.Unit(0,0,0,0,0,10,20,0),
                new OcrV3ExpansionController.Unit(2,1,0,0,28,10,48,2)),5);
    }

    @Test public void originalPaddleLineAndOffsetOrderBeatsMlBlockOrder(){
        OcrV3ExpansionController c=sample();
        assertEquals(5,c.size());
        for(int i=0;i<5;i++)assertEquals(i,c.unit(i).glyphId);
    }

    @Test public void handleExpandsAcrossLineWithoutResettingSelection(){
        OcrV3ExpansionController c=sample();
        assertTrue(c.prepare(List.of(0,1)));
        assertEquals(0,c.first());assertEquals(1,c.last());
        assertTrue(c.begin(OcrV3ExpansionController.END,22,30));
        assertTrue(c.drag(7,58,5));
        assertEquals(0,c.first());assertEquals(2,c.last());
        assertTrue(c.drag(19,58,5));
        assertEquals(0,c.first());assertEquals(3,c.last());
        c.release();
        assertEquals(0,c.moving());
    }

    @Test public void returningGripShrinksTheOtherEndDoesNotMove(){
        OcrV3ExpansionController c=sample();
        assertTrue(c.prepare(List.of(0,1,2,3)));
        assertTrue(c.begin(OcrV3ExpansionController.END,22,58));
        assertTrue(c.drag(7,58,5));
        assertEquals(0,c.first());assertEquals(2,c.last());
        assertTrue(c.drag(19,30,5));
        assertEquals(0,c.first());assertEquals(1,c.last());
    }

    @Test public void crossedGripsSwapMovingSideAndStayContinuous(){
        OcrV3ExpansionController c=sample();
        assertTrue(c.prepare(List.of(1,2)));
        assertTrue(c.begin(OcrV3ExpansionController.START,12,26));
        assertTrue(c.drag(19,54,5));
        assertEquals(2,c.first());assertEquals(3,c.last());
        assertEquals(OcrV3ExpansionController.END,c.moving());
    }

    @Test public void distantWallpaperDoesNotSnapToUnrelatedIcon(){
        OcrV3ExpansionController c=sample();
        assertTrue(c.prepare(List.of(0)));
        assertTrue(c.begin(OcrV3ExpansionController.END,10,28));
        assertFalse(c.drag(120,120,15));
        assertEquals(0,c.first());assertEquals(0,c.last());
        assertFalse(c.changed());
    }

    @Test public void initialFingerOffsetPreventsFirstFrameJump(){
        OcrV3ExpansionController c=sample();
        assertTrue(c.prepare(List.of(0)));
        assertTrue(c.begin(OcrV3ExpansionController.END,10,32));
        assertFalse(c.drag(10,32,5));
        assertEquals(0,c.first());assertEquals(0,c.last());
        assertFalse(c.changed());
    }

    @Test public void consecutiveDragSessionsPreserveFineRange(){
        OcrV3ExpansionController c=sample();
        assertTrue(c.prepare(List.of(0,1)));
        assertTrue(c.begin(OcrV3ExpansionController.END,22,30));
        assertTrue(c.drag(7,58,5));
        c.release();
        assertEquals(2,c.last());
        assertTrue(c.begin(OcrV3ExpansionController.START,0,57));
        assertEquals(0,c.first());assertEquals(2,c.last());
    }

    @Test public void endHandleNeverLosesOriginalPaddleStart(){
        OcrV3ExpansionController c=sample();
        assertTrue(c.prepare(List.of(0,1)));
        assertTrue(c.begin(OcrV3ExpansionController.END,22,30));
        assertTrue(c.drag(315,330,6));
        assertEquals(0,c.first());assertEquals(4,c.last());
    }
}
