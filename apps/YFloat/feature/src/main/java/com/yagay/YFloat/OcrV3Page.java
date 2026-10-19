package com.yagay.YFloat;

import android.graphics.Rect;
import java.util.ArrayList;
import java.util.List;

/**
 * V3 recognition result. The original Paddle line text remains authoritative.
 * Character offsets are slices of that text, never synthesized whitespace.
 */
final class OcrV3Page {
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

    /** Resolve the ORIGINAL Paddle substrings, including its own spaces. */
    String selectedText(List<Integer> selected) {
        if(selected==null||selected.isEmpty())return "";
        int lastLine=-1,start=-1,end=-1;
        StringBuilder out=new StringBuilder();
        for(int idx:selected) {
            if(idx<0||idx>=glyphs.size())continue;
            Glyph g=glyphs.get(idx);
            if(g.start<0||g.end<=g.start||g.line<0||g.line>=lines.size())continue;
            if(g.line!=lastLine) {
                if(lastLine>=0 && start>=0)appendRaw(out,lastLine,start,end);
                if(lastLine>=0)out.append('\n');
                lastLine=g.line;
                start=g.start;end=g.end;
            } else {
                if(g.start>end) {
                    // Padding is read from the actual Paddle string.
                    end=g.end;
                } else end=Math.max(end,g.end);
                start=Math.min(start,g.start);
            }
        }
        if(lastLine>=0&&start>=0)appendRaw(out,lastLine,start,end);
        return out.toString();
    }
    private void appendRaw(StringBuilder sb,int line,int start,int end) {
        String text=lines.get(line).raw;
        int left=Math.max(0,Math.min(text.length(),start));
        int right=Math.max(left,Math.min(text.length(),end));
        sb.append(text,left,right);
    }
}
