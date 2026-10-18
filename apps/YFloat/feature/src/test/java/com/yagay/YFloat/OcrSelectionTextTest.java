package com.yagay.YFloat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public final class OcrSelectionTextTest {
    private static String pick(String source,List<String> tokens,List<Integer> selected) {
        List<OcrSelectionText.Span> ranges=OcrSelectionText.align(source,tokens,0);
        return OcrSelectionText.selected(tokens,ranges,selected,
                java.util.Collections.nCopies(tokens.size(),0));
    }

    @Test public void chineseCharactersDoNotReceiveGroupBasedSpaces() {
        String chinese="中华人民共和国";
        List<String> units=List.of("中","华","人","民","共","和","国");
        assertEquals(chinese,pick(chinese,units,List.of(0,1,2,3,4,5,6)));
    }

    @Test public void nativeCircleThreeGroupsNeverCreateAnExtraSpace() {
        // A recognition line can have three selectable units spanning five
        // actual characters. Group boundaries must not imply word breaks.
        assertEquals("hello",pick("hello",
                List.of("he","l","lo"),List.of(0,1,2)));
        assertEquals("AB CD",pick("AB CD",
                List.of("A","B","CD"),List.of(0,1,2)));
    }

    @Test public void preservesExactEnglishSpacingFromSource() {
        assertEquals("Hello  world",pick("Hello  world",
                List.of("Hello","world"),List.of(0,1)));
        assertEquals("recognition",pick("recognition",
                List.of("recog","nition"),List.of(0,1)));
    }

    @Test public void preservesMixedScriptAndPunctuation() {
        assertEquals("中文 English，测试",pick("中文 English，测试",
                List.of("中","文","English","，","测","试"),
                List.of(0,1,2,3,4,5)));
    }

    @Test public void partialSelectionUsesOnlyChosenSourceRange() {
        assertEquals("AB",pick("ABCDE",List.of("A","B","C","D","E"),
                List.of(0,1)));
        assertEquals("AC",pick("ABCDE",List.of("A","B","C","D","E"),
                List.of(0,2)));
    }

    @Test public void ambiguousGlyphMappingDoesNotInventWhitespace() {
        List<String> units=List.of("中","unused","文");
        List<OcrSelectionText.Span> ranges=
                OcrSelectionText.align("中文",units,0);
        assertNull(ranges.get(1));
        assertEquals("中unused文",OcrSelectionText.selected(units,ranges,
                List.of(0,1,2),List.of(0,0,0)));
    }

    @Test public void distinctSourceLinesKeepLineBreaks() {
        List<String> tokens=List.of("第一行","第二行");
        List<OcrSelectionText.Span> a=
                OcrSelectionText.align("第一行",List.of("第一行"),0);
        List<OcrSelectionText.Span> b=
                OcrSelectionText.align("第二行",List.of("第二行"),1);
        assertEquals("第一行\n第二行",
                OcrSelectionText.selected(tokens,List.of(a.get(0),b.get(0)),
                        List.of(0,1),List.of(0,1)));
    }

    @Test public void neverBridgesDifferentStringsThatShareALineIndex() {
        List<String> units=List.of("a","z");
        List<OcrSelectionText.Span> spans=List.of(
                new OcrSelectionText.Span("abc",0,0,0,1),
                new OcrSelectionText.Span("xyz",0,1,2,3));
        assertEquals("az",OcrSelectionText.selected(units,spans,
                List.of(0,1),List.of(0,0)));
    }

    @Test public void unmatchedRepeatedGlyphNeverBacktracks() {
        List<OcrSelectionText.Span> a=OcrSelectionText.align("人人人",
                List.of("人","人","人","人"),0);
        assertEquals(0,a.get(0).start);
        assertEquals(1,a.get(1).start);
        assertEquals(2,a.get(2).start);
        assertNull(a.get(3));
    }
}
