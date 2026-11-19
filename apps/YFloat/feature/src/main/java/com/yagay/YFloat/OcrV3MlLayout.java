package com.yagay.YFloat;

import android.graphics.Bitmap;
import android.graphics.Rect;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Original ML Kit TextBlock -> Line -> Element -> Symbol coordinates. */
final class OcrV3MlLayout {
    interface Callback { void complete(List<Word> words); }
    static final class Symbol {
        final String observed; // Only for cross-engine alignment, NEVER for copying.
        final Rect box;
        Symbol(String text,Rect rect) {
            observed=text;
            box=new Rect(rect);
        }
    }
    static final class Word {
        final String block;
        final int line, element;
        final Rect box;
        final List<Symbol> symbols;
        Word(String block,int line,int element,Rect box,List<Symbol> symbols) {
            this.block=block;this.line=line;this.element=element;
            this.box=new Rect(box);this.symbols=List.copyOf(symbols);
        }
    }
    private OcrV3MlLayout(){}

    static void scan(Bitmap frozen,Callback callback) {
        if(frozen==null||frozen.isRecycled()){callback.complete(List.of());return;}
        final Bitmap input;
        final TextRecognizer chinese,latin;
        try {
            input=frozen.copy(Bitmap.Config.ARGB_8888,false);
            if(input==null)throw new IllegalStateException("bitmap copy");
        }catch(Throwable t){callback.complete(List.of());return;}
        try {
            chinese=TextRecognition.getClient(new ChineseTextRecognizerOptions.Builder().build());
            latin=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        }catch(Throwable t){
            input.recycle();callback.complete(List.of());return;
        }
        InputImage source=InputImage.fromBitmap(input,0);
        ArrayList<Word> all=new ArrayList<>();
        AtomicInteger remaining=new AtomicInteger(2);
        // Both recognizers report in callbacks on the Android main thread.
        class Done {
            void receive(Text text,String tag){
                if(text!=null)all.addAll(extract(text,tag));
                if(remaining.decrementAndGet()!=0)return;
                try{chinese.close();}catch(Throwable ignored){}
                try{latin.close();}catch(Throwable ignored){}
                if(!input.isRecycled())input.recycle();
                callback.complete(List.copyOf(all));
            }
        }
        Done done=new Done();
        try{
            chinese.process(source).addOnSuccessListener(v->done.receive(v,"zh"))
                    .addOnFailureListener(e->done.receive(null,"zh"));
        }catch(Throwable t){done.receive(null,"zh");}
        try{
            latin.process(source).addOnSuccessListener(v->done.receive(v,"latin"))
                    .addOnFailureListener(e->done.receive(null,"latin"));
        }catch(Throwable t){done.receive(null,"latin");}
    }
    private static List<Word> extract(Text result,String source){
        ArrayList<Word> words=new ArrayList<>();
        int block=0;
        for(Text.TextBlock b:result.getTextBlocks()){
            String id=source+":"+(block++);
            int line=0;
            for(Text.Line l:b.getLines()){
                int element=0;
                for(Text.Element e:l.getElements()){
                    Rect er=e.getBoundingBox();
                    if(er==null||er.isEmpty()){element++;continue;}
                    ArrayList<Symbol> symbols=new ArrayList<>();
                    for(Text.Symbol c:e.getSymbols()){
                        Rect r=c.getBoundingBox();
                        if(r!=null&&!r.isEmpty()&&c.getText()!=null&&!c.getText().isEmpty())
                            symbols.add(new Symbol(c.getText(),r));
                    }
                    // The ML Element geometry remains the word selection
                    // boundary. Never fabricate Paddle text for missing ML glyphs.
                    words.add(new Word(id,line,element,new Rect(er),symbols));
                    element++;
                }
                line++;
            }
        }
        return words;
    }
}
