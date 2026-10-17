package com.yagay.YFloat;

import static org.junit.Assert.*;
import org.junit.Test;

public final class NativeCircleGoogleRecognitionCropTest {
    @Test public void tappedWordUsesReadableNeighbourhood() {
        int[] r=NativeCircleGoogleRecognitionCrop.forGesture(
                "TAP",100,200,101,201,100,200,1272,2641,3f);
        assertTrue(r[2]-r[0]>=600);
        assertTrue(r[3]-r[1]>=260);
        assertTrue(r[0]<=100 && r[2]>100);
    }
    @Test public void highlightIsNotAnUnreadableOnePixelImage() {
        int[] r=NativeCircleGoogleRecognitionCrop.forGesture(
                "HIGHLIGHT",30,40,170,42,100,41,500,700,2f);
        assertTrue(r[3]-r[1]>=176);
        assertTrue(r[2]-r[0]>=320);
    }
    @Test public void clampAtEveryEdge() {
        int[] r=NativeCircleGoogleRecognitionCrop.forGesture(
                "TAP",0,0,1,1,0,0,100,120,3f);
        assertArrayEquals(new int[]{0,0,100,120},r);
    }
    @Test public void tinyFrameNeverGrowsOutsideBitmap() {
        int[] r=NativeCircleGoogleRecognitionCrop.forGesture(
                "HIGHLIGHT",0,0,2,2,1,1,3,4,1f);
        assertArrayEquals(new int[]{0,0,3,4},r);
    }
}
