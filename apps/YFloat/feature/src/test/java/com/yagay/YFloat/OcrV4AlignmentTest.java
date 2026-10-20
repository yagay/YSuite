package com.yagay.YFloat;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public final class OcrV4AlignmentTest {
    @Test public void sourceOnlyMiddleThreeCharacters(){
        var a=OcrV4Alignment.align(List.of("p","r","o","g","r","a","m"),"program");
        assertTrue(a.reliable);
        assertEquals("rog",a.slice(1,3));
        assertEquals("program",a.slice(0,6));
    }
    @Test public void cannotInventLostWordPrefix(){
        var a=OcrV4Alignment.align(List.of("W","h","a","t","s","A","p","p"),"atsApp");
        assertFalse(a.reliable);
        assertEquals("",a.slice(0,7));
    }
    @Test public void identicalCountsCannotAuthorizeDifferentText(){
        var a=OcrV4Alignment.align(List.of("p","r","o","g","r","a","m"),"ABCDEFG");
        assertFalse(a.reliable);
        assertEquals("",a.slice(1,3));
    }
    @Test public void wordWhitespaceComesOnlyFromPaddle(){
        var a=OcrV4Alignment.align(List.of("H","i","Y","o","u"),"Hi You");
        assertTrue(a.reliable);
        assertEquals("i Yo",a.slice(1,3));
    }
    @Test public void unicodeSupplementaryCharDoesNotShiftOffsets(){
        var a=OcrV4Alignment.align(List.of("A","😀","B"),"A😀B");
        assertTrue(a.reliable);
        assertEquals("😀",a.slice(1,1));
    }
    @Test public void simplifiedChinesePreservesExactPaddleLine(){
        var a=OcrV4Alignment.align(List.of("中","国","文","字"),"中国文字");
        assertTrue(a.reliable);
        assertEquals("国文",a.slice(1,2));
    }
    @Test public void caseFoldIsAlignmentOnlyPaddleCaseWins(){
        var a=OcrV4Alignment.align(List.of("w","h","a","t","s","a","p","p"),"WhatsApp");
        assertTrue(a.reliable);
        assertEquals("WhatsApp",a.slice(0,7));
    }
    @Test public void oneMlSymbolMayHoldMoreThanOneCodepoint(){
        var a=OcrV4Alignment.align(List.of("Th","e","re"),"There");
        assertTrue(a.reliable);
        assertEquals("There",a.slice(0,2));
        assertEquals("re",a.slice(2,2));
    }
    @Test public void embeddedPaddleWhitespaceNotRepurposedAsMlUnit(){
        var a=OcrV4Alignment.align(List.of("g","o","o","d","m","o","r","n","i","n","g"),"good morning");
        assertTrue(a.reliable);
        assertEquals("d m",a.slice(3,4));
    }
}