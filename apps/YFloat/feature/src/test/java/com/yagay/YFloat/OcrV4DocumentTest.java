package com.yagay.YFloat;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public final class OcrV4DocumentTest {
    private static OcrV4Document.Box b(int l,int t,int r,int bot){
        return new OcrV4Document.Box(l,t,r,bot);
    }
    private static OcrV4Document.WordSpec word(String block,int line,int element,
                                                 String literal,int x,int y){
        var symbols=new java.util.ArrayList<OcrV4Document.SymbolSpec>();
        int k=0;
        for(int p=0;p<literal.length();p++){
            symbols.add(new OcrV4Document.SymbolSpec(
                    literal.substring(p,p+1),b(x+k*10,y,x+(k+1)*10,y+18)));
            k++;
        }
        return new OcrV4Document.WordSpec(block,line,element,
                b(x,y,x+k*10,y+18),symbols);
    }
    @Test public void selectionNeverConsultsPaddleGlyphBoxes(){
        var d=OcrV4Document.build(List.of(word("latin:0",0,0,"program",10,20)));
        assertEquals(1,d.wordCount());assertEquals(7,d.unitCount());
        assertEquals(1,d.lineCount());
        assertEquals("",d.selectedText(1,3));
        assertTrue(d.accept(0,"program",.96f));
        assertEquals("rog",d.selectedText(1,3));
    }
    @Test public void fullWordCanUseIndependentHighConfidencePaddleEvenIfMlTextWrong(){
        var d=OcrV4Document.build(List.of(word("latin:0",0,0,"ZZotsApp",10,20)));
        assertFalse(d.accept(0,"WhatsApp",.98f)); // ML mismatch on line
        assertTrue(d.acceptWord(0,"WhatsApp",.95f));
        assertEquals("WhatsApp",d.selectedText(0,7));
        // Middle letters still need verified correspondence:
        assertEquals("",d.selectedText(1,3));
    }
    @Test public void singleWordFallbackWorksWhenAnotherWordBreaksWholeLine(){
        var d=OcrV4Document.build(List.of(
                word("latin:0",0,0,"program",10,20),
                word("latin:0",0,1,"different",90,20)));
        assertFalse(d.accept(0,"program other",.95f));
        assertTrue(d.acceptWord(0,"program",.97f));
        assertEquals("program",d.selectedText(0,6));
        assertEquals("rog",d.selectedText(1,3));
        assertEquals("",d.selectedText(0,15)); // Multi-word unverified
    }
    @Test public void neighboringWordInRoiMustNotBeSilentlyCopied(){
        var d=OcrV4Document.build(List.of(word("latin:0",0,0,"hello",10,20)));
        assertFalse(d.acceptWord(0,"hello world",.99f));
        assertEquals("",d.selectedText(0,4));
    }
    @Test public void lowConfidenceWordIsNotAccepted(){
        var d=OcrV4Document.build(List.of(word("latin:0",0,0,"program",10,20)));
        assertFalse(d.acceptWord(0,"program",.40f));
        assertEquals("",d.selectedText(0,6));
    }
    @Test public void sourceCanRecoverLettersMissingFromMlSymbolList(){
        var d=OcrV4Document.build(List.of(word("latin:0",0,0,"atsApp",10,20)));
        assertTrue(d.acceptWord(0,"WhatsApp",.97f));
        assertEquals("WhatsApp",d.selectedText(0,5));
        assertEquals("",d.selectedText(1,3));
    }
    @Test public void missingPaddlePrefixBlocksWrongCopy(){
        var d=OcrV4Document.build(List.of(word("latin:0",0,0,"WhatsApp",10,20)));
        assertFalse(d.accept(0,"atsApp",.99f));
        assertEquals("",d.selectedText(0,7));
    }
    @Test public void separateWordsInOneLineMapToOnePaddleSource(){
        var d=OcrV4Document.build(List.of(
                word("latin:0",0,0,"hello",10,20),
                word("latin:0",0,1,"world",75,20)));
        assertEquals(1,d.lineCount());assertEquals(2,d.wordCount());
        assertTrue(d.accept(0,"hello world",.95f));
        assertEquals("hello",d.selectedText(0,4));
        assertEquals("world",d.selectedText(5,9));
        assertEquals("lo wo",d.selectedText(3,6));
    }
    @Test public void crossingRealLinesRequiresBothPaddleSources(){
        var d=OcrV4Document.build(List.of(
                word("latin:0",0,0,"abcd",10,10),
                word("latin:0",1,0,"xyz",10,50)));
        assertTrue(d.accept(0,"abcd",.97f));
        assertEquals("",d.selectedText(1,5));
        assertTrue(d.accept(1,"xyz",.97f));
        assertEquals("bcd\nxy",d.selectedText(1,5));
    }
    @Test public void samePixelsChineseLatinDuplicatedOnlyOnce(){
        var d=OcrV4Document.build(List.of(
                word("zh:0",0,0,"hello",10,20),
                word("latin:0",0,0,"hello",11,20)));
        assertEquals(1,d.wordCount());assertEquals(1,d.duplicates());
    }
    @Test public void noMlSymbolsIsSelectableButNotCopyable(){
        var d=OcrV4Document.build(List.of(
                new OcrV4Document.WordSpec("latin:0",0,0,b(10,10,40,30),List.of())));
        assertEquals(1,d.wordCount());assertEquals(1,d.unitCount());
        assertFalse(d.accept(0,"Word",.99f));
        assertEquals("",d.selectedText(0,0));
    }
    @Test public void unverifiedReplacementCannotErasePreviouslyVerifiedSource(){
        var d=OcrV4Document.build(List.of(word("latin:0",0,0,"program",10,20)));
        assertTrue(d.accept(0,"program",.97f));
        assertFalse(d.accept(0,"abcd",.99f));
        assertEquals("rog",d.selectedText(1,3));
    }
}