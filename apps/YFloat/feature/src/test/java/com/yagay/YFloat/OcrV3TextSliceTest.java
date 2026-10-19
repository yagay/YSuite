package com.yagay.YFloat;

import static org.junit.Assert.*;
import java.util.List;
import org.junit.Test;

public final class OcrV3TextSliceTest {
    private static OcrV3TextSlice.Span s(int line,int start,int end){
        return new OcrV3TextSlice.Span(line,start,end);
    }

    @Test public void originalWhitespaceIsPreservedNotRepaired(){
        String original="头 条  今 日";
        assertEquals(original,OcrV3TextSlice.extract(
                List.of(original),List.of(s(0,0,1),s(0,2,3),s(0,5,6),s(0,7,8))));
    }
    @Test public void unrelatedIconTextNeverAppearsBetweenSelectedGlyphs(){
        String raw="legado rednote 抖音";
        assertEquals("legado抖音",OcrV3TextSlice.extract(
                List.of(raw),List.of(s(0,0,6),s(0,15,17))));
    }
    @Test public void originalPaddleSpacesRemainExact(){
        assertEquals("Hello  world",OcrV3TextSlice.extract(
                List.of("Hello  world"),List.of(s(0,0,5),s(0,7,12))));
    }
    @Test public void lineBreaksPreservePaddleLineBoundaries(){
        assertEquals("原文\nsecond",OcrV3TextSlice.extract(
                List.of("原文","second"),List.of(s(1,0,6),s(0,0,2))));
    }
    @Test public void duplicateOrOverlappingGlyphOffsetsAreNotRepeated(){
        assertEquals("ABCDE",OcrV3TextSlice.extract(
                List.of("ABCDE"),List.of(s(0,0,3),s(0,0,3),s(0,2,5))));
    }
    @Test public void invalidIndicesCannotCrashAnEmptyOcrPage(){
        assertEquals("",OcrV3TextSlice.extract(List.of(),List.of(s(1,5,9))));
        assertEquals("",OcrV3TextSlice.extract(List.of("a"),List.of()));
    }
}
