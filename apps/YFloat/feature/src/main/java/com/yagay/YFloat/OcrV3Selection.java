package com.yagay.YFloat;

import android.graphics.Rect;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

    OcrV3Selection(OcrV3Page page,List<OcrV3MlLayout.Word> ml){
        this.page=page;
        ArrayList<Word> aligned=new ArrayList<>();
        Set<Integer> assigned=new HashSet<>();
        if(page!=null&&ml!=null)for(OcrV3MlLayout.Word w:ml){
            ArrayList<Integer> indices=new ArrayList<>();
            for(OcrV3MlLayout.Symbol s:w.symbols){
                int found=-1;
                float best=Float.POSITIVE_INFINITY;
                for(int i=0;i<page.glyphs.size();i++){
                    if(assigned.contains(i))continue;
                    OcrV3Page.Glyph g=page.glyphs.get(i);
                    if(!g.text.equals(s.observed))continue;
                    float dx=g.rect.exactCenterX()-s.box.exactCenterX();
                    float dy=g.rect.exactCenterY()-s.box.exactCenterY();
                    float scale=Math.max(6f,Math.max(g.rect.height(),s.box.height()));
                    float norm=(dx*dx+dy*dy)/(scale*scale);
                    if(norm<.9f&&norm<best){best=norm;found=i;}
                }
                if(found>=0){indices.add(found);assigned.add(found);}
            }
            if(indices.isEmpty())continue; // Never use ML-decoded text as output.
            indices.sort(Comparator.comparingInt((Integer i)->page.glyphs.get(i).line)
                    .thenComparingInt(i->page.glyphs.get(i).start));
            aligned.add(new Word(w.block,w.box,indices));
        }
        words=List.copyOf(aligned);
    }

    int size(){return words.size();}
    boolean selected(){return anchor>=0&&extent>=0;}
    boolean handleActive(){return active!=0;}

    void clear(){anchor=extent=-1;active=0;}
    int hit(float x,float y){
        // ML Kit's original Element boxes are the only hit regions.
        for(int i=0;i<words.size();i++)
            if(words.get(i).box.contains((int)x,(int)y))return i;
        return -1;
    }
    boolean tap(float x,float y){
        int i=hit(x,y);
        if(i<0){clear();return false;}
        anchor=extent=i;active=0;return true;
    }
    boolean begin(float x,float y,float handleRadius){
        if(!selected())return false;
        Rect first=words.get(anchor).box,last=words.get(extent).box;
        float start=(float)Math.hypot(x-first.left,y-(first.bottom+7f));
        float end=(float)Math.hypot(x-last.right,y-(last.bottom+7f));
        if(Math.min(start,end)>handleRadius)return false;
        active=start<=end?1:2;
        return true;
    }
    boolean drag(float x,float y){
        if(active==0||!selected())return false;
        int i=hit(x,y);
        if(i<0)return false; // Wallpaper does not snap to remote text.
        if(!words.get(i).block.equals(words.get(anchor).block)){
            // ML Kit treats distinct TextBlocks as separate selections;
            // touching another icon starts that label, never a disjoint range.
            anchor=extent=i;
            return true;
        }
        if(active==1)anchor=i;
        else extent=i;
        if(anchor>extent){
            int old=anchor;anchor=extent;extent=old;
            active=active==1?2:1;
        }
        return true;
    }
    void release(){active=0;}
    int first(){return anchor;}
    int last(){return extent;}

    List<Rect> highlights(){
        if(!selected())return List.of();
        ArrayList<Rect> r=new ArrayList<>();
        for(int i=anchor;i<=extent;i++)
            if(words.get(i).block.equals(words.get(anchor).block))
                r.add(new Rect(words.get(i).box));
        return List.copyOf(r);
    }
    Rect firstBox(){return selected()?new Rect(words.get(anchor).box):new Rect();}
    Rect lastBox(){return selected()?new Rect(words.get(extent).box):new Rect();}
    String text(){
        if(!selected())return "";
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
