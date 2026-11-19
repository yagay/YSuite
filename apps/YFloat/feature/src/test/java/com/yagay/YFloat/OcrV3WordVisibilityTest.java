package com.yagay.YFloat;

import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** These are ML-only shape tests; no Android Rect or Paddle models. */
public final class OcrV3WordVisibilityTest {
    @Test public void independentVisualWordDoesNotNeedPaddleAssignment(){
        List<Integer> words=OcrV3WordVisibility.select(List.of(
                new OcrV3WordVisibility.Entry(4,100,40,180,60,8,true)));
        assertEquals(List.of(4),words);
    }
    @Test public void duplicateChineseLatinPassesProduceOneBlueElement(){
        List<Integer> words=OcrV3WordVisibility.select(List.of(
                new OcrV3WordVisibility.Entry(0,20,30,101,50,6,false),
                new OcrV3WordVisibility.Entry(1,22,30,101,50,8,true)));
        assertEquals(List.of(1),words);
    }
    @Test public void neighboringLabelsDoNotDisappear(){
        List<Integer> words=OcrV3WordVisibility.select(List.of(
                new OcrV3WordVisibility.Entry(1,10,0,70,20,6,true),
                new OcrV3WordVisibility.Entry(2,75,0,100,20,3,true)));
        assertEquals(2,words.size());
    }
    @Test public void unrecognizedButValidMlElementRemainsVisible(){
        List<Integer> words=OcrV3WordVisibility.select(List.of(
                new OcrV3WordVisibility.Entry(6,3,5,93,30,0,false)));
        assertEquals(List.of(6),words);
    }
    @Test public void invalidGeometryCannotBeSelectTarget(){
        assertTrue(OcrV3WordVisibility.select(List.of(
                new OcrV3WordVisibility.Entry(0,4,4,4,10,1,true))).isEmpty());
    }
}
