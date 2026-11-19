package com.yagay.YFloat;

import java.util.List;

/**
 * Android-free geometric hit testing and visited-word range for a freehand
 * OCR underline. Layout boxes are ML Kit elements; text remains Paddle-owned.
 *
 * This core deliberately has no android.graphics.Rect dependency so the real
 * gesture algorithm can be exercised by the module's plain JVM unit tests.
 */
final class OcrV3StrokeRange {
    static final class Word {
        final String block;
        final int left, top, right, bottom;
        Word(String block,int left,int top,int right,int bottom) {
            this.block=block;
            this.left=left;this.top=top;this.right=right;this.bottom=bottom;
        }
    }

    private final List<Word> words;
    private int origin=-1, first=-1, last=-1;

    OcrV3StrokeRange(List<Word> words) {
        this.words=List.copyOf(words);
    }

    void clear() { origin=first=last=-1; }

    void start(int index) {
        if(index<0||index>=words.size()) { clear();return; }
        origin=first=last=index;
    }

    int first() { return first; }
    int last() { return last; }

    /** Hit real ML Kit word bounds, with only a small underline tolerance. */
    private int hit(float x,float y,float padding) {
        for(int i=0;i<words.size();i++){
            Word w=words.get(i);
            if(x>=w.left&&x<w.right&&y>=w.top&&y<w.bottom)return i;
        }
        int nearest=-1;
        float best=padding*padding;
        for(int i=0;i<words.size();i++){
            Word w=words.get(i);
            float dx=Math.max(w.left-x,Math.max(0f,x-w.right));
            float dy=Math.max(w.top-y,Math.max(0f,y-w.bottom));
            float square=dx*dx+dy*dy;
            if(square<=best){
                best=square;nearest=i;
            }
        }
        return nearest;
    }

    /**
     * Interpolate across sparse MotionEvents. Blank-start strokes may first
     * cross a valid word mid-segment; a later unrelated text block cannot
     * expand the initial block or absorb unrelated application labels.
     */
    boolean extend(float x0,float y0,float x1,float y1,float padding) {
        float length=(float)Math.hypot(x1-x0,y1-y0);
        int steps=Math.min(128,Math.max(1,(int)Math.ceil(
                length/Math.max(3f,padding*0.5f))));
        boolean changed=false;
        for(int k=0;k<=steps;k++){
            float fraction=(float)k/steps;
            int index=hit(x0+(x1-x0)*fraction,
                    y0+(y1-y0)*fraction,padding);
            if(index<0)continue;
            if(origin<0){
                start(index);
                changed=true;
            }else if(words.get(index).block.equals(words.get(origin).block)){
                int a=Math.min(first,index),b=Math.max(last,index);
                if(a!=first||b!=last){
                    first=a;last=b;changed=true;
                }
            }
        }
        return changed;
    }
}
