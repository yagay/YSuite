package com.yagay.YFloat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OCR V4's sole selection-document schema. Immutable ML Kit geometry,
 * independently attached Paddle ORIGINAL line text, and stable symbol IDs.
 *
 * No Paddle glyph bounding boxes, candidate word names or inferred text.
 * A line is either verified source-aligned or unavailable for copy.
 */
final class OcrV4Document {
    static final class Box {
        final int left,top,right,bottom;
        Box(int left,int top,int right,int bottom){
            this.left=left;this.top=top;this.right=right;this.bottom=bottom;
        }
        boolean valid(){return right>left&&bottom>top;}
        Box union(Box b){
            return new Box(Math.min(left,b.left),Math.min(top,b.top),
                    Math.max(right,b.right),Math.max(bottom,b.bottom));
        }
    }
    static final class SymbolSpec {
        final String observed;
        final Box box;
        SymbolSpec(String observed,Box box){
            this.observed=observed==null?"":observed;this.box=box;
        }
    }
    static final class WordSpec {
        final String block;
        final int line,element;
        final Box box;
        final List<SymbolSpec> symbols;
        WordSpec(String block,int line,int element,Box box,List<SymbolSpec> symbols){
            this.block=block;this.line=line;this.element=element;
            this.box=box;this.symbols=List.copyOf(symbols);
        }
    }
    static final class Unit {
        final int index,wordId,lineId,lineOffset;
        final Box box;
        final boolean mlSymbol;
        Unit(int id,int word,int line,int offset,Box box,boolean mlSymbol){
            index=id;wordId=word;lineId=line;lineOffset=offset;
            this.box=box;this.mlSymbol=mlSymbol;
        }
    }
    static final class Word {
        final int id,lineId,firstUnit,lastUnit;
        final String block;
        final Box box;
        Word(int id,int lineId,int first,int last,String block,Box box){
            this.id=id;this.lineId=lineId;firstUnit=first;lastUnit=last;
            this.block=block;this.box=box;
        }
    }
    static final class Line {
        final int id;
        final Box box;
        final List<String> visualSymbols;
        final int firstUnit,lastUnit;
        Line(int id,Box box,List<String> symbols,int first,int last){
            this.id=id;this.box=box;
            visualSymbols=List.copyOf(symbols);
            firstUnit=first;lastUnit=last;
        }
    }
    private final List<Word> words;
    private final List<Unit> units;
    private final List<Line> lines;
    private final Map<Integer,OcrV4Alignment.Result> source=new HashMap<>();
    /** Independently recognized Paddle source for a COMPLETE ML word.
     *  Partial-character copy still requires validated symbol alignment. */
    private final Map<Integer,WordSource> wordSource=new HashMap<>();
    private static final class WordSource {
        final String raw;
        final OcrV4Alignment.Result aligned;
        WordSource(String raw,OcrV4Alignment.Result aligned){
            this.raw=raw;this.aligned=aligned;
        }
    }
    private final int rejectedDuplicates;

    private OcrV4Document(List<Word> words,List<Unit> units,
                          List<Line> lines,int rejectedDuplicates){
        this.words=List.copyOf(words);
        this.units=List.copyOf(units);
        this.lines=List.copyOf(lines);
        this.rejectedDuplicates=rejectedDuplicates;
    }

    static OcrV4Document build(List<WordSpec> input){
        ArrayList<OcrV3WordVisibility.Entry> candidates=new ArrayList<>();
        for(int i=0;i<input.size();i++){
            WordSpec w=input.get(i);
            Box b=w.box;
            candidates.add(new OcrV3WordVisibility.Entry(i,b.left,b.top,b.right,
                    b.bottom,w.symbols.size(),w.block.startsWith("latin:")));
        }
        List<Integer> selected=OcrV3WordVisibility.select(candidates);
        // Group by the actual ML line in its owning TextBlock. Each visual
        // word can appear only once after ML Chinese/Latin deduplication.
        Map<String,ArrayList<WordSpec>> groups=new LinkedHashMap<>();
        for(int id:selected){
            WordSpec spec=input.get(id);
            String groupKey=spec.block+"#"+spec.line;
            groups.computeIfAbsent(groupKey,k->new ArrayList<>()).add(spec);
        }
        ArrayList<ArrayList<WordSpec>> ordered=new ArrayList<>(groups.values());
        ordered.sort(Comparator.comparingInt(
                (ArrayList<WordSpec> group)->group.stream()
                        .mapToInt(w->w.box.top).min().orElse(0))
                .thenComparingInt(group->group.stream()
                        .mapToInt(w->w.box.left).min().orElse(0)));
        ArrayList<Word> resultWords=new ArrayList<>();
        ArrayList<Unit> resultUnits=new ArrayList<>();
        ArrayList<Line> resultLines=new ArrayList<>();
        for(ArrayList<WordSpec> group:ordered){
            group.sort(Comparator.comparingInt((WordSpec w)->w.box.left)
                    .thenComparingInt(w->w.element));
            int lineId=resultLines.size(),first=resultUnits.size();
            Box bounds=null;
            ArrayList<String> observed=new ArrayList<>();
            for(WordSpec word:group){
                bounds=bounds==null?word.box:bounds.union(word.box);
                int wordId=resultWords.size(),wordStart=resultUnits.size();
                if(word.symbols.isEmpty()){
                    // A real ML Element can be highlighted even if no Symbol
                    // is available; this does NOT synthesize a text character.
                    resultUnits.add(new Unit(resultUnits.size(),wordId,lineId,
                            observed.size(),word.box,false));
                    observed.add("");
                }else{
                    for(SymbolSpec symbol:word.symbols){
                        resultUnits.add(new Unit(resultUnits.size(),wordId,lineId,
                                observed.size(),symbol.box,true));
                        observed.add(symbol.observed);
                    }
                }
                resultWords.add(new Word(wordId,lineId,wordStart,
                        resultUnits.size()-1,word.block,word.box));
            }
            resultLines.add(new Line(lineId,bounds,observed,first,
                    resultUnits.size()-1));
        }
        return new OcrV4Document(resultWords,resultUnits,resultLines,
                input.size()-selected.size());
    }

