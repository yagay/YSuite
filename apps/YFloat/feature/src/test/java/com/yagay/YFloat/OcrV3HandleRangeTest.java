package com.yagay.YFloat;

import static org.junit.Assert.*;
import org.junit.Test;

public final class OcrV3HandleRangeTest {
    @Test public void sameMlTextBlockExpandsOnlyToFingerTarget(){
        OcrV3HandleRange m=OcrV3HandleRange.move(1,1,3,2,true);
        assertEquals(1,m.first);assertEquals(3,m.last);assertEquals(2,m.moving);
    }
    @Test public void returningHandShrinksPreviouslySelectedWords(){
        OcrV3HandleRange m=OcrV3HandleRange.move(1,5,2,2,true);
        assertEquals(1,m.first);assertEquals(2,m.last);
    }
    @Test public void crossingGripsSwapsActiveSide(){
        OcrV3HandleRange m=OcrV3HandleRange.move(2,5,8,1,true);
        assertEquals(5,m.first);assertEquals(8,m.last);assertEquals(2,m.moving);
    }
    @Test public void otherMlTextBlockNeverImportsIntermediateIcons(){
        OcrV3HandleRange m=OcrV3HandleRange.move(1,3,9,2,false);
        assertEquals(9,m.first);assertEquals(9,m.last);
    }
    @Test public void emptyWallpaperDoesNotSnapAnywhere(){
        OcrV3HandleRange m=OcrV3HandleRange.move(1,3,-1,2,true);
        assertEquals(1,m.first);assertEquals(3,m.last);
    }
}
