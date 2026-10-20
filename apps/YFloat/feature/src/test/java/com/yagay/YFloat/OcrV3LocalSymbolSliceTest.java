package com.yagay.YFloat;

import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Pure Java regression for Paddle-source/ML-geometry span isolation. */
public final class OcrV3LocalSymbolSliceTest {
    private static final List<Integer> LETTERS=List.of(1,1,1,1,1,1,1);

    @Test public void canSelectRogInsideRealPaddleProgramString(){
        assertEquals("rog",OcrV3LocalSymbolSlice.extract(
                "program",LETTERS,1,3));
    }
    @Test public void wholeWordKeepsExactlyOriginalPaddleCase(){
        assertEquals("ProGram",OcrV3LocalSymbolSlice.extract(
                "ProGram",LETTERS,0,6));
    }
    @Test public void missingPaddleCharactersCannotCreateFabricatedSubstring(){
        assertNull(OcrV3LocalSymbolSlice.extract(
                "rog",LETTERS,1,3));
    }
    @Test public void supplementaryUnicodeIsSlicedByCodePointNotUtf16(){
        assertEquals("😀",OcrV3LocalSymbolSlice.extract(
                "A😀B",List.of(1,1,1),1,1));
    }
    @Test public void mismatchedMlSymbolLengthFailsClosed(){
        assertNull(OcrV3LocalSymbolSlice.extract(
                "program",List.of(1,1,1),0,1));
    }
    @Test public void invalidIndexesReturnNull(){
        assertNull(OcrV3LocalSymbolSlice.extract(
                "program",LETTERS,-1,3));
    }
}
