package com.yagay.YFloat;

import static org.junit.Assert.*;
import org.junit.Test;

/**
 * Launcher screenshot regression: selecting bitmap-rendered Chinese text on a
 * saturated red icon must preserve precise translucent selection bounds.
 */
public final class NativeCircleSelectionVisualTest {
    @Test public void nativeTextSelectionHasVisibleTranslucentBlueWash(){
        int alpha=NativeCircleVisualPolicy.TEXT_HIGHLIGHT_FILL_ALPHA;
        assertTrue("Selection must remain visibly blue",alpha>=72);
        assertTrue("Selection must not obscure source glyphs",alpha<=132);
        assertTrue("Selection is translucent, never opaque",alpha<255);
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
