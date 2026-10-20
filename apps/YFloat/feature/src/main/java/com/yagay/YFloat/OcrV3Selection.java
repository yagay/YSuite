package com.yagay.YFloat;

import android.graphics.Rect;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
    private final Map<Integer,String> localPaddle=new HashMap<>();
    private final List<OcrV3MlSelectionVisual.Box> mlBoxes;
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
        ArrayList<OcrV3MlSelectionVisual.Box> renderBoxes=new ArrayList<>();
        for(Word word:words){
            Rect r=word.box;
            renderBoxes.add(new OcrV3MlSelectionVisual.Box(
                    r.left,r.top,r.right,r.bottom));
        }
        mlBoxes=List.copyOf(renderBoxes);
        expansion=new OcrV3ExpansionController(buildUnits(),words.size());
    }

    /**
     * Selection positions belong to ML Symbols, independently from Paddle
     * characters. A missing Paddle glyph is represented as -1 rather than
     * making the ML text or its handle unreachable.
     */
    private List<OcrV3ExpansionController.Unit> buildUnits(){
        ArrayList<OcrV3ExpansionController.Unit> out=new ArrayList<>();
        for(int wordId=0;wordId<words.size();wordId++){
            Word word=words.get(wordId);
            Set<Integer> used=new HashSet<>();
            if(word.symbols.isEmpty()){
                Rect box=word.box;
                out.add(new OcrV3ExpansionController.Unit(
                        -1,wordId,0,box.left,box.top,box.right,box.bottom,
                        wordId,false));
                continue;
            }
            for(int index=0;index<word.symbols.size();index++){
                OcrV3MlLayout.Symbol symbol=word.symbols.get(index);
                Rect box=symbol.box;
                int best=-1;
                double score=Double.NEGATIVE_INFINITY;
                for(int glyphId:word.paddle){
                    if(glyphId<0||glyphId>=page.glyphs.size()||
                            used.contains(glyphId))continue;
                    OcrV3Page.Glyph g=page.glyphs.get(glyphId);
                    Rect rect=g.rect;
                    double candidate=OcrV3GeometryMatch.score(
                            rect.left,rect.top,rect.right,rect.bottom,
                            box.left,box.top,box.right,box.bottom);
                    if(!Double.isFinite(candidate))continue;
                    if(g.text.equals(symbol.observed))candidate+=.03d;
                    if(candidate>score){score=candidate;best=glyphId;}
                }
                if(best>=0)used.add(best);
                out.add(new OcrV3ExpansionController.Unit(
                        best,wordId,index,box.left,box.top,box.right,
                        box.bottom,wordId,true));
            }
        }
        return List.copyOf(out);
    }

    int matchedGlyphs(){return mapping.mappedGlyphs;}
    int unmatchedGlyphs(){return mapping.unmappedGlyphs;}
    int unmappedMlElements(){return mapping.unmappedElements;}

    /**
     * No recognized words are written to diagnostics. Length-only source
     * comparison distinguishes an upstream Paddle text omission from a
     * downstream selection/alignment omission on a tapped ML Element.
     */
    String textCoverageDiagnostic(){
        if(all||expanded||anchor<0||extent!=anchor||
                anchor>=words.size())return "";
        Word word=words.get(anchor);
        int mlChars=0;
        for(OcrV3MlLayout.Symbol symbol:word.symbols)
            mlChars+=symbol.observed.length();
        int sourceChars=0,tokenChars=0;
        for(int glyphId:word.paddle){
            if(glyphId<0||glyphId>=page.glyphs.size())continue;
            OcrV3Page.Glyph glyph=page.glyphs.get(glyphId);
            sourceChars+=glyph.text.length();
            if(glyph.line<0||glyph.line>=page.lines.size())continue;
            String raw=page.lines.get(glyph.line).raw;
            int start=glyph.start,end=glyph.end;
            if(start<0||end>raw.length()||start>=end)continue;
            // Only original source ASCII-token boundaries. No guessing from
            // app names and no ML text substitution.
            boolean latin=true;
            for(int k=start;k<end;k++){
                char c=raw.charAt(k);
                if(!((c>='A'&&c<='Z')||(c>='a'&&c<='z')||
                        (c>='0'&&c<='9'))){latin=false;break;}
            }
            if(!latin)continue;
            while(start>0&&asciiAlphaNum(raw.charAt(start-1)))start--;
            while(end<raw.length()&&asciiAlphaNum(raw.charAt(end)))end++;
            tokenChars=Math.max(tokenChars,end-start);
        }
        return " ml_symbol_chars="+mlChars+
                " paddle_mapped_chars="+sourceChars+
                " paddle_source_token_chars="+tokenChars+
                " ml_unmapped_elements="+mapping.unmappedElements;
    }

    private static boolean asciiAlphaNum(char c){
        return c>='A'&&c<='Z'||c>='a'&&c<='z'||c>='0'&&c<='9';
    }
    /** For a single selected real ML Element, detect missing source coverage. */
    int activeWordIndex(){
        return !all&&!expanded&&anchor>=0&&anchor==extent
                ?anchor:-1;
    }

    Rect wordBox(int index){
        return index>=0&&index<words.size()
                ?new Rect(words.get(index).box):new Rect();
    }

    private String originalWordText(int index){
        if(index<0||index>=words.size())return "";
        return page.selectedText(words.get(index).paddle);
    }

    boolean needsLocalRecognition(int index){
        if(index!=activeWordIndex()||localPaddle.containsKey(index))
            return false;
        int mlChars=0;
        for(OcrV3MlLayout.Symbol symbol:words.get(index).symbols)
            mlChars+=symbol.observed.length();
        return OcrV3LocalTextDecision.needsRecognition(
                mlChars,originalWordText(index));
    }

    /** Local recognition may IMPROVE only the actual Paddle source text. */
    boolean acceptLocal(int index,String recognized,float confidence){
        if(index<0||index>=words.size())return false;
        String source=originalWordText(index);
        String chosen=OcrV3LocalTextDecision.choose(
                source,recognized,confidence);
        if(chosen.equals(source))return false;
        localPaddle.put(index,chosen);
        return true;
    }

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

    /**
     * SINGLE visual geometry path: ML Element rectangles for complete words,
     * genuine ML Symbol bounds for a partial boundary word. Paddle is used
     * only by text() and cannot change the visible blue highlight style.
     */
    private OcrV3MlSelectionVisual.Snapshot expandedVisual(){
        return OcrV3MlSelectionVisual.expanded(mlBoxes,expansion);
    }

    private static Rect rect(OcrV3MlSelectionVisual.Box b){
        return b==null?new Rect():new Rect(b.left,b.top,b.right,b.bottom);
    }

    int fallbackMlWords(){
        return expanded&&expansion.hasRange()
                ?expandedVisual().fallbackWords:0;
    }

    List<Rect> highlights(){
        if(!selected())return List.of();
        ArrayList<Rect> boxes=new ArrayList<>();
        if(expanded&&expansion.hasRange()){
            for(OcrV3MlSelectionVisual.Box box:expandedVisual().highlights)
                boxes.add(rect(box));
        }else if(all){
            for(Word word:words)boxes.add(new Rect(word.box));
        }else if(anchor>=0&&extent>=0){
            for(int i=Math.min(anchor,extent);i<=Math.max(anchor,extent);i++)
                if(words.get(i).block.equals(words.get(anchor).block))
                    boxes.add(new Rect(words.get(i).box));
        }
        return List.copyOf(boxes);
    }

    Rect firstBox(){
        if(!selected())return new Rect();
        if(expanded&&expansion.hasRange())return rect(expandedVisual().start);
        return new Rect(words.get(anchor).box);
    }

    Rect lastBox(){
        if(!selected())return new Rect();
        if(expanded&&expansion.hasRange())return rect(expandedVisual().end);
        return new Rect(words.get(extent).box);
    }

    String text(){
        if(!selected())return "";
        if(all)return page.fullText;
        int selectedWord=activeWordIndex();
        if(selectedWord>=0&&localPaddle.containsKey(selectedWord))
            return localPaddle.get(selectedWord);
        if(expanded&&expansion.hasRange()){
            int startWord=expansion.unit(expansion.first()).wordId;
            int endWord=expansion.unit(expansion.last()).wordId;
            if(startWord==endWord&&localPaddle.containsKey(startWord)){
                boolean complete=true;
                for(int i=0;i<expansion.size();i++)
                    if(expansion.unit(i).wordId==startWord &&
                            (i<expansion.first()||i>expansion.last())){
                        complete=false;break;
                    }
                if(complete)return localPaddle.get(startWord);
            }
        }
        ArrayList<Integer> ids=new ArrayList<>();
        if(expanded&&expansion.hasRange()){
            for(int i=expansion.first();i<=expansion.last();i++){
                int id=expansion.unit(i).glyphId;
                if(id>=0)ids.add(id);
            }
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