    int wordCount(){return words.size();}
    int unitCount(){return units.size();}
    int lineCount(){return lines.size();}
    int duplicates(){return rejectedDuplicates;}
    Word word(int id){return words.get(id);}
    Unit unit(int index){return units.get(index);}
    Line line(int id){return lines.get(id);}

    boolean accept(int lineId,String raw,float confidence){
        if(lineId<0||lineId>=lines.size()||
                raw==null||!Float.isFinite(confidence)||confidence<0.50f)
            return false;
        OcrV4Alignment.Result aligned=OcrV4Alignment.align(
                lines.get(lineId).visualSymbols,raw);
        if(!aligned.reliable)return false;
        source.put(lineId,aligned);
        return true;
    }
    boolean ready(int id){return source.containsKey(id);}

    /**
     * Independent Paddle ROI for ONE ML Element, not the same failed LINE
     * crop. The original Paddle transcription owns all copied characters.
     * ML's symbol count only constrains whether the crop probably absorbed
     * unrelated neighbouring text; it never supplies source text.
     */
    boolean acceptWord(int wordId,String raw,float confidence){
        if(wordId<0||wordId>=words.size()||raw==null||raw.isBlank()||
                !Float.isFinite(confidence)||confidence<.85f)
            return false;
        Word word=words.get(wordId);
        String candidate=raw;
        if(candidate.codePoints().anyMatch(Character::isISOControl) ||
                candidate.codePoints().anyMatch(Character::isWhitespace))
            return false; // One ML Element must remain ONE Paddle token.
        int actual=candidate.codePointCount(0,candidate.length());
        if(actual<1||actual>128)return false;
        List<String> symbols=lines.get(word.lineId).visualSymbols.subList(
                units.get(word.firstUnit).lineOffset,
                units.get(word.lastUnit).lineOffset+1);
        int observed=0;
        for(String symbol:symbols)observed+=
                symbol.codePointCount(0,symbol.length());
        if(observed>0 && actual>observed+Math.max(2,observed/3))
            return false;
        OcrV4Alignment.Result aligned=OcrV4Alignment.align(symbols,candidate);
        wordSource.put(wordId,new WordSource(candidate,aligned));
        return true;
    }

    boolean wordReady(int wordId){return wordSource.containsKey(wordId);}


    List<Integer> lineIds(int firstUnit,int lastUnit){
        if(firstUnit<0||lastUnit>=units.size()||lastUnit<firstUnit)
            return List.of();
        ArrayList<Integer> result=new ArrayList<>();
        int prev=-1;
        for(int i=firstUnit;i<=lastUnit;i++){
            int lineId=units.get(i).lineId;
            if(lineId!=prev){result.add(lineId);prev=lineId;}
        }
        return List.copyOf(result);
    }

    /** Returns empty if ANY source line is not proven aligned. */
    String selectedText(int firstUnit,int lastUnit){
        List<Integer> selectedLines=lineIds(firstUnit,lastUnit);
        if(selectedLines.isEmpty())return "";
        // For a complete single ML Element, Paddle's independent word ROI
        // remains valid even when the two OCR engines disagree on spelling.
        // Never apply this bypass to partial-symbol or multiword selection.
        int firstWord=units.get(firstUnit).wordId;
        int lastWord=units.get(lastUnit).wordId;
        if(firstWord==lastWord){
            Word w=words.get(firstWord);
            WordSource local=wordSource.get(firstWord);
            if(local!=null){
                if(firstUnit==w.firstUnit&&lastUnit==w.lastUnit)
                    return local.raw;
                if(local.aligned.reliable){
                    int a=units.get(firstUnit).lineOffset-
                            units.get(w.firstUnit).lineOffset;
                    int b=units.get(lastUnit).lineOffset-
                            units.get(w.firstUnit).lineOffset;
                    return local.aligned.slice(a,b);
                }
                // Do not infer character offsets from unequal OCR strings.
                return "";
            }
        }
        StringBuilder output=new StringBuilder();
        for(int id:selectedLines){
            OcrV4Alignment.Result a=source.get(id);
            if(a==null)return "";
            int first=-1,last=-1;
            for(int i=firstUnit;i<=lastUnit;i++){
                Unit u=units.get(i);
                if(u.lineId!=id)continue;
                if(first<0)first=u.lineOffset;
                last=u.lineOffset;
            }
            String part=a.slice(first,last);
            if(part.isEmpty())return "";
            if(output.length()>0)output.append('\n');
            output.append(part);
        }
        return output.toString();
    }
}
