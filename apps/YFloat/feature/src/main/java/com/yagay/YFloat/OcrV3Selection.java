package com.yagay.YFloat;

import android.graphics.Rect;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * V3 selection consists ONLY of ML Kit Elements whose symbols can be
 * associated with original Paddle characters. No old OCR-index selection,
 * custom word merging, absorption radius or heuristic text rewriting.
 */
final class OcrV3Selection {
    static final class Word {
        final String block;
        final Rect box;
        final List<Integer> paddle;
        Word(String block,Rect box,List<Integer> indices){
            this.block=block;this.box=new Rect(box);
            paddle=List.copyOf(indices);
        }
    }

    private final OcrV3Page page;
    private final List<Word> words;
    private int anchor=-1, extent=-1, active=0; // 1=start, 2=end
    private int strokeOrigin=-1;
    private boolean all;

    private final OcrV3WordMapper.Result mapping;

    OcrV3Selection(OcrV3Page page,List<OcrV3MlLayout.Word> ml){
        this.page=page;
        mapping=OcrV3WordMapper.map(page,ml);
        words=mapping.words;
    }

    int matchedGlyphs(){return mapping.mappedGlyphs;}
    int unmatchedGlyphs(){return mapping.unmappedGlyphs;}
    int missingMlSymbols(){return mapping.missingSymbols;}

    int size(){return words.size();}
    boolean selected(){return all||(anchor>=0&&extent>=0);}
    boolean handleActive(){return active!=0;}

    void clear(){anchor=extent=-1;active=0;strokeOrigin=-1;all=false;}
    void selectAll(){
        clear();
        if(!words.isEmpty()){anchor=0;extent=words.size()-1;all=true;}
    }
    int hit(float x,float y){
        // ML Kit's original Element boxes are the only hit regions.
        for(int i=0;i<words.size();i++)
            if(words.get(i).box.contains((int)x,(int)y))return i;
        return -1;
    }
    boolean tap(float x,float y){
        int i=hit(x,y);
        if(i<0){clear();return false;}
        anchor=extent=i;strokeOrigin=i;active=0;all=false;return true;
    }
    /**
     * A text stroke starts at a real ML Element; other TextBlocks do not get
     * implicitly included in its range. Blank wallpaper never snaps.
     */
    boolean extendStroke(float x,float y){
        if(strokeOrigin<0||all)return false;
        int current=hit(x,y);
        if(current<0 || !words.get(current).block.equals(
                words.get(strokeOrigin).block))return false;
        int first=Math.min(strokeOrigin,current);
        int last=Math.max(strokeOrigin,current);
        if(first==anchor&&last==extent)return false;
        anchor=first;extent=last;
        return true;
    }

    /**
     * Follow every physical segment of a freehand underline, including a stroke
     * which STARTS in blank space. Element hit boxes are ML Kit geometry;
     * extracted text remains exclusively from the mapped Paddle glyphs.
     */
    boolean extendStrokeSegment(float x0,float y0,float x1,float y1,float padding){
        float distance=(float)Math.hypot(x1-x0,y1-y0);
        int steps=Math.min(128,Math.max(1,(int)Math.ceil(
                distance/Math.max(3f,padding*0.5f))));
        boolean changed=false;
        for(int k=0;k<=steps;k++){
            float fraction=(float)k/steps;
            int i=hitNear(x0+(x1-x0)*fraction,
                    y0+(y1-y0)*fraction,padding);
            if(i<0)continue;
            if(strokeOrigin<0){
                strokeOrigin=i;anchor=extent=i;all=false;active=0;
                changed=true;
            }else if(words.get(i).block.equals(words.get(strokeOrigin).block)){
                int first=Math.min(strokeOrigin,i),last=Math.max(strokeOrigin,i);
                // Preserve the complete visited range even if the stroke
                // doubles back or motion events are delivered out of order.
                first=Math.min(first,anchor);
                last=Math.max(last,extent);
                if(first!=anchor||last!=extent){
                    anchor=first;extent=last;changed=true;
                }
            }
        }
        return changed;
    }

    private int hitNear(float x,float y,float padding){
        int hit=hit(x,y);
        if(hit>=0)return hit;
        int closest=-1;
        float nearest=Float.POSITIVE_INFINITY;
        for(int i=0;i<words.size();i++){
            Rect r=words.get(i).box;
            float dx=Math.max(r.left-x,Math.max(0f,x-r.right));
            float dy=Math.max(r.top-y,Math.max(0f,y-r.bottom));
            float d=dx*dx+dy*dy;
            if(d<=padding*padding && d<nearest){nearest=d;closest=i;}
        }
        return closest;
    }

    boolean begin(float x,float y,float handleRadius,float bitmapStem){
        if(!selected()||all)return false;
        Rect first=words.get(anchor).box,last=words.get(extent).box;
        float start=(float)Math.hypot(x-first.left,y-(first.bottom+bitmapStem));
        float end=(float)Math.hypot(x-last.right,y-(last.bottom+bitmapStem));
        if(Math.min(start,end)>handleRadius)return false;
        active=start<=end?1:2;
        return true;
    }
    boolean drag(float x,float y){
        if(active==0||!selected()||all)return false;
        int i=hit(x,y);
        if(i<0)return false; // Wallpaper does not snap to remote text.
        OcrV3HandleRange moved=OcrV3HandleRange.move(
                anchor,extent,i,active,
                words.get(i).block.equals(words.get(anchor).block));
        anchor=moved.first;extent=moved.last;active=moved.moving;
        return true;
    }
    void release(){active=0;}
    int first(){return anchor;}
    int last(){return extent;}

    List<Rect> highlights(){
        if(!selected())return List.of();
        ArrayList<Rect> r=new ArrayList<>();
        if(all){for(Word w:words)r.add(new Rect(w.box));return List.copyOf(r);}
        for(int i=anchor;i<=extent;i++)
            if(words.get(i).block.equals(words.get(anchor).block))
                r.add(new Rect(words.get(i).box));
        return List.copyOf(r);
    }
    Rect firstBox(){return selected()?new Rect(words.get(anchor).box):new Rect();}
    Rect lastBox(){return selected()?new Rect(words.get(extent).box):new Rect();}
    String text(){
        if(!selected())return "";
        if(all)return page.fullText;
        ArrayList<Integer> selected=new ArrayList<>();
        for(int i=anchor;i<=extent;i++)
            if(words.get(i).block.equals(words.get(anchor).block))
                selected.addAll(words.get(i).paddle);
        selected.sort(Comparator.comparingInt((Integer i)->page.glyphs.get(i).line)
                .thenComparingInt(i->page.glyphs.get(i).start));
        // Duplicate source symbols are never copied twice.
        ArrayList<Integer> unique=new ArrayList<>();
        int prev=-1;
        for(int idx:selected)if(idx!=prev){unique.add(idx);prev=idx;}
        return page.selectedText(unique);
    }
}
