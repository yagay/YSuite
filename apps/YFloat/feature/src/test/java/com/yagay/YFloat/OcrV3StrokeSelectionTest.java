package com.yagay.YFloat;

import android.graphics.Rect;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Regressions: a stroke may start outside text and must not create a crop. */
public final class OcrV3StrokeSelectionTest {
    private static OcrV3Selection sample(){
        List<OcrV3Page.Glyph> glyphs=List.of(
                new OcrV3Page.Glyph("A",new Rect(20,10,40,30),0,0,1),
                new OcrV3Page.Glyph("B",new Rect(44,10,64,30),0,1,2),
                new OcrV3Page.Glyph("C",new Rect(68,10,88,30),0,2,3),
                new OcrV3Page.Glyph("D",new Rect(92,10,112,30),0,3,4));
        OcrV3Page page=new OcrV3Page(
                List.of(new OcrV3Page.Line("ABCD",
                        new Rect(20,10,112,30),glyphs)),4,4,0,0,0);
        List<OcrV3MlLayout.Word> words=List.of(
                new OcrV3MlLayout.Word("zh:0",0,0,
                        new Rect(20,10,40,30),List.of()),
                new OcrV3MlLayout.Word("zh:0",0,1,
                        new Rect(44,10,64,30),List.of()),
                new OcrV3MlLayout.Word("zh:0",0,2,
                        new Rect(68,10,88,30),List.of()),
                new OcrV3MlLayout.Word("zh:1",0,0,
                        new Rect(92,10,112,30),List.of()));
        return new OcrV3Selection(page,words);
    }

    @Test public void underlineBeginsInBlankAndSelectsAllCrossedWords(){
        OcrV3Selection selection=sample();
        assertTrue(selection.extendStrokeSegment(0,34,90,34,5));
        assertEquals(0,selection.first());
        assertEquals(2,selection.last());
        assertEquals("ABC",selection.text());
    }

    @Test public void returningMotionDoesNotLoseEarlierVisitedWords(){
        OcrV3Selection selection=sample();
        assertTrue(selection.extendStrokeSegment(0,25,80,25,4));
        selection.extendStrokeSegment(80,25,30,25,4);
        assertEquals(0,selection.first());
        assertEquals(2,selection.last());
    }

    @Test public void unrelatedBlockIsNotAbsorbed(){
        OcrV3Selection selection=sample();
        selection.extendStrokeSegment(0,25,120,25,5);
        assertEquals("ABC",selection.text());
    }

    @Test public void blankStrokeDoesNotSnapToDistantText(){
        OcrV3Selection selection=sample();
        assertFalse(selection.extendStrokeSegment(0,100,120,100,8));
        assertFalse(selection.selected());
    }
}
