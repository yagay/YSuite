package com.yagay.YFloat;

import android.graphics.Rect;
import java.util.ArrayList;
import java.util.List;

/**
 * V4 selection facade: one ML-owned document, one canonical symbol range.
 * Neither touch gestures nor clipboard text consult Paddle glyph IDs,
 * nearest OCR word geometry, or source-length guesses.
 */
final class OcrV4Selection {
    private final OcrV4Document document;
    private final OcrV3StrokeRange stroke;
    private final OcrV3ExpansionController expansion;
    private final List<OcrV3MlSelectionVisual.Box> renderBoxes;
    private int anchor=-1,extent=-1;
    private boolean all,expanded;

    OcrV4Selection(List<OcrV3MlLayout.Word> ml){
        ArrayList<OcrV4Document.WordSpec> source=new ArrayList<>();
        for(OcrV3MlLayout.Word word:ml){
            ArrayList<OcrV4Document.SymbolSpec> symbols=new ArrayList<>();
            for(OcrV3MlLayout.Symbol c:word.symbols){
                symbols.add(new OcrV4Document.SymbolSpec(c.observed,box(c.box)));
            }
            source.add(new OcrV4Document.WordSpec(word.block,word.line,
                    word.element,box(word.box),symbols));
        }
        document=OcrV4Document.build(source);
        ArrayList<OcrV3StrokeRange.Word> strokes=new ArrayList<>();
        ArrayList<OcrV3MlSelectionVisual.Box> boxes=new ArrayList<>();
        for(int i=0;i<document.wordCount();i++){
            OcrV4Document.Word w=document.word(i);
            OcrV4Document.Box b=w.box;
            strokes.add(new OcrV3StrokeRange.Word(
                    w.block,b.left,b.top,b.right,b.bottom));
            boxes.add(new OcrV3MlSelectionVisual.Box(
                    b.left,b.top,b.right,b.bottom));
        }
        renderBoxes=List.copyOf(boxes);
        stroke=new OcrV3StrokeRange(strokes);
        ArrayList<OcrV3ExpansionController.Unit> units=new ArrayList<>();
        for(int i=0;i<document.unitCount();i++){
            OcrV4Document.Unit unit=document.unit(i);
            OcrV4Document.Box b=unit.box;
            // Here line==stable global ML symbol index, NOT the Paddle
            // recognizer line. Controller sorting is deterministic.
            units.add(new OcrV3ExpansionController.Unit(-1,i,0,
                    b.left,b.top,b.right,b.bottom,unit.wordId,
                    unit.mlSymbol));
        }
        expansion=new OcrV3ExpansionController(units,document.wordCount());
    }

    private static OcrV4Document.Box box(Rect rect){
        return new OcrV4Document.Box(
                rect.left,rect.top,rect.right,rect.bottom);
    }
    private static Rect rect(OcrV4Document.Box box){
        return new Rect(box.left,box.top,box.right,box.bottom);
    }
    private static Rect rect(OcrV3MlSelectionVisual.Box box){
        return box==null?new Rect():
                new Rect(box.left,box.top,box.right,box.bottom);
    }

    int size(){return document.wordCount();}
    int lineCount(){return document.lineCount();}
    int expansionUnits(){return expansion.size();}
    int duplicates(){return document.duplicates();}
    boolean selected(){return all||expanded||(anchor>=0&&extent>=0);}
    boolean expanded(){return expanded;}
    int movingHandle(){return expansion.moving();}
    int first(){return expanded?expansion.first():anchor;}
    int last(){return expanded?expansion.last():extent;}

    void clear(){
        anchor=extent=-1;all=false;expanded=false;
        stroke.clear();expansion.clear();
    }
    void selectAll(){
        clear();
        if(size()>0){anchor=0;extent=size()-1;all=true;}
    }
    int hit(float x,float y){
        for(int i=0;i<size();i++){
            OcrV4Document.Box b=document.word(i).box;
            if(x>=b.left&&x<b.right&&y>=b.top&&y<b.bottom)return i;
        }
        return -1;
    }
    boolean tap(float x,float y){
        int id=hit(x,y);
        if(id<0){clear();return false;}
        clear();anchor=extent=id;stroke.start(id);return true;
    }
    boolean extendStrokeSegment(float x0,float y0,float x1,float y1,float padding){
        if(all)return false;
        if(expanded){expanded=false;expansion.clear();}
        boolean changed=stroke.extend(x0,y0,x1,y1,padding);
        if(changed){anchor=stroke.first();extent=stroke.last();}
        return changed;
    }

