package com.yagay.YFloat;

/**
 * One visual contract for native Circle. The base mask proves capture mode is
 * active, but finalized image selections use a single unshaded rectangle with
 * an outside-only scrim so the crop exactly matches the result.
 */
final class NativeCircleVisualPolicy {
    static final int SELECTION_OUTSIDE_ALPHA = 112;

    // Android-style text selection: a visible, translucent wash over the
    // exact TextGeometry V3 polygon. No border/stroke; the captured font
    // remains visible beneath the blue wash instead of being re-typeset.
    static final int TEXT_HIGHLIGHT_FILL_ALPHA = 104;
    /**
     * A selection handle is ATTACHED to the actual filled polygon corner,
     * never positioned using an external outline margin. Input is the top
     * and bottom of its side edge in view coordinates (TL->BL or TR->BR).
     * Returns [stemStartX,stemStartY,knobX,knobY].
     *
     * Using the side's direction preserves contact with rotated selections;
     * the fallback is straight down for degenerate OCR boxes.
     */
    static float[] attachedTextHandle(float topX,float topY,
            float bottomX,float bottomY,float knobRadius) {
        float vx=bottomX-topX,vy=bottomY-topY;
        float length=(float)Math.hypot(vx,vy);
        if(length < 0.001f){vx=0f;vy=1f;}
        else {vx/=length;vy/=length;}
        // A filled circular knob is tangent to the selected edge: zero
        // visible space between the blue polygon and the handle itself.
        float extension=Math.max(0.5f,knobRadius);
        return new float[]{bottomX,bottomY,bottomX+vx*extension,bottomY+vy*extension};
    }

    /**
     * Text handles keep generous 48dp touch targets, which naturally overlap
     * on short Chinese words. Resolve overlapping targets by the closest
     * visible knob, rather than always choosing the leading handle.
     */
    static int chooseTouchedTextHandle(boolean firstHit,boolean lastHit,
            float touchX,float touchY,float firstX,float firstY,
            float lastX,float lastY) {
        if(!firstHit)return lastHit?2:0;
        if(!lastHit)return 1;
        float d1=(touchX-firstX)*(touchX-firstX)
                +(touchY-firstY)*(touchY-firstY);
        float d2=(touchX-lastX)*(touchX-lastX)
                +(touchY-lastY)*(touchY-lastY);
        return d1<=d2?1:2;
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
        // Minimal pixel allowance for OCR quantisation; the translucent
        // fill follows the selected polygon without an external stroke.
        float pad=Math.max(0.5f,Math.min(1.5f,(bottom-top)*0.015f));
        return new float[]{left-pad,top-pad,right+pad,bottom+pad};
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
