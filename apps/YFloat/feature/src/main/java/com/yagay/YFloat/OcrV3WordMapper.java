package com.yagay.YFloat;

import android.graphics.Rect;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Two INDEPENDENT contracts:
 *  (1) selectable ML Kit Element geometry exists without any Paddle glyph;
 *  (2) optional Paddle CTC glyphs associate to real ML Elements by overlap.
 *
 * A missed detector glyph may never make an ML Element unselectable.
 * Chinese/Latin duplicate OCR passes are deduplicated geometrically.
 * ML recognition text is never copied or used to synthesize Paddle text.
 */
final class OcrV3WordMapper {
    static final class Result {
        final List<OcrV3Selection.Word> words;
        final int mappedGlyphs,unmappedGlyphs,missingSymbols,unmappedElements;
        Result(List<OcrV3Selection.Word> words,int mapped,int unmapped,
               int noSymbols,int unmappedElements){
            this.words=List.copyOf(words);
            mappedGlyphs=mapped;unmappedGlyphs=unmapped;
            missingSymbols=noSymbols;this.unmappedElements=unmappedElements;
        }
    }
    private OcrV3WordMapper(){}

    static Result map(OcrV3Page page,List<OcrV3MlLayout.Word> source){
        if(source==null||source.isEmpty())
            return new Result(List.of(),0,page==null?0:page.glyphs.size(),0,0);
        ArrayList<OcrV3WordVisibility.Entry> candidates=new ArrayList<>();
        for(int i=0;i<source.size();i++){
            OcrV3MlLayout.Word ml=source.get(i);
            Rect b=ml.box;
            candidates.add(new OcrV3WordVisibility.Entry(
                    i,b.left,b.top,b.right,b.bottom,ml.symbols.size(),
                    ml.block.startsWith("latin:")));
        }
        ArrayList<Integer> kept=new ArrayList<>(
                OcrV3WordVisibility.select(candidates));
        int withoutSymbols=0;
        for(int original:kept)
            if(source.get(original).symbols.isEmpty())withoutSymbols++;
        Map<String,Rect> blockBounds=new HashMap<>();
        for(int original:kept){
            OcrV3MlLayout.Word word=source.get(original);
            Rect existing=blockBounds.get(word.block);
            if(existing==null)blockBounds.put(word.block,new Rect(word.box));
            else existing.union(word.box);
        }
        kept.sort(Comparator.comparingInt((Integer i)->blockBounds.get(source.get(i).block).top)
                .thenComparingInt(i->blockBounds.get(source.get(i).block).left)
                .thenComparing(i->source.get(i).block)
                .thenComparingInt(i->source.get(i).line)
                .thenComparingInt(i->source.get(i).element));

        List<ArrayList<Integer>> assignments=new ArrayList<>(kept.size());
        for(int i=0;i<kept.size();i++)assignments.add(new ArrayList<>());
        int mapped=0;
        if(page!=null){
            for(int glyphId=0;glyphId<page.glyphs.size();glyphId++){
                OcrV3Page.Glyph glyph=page.glyphs.get(glyphId);
                Rect rect=glyph.rect;
                if(rect.isEmpty())continue;
                int best=-1;
                double score=Double.NEGATIVE_INFINITY;
                for(int i=0;i<kept.size();i++){
                    OcrV3MlLayout.Word word=source.get(kept.get(i));
                    Rect box=word.box;
                    double candidate=OcrV3GeometryMatch.score(
                            rect.left,rect.top,rect.right,rect.bottom,
                            box.left,box.top,box.right,box.bottom);
                    if(!Double.isFinite(candidate))continue;
                    // A genuine matching ML Symbol is only a small tie-break.
                    for(OcrV3MlLayout.Symbol symbol:word.symbols)
                        if(glyph.text.equals(symbol.observed)&&
                                Rect.intersects(rect,symbol.box)){
                            candidate+=.03d;break;
                        }
                    if(candidate>score){score=candidate;best=i;}
                }
                if(best>=0){assignments.get(best).add(glyphId);mapped++;}
            }
        }
        ArrayList<OcrV3Selection.Word> result=new ArrayList<>();
        int withoutGlyphs=0;
        for(int i=0;i<kept.size();i++){
            OcrV3MlLayout.Word ml=source.get(kept.get(i));
            ArrayList<Integer> indices=assignments.get(i);
            if(indices.isEmpty())withoutGlyphs++;
            if(page!=null)indices.sort(
                    Comparator.comparingInt((Integer id)->page.glyphs.get(id).line)
                    .thenComparingInt(id->page.glyphs.get(id).start));
            result.add(new OcrV3Selection.Word(
                    ml.block,ml.box,indices,ml.symbols));
        }
        return new Result(result,mapped,page==null?0:page.glyphs.size()-mapped,
                withoutSymbols,withoutGlyphs);
    }
}
