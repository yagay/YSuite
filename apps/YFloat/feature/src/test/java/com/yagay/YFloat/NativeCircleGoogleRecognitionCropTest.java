package com.yagay.YFloat;

import static org.junit.Assert.*;
import org.junit.Test;

public class NativeCircleGoogleRecognitionCropTest {
    @Test public void highlightGetsGlyphHeightInsteadOfThinTouchStroke() {
        int[] crop=NativeCircleGoogleRecognitionCrop.forGesture(
                "HIGHLIGHT",180f,620f,530f,624f,355f,622f,1272,2772,2f);
        assertNotNull(crop);
        assertTrue(crop[0]<=180);
        assertTrue(crop[2]>=530);
        assertTrue(crop[1]<620);
        assertTrue(crop[3]>624);
        assertTrue(crop[3]-crop[1]>=176);
    }

    @Test public void tapGetsSurroundingTextRatherThanOnePixel() {
        int[] crop=NativeCircleGoogleRecognitionCrop.forGesture(
                "TAP",650f,980f,651f,981f,650f,980f,1272,2772,2f);
        assertNotNull(crop);
        assertTrue(crop[0]<650 && crop[2]>650);
        assertTrue(crop[1]<980 && crop[3]>980);
        assertTrue(crop[2]-crop[0]>=440);
        assertTrue(crop[3]-crop[1]>=256);
    }

    @Test public void bottomRightAndTopLeftPreserveReadableSize() {
        int[] bottom=NativeCircleGoogleRecognitionCrop.forGesture(
                "TAP",1190,2700,1191,2701,1190,2700,1272,2772,2);
        int[] top=NativeCircleGoogleRecognitionCrop.forGesture(
                "HIGHLIGHT",0,1,45,2,23,2,1272,2772,2);
        assertNotNull(bottom);
        assertNotNull(top);
        assertTrue(bottom[2]<=1272 && bottom[3]<=2772);
        assertTrue(bottom[2]-bottom[0]>=440);
        assertTrue(top[0]>=0 && top[1]>=0);
        assertTrue(top[3]-top[1]>=176);
    }

    @Test public void minimumFrameIsHandledSafely() {
        int[] crop=NativeCircleGoogleRecognitionCrop.forGesture(
                "TAP",0,0,1,1,0,0,32,24,3);
        assertArrayEquals(new int[]{0,0,32,24},crop);
        assertNull(NativeCircleGoogleRecognitionCrop.forGesture(
                "TAP",0,0,1,1,0,0,0,24,3));
    }

    @Test public void localScreenshotAndGoogleTextIntentStayDistinct() {
        assertEquals(NativeCircleIntentRouter.Route.IMAGE,
                NativeCircleIntentRouter.classify("CIRCLE",false));
        assertEquals(NativeCircleIntentRouter.Route.IMAGE,
                NativeCircleIntentRouter.classify("SCRIBBLE",false));
        assertEquals(NativeCircleIntentRouter.Route.NEED_OCR,
                NativeCircleIntentRouter.classify("HIGHLIGHT",false));
        assertEquals(NativeCircleIntentRouter.Route.NEED_OCR,
                NativeCircleIntentRouter.classify("TAP",false));
    }
}
