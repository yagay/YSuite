package com.yagay.YFloat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Read-only source substring extraction. No OCR or whitespace correction. */
final class OcrV3TextSlice {
    static final class Span {
        final int line,from,to;
        Span(int line,int from,int to){this.line=line;this.from=from;this.to=to;}
    }
    private OcrV3TextSlice(){}

    static String extract(List<String> originalLines,List<Span> requested){
        if(originalLines==null||requested==null||requested.isEmpty())return "";
        ArrayList<Span> sorted=new ArrayList<>(requested);
        sorted.sort(Comparator.comparingInt((Span s)->s.line)
                .thenComparingInt(s->s.from));
        StringBuilder value=new StringBuilder();
        int priorLine=-1,priorEnd=-1;
        for(Span span:sorted){
            if(span.line<0||span.line>=originalLines.size())continue;
            String original=originalLines.get(span.line);
            int from=Math.max(0,Math.min(original.length(),span.from));
            int to=Math.max(from,Math.min(original.length(),span.to));
            if(from==to)continue;
            if(priorLine!=span.line){
                if(priorLine>=0)value.append('\n');
                priorLine=span.line;priorEnd=from;
            }
            if(from>priorEnd){
                // Only whitespace ALREADY PRESENT in original Paddle result
                // may appear between distinct selected glyphs. Never copy an
                // unselected word lying between source offsets.
                String gap=original.substring(priorEnd,from);
                if(gap.codePoints().allMatch(Character::isWhitespace))
                    value.append(gap);
            }
            if(to>priorEnd){
                value.append(original,Math.max(from,priorEnd),to);
                priorEnd=to;
            }
        }
        return value.toString();
    }
}
