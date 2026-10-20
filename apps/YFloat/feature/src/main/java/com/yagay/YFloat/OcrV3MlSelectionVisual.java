package com.yagay.YFloat;

import java.util.ArrayList;
import java.util.List;

/**
 * One and only blue-highlight geometry owner for OCR V3.
 *
 * All rectangles here originate in ML Kit Elements or ML Kit Symbols.
 * Paddle's CTC character boxes may help match Symbols to source text, but
 * must NEVER reach this renderer. The same Element-sized highlight is drawn
 * before and after full-word expansion; partial words may use a continuous
 * horizontal span of genuine ML Symbol bounds. No individual Paddle glyph
 * rectangles or synthetic widths are drawn.
 *
 * Pure Java so the invariant is enforced by JVM tests without Android stubs.
 */
final class OcrV3MlSelectionVisual {
    static final class Box {
        final int left,top,right,bottom;
        Box(int l,int t,int r,int b){
            left=l;top=t;right=r;bottom=b;
        }
        boolean valid(){return left<right&&top<bottom;}
        boolean equalsBox(Box o){
            return o!=null&&left==o.left&&top==o.top
                    &&right==o.right&&bottom==o.bottom;
        }
    }

    static final class Snapshot {
        final List<Box> highlights;
        final Box start,end;
        final int fallbackWords,partialWords;
        Snapshot(List<Box> highlights,Box start,Box end,
                 int fallbackWords,int partialWords){
            this.highlights=List.copyOf(highlights);
            this.start=start;this.end=end;
            this.fallbackWords=fallbackWords;
            this.partialWords=partialWords;
        }
    }

    private static final class Bucket {
        int count,selected;
        int left=Integer.MAX_VALUE,right=Integer.MIN_VALUE;
        boolean allSymbols=true;
    }

    private OcrV3MlSelectionVisual(){}

    /**
     * Expand a Paddle glyph INDEX range but display only ML-owned geometry.
     * Every selected ML Element is painted exactly once, not once per Paddle
     * glyph (the latter changes height/shape and multiplies blue alpha).
     */
    static Snapshot expanded(List<Box> elements,OcrV3ExpansionController range){
        if(range==null||!range.hasRange()||elements.isEmpty())
            return new Snapshot(List.of(),null,null,0,0);
        Bucket[] buckets=new Bucket[elements.size()];
        for(int i=0;i<elements.size();i++)buckets[i]=new Bucket();
        for(int i=0;i<range.size();i++){
            OcrV3ExpansionController.Unit u=range.unit(i);
            int owner=u.wordId;
            if(owner<0||owner>=elements.size())continue;
            Bucket b=buckets[owner];
            b.count++;
            if(i<range.first()||i>range.last())continue;
            b.selected++;
            Box element=elements.get(owner);
            boolean valid=u.mlSymbol && u.left<u.right && u.top<u.bottom
                    && u.right>element.left && u.left<element.right
                    && u.bottom>element.top && u.top<element.bottom;
            if(!valid){b.allSymbols=false;continue;}
            b.left=Math.min(b.left,Math.max(element.left,u.left));
            b.right=Math.max(b.right,Math.min(element.right,u.right));
        }
        ArrayList<Box> selected=new ArrayList<>();
        Box[] painted=new Box[elements.size()];
        int fallback=0,partial=0;
        for(int wordId=0;wordId<buckets.length;wordId++){
            Bucket b=buckets[wordId];
            if(b.selected==0)continue;
            Box mlElement=elements.get(wordId);
            Box finalBox=mlElement;
            if(b.selected<b.count){
                partial++;
                // If even ONE selected character has no trustworthy ML Symbol
                // geometry, highlight its actual ML Element instead of
                // switching to an unrelated Paddle glyph rectangle.
                if(b.allSymbols&&b.left<b.right){
                    finalBox=new Box(b.left,mlElement.top,
                            b.right,mlElement.bottom);
                }else fallback++;
            }
            painted[wordId]=finalBox;
            selected.add(finalBox);
        }
        int startWord=range.unit(range.first()).wordId;
        int endWord=range.unit(range.last()).wordId;
        Box first=startWord>=0&&startWord<painted.length
                ?painted[startWord]:null;
        Box last=endWord>=0&&endWord<painted.length
                ?painted[endWord]:null;
        return new Snapshot(selected,first,last,fallback,partial);
    }
}
