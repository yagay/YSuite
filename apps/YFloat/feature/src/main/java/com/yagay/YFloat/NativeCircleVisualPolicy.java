package com.yagay.YFloat;

/**
 * One visual contract for native Circle. The base mask proves capture mode is
 * active, but finalized image selections use a single unshaded rectangle with
 * an outside-only scrim so the crop exactly matches the result.
 */
final class NativeCircleVisualPolicy {
    static final int SELECTION_OUTSIDE_ALPHA = 112;

    // Never paint translucent blue over OCR text: selected pixels are
    // restored verbatim from the frozen screenshot after the outside scrim.
    // The visible outline stays *outside* the selected pixel band.
    static final int TEXT_HIGHLIGHT_FILL_ALPHA = 0;
    static final int TEXT_HIGHLIGHT_BORDER_ALPHA = 214;
    static float textBorderOutsideGap(float density){
        return Math.max(1f,2.5f*density);
    }

    /**
     * OCR detection gives a much better whole-line left/right edge than CTC
     * character spacing on large launcher icons (for example, 头条).
     * CTC glyph bounds, however, are generally tighter vertically. For a
     * complete OCR line prefer the detector's horizontal edges and the
     * overlapping glyph/detector vertical band. Partial selections must
     * continue to use only the selected glyphs.
     *
     * All inputs/outputs are physical SCREEN pixels; mapping to View happens
     * exactly once at the caller. The detector is rejected if it appears to
     * span unrelated regions or barely intersects the selected characters.
     */
    static float[] textSelectionScreenBand(float glyphLeft,float glyphTop,
            float glyphRight,float glyphBottom,float rowLeft,float rowTop,
            float rowRight,float rowBottom,boolean wholeLine) {
        float left=glyphLeft,top=glyphTop,right=glyphRight,bottom=glyphBottom;
        float gw=glyphRight-glyphLeft,gh=glyphBottom-glyphTop;
        float rw=rowRight-rowLeft,rh=rowBottom-rowTop;
        if(wholeLine && gw>0f && gh>0f && rw>0f && rh>0f){
            float sharedWidth=Math.max(0f,Math.min(glyphRight,rowRight)
                    -Math.max(glyphLeft,rowLeft));
            float sharedHeight=Math.max(0f,Math.min(glyphBottom,rowBottom)
                    -Math.max(glyphTop,rowTop));
            boolean related=rw>=gw*0.65f && rw<=gw*1.8f
                    && rh>=gh*0.55f && rh<=gh*1.8f
                    && sharedWidth>=Math.min(gw,rw)*0.55f
                    && sharedHeight>=Math.min(gh,rh)*0.55f;
            if(related){
                left=rowLeft;
                right=rowRight;
                // Never enlarge the band to include the icon's upper/lower
                // solid-color strips just because OCR's line box has padding.
                float croppedTop=Math.max(glyphTop,rowTop);
                float croppedBottom=Math.min(glyphBottom,rowBottom);
                if(croppedBottom-croppedTop>=gh*0.55f){
                    top=croppedTop;
                    bottom=croppedBottom;
                }
            }
        }
        // Minimal pixel allowance for OCR quantisation. The visible blue
        // outline has its own OUTWARD-only gap, separate from these pixels.
        float pad=Math.max(0.5f,Math.min(1.5f,(bottom-top)*0.015f));
        return new float[]{left-pad,top-pad,right+pad,bottom+pad};
    }

    /** Handle stems are fully outside the text glyph band. */
    static float leadingHandleX(float bandLeft,float density){
        return bandLeft-5f*density;
    }
    static float trailingHandleX(float bandRight,float density){
        return bandRight+5f*density;
    }
    static float handleStemTop(float bandBottom,float density){
        return bandBottom+1f*density;
    }
    static float handleCenterY(float bandBottom,float density){
        return bandBottom+9f*density;
    }

    static int baseScrimAlpha(NativeCircleState.Phase phase) {
        if (phase == null) return 0;
        return switch (phase) {
            case IDLE -> 68;
            case DRAWING -> 49;
            case TEXT_READY -> 38;
            case RESOLVING, IMAGE_READY, CLOSED -> 0;
        };
    }

    static boolean showRectangle(NativeCircleState.Phase phase) {
        return phase == NativeCircleState.Phase.RESOLVING
                || phase == NativeCircleState.Phase.IMAGE_READY;
    }

    private NativeCircleVisualPolicy() { }
}
