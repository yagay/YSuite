package com.yagay.YFloat;

import static org.junit.Assert.*;
import org.junit.Test;

public final class CircleCharSpatialIndexTest {
    @Test public void nearGlyphUsesLocalBucket(){
        CircleCharSpatialIndex index=new CircleCharSpatialIndex(
            new float[]{20,120,220,600},new float[]{100,100,100,600});
        assertEquals(0,index.nearest(26,110));
        assertEquals(1,index.nearest(128,110));
        assertEquals(2,index.nearest(215,95));
    }
    @Test public void offLineHandleStillSnapsNearest(){
        CircleCharSpatialIndex index=new CircleCharSpatialIndex(
            new float[]{30,490,510},new float[]{30,520,520});
        assertEquals(1,index.nearest(480,680));
    }
    @Test public void emptyIndexAndInvalidParametersAreSafe(){
        assertEquals(-1,new CircleCharSpatialIndex(
                new float[0],new float[0]).nearest(20,10));
        try{
            new CircleCharSpatialIndex(new float[]{1f},new float[0]);
            fail("Expected mismatch protection");
        }catch(IllegalArgumentException expected){}
    }
}
