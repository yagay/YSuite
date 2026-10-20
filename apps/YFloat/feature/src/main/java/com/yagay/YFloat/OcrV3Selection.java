package com.yagay.YFloat;

import android.graphics.Rect;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * OCR V3 text selection with two independent input modes:
 *
 * 1) ML Element strokes/taps preserve the already working word selection.
 * 2) A dedicated glyph-level handle session extends/shrinks continuously
 *    across lines and ML blocks in the original Paddle reading order.
 *
 * ML Kit contributes REAL geometry only; Paddle remains the sole source for
 * selected text, original spacing, punctuation and UTF-16 offsets.
 */
final class OcrV3Selection {
    static final class Word {
        final String block;
        final Rect box;
        final List<Integer> paddle;
        final List<OcrV3MlLayout.Symbol> symbols;
        Word(String block,Rect box,List<Integer> indices){
            this(block,box,indices,List.of());
        }
        Word(String block,Rect box,List<Integer> indices,
             List<OcrV3MlLayout.Symbol> symbols){
            this.block=block;this.box=new Rect(box);
            paddle=List.copyOf(indices);
            this.symbols=List.copyOf(symbols);
        }
    }

    private final OcrV3Page page;
    private final List<Word> words;
    private final OcrV3WordMapper.Result mapping;
    private final OcrV3StrokeRange strokeRange;
    private final OcrV3ExpansionController expansion;
    private int anchor=-1,extent=-1; // current ML word selection
    private boolean all,expanded;

    OcrV3Selection(OcrV3Page page,List<OcrV3MlLayout.Word> ml){
        this.page=page;
        mapping=OcrV3WordMapper.map(page,ml);
        words=mapping.words;
        ArrayList<OcrV3StrokeRange.Word> boxes=new ArrayList<>(words.size());
        for(Word w:words){
            Rect b=w.box;
            boxes.add(new OcrV3StrokeRange.Word(
                    w.block,b.left,b.top,b.right,b.bottom));
        }
        strokeRange=new OcrV3StrokeRange(boxes);
        expansion=new OcrV3ExpansionController(buildUnits(),words.size());
    }

    /** Prefer matching ML symbols; use genuine Paddle glyph rectangles when
     * ML does not expose enough usable symbols. Never synthesize character
     * widths from guessed OCR text or rewrite OCR strings. */
    private List<OcrV3ExpansionController.Unit> buildUnits(){
        ArrayList<OcrV3ExpansionController.Unit> out=new ArrayList<>();
        Set<Integer> owned=new HashSet<>();
        for(int wordId=0;wordId<words.size();wordId++){
            Word w=words.get(wordId);
            boolean[] used=new boolean[w.symbols.size()];
            for(int glyphId:w.paddle){
                if(glyphId<0||glyphId>=page.glyphs.size()||
                        !owned.add(glyphId))continue;
                OcrV3Page.Glyph g=page.glyphs.get(glyphId);
                Rect geometry=g.rect;
                int best=-1;
                double bestScore=Double.NEGATIVE_INFINITY;
                if(!geometry.isEmpty()){
                    for(int i=0;i<w.symbols.size();i++){
                        if(used[i])continue;
                        OcrV3MlLayout.Symbol s=w.symbols.get(i);
                        Rect r=s.box;
                        if(r.isEmpty())continue;
                        double score=OcrV3GeometryMatch.score(
                                geometry.left,geometry.top,
                                geometry.right,geometry.bottom,
                                r.left,r.top,r.right,r.bottom);
                        if(!Double.isFinite(score))continue;
                        if(g.text.equals(s.observed))score+=.03d;
                        if(score>bestScore){bestScore=score;best=i;}
                    }
                }
                if(best>=0){
                    used[best]=true;
                    geometry=w.symbols.get(best).box;
                }else if(geometry.isEmpty()||
                        !Rect.intersects(geometry,w.box)){
                    geometry=w.box; // exact ML Element, not a fabricated glyph
                }
                out.add(new OcrV3ExpansionController.Unit(
                        glyphId,g.line,g.start,
                        geometry.left,geometry.top,
                        geometry.right,geometry.bottom,wordId));
            }
        }
        return List.copyOf(out);
    }

    int matchedGlyphs(){return mapping.mappedGlyphs;}
    int unmatchedGlyphs(){return mapping.unmappedGlyphs;}
    int missingMlSymbols(){return mapping.missingSymbols;}
    int size(){return words.size();}
    int expansionUnits(){return expansion.size();}
    boolean selected(){return all||expanded||(anchor>=0&&extent>=0);}
    boolean handleActive(){return expansion.moving()!=0;}
    boolean expanded(){return expanded;}
    int movingHandle(){return expansion.moving();}

    void clear(){
        anchor=extent=-1;all=false;expanded=false;
        strokeRange.clear();expansion.clear();
    }

    void selectAll(){
        clear();
        if(!words.isEmpty()){
            anchor=0;extent=words.size()-1;all=true;
        }
    }

