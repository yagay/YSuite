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

    void clear(){anchor=extent=-1;active=0;all=false;}
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
        anchor=extent=i;active=0;all=false;return true;
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
