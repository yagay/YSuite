package com.yagay.YFloat;

/**
 * Caret geometry is separate from OCR glyph bounds, painted highlight and
 * touch targets. The hotspot is on the selection boundary, while the visual
 * grip extends OUTSIDE it. Input coordinates are VIEW pixels.
 */
final class TextCaretGeometry {
    static final class Caret {
        final float x,y,angleDegrees;
        Caret(float x,float y,float angleDegrees){
            this.x=x;this.y=y;this.angleDegrees=angleDegrees;
        }
    }
    static final class Pair {
        final Caret start,end;
        Pair(Caret start,Caret end){this.start=start;this.end=end;}
    }
    private TextCaretGeometry() {}

    /** Quad order TL,TR,BR,BL; first and last may belong to different lines. */
    static Pair forQuads(float[] first,float[] last){
        if(!valid(first)||!valid(last))return null;
        // The fill boundary is the hotspot. In contrast, OCR character
        // centres only supply snapping/hit semantics and may not coincide
        // with pixel-corrected selection edges.
        Caret start=new Caret(first[6],first[7],angle(first));
        Caret end=new Caret(last[4],last[5],angle(last));
        return new Pair(start,end);
    }
    private static boolean valid(float[] values){
        if(values==null||values.length!=8)return false;
        for(float v:values)if(!Float.isFinite(v))return false;
        float dx=values[2]-values[0],dy=values[3]-values[1];
        float sideX=values[6]-values[0],sideY=values[7]-values[1];
        return dx*dx+dy*dy>=1f && sideX*sideX+sideY*sideY>=1f;
    }
    private static float angle(float[] q){
        return (float)Math.toDegrees(Math.atan2(q[3]-q[1],q[2]-q[0]));
    }
}
