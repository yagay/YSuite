package com.yagay.YFloat;

/**
 * Source-line boundaries are authoritative. This pure policy prevents a
 * horizontal highlight from connecting separate texts in app icons/captions.
 */
final class CircleTextRangePolicy {
    static float lineScore(int touchedCount,float distanceFromStroke,float lineHeight) {
        return touchedCount*0.8f-
                Math.max(0f,distanceFromStroke)/Math.max(8f,lineHeight)*2.2f;
    }
    static boolean splitsLabel(int visualGap,float smallerGlyphHeight) {
        return visualGap>Math.max(36f,2.2f*Math.max(1f,smallerGlyphHeight));
    }
    static boolean sameLine(int sourceLine,int selectionLine) {
        return selectionLine<0 || sourceLine==selectionLine;
    }
    private CircleTextRangePolicy(){}
}
