package com.yagay.YFloat;

import static org.junit.Assert.*;
import java.util.List;
import org.junit.Test;

/** The OCR document must never rewrite the SDK's decoded whitespace. */
public final class OcrRawTextPreservationTest {
    @Test public void preserveOriginalLeadingTrailingAndCjkSpaces(){
        String raw="  中文 空 格\nEnglish  spaces \u3000";
        OcrDocument result=new OcrDocument(
                raw,List.of(),List.of(),"paddle-raw-test",.9f,.9,100,100);
        assertEquals(raw,result.fullText());
    }

    @Test public void preserveOriginalLineSeparatorsAndPunctuation(){
        String raw="原文、测试！\n\n第  二  行";
        OcrDocument result=new OcrDocument(
                raw,List.of(),List.of(),"paddle-raw-test",1f,1,100,100);
        assertEquals(raw,result.fullText());
    }

    @Test public void emptyAndNullAreSafeWithoutAutomaticTrimming(){
        assertEquals("",new OcrDocument(null,List.of(),List.of(),
                "test",0f,0,1,1).fullText());
        assertEquals("  ",new OcrDocument("  ",List.of(),List.of(),
                "test",0f,0,1,1).fullText());
    }
}
