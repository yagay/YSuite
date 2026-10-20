package com.yagay.YFloat;

import android.graphics.Rect;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * V3 engine boundary: associate ML's actual Element rectangles with Paddle's
 * original CTC glyph rectangles. Neither engine's text is edited or replaced.
 * A glyph belongs to at most one ML Element, even when Chinese and Latin
 * recognizers detect the same text twice. No nearest-word snapping.
 */
final class OcrV3WordMapper {
    static final class Result {
        final List<OcrV3Selection.Word> words;
        final int mappedGlyphs,unmappedGlyphs,missingSymbols;
        Result(List<OcrV3Selection.Word> words,int mapped,int unmapped,int noSymbols){
            this.words=List.copyOf(words);
            mappedGlyphs=mapped;unmappedGlyphs=unmapped;missingSymbols=noSymbols;
        }
    }
    private OcrV3WordMapper(){}

    static Result map(OcrV3Page page,List<OcrV3MlLayout.Word> source){
        if(page==null||source==null||source.isEmpty())
            return new Result(List.of(),0,page==null?0:page.glyphs.size(),0);
        List<ArrayList<Integer>> assignments=new ArrayList<>(source.size());
        for(int i=0;i<source.size();i++)assignments.add(new ArrayList<>());
        int mapped=0;
        for(int glyphIndex=0;glyphIndex<page.glyphs.size();glyphIndex++){
            OcrV3Page.Glyph glyph=page.glyphs.get(glyphIndex);
            Rect g=glyph.rect;
            if(g.isEmpty())continue;
            int best=-1;double bestScore=Double.NEGATIVE_INFINITY;
            for(int j=0;j<source.size();j++){
                OcrV3MlLayout.Word w=source.get(j);
                Rect box=w.box;
                if(box.isEmpty())continue;
                // Match only real overlapping image pixels, not OCR text
                // equality. ML Kit's output string cannot replace Paddle.
                double score=OcrV3GeometryMatch.score(g.left,g.top,g.right,
                        g.bottom,box.left,box.top,box.right,box.bottom);
                if(!Double.isFinite(score))continue;
                // CTC text can differ from ML symbol text; equality is only a
                // tie breaker, NEVER a requirement or source of copied text.
                for(OcrV3MlLayout.Symbol symbol:w.symbols)
                    if(glyph.text.equals(symbol.observed) && Rect.intersects(g,symbol.box)){
                        score+=.03;break;
                    }
                if(score>bestScore){best=j;bestScore=score;}
            }
            if(best>=0){
                assignments.get(best).add(glyphIndex);
                mapped++;
            }
        }
        ArrayList<Integer> kept=new ArrayList<>();
        int withoutSymbols=0;
        for(int i=0;i<source.size();i++){
            if(source.get(i).symbols.isEmpty())withoutSymbols++;
            if(!assignments.get(i).isEmpty())kept.add(i);
        }
        Map<String,Rect> blockBounds=new HashMap<>();
        for(int i:kept){
            OcrV3MlLayout.Word word=source.get(i);
            Rect all=blockBounds.get(word.block);
            if(all==null)blockBounds.put(word.block,new Rect(word.box));
            else all.union(word.box);
        }
        kept.sort(Comparator.comparingInt((Integer i)->blockBounds.get(source.get(i).block).top)
                .thenComparingInt(i->blockBounds.get(source.get(i).block).left)
                .thenComparing(i->source.get(i).block)
                .thenComparingInt(i->source.get(i).line)
                .thenComparingInt(i->source.get(i).element));
        ArrayList<OcrV3Selection.Word> result=new ArrayList<>();
        for(int i:kept){
            ArrayList<Integer> original=assignments.get(i);
            original.sort(Comparator.comparingInt((Integer id)->page.glyphs.get(id).line)
                    .thenComparingInt(id->page.glyphs.get(id).start));
            OcrV3MlLayout.Word ml=source.get(i);
            result.add(new OcrV3Selection.Word(ml.block,ml.box,original));
        }
        return new Result(result,mapped,page.glyphs.size()-mapped,withoutSymbols);
    }
}