    int hit(float x,float y){
        for(int i=0;i<words.size();i++)
            if(words.get(i).box.contains((int)x,(int)y))return i;
        return -1;
    }

    boolean tap(float x,float y){
        int i=hit(x,y);
        if(i<0){clear();return false;}
        clear();
        anchor=extent=i;
        strokeRange.start(i);
        return true;
    }

    /** Keep the proven freehand gesture behavior separate from grip expansion. */
    boolean extendStrokeSegment(float x0,float y0,float x1,float y1,
                                float padding){
        if(all)return false;
        if(expanded){
            expanded=false;expansion.clear();
        }
        boolean changed=strokeRange.extend(x0,y0,x1,y1,padding);
        if(changed){
            anchor=strokeRange.first();extent=strokeRange.last();
        }
        return changed;
    }

    /**
     * Prepare a new handle session from selected ML words, but preserve
     * their visual result until the user actually moves a grip.
     */
    boolean begin(float x,float y,float handleRadius,float bitmapStem){
        if(!selected()||all)return false;
        Rect a=firstBox(),b=lastBox();
        float dStart=(float)Math.hypot(x-a.left,y-(a.bottom+bitmapStem));
        float dEnd=(float)Math.hypot(x-b.right,y-(b.bottom+bitmapStem));
        if(Math.min(dStart,dEnd)>handleRadius)return false;
        List<Integer> wordIds=new ArrayList<>();
        if(expanded){
            // Retain the already expanded range for consecutive handle drags.
            // Never overwrite its fine-grained endpoints from word geometry.
            return expansion.begin(dStart<=dEnd
                    ?OcrV3ExpansionController.START
                    :OcrV3ExpansionController.END,x,y);
        }
        if(anchor<0||extent<0)return false;
        for(int i=Math.min(anchor,extent);i<=Math.max(anchor,extent);i++){
            if(words.get(i).block.equals(words.get(anchor).block))
                wordIds.add(i);
        }
        if(!expansion.prepare(wordIds))return false;
        return expansion.begin(dStart<=dEnd
                ?OcrV3ExpansionController.START
                :OcrV3ExpansionController.END,x,y);
    }

    /**
     * Real screen position, compensated for the original finger-to-handle
     * offset. Never replace the anchor with an unrelated TextBlock.
     */
    boolean drag(float x,float y,float nearestDistance){
        boolean changed=expansion.drag(x,y,nearestDistance);
        if(changed)expanded=true;
        return changed;
    }

    void release(){expansion.release();}
    int first(){return expanded?expansion.first():anchor;}
    int last(){return expanded?expansion.last():extent;}

    List<Rect> highlights(){
        if(!selected())return List.of();
        ArrayList<Rect> boxes=new ArrayList<>();
        if(all){
            for(Word w:words)boxes.add(new Rect(w.box));
        }else if(expanded&&expansion.hasRange()){
            for(int i=expansion.first();i<=expansion.last();i++){
                OcrV3ExpansionController.Unit g=expansion.unit(i);
                boxes.add(new Rect(g.left,g.top,g.right,g.bottom));
            }
        }else if(anchor>=0&&extent>=0){
            for(int i=Math.min(anchor,extent);i<=Math.max(anchor,extent);i++)
                if(words.get(i).block.equals(words.get(anchor).block))
                    boxes.add(new Rect(words.get(i).box));
        }
        return List.copyOf(boxes);
    }

    Rect firstBox(){
        if(!selected())return new Rect();
        if(expanded&&expansion.hasRange()){
            OcrV3ExpansionController.Unit u=expansion.unit(expansion.first());
            return new Rect(u.left,u.top,u.right,u.bottom);
        }
        return new Rect(words.get(anchor).box);
    }

    Rect lastBox(){
        if(!selected())return new Rect();
        if(expanded&&expansion.hasRange()){
            OcrV3ExpansionController.Unit u=expansion.unit(expansion.last());
            return new Rect(u.left,u.top,u.right,u.bottom);
        }
        return new Rect(words.get(extent).box);
    }

    String text(){
        if(!selected())return "";
        if(all)return page.fullText;
        ArrayList<Integer> ids=new ArrayList<>();
        if(expanded&&expansion.hasRange()){
            for(int i=expansion.first();i<=expansion.last();i++)
                ids.add(expansion.unit(i).glyphId);
        }else if(anchor>=0&&extent>=0){
            for(int i=Math.min(anchor,extent);i<=Math.max(anchor,extent);i++)
                if(words.get(i).block.equals(words.get(anchor).block))
                    ids.addAll(words.get(i).paddle);
        }
        ids.sort(Comparator.comparingInt((Integer i)->page.glyphs.get(i).line)
                .thenComparingInt(i->page.glyphs.get(i).start));
        ArrayList<Integer> unique=new ArrayList<>();
        int prev=-1;
        for(int id:ids)if(id!=prev){unique.add(id);prev=id;}
        return page.selectedText(unique);
    }
}
