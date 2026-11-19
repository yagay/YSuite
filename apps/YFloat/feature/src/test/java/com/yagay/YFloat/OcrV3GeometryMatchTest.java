package com.yagay.YFloat;

import org.junit.Test;
import static org.junit.Assert.*;

/** Text from independent OCR engines may disagree while boxes still match. */
public final class OcrV3GeometryMatchTest {
    @Test public void glyphFullyInsideWordIsSelectableDespiteDifferentRecognizedText(){
        double score=OcrV3GeometryMatch.score(10,10,20,30,8,8,58,34);
        assertTrue(Double.isFinite(score));
    }

    @Test public void nearbyButDisjointAppLabelsMustNeverMatch(){
        assertEquals(Double.NEGATIVE_INFINITY,
                OcrV3GeometryMatch.score(20,100,36,120,120,100,185,121),0);
    }

    @Test public void overlyWideWordBoxLosesToTightRealElement(){
        double narrow=OcrV3GeometryMatch.score(20,100,38,122,17,98,41,124);
        double overlyWide=OcrV3GeometryMatch.score(20,100,38,122,0,95,900,127);
        assertTrue(narrow>overlyWide);
    }

    @Test public void onePixelContactIsNotOverlap(){
        assertEquals(Double.NEGATIVE_INFINITY,
                OcrV3GeometryMatch.score(10,10,20,30,20,10,35,30),0);
    }

    @Test public void tinyOverlapCannotSelectAdjacentIcon(){
        assertEquals(Double.NEGATIVE_INFINITY,
                OcrV3GeometryMatch.score(10,10,30,30,29,8,60,31),0);
    }

    @Test public void differingGlyphAndMlBoxSizesRemainUsable(){
        double score=OcrV3GeometryMatch.score(25,25,45,42,20,20,53,47);
        assertTrue(score>1);
    }
}
