package com.yagay.YFloat;

import org.junit.Test;
import static org.junit.Assert.*;

/** The final text is always original Paddle source, never ML's OCR string. */
public final class OcrV3LocalTextDecisionTest {
    @Test public void incompleteLatinCoverageRequiresDirectRecognition(){
        assertTrue(OcrV3LocalTextDecision.needsRecognition(8,"atsApp"));
        assertFalse(OcrV3LocalTextDecision.needsRecognition(8,"WhatsApp"));
    }
    @Test public void missingPaddleLineStillLeavesMlElementSelectable(){
        assertTrue(OcrV3LocalTextDecision.needsRecognition(8,""));
        assertTrue(OcrV3LocalTextDecision.needsRecognition(0,""));
    }
    @Test public void directPaddleCanRecoverActualPrefixWithoutBrandDictionary(){
        assertEquals("WhatsApp",OcrV3LocalTextDecision.choose(
                "atsApp","WhatsApp",0.88f));
    }
    @Test public void noInventionFromUnrelatedTranscript(){
        assertEquals("atsApp",OcrV3LocalTextDecision.choose(
                "atsApp","Whatsapp login",0.93f));
    }
    @Test public void blankAndUncertainRecognitionDoNotCorruptSource(){
        assertEquals("atsApp",OcrV3LocalTextDecision.choose("atsApp","",.95f));
        assertEquals("atsApp",OcrV3LocalTextDecision.choose(
                "atsApp","WhatsApp",.22f));
    }
    @Test public void validDirectPaddleCanFillPreviouslyEmptySource(){
        assertEquals("Example",OcrV3LocalTextDecision.choose(
                "","Example",.91f));
    }
    @Test public void mismappedOneCharacterDoesNotVetoAccurateSevenSymbolLocalPaddle(){
        assertEquals("program",OcrV3LocalTextDecision.choose(
                "g","program",.957f,7));
    }

    @Test public void mismatchCandidateRejectedWhenDifferentFromMlGeometryLength(){
        assertEquals("g",OcrV3LocalTextDecision.choose(
                "g","program login",.99f,7));
    }

    @Test public void mismatchedLocalBelowConfidenceFloorDoesNotOverwritePaddle(){
        assertEquals("g",OcrV3LocalTextDecision.choose(
                "g","program",.75f,7));
    }

    @Test public void completePaddleNeverReplacedByCompletelyUnrelatedLocal(){
        assertEquals("program",OcrV3LocalTextDecision.choose(
                "program","abcdefg",.999f,7));
    }

    @Test public void candidateCannotShrinkCompleteSource(){
        assertEquals("WhatsApp",OcrV3LocalTextDecision.choose(
                "WhatsApp","atsApp",.98f));
    }
}