    boolean begin(float x,float y,float radius,float bitmapStem){
        if(!selected()||all)return false;
        Rect a=firstBox(),b=lastBox();
        float da=(float)Math.hypot(x-a.left,y-(a.bottom+bitmapStem));
        float db=(float)Math.hypot(x-b.right,y-(b.bottom+bitmapStem));
        if(Math.min(da,db)>radius)return false;
        int side=da<=db?OcrV3ExpansionController.START:
                OcrV3ExpansionController.END;
        if(expanded)return expansion.begin(side,x,y);
        if(anchor<0||extent<0)return false;
        ArrayList<Integer> ids=new ArrayList<>();
        int first=Math.min(anchor,extent),last=Math.max(anchor,extent);
        for(int i=first;i<=last;i++)
            if(document.word(i).block.equals(document.word(anchor).block))
                ids.add(i);
        return expansion.prepare(ids)&&expansion.begin(side,x,y);
    }
    boolean drag(float x,float y,float nearestDistance){
        boolean changed=expansion.drag(x,y,nearestDistance);
        if(changed)expanded=true;
        return changed;
    }
    void release(){expansion.release();}

    private int[] selectedUnitRange(){
        if(!selected()||document.unitCount()==0)return null;
        if(all)return new int[]{0,document.unitCount()-1};
        if(expanded&&expansion.hasRange())
            return new int[]{expansion.first(),expansion.last()};
        if(anchor<0||extent<0)return null;
        int first=Math.min(anchor,extent),last=Math.max(anchor,extent);
        // Preserve freehand behavior: selection cannot silently spill into
        // another ML TextBlock on a stroke.
        while(last>first && !document.word(last).block.equals(
                document.word(anchor).block))last--;
        return new int[]{document.word(first).firstUnit,
                document.word(last).lastUnit};
    }
    /** A safe Paddle-only fallback applies to exactly one entire Element.
     *  A partial word MUST use a verified Paddle-to-Symbol alignment. */
    int completeSelectedWord(){
        int[] range=selectedUnitRange();
        if(range==null)return -1;
        OcrV4Document.Unit a=document.unit(range[0]);
        OcrV4Document.Unit b=document.unit(range[1]);
        if(a.wordId!=b.wordId)return -1;
        OcrV4Document.Word w=document.word(a.wordId);
        return range[0]==w.firstUnit&&range[1]==w.lastUnit?w.id:-1;
    }
    Rect wordBox(int id){
        return id<0||id>=document.wordCount()?new Rect():
                rect(document.word(id).box);
    }
    boolean acceptWord(int id,String text,float confidence){
        return document.acceptWord(id,text,confidence);
    }

    List<Integer> requiredLines(){
        int[] r=selectedUnitRange();
        return r==null?List.of():document.lineIds(r[0],r[1]);
    }
    int nextUnreadyLine(){
        for(int id:requiredLines())if(!document.ready(id))return id;
        return -1;
    }
    Rect lineBox(int lineId){
        return lineId<0||lineId>=document.lineCount()?new Rect():
                rect(document.line(lineId).box);
    }
    boolean acceptLine(int id,String paddle,float confidence){
        return document.accept(id,paddle,confidence);
    }
    String text(){
        int[] r=selectedUnitRange();
        return r==null?"":document.selectedText(r[0],r[1]);
    }
    List<Rect> highlights(){
        if(!selected())return List.of();
        ArrayList<Rect> list=new ArrayList<>();
        if(expanded&&expansion.hasRange()){
            for(OcrV3MlSelectionVisual.Box b:
                    OcrV3MlSelectionVisual.expanded(renderBoxes,expansion).highlights)
                list.add(rect(b));
        }else if(all){
            for(int i=0;i<size();i++)list.add(rect(document.word(i).box));
        }else{
            for(int i=Math.min(anchor,extent);i<=Math.max(anchor,extent);i++)
                if(document.word(i).block.equals(document.word(anchor).block))
                    list.add(rect(document.word(i).box));
        }
        return List.copyOf(list);
    }
    Rect firstBox(){
        if(!selected())return new Rect();
        if(expanded&&expansion.hasRange())return rect(
                OcrV3MlSelectionVisual.expanded(renderBoxes,expansion).start);
        return rect(document.word(Math.min(anchor,extent)).box);
    }
    Rect lastBox(){
        if(!selected())return new Rect();
        if(expanded&&expansion.hasRange())return rect(
                OcrV3MlSelectionVisual.expanded(renderBoxes,expansion).end);
        return rect(document.word(Math.max(anchor,extent)).box);
    }
    int fallbackMlWords(){
        return expanded&&expansion.hasRange()?
                OcrV3MlSelectionVisual.expanded(renderBoxes,expansion).fallbackWords:0;
    }
    String diagnostics(){
        int[] range=selectedUnitRange();
        return " v4_ml_words="+document.wordCount()+
                " ml_symbols="+document.unitCount()+
                " lines="+document.lineCount()+
                " duplicate_ml="+document.duplicates()+
                " pending_lines="+requiredLines().stream()
                    .filter(id->!document.ready(id)).count()+
                " selected_units="+(range==null?0:range[1]-range[0]+1);
    }
}
