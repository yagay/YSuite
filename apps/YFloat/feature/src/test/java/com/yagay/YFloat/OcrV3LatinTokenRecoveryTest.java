package com.yagay.YFloat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Paddle source ownership regression: source-initial letters can be missing
 * from ML geometric mapping without being missing from Paddle OCR text. */
public final class OcrV3LatinTokenRecoveryTest {
    private static List<OcrV3LatinTokenRecovery.Span> letters(
            int line,String raw){
        List<OcrV3LatinTokenRecovery.Span> spans=new ArrayList<>();
        for(int i=0;i<raw.length();i++)
            spans.add(new OcrV3LatinTokenRecovery.Span(line,i,i+1));
        return spans;
    }

    @Test public void whatsappMissingInitialTwoGeometryMatchesRestoresOriginalSource(){
        List<OcrV3LatinTokenRecovery.Span> spans=letters(0,"WhatsApp");
        int[] owners={-1,-1,3,3,3,3,3,3};
        OcrV3LatinTokenRecovery.Result r=OcrV3LatinTokenRecovery.recover(
                List.of("WhatsApp"),spans,owners);
        assertEquals(2,r.recovered);
        assertEquals(0,r.unmatched);
        for(int owner:r.owners)assertEquals(3,owner);
        assertEquals(-1,owners[0]); // original geometric mapping is immutable
    }

    @Test public void nonexistentPaddlePrefixIsNeverMadeUp(){
        int[] owners={2,2,2,2,2,2};
        OcrV3LatinTokenRecovery.Result r=OcrV3LatinTokenRecovery.recover(
                List.of("atsApp"),letters(0,"atsApp"),owners);
        assertEquals(0,r.recovered);
        assertEquals(6,r.owners.length);
    }

    @Test public void separatedAppLabelsCannotBorrowEachOthersLetters(){
        List<OcrV3LatinTokenRecovery.Span> spans=List.of(
                new OcrV3LatinTokenRecovery.Span(0,0,1),
                new OcrV3LatinTokenRecovery.Span(0,1,2),
                new OcrV3LatinTokenRecovery.Span(0,3,4),
                new OcrV3LatinTokenRecovery.Span(0,4,5));
        OcrV3LatinTokenRecovery.Result r=OcrV3LatinTokenRecovery.recover(
                List.of("AB CD"),spans,new int[]{0,0,-1,1});
        assertEquals(-1,r.owners[2]);
        assertEquals(1,r.unmatched);
    }

    @Test public void ambiguousTwoMlElementsMustNotAbsorbSharedToken(){
        int[] owners={-1,1,1,2,2,-1};
        OcrV3LatinTokenRecovery.Result r=OcrV3LatinTokenRecovery.recover(
                List.of("ABCDEF"),letters(0,"ABCDEF"),owners);
        assertEquals(0,r.recovered);
        assertEquals(-1,r.owners[0]);
        assertEquals(-1,r.owners[5]);
    }

    @Test public void singleGeometryAnchorCannotAbsorbWholeLabel(){
        int[] owners={-1,-1,-1,2,-1,-1};
        OcrV3LatinTokenRecovery.Result r=OcrV3LatinTokenRecovery.recover(
                List.of("abcdef"),letters(0,"abcdef"),owners);
        assertEquals(0,r.recovered);
        assertEquals(5,r.unmatched);
    }

    @Test public void multiplePaddleLinesNeverCrossConnect(){
        List<OcrV3LatinTokenRecovery.Span> spans=new ArrayList<>();
        spans.addAll(letters(0,"Whats"));
        spans.addAll(letters(1,"App"));
        int[] owners={-1,-1,3,3,3,-1,5,5};
        OcrV3LatinTokenRecovery.Result r=OcrV3LatinTokenRecovery.recover(
                List.of("Whats","App"),spans,owners);
        assertEquals(3,r.recovered);
        assertEquals(3,r.owners[0]);
        assertEquals(5,r.owners[5]);
        assertEquals(0,r.unmatched);
    }

    @Test public void unicodeAndPunctuationNeverTriggerLatinInference(){
        List<OcrV3LatinTokenRecovery.Span> spans=List.of(
                new OcrV3LatinTokenRecovery.Span(0,0,1),
                new OcrV3LatinTokenRecovery.Span(0,1,2),
                new OcrV3LatinTokenRecovery.Span(0,2,3));
        OcrV3LatinTokenRecovery.Result r=OcrV3LatinTokenRecovery.recover(
                List.of("A中文"),spans,new int[]{-1,5,5});
        assertEquals(0,r.recovered);
        assertEquals(-1,r.owners[0]);
    }
}
