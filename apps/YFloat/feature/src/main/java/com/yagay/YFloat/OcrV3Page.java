package com.yagay.YFloat;

import android.graphics.Rect;
import java.util.ArrayList;
import java.util.List;

/**
 * V3 recognition result. The original Paddle line text remains authoritative.
 * Character offsets are slices of that text, never synthesized whitespace.
 */
public final class OcrV3Page {
    static final class Glyph {
        final String text;
        final Rect rect;
        final int line, start, end;
        Glyph(String text, Rect rect, int line, int start, int end) {
            this.text=text;
            this.rect=new Rect(rect);
            this.line=line;
            this.start=start;
            this.end=end;
        }
    }
    static final class Line {
        final String raw;
        final Rect rect;
        final List<Glyph> glyphs;
        Line(String raw, Rect rect, List<Glyph> glyphs) {
            this.raw=raw;
            this.rect=new Rect(rect);
            this.glyphs=List.copyOf(glyphs);
        }
    }
    final List<Line> lines;
    final List<Glyph> glyphs;
    final String fullText;

    OcrV3Page(List<Line> lines) {
        this.lines=List.copyOf(lines);
        ArrayList<Glyph> all=new ArrayList<>();
        ArrayList<String> raw=new ArrayList<>();
        for(Line line:lines) {
            all.addAll(line.glyphs);
            raw.add(line.raw);
        }
        glyphs=List.copyOf(all);
        fullText=String.join("\n",raw);
    }

    /** Only the original Paddle UTF-16 slices; no model text replacement. */
    String selectedText(List<Integer> selected) {
        if(selected==null||selected.isEmpty())return "";
        ArrayList<OcrV3TextSlice.Span> slices=new ArrayList<>();
        ArrayList<String> original=new ArrayList<>(lines.size());
        for(Line l:lines)original.add(l.raw);
        for(int index:selected){
            if(index<0||index>=glyphs.size())continue;
            Glyph g=glyphs.get(index);
            slices.add(new OcrV3TextSlice.Span(g.line,g.start,g.end));
        }
        return OcrV3TextSlice.extract(original,slices);
    }

}
