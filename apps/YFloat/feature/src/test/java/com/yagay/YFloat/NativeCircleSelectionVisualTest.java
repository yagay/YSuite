package com.yagay.YFloat;

import static org.junit.Assert.*;
import org.junit.Test;

/**
 * Launcher screenshot regression: selecting bitmap-rendered Chinese text on a
 * saturated red icon must never draw handle stems through the glyphs.
 */
public final class NativeCircleSelectionVisualTest {
    @Test public void connectedHandlesBeginExactlyAtTheBlueSelectionCorners(){
        float density=3f;
        float[] left=NativeCircleVisualPolicy.attachedTextHandle(
                120f,290f,120f,360f,density);
        float[] right=NativeCircleVisualPolicy.attachedTextHandle(
                240f,290f,240f,360f,density);
        assertEquals(120f,left[0],0.001f);
        assertEquals(360f,left[1],0.001f);
        assertEquals(240f,right[0],0.001f);
        assertEquals(360f,right[1],0.001f);
        assertEquals("The knob is directly below its anchor",120f,left[2],0.001f);
        assertEquals("The knob is directly below its anchor",240f,right[2],0.001f);
        assertTrue(left[3]>left[1]);
        assertEquals(left[3],right[3],0.001f);
    }

    @Test public void nativeTextSelectionHasVisibleTranslucentBlueWash(){
        int alpha=NativeCircleVisualPolicy.TEXT_HIGHLIGHT_FILL_ALPHA;
        assertTrue("Selection must remain visibly blue",alpha>=72);
        assertTrue("Selection must not obscure source glyphs",alpha<=132);
        assertTrue("Selection is translucent, never opaque",alpha<255);
    }

    @Test public void rotatedTextHandlesConnectAlongTheOriginalQuadSides(){
        float[] left=NativeCircleVisualPolicy.attachedTextHandle(
                80f,70f,68f,108f,2f);
        assertEquals(68f,left[0],0.001f);
        assertEquals(108f,left[1],0.001f);
        assertTrue("Tilted quad handle should extend left with the glyph edge",left[2]<68f);
        assertTrue(left[3]>108f);
        float edgeDx=68f-80f,edgeDy=108f-70f;
        float stemDx=left[2]-left[0],stemDy=left[3]-left[1];
        assertEquals("Stem and source edge must remain collinear",
                0f,edgeDx*stemDy-edgeDy*stemDx,0.001f);
    }

    @Test public void handlesConnectAtEveryDensityWithoutOldOutsideGap(){
        for(float density:new float[]{1f,2.75f,4f}){
            float[] handle=NativeCircleVisualPolicy.attachedTextHandle(
                    25f,30f,25f,58f,density);
            assertEquals(25f,handle[0],0.001f);
            assertEquals(58f,handle[1],0.001f);
            assertEquals(8f*density,handle[3]-handle[1],0.001f);
        }
    }

    @Test public void degenerateSideEdgeStillAttachesAndDoesNotCrash(){
        float[] handle=NativeCircleVisualPolicy.attachedTextHandle(
                40f,45f,40f,45f,3f);
        assertEquals(40f,handle[0],0.001f);
        assertEquals(45f,handle[1],0.001f);
        assertEquals(40f,handle[2],0.001f);
        assertTrue(handle[3]>45f);
    }
    /** Reproduces the Toutiao (头条) icon: character-space CTC bounds
     * finish before the second Chinese glyph, while detector line bounds
     * correctly cover it. Vertical CTC padding must not enlarge the frame. */
    @Test public void entireIconWordUsesDetectorWidthAndTightVerticalOverlap(){
        float[] bounds=NativeCircleVisualPolicy.textSelectionScreenBand(
                156f,85f,446f,370f,180f,135f,500f,315f,true);
        assertTrue("Left edge should follow actual detected word",bounds[0]>175f);
        assertTrue("Right edge should include 条 rather than cut through it",bounds[2]>495f);
        assertTrue("Avoid top icon band",bounds[1]>130f);
        assertTrue("Avoid bottom icon band",bounds[3]<320f);
        float density=3f;
        float[] connected=NativeCircleVisualPolicy.attachedTextHandle(
                bounds[0],bounds[1],bounds[0],bounds[3],density);
        assertEquals("Handle begins on the filled text edge",
                bounds[0],connected[0],0.001f);
        assertEquals(bounds[3],connected[1],0.001f);
    }

    @Test public void draggingOneCharacterNeverExpandsToWholeIconWord(){
        float[] bounds=NativeCircleVisualPolicy.textSelectionScreenBand(
                158f,130f,300f,306f,170f,117f,505f,323f,false);
        assertTrue(bounds[0]<160f);
        assertTrue(bounds[2]<305f);
        assertTrue(bounds[1]>125f);
        assertTrue(bounds[3]<310f);
    }

    @Test public void detectorCrossingUnrelatedIconIsRejected(){
        float[] bounds=NativeCircleVisualPolicy.textSelectionScreenBand(
                155f,140f,445f,310f,15f,55f,755f,470f,true);
        assertTrue("Oversized detection must not expand selection",bounds[0]>150f);
        assertTrue(bounds[2]<450f);
        assertTrue(bounds[1]>135f);
        assertTrue(bounds[3]<315f);
    }

    @Test public void nonOverlappingDetectorFallsBackToSelectedCharacters(){
        float[] bounds=NativeCircleVisualPolicy.textSelectionScreenBand(
                155f,140f,445f,310f,500f,140f,750f,310f,true);
        assertTrue(bounds[0]<158f);
        assertTrue(bounds[2]<450f);
    }

}
