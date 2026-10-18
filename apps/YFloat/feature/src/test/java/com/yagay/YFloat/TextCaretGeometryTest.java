package com.yagay.YFloat;

import static org.junit.Assert.*;
import org.junit.Test;

/** Independent cursor hotspots on the ACTUAL selected blue glyph quad. */
public final class TextCaretGeometryTest {
    @Test public void toutiaoHotspotsAttachToStartAndEndOfFilledWord(){
        float[] q={90f,30f,230f,30f,230f,78f,90f,78f};
        TextCaretGeometry.Pair handles=TextCaretGeometry.forQuads(q,q);
        assertNotNull(handles);
        assertEquals(90f,handles.start.x,0.001f);
        assertEquals(78f,handles.start.y,0.001f);
        assertEquals(230f,handles.end.x,0.001f);
        assertEquals(78f,handles.end.y,0.001f);
        assertEquals(0f,handles.start.angleDegrees,0.001f);
    }
    @Test public void separateLinesHaveIndependentStartAndEndHotspots(){
        float[] first={20f,20f,150f,20f,150f,50f,20f,50f};
        float[] last={80f,100f,200f,100f,200f,130f,80f,130f};
        TextCaretGeometry.Pair pair=TextCaretGeometry.forQuads(first,last);
        assertNotNull(pair);
        assertEquals(20f,pair.start.x,0.001f);
        assertEquals(50f,pair.start.y,0.001f);
        assertEquals(200f,pair.end.x,0.001f);
        assertEquals(130f,pair.end.y,0.001f);
    }
    @Test public void rotatedQuadKeepsCaretRotation(){
        float[] q={40f,60f,80f,83f,64f,111f,24f,88f};
        TextCaretGeometry.Pair pair=TextCaretGeometry.forQuads(q,q);
        assertNotNull(pair);
        assertEquals(24f,pair.start.x,0.001f);
        assertEquals(88f,pair.start.y,0.001f);
        assertTrue(pair.end.angleDegrees>25f);
        assertTrue(pair.end.angleDegrees<35f);
    }
    @Test public void malformedQuadsAreRejected(){
        assertNull(TextCaretGeometry.forQuads(new float[6],new float[8]));
        assertNull(TextCaretGeometry.forQuads(new float[8],new float[8]));
        assertNull(TextCaretGeometry.forQuads(
                new float[]{1,1,Float.NaN,2,3,4,1,4},
                new float[]{1,1,5,1,5,5,1,5}));
    }
}
