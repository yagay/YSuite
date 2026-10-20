package com.yagay.YFloat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Canonical ML Kit visual-word eligibility, independent of Paddle entirely.
 * Deduplicates overlapping Chinese/Latin detections by real geometry. A word
 * without a matching Paddle source glyph remains selectable.
 */
final class OcrV3WordVisibility {
    static final class Entry {
        final int index,left,top,right,bottom,symbols;
        final boolean latin;
        Entry(int index,int l,int t,int r,int b,int symbols,boolean latin){
            this.index=index;left=l;top=t;right=r;bottom=b;
            this.symbols=symbols;this.latin=latin;
        }
        long area(){return (long)(right-left)*(bottom-top);}
        boolean valid(){return right>left&&bottom>top;}
    }
    private OcrV3WordVisibility(){}

    private static boolean duplicates(Entry a,Entry b){
        int dx=Math.min(a.right,b.right)-Math.max(a.left,b.left);
        int dy=Math.min(a.bottom,b.bottom)-Math.max(a.top,b.top);
        if(dx<=0||dy<=0)return false;
        long overlap=(long)dx*dy;
        return overlap>=.78*Math.min(a.area(),b.area())
                &&overlap>=.62*Math.max(a.area(),b.area());
    }

    static List<Integer> select(List<Entry> candidates){
        ArrayList<Entry> sorted=new ArrayList<>();
        for(Entry word:candidates)if(word.valid())sorted.add(word);
        sorted.sort(Comparator
                .comparingInt((Entry e)->e.symbols).reversed()
                .thenComparingInt(e->e.latin?0:1)
                .thenComparingInt(e->e.index));
        ArrayList<Entry> distinct=new ArrayList<>();
        for(Entry candidate:sorted){
            boolean dup=false;
            for(Entry kept:distinct)
                if(duplicates(candidate,kept)){dup=true;break;}
            if(!dup)distinct.add(candidate);
        }
        ArrayList<Integer> indices=new ArrayList<>(distinct.size());
        for(Entry word:distinct)indices.add(word.index);
        return List.copyOf(indices);
    }
}
