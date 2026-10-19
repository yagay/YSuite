package com.yagay.YFloat;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.util.ArrayList;
import java.util.List;

/**
 * SECONDARY visual geometry only. ML Kit output text never enters
 * OcrDocument/clipboard. One frozen full-frame bitmap, two on-device scripts,
 * one geometry result; recognizers and screenshot copies are always closed.
 */
final class CircleMlKitGeometry {
    interface Callback {
        void onComplete(List<Glyph> glyphs,String detail);
    }
    static final class Glyph {
        final String text;
        final Rect bounds; // absolute screen coordinates
        final List<PointF> corners; // TL/TR/BR/BL
        final String blockId; // ML Kit's recognized TextBlock, source/pass scoped
        final int lineId,wordId,symbolId; // ML hierarchy, not Paddle order
        Glyph(String text,Rect bounds,List<PointF> points){
            this(text,bounds,points,"unassigned",-1,-1,-1);
        }
        Glyph(String text,Rect bounds,List<PointF> points,String blockId,
              int lineId,int wordId,int symbolId){
            this.blockId=blockId;
            this.lineId=lineId;this.wordId=wordId;this.symbolId=symbolId;
            this.text=text==null?"":text;
            this.bounds=new Rect(bounds);
            ArrayList<PointF> copy=new ArrayList<>(4);
            for(PointF p:points)copy.add(new PointF(p.x,p.y));
            corners=List.copyOf(copy);
        }
    }

    private CircleMlKitGeometry(){}

    static void recognize(Context context,Bitmap frozen,
                          ScreenBitmapTransform transform,Callback callback){
        if(callback==null)return;
        if(frozen==null||frozen.isRecycled()||transform==null){
            callback.onComplete(List.of(),"invalid_frame");
            return;
        }
        final Bitmap input;
        try {
            input=frozen.copy(Bitmap.Config.ARGB_8888,false);
            if(input==null)throw new IllegalStateException("copy_null");
        }catch(Throwable failure){
            callback.onComplete(List.of(),"bitmap_copy_failed");
            return;
        }
        final TextRecognizer zh,latin;
        try {
            zh=TextRecognition.getClient(
                    new ChineseTextRecognizerOptions.Builder().build());
            latin=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        }catch(Throwable error){
            input.recycle();
            callback.onComplete(List.of(),"recognizer_unavailable");
            return;
        }
        final class Combined {
            final ArrayList<Glyph> collected=new ArrayList<>();
            int remaining=2;
            int success=0;
            void finish(Text value,String script){
                if(value!=null){
                    success++;
                    collected.addAll(extract(value,transform,script));
                }
                if(--remaining!=0)return;
                try { zh.close(); }catch(Exception ignored){}
                try { latin.close(); }catch(Exception ignored){}
                if(!input.isRecycled())input.recycle();
                callback.onComplete(List.copyOf(collected),
                        "passes="+success+"/2 glyphs="+collected.size());
            }
        }
        Combined combined=new Combined();
        InputImage source;
        try { source=InputImage.fromBitmap(input,0); }
        catch(Throwable error){
            try {zh.close();latin.close();}catch(Exception ignored){}
            input.recycle();
            callback.onComplete(List.of(),"input_image_failed");
            return;
        }
        try{
            zh.process(source)
                .addOnSuccessListener(result->combined.finish(result,"zh"))
                .addOnFailureListener(error->combined.finish(null,"zh"));
        }catch(Throwable error){combined.finish(null,"zh");}
        try{
            latin.process(source)
                .addOnSuccessListener(result->combined.finish(result,"latin"))
                .addOnFailureListener(error->combined.finish(null,"latin"));
        }catch(Throwable error){combined.finish(null,"latin");}
    }

    private static List<Glyph> extract(Text text,ScreenBitmapTransform transform,
                                       String script){
        ArrayList<Glyph> out=new ArrayList<>();
        if(text==null)return out;
        int blockIndex=0;
        for(Text.TextBlock block:text.getTextBlocks()){
            String blockId=script+":block:"+blockIndex++;
            int lineIndex=0;
            for(Text.Line line:block.getLines()){
                int currentLine=lineIndex++;
                int wordIndex=0;
                for(Text.Element element:line.getElements()){
                    int currentWord=wordIndex++;
                    int symbolIndex=0;
                    List<Text.Symbol> symbols=element.getSymbols();
                    if(symbols==null)continue;
                    for(Text.Symbol symbol:symbols){
                        String token=symbol.getText();
                        if(token==null||token.isBlank())continue;
                        List<PointF> corners=mapCorners(symbol.getCornerPoints(),
                                symbol.getBoundingBox(),transform);
                        if(corners.size()!=4)continue;
                        Rect r=rectOf(corners);
                        if(!r.isEmpty())out.add(new Glyph(token,r,corners,
                                blockId,currentLine,currentWord,symbolIndex));
                        symbolIndex++;
                    }
                }
            }
        }
        return out;
    }

    private static List<PointF> mapCorners(Point[] points,Rect box,
                                           ScreenBitmapTransform transform){
        ArrayList<PointF> out=new ArrayList<>(4);
        if(points!=null && points.length==4){
            for(Point p:points){
                if(p==null)return List.of();
                out.add(transform.bitmapToScreen(p.x,p.y));
            }
        }else if(box!=null&&!box.isEmpty()){
            out.add(transform.bitmapToScreen(box.left,box.top));
            out.add(transform.bitmapToScreen(box.right,box.top));
            out.add(transform.bitmapToScreen(box.right,box.bottom));
            out.add(transform.bitmapToScreen(box.left,box.bottom));
        }
        return out.size()==4?List.copyOf(out):List.of();
    }
    private static Rect rectOf(List<PointF> points){
        float l=Float.POSITIVE_INFINITY,t=Float.POSITIVE_INFINITY;
        float r=Float.NEGATIVE_INFINITY,b=Float.NEGATIVE_INFINITY;
        for(PointF p:points){
            l=Math.min(l,p.x);t=Math.min(t,p.y);
            r=Math.max(r,p.x);b=Math.max(b,p.y);
        }
        return new Rect((int)Math.floor(l),(int)Math.floor(t),
                (int)Math.ceil(r),(int)Math.ceil(b));
    }
}
