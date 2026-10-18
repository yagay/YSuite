package com.yagay.YFloat;

import java.util.ArrayList;
import java.util.List;

/**
 * OCR text is the ONLY authority for selected text.
 * Bounding boxes and group ids control selection geometry, never whitespace.
 *
 * Every geometry token receives its UTF-16 [start,end) range in the original
 * recognized line; a mismatch is marked unknown rather than guessed. This
 * helper is pure Java so span mapping is regression-testable without Android.
 */
final class OcrSelectionText {
    static final class Span {
        final String source;
        final int line;
        final int ordinal;
        final int start;
        final int end;
        Span(String source,int line,int ordinal,int start,int end) {
            this.source=source;
            this.line=line;
            this.ordinal=ordinal;
            this.start=start;
            this.end=end;
        }
        String text() { return source.substring(start,end); }
    }

    static List<Span> align(String source,List<String> tokens,int sourceLine) {
        if(source==null)source="";
        if(tokens==null)return List.of();
        ArrayList<Span> result=new ArrayList<>(tokens.size());
        int cursor=0;
        for(int i=0;i<tokens.size();i++){
            String token=tokens.get(i);
            if(token==null||token.isBlank()){
                result.add(null);
                continue;
            }
            int match=source.indexOf(token,cursor);
            // Some engines may return decomposed Unicode while the source
            // line is composed. Do not manufacture a guessed position.
            if(match<0){
                result.add(null);
                continue;
            }
            result.add(new Span(source,sourceLine,i,match,match+token.length()));
            cursor=match+token.length();
        }
        return result;
    }

    /**
     * Source-accurate text for selected units in visual selection order.
     * Adjacent aligned units copy their ENTIRE original text slice, including
     * exact spaces/punctuation. Gaps for non-contiguous selections are never
     * bridged with unselected glyphs. Unknown spans copy only the unit's text.
     */
    static String selected(List<String> unitText,List<Span> spans,
                           List<Integer> indices,List<Integer> visualLines) {
        if(unitText==null||spans==null||indices==null||visualLines==null)
            return "";
        StringBuilder out=new StringBuilder();
        Span previous=null;
        int previousIndex=-1;
        int previousLine=-1;
        for(Integer boxed:indices){
            if(boxed==null)continue;
            int index=boxed;
            if(index<0||index>=unitText.size()||index>=spans.size()||
                    index>=visualLines.size())continue;
            String fallback=unitText.get(index);
            if(fallback==null||fallback.isBlank())continue;
            Span current=spans.get(index);
            int line=visualLines.get(index);
            if(current!=null) {
                boolean canBridge=previous!=null&&previousIndex+1==index&&
                        current.line==previous.line &&
                        current.ordinal==previous.ordinal+1 &&
                        current.start>=previous.end;
                if(canBridge){
                    out.append(current.source,previous.end,current.end);
                }else{
                    if(out.length()>0 && line!=previousLine)out.append('\n');
                    out.append(current.text());
                }
            }else{
                if(out.length()>0 && line!=previousLine)out.append('\n');
                out.append(fallback);
            }
            previous=current;
            previousIndex=index;
            previousLine=line;
        }
        return out.toString().trim();
    }

    private OcrSelectionText(){}
}
