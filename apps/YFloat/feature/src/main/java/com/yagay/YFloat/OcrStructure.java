package com.yagay.YFloat;

import android.graphics.PointF;
import android.graphics.Rect;
import java.util.ArrayList;
import java.util.List;

/**
 * Engine-neutral document geometry. Every polygon keeps its provenance,
 * avoiding confusion between detector quads, CTC-aligned glyphs and
 * postprocessed word/layout regions.
 */
public final class OcrStructure {
    public interface PointMapper { PointF map(float x,float y); }

    public static final class Quad {
        private final List<PointF> points;
        private final String origin;
        public Quad(List<PointF> points,String origin) {
            ArrayList<PointF> safe=new ArrayList<>();
            if(points!=null)for(PointF p:points)
                if(p!=null&&Float.isFinite(p.x)&&Float.isFinite(p.y))
                    safe.add(new PointF(p.x,p.y));
            this.points=List.copyOf(safe);
            this.origin=origin==null?"unknown":origin;
        }
        public List<PointF> points(){
            ArrayList<PointF> copy=new ArrayList<>();
            for(PointF p:points)copy.add(new PointF(p.x,p.y));
            return List.copyOf(copy);
        }
        public String origin(){return origin;}
        public Rect bounds(){
            if(points.isEmpty())return new Rect();
            float left=Float.POSITIVE_INFINITY,top=Float.POSITIVE_INFINITY;
            float right=Float.NEGATIVE_INFINITY,bottom=Float.NEGATIVE_INFINITY;
            for(PointF p:points){
                left=Math.min(left,p.x);top=Math.min(top,p.y);
                right=Math.max(right,p.x);bottom=Math.max(bottom,p.y);
            }
            return new Rect((int)Math.floor(left),(int)Math.floor(top),
                    (int)Math.ceil(right),(int)Math.ceil(bottom));
        }
        Quad mapped(PointMapper mapper) {
            ArrayList<PointF> transformed=new ArrayList<>();
            for(PointF p:points){
                PointF result=mapper.map(p.x,p.y);
                if(result!=null)transformed.add(result);
            }
            return new Quad(transformed,origin);
        }
    }

    public static final class Symbol {
        public final String text;
        public final Quad quad;
        public final float confidence;
        public Symbol(String text,Quad quad,float confidence){
            this.text=text==null?"":text;this.quad=quad;this.confidence=confidence;
        }
        Symbol mapped(PointMapper mapper){return new Symbol(text,quad.mapped(mapper),confidence);}
    }

    public static final class Word {
        public final String text;
        public final Quad quad;
        public final List<Symbol> symbols;
        public Word(String text,Quad quad,List<Symbol> symbols) {
            this.text=text==null?"":text;this.quad=quad;
            this.symbols=symbols==null?List.of():List.copyOf(symbols);
        }
        Word mapped(PointMapper mapper){
            ArrayList<Symbol> out=new ArrayList<>();
            for(Symbol symbol:symbols)out.add(symbol.mapped(mapper));
            return new Word(text,quad.mapped(mapper),out);
        }
    }

    public static final class Line {
        public final String text;
        public final Quad quad;
        public final float confidence;
        public final List<Word> words;
        public Line(String text,Quad quad,float confidence,List<Word> words){
            this.text=text==null?"":text;this.quad=quad;this.confidence=confidence;
            this.words=words==null?List.of():List.copyOf(words);
        }
        Line mapped(PointMapper mapper){
            ArrayList<Word> out=new ArrayList<>();
            for(Word word:words)out.add(word.mapped(mapper));
            return new Line(text,quad.mapped(mapper),confidence,out);
        }
    }

    public static final class Block {
        public final String kind;
        public final Quad quad;
        public final float confidence;
        public final List<Line> lines;
        public Block(String kind,Quad quad,float confidence,List<Line> lines){
            this.kind=kind==null?"unclassified":kind;
            this.quad=quad;this.confidence=confidence;
            this.lines=lines==null?List.of():List.copyOf(lines);
        }
        Block mapped(PointMapper mapper){
            ArrayList<Line> out=new ArrayList<>();
            for(Line line:lines)out.add(line.mapped(mapper));
            return new Block(kind,quad.mapped(mapper),confidence,out);
        }
    }

    private final List<Block> blocks;
    public OcrStructure(List<Block> blocks){
        this.blocks=blocks==null?List.of():List.copyOf(blocks);
    }
    public List<Block> blocks(){return blocks;}
    public OcrStructure mapped(PointMapper mapper){
        if(mapper==null)return this;
        ArrayList<Block> out=new ArrayList<>();
        for(Block block:blocks)out.add(block.mapped(mapper));
        return new OcrStructure(out);
    }
}
