package com.yagay.YFloat;

import static org.junit.Assert.*;
import org.junit.Test;

/**
 * Launcher screenshot regression: selecting bitmap-rendered Chinese text on a
 * saturated red icon must never draw handle stems through the glyphs.
 */
public final class NativeCircleSelectionVisualTest {
    @Test public void stemsAreOutsideOriginalGlyphBand(){
        float density=3f;
        float left=120f,right=240f,bottom=360f;
        float leading=NativeCircleVisualPolicy.leadingHandleX(left,density);
        float trailing=NativeCircleVisualPolicy.trailingHandleX(right,density);
        float start=NativeCircleVisualPolicy.handleStemTop(bottom,density);
        float center=NativeCircleVisualPolicy.handleCenterY(bottom,density);
        assertTrue(leading<left);
        assertTrue(trailing>right);
        assertTrue(start>bottom);
        assertTrue(center>start);
    }

    @Test public void highContrastOutlineDoesNotRequireOpaqueGlyphOverlay(){
        assertTrue(NativeCircleVisualPolicy.TEXT_HIGHLIGHT_BORDER_ALPHA>=190);
        assertTrue(NativeCircleVisualPolicy.TEXT_HIGHLIGHT_FILL_ALPHA<80);
        assertTrue(NativeCircleVisualPolicy.TEXT_HIGHLIGHT_BORDER_ALPHA>
                NativeCircleVisualPolicy.TEXT_HIGHLIGHT_FILL_ALPHA);
    }

    @Test public void handleSizesStayValidAtLowAndHighDensities(){
        for(float density:new float[]{1f,2.75f,4f}){
            assertEquals(-5f*density,
                    NativeCircleVisualPolicy.leadingHandleX(0f,density),0.001f);
            assertEquals(9f*density,
                    NativeCircleVisualPolicy.handleCenterY(0f,density),0.001f);
        }
    }
}
