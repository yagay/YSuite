package com.yagay.YFloat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Single canonical range owner for OCR selection-handle dragging.
 *
 * Pure Java: Android merely forwards bitmap-space touch events and paints
 * resulting rectangles. Units are genuine OCR glyphs ordered by the ORIGINAL
 * Paddle source spans; their rectangles come from ML symbols where aligned.
 *
 * The pointer's initial offset from the text unit is retained, so a handle
 * can be dragged below the line without jumping or demanding pixel-perfect
 * finger placement on a character.
 */
final class OcrV3ExpansionController {
    static final int START=1, END=2;

    static final class Unit {
        final int glyphId;
        final int line, offset;
        final int left, top, right, bottom;
        final int wordId;
        Unit(int glyphId,int line,int offset,int left,int top,
             int right,int bottom,int wordId){
            this.glyphId=glyphId;this.line=line;this.offset=offset;
            this.left=left;this.top=top;this.right=right;this.bottom=bottom;
            this.wordId=wordId;
        }
        float centerX(){return (left+right)/2f;}
        float centerY(){return (top+bottom)/2f;}
        float distanceSquared(float x,float y){
            float dx=Math.max(left-x,Math.max(0f,x-right));
            float dy=Math.max(top-y,Math.max(0f,y-bottom));
            return dx*dx+dy*dy;
        }
    }

    private final List<Unit> units;
    private final List<List<Integer>> wordUnits;
    private int first=-1,last=-1,moving=0;
    private float offsetX,offsetY;
    private boolean changed;

    OcrV3ExpansionController(List<Unit> raw,int wordCount){
        ArrayList<Unit> ordered=new ArrayList<>(raw);
        ordered.sort(Comparator.comparingInt((Unit u)->u.line)
                .thenComparingInt(u->u.offset)
                .thenComparingInt(u->u.glyphId));
        units=List.copyOf(ordered);
        ArrayList<List<Integer>> byWord=new ArrayList<>();
        for(int i=0;i<wordCount;i++)byWord.add(new ArrayList<>());
        for(int i=0;i<units.size();i++){
            int owner=units.get(i).wordId;
            if(owner>=0&&owner<wordCount)byWord.get(owner).add(i);
        }
        wordUnits=List.copyOf(byWord);
    }

    int size(){return units.size();}
    Unit unit(int index){return units.get(index);}
    boolean hasRange(){return first>=0&&last>=first&&last<units.size();}
    int first(){return first;}
    int last(){return last;}
    int moving(){return moving;}
    boolean changed(){return changed;}

    void clear(){
        first=last=-1;moving=0;
        offsetX=offsetY=0f;changed=false;
    }

    /**
     * Convert an existing ML word selection to its original Paddle glyphs.
     * This method never changes the live word selection by itself.
     */
    boolean prepare(List<Integer> selectedWords){
        clear();
        int min=Integer.MAX_VALUE,max=-1;
        if(selectedWords!=null)for(int wordId:selectedWords){
            if(wordId<0||wordId>=wordUnits.size())continue;
            for(int id:wordUnits.get(wordId)){
                min=Math.min(min,id);max=Math.max(max,id);
            }
        }
        if(max<0)return false;
        first=min;last=max;
        return true;
    }

    /** Capture the touch offset relative to the glyph center rather than the
     * handle center. This keeps finger movement geometrically continuous. */
    boolean begin(int side,float fingerX,float fingerY){
        if(!hasRange()||(side!=START&&side!=END))return false;
        moving=side;changed=false;
        Unit u=units.get(side==START?first:last);
        offsetX=fingerX-u.centerX();
        offsetY=fingerY-u.centerY();
        return true;
    }

    /**
     * Prefer real symbol box hits. Nearby blank-line gaps may snap to the
     * nearest OCR unit, but distant wallpaper is never a target.
     */
    int target(float x,float y,float maxDistance){
        if(units.isEmpty())return -1;
        int best=-1;
        float closest=Float.POSITIVE_INFINITY;
        float limit=maxDistance*maxDistance;
        for(int i=0;i<units.size();i++){
            Unit u=units.get(i);
            float d=u.distanceSquared(x,y);
            if(d>limit)continue;
            if(d<closest){
                closest=d;best=i;
            }else if(d==closest && best>=0 && hasRange()){
                // Stable tie-breaking near a word boundary favors the current
                // end, avoiding flicker between overlapping OCR boxes.
                int previous=moving==START?first:last;
                if(Math.abs(i-previous)<Math.abs(best-previous))best=i;
            }
        }
        return best;
    }

    boolean drag(float fingerX,float fingerY,float maxDistance){
        if(moving==0||!hasRange())return false;
        int target=target(fingerX-offsetX,fingerY-offsetY,maxDistance);
        if(target<0)return false;
        int a=first,b=last,side=moving;
        if(side==START)a=target;
        else b=target;
        if(a>b){
            int t=a;a=b;b=t;
            side=side==START?END:START;
        }
        boolean moved=a!=first||b!=last||side!=moving;
        first=a;last=b;moving=side;
        if(moved)changed=true;
        return moved;
    }

    void release(){moving=0;}
}
