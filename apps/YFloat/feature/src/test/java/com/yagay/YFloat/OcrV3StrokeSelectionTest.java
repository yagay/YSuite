package com.yagay.YFloat;

import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Exercise the same Android-free stroke tracker used by OcrV3Selection.
 * Plain JVM Android SDK stubs throw from new android.graphics.Rect(...),
 * so regression fixtures use the primitive ML word bounds directly.
 */
public final class OcrV3StrokeSelectionTest {
    private static OcrV3StrokeRange sample(){
        return new OcrV3StrokeRange(List.of(
                new OcrV3StrokeRange.Word("zh:0",20,10,40,30),
                new OcrV3StrokeRange.Word("zh:0",44,10,64,30),
                new OcrV3StrokeRange.Word("zh:0",68,10,88,30),
                new OcrV3StrokeRange.Word("zh:1",92,10,112,30)));
    }

    @Test public void underlineBeginsInBlankAndSelectsAllCrossedWords(){
        OcrV3StrokeRange range=sample();
        assertTrue(range.extend(0,34,90,34,5));
        assertEquals(0,range.first());
        assertEquals(2,range.last());
    }

    @Test public void returningMotionDoesNotLoseEarlierVisitedWords(){
        OcrV3StrokeRange range=sample();
        assertTrue(range.extend(0,25,80,25,4));
        range.extend(80,25,30,25,4);
        assertEquals(0,range.first());
        assertEquals(2,range.last());
    }

    @Test public void unrelatedBlockIsNotAbsorbed(){
        OcrV3StrokeRange range=sample();
        range.extend(0,25,120,25,5);
        assertEquals(0,range.first());
        assertEquals(2,range.last());
    }

    @Test public void blankStrokeDoesNotSnapToDistantText(){
        OcrV3StrokeRange range=sample();
        assertFalse(range.extend(0,100,120,100,8));
        assertEquals(-1,range.first());
        assertEquals(-1,range.last());
    }

    @Test public void tapStartStillSupportsOrdinarySelectionExpansion(){
        OcrV3StrokeRange range=sample();
        range.start(1);
        assertTrue(range.extend(54,20,85,20,4));
        assertEquals(1,range.first());
        assertEquals(2,range.last());
        range.clear();
        assertEquals(-1,range.first());
    }
}
