package com.yagay.YFloat;

import android.graphics.Rect;
import java.util.List;

/**
 * Unlimited OCR selection within the frozen screenshot. Initial gesture
 * locality is a separate policy from deliberate handle expansion.
 */
final class CircleTextExpansionPolicy {
    static final int START=1,END=2;
    static final class Range {
        final int first,last,active;
        Range(int first,int last,int active) {
            this.first=first;this.last=last;this.active=active;
        }
    }
    private CircleTextExpansionPolicy(){}

    /** Handles cross rather than clamp at the original one-line selection. */
    static Range move(int first,int last,int moving,int target) {
        if(first<0||last<first||target<0)return new Range(first,last,moving);
        if(moving==START)
            return target<=last?new Range(target,last,START)
                    :new Range(last,target,END);
        if(moving==END)
            return target>=first?new Range(first,target,END)
                    :new Range(target,first,START);
        return new Range(first,last,moving);
    }

    /**
     * Match an old OCR character to a full-screen OCR result by absolute
     * position first; text only breaks nearby ties. Matching by index would
     * select a completely different glyph after ROI expansion.
     */
    static int remap(List<OcrDocument.CharUnit> target,OcrDocument.CharUnit from) {
        if(from==null||target==null||target.isEmpty())return -1;
        Rect a=from.bounds();
        if(a.isEmpty())return -1;
        float best=Float.POSITIVE_INFINITY;
        int index=-1;
        for(int i=0;i<target.size();i++){
            OcrDocument.CharUnit to=target.get(i);
            Rect b=to.bounds();
            if(b.isEmpty()||to.text().isBlank())continue;
            float dx=b.exactCenterX()-a.exactCenterX();
            float dy=b.exactCenterY()-a.exactCenterY();
            float scale=Math.max(4f,Math.min(a.height(),b.height()));
            float score=remapScore(dx,dy,scale,
                    to.text().equals(from.text()));
            if(score<best){best=score;index=i;}
        }
        // Reject unrelated text when full OCR misses the original symbol.
        return best<=14f?index:-1;
    }

    static float remapScore(float dx,float dy,float scale,
                            boolean sameText) {
        float divisor=Math.max(4f,scale);
        return (dx*dx+dy*dy)/(divisor*divisor)+(sameText?0f:2f);
    }
    static float boxDistance(float x,float y,
                             float left,float top,float right,float bottom) {
        float dx=x<left?left-x:x>right?x-right:0f;
        float dy=y<top?top-y:y>bottom?y-bottom:0f;
        return dx*dx+dy*dy*1.5f;
    }

    /** A free drag must find the nearest text even beyond the 96dp main UI snap. */
    static int unrestrictedNearest(List<OcrDocument.CharUnit> chars,float x,float y) {
        if(chars==null||chars.isEmpty())return -1;
        int best=-1;
        float distance=Float.POSITIVE_INFINITY;
        for(int i=0;i<chars.size();i++){
            Rect b=chars.get(i).bounds();
            if(b.isEmpty()||chars.get(i).text().isBlank())continue;
            float value=boxDistance(x,y,b.left,b.top,b.right,b.bottom);
            if(value<distance){distance=value;best=i;}
        }
        return best;
    }
}
