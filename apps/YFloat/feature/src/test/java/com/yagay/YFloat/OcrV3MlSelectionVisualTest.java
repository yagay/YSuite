package com.yagay.YFloat;

import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Production ML geometry renderer tests run on plain JVM, without Android
 * graphics stubs. Paddle box shapes are not an input to the renderer.
 */
public final class OcrV3MlSelectionVisualTest {
    private static final List<OcrV3MlSelectionVisual.Box> WORDS=List.of(
            new OcrV3MlSelectionVisual.Box(10,10,42,36),
            new OcrV3MlSelectionVisual.Box(50,10,83,36),
            new OcrV3MlSelectionVisual.Box(10,50,47,76));

    private static OcrV3ExpansionController controller(){
        return new OcrV3ExpansionController(List.of(
                new OcrV3ExpansionController.Unit(0,0,0,12,13,22,31,0,true),
                new OcrV3ExpansionController.Unit(1,0,1,24,13,40,31,0,true),
                new OcrV3ExpansionController.Unit(2,0,2,53,13,82,31,1,true),
                new OcrV3ExpansionController.Unit(3,1,0,11,53,46,71,2,true)),3);
    }

    private static void equalBox(OcrV3MlSelectionVisual.Box actual,
                                 int l,int t,int r,int b){
        assertNotNull(actual);
        assertEquals(l,actual.left);
        assertEquals(t,actual.top);
        assertEquals(r,actual.right);
        assertEquals(b,actual.bottom);
    }

    @Test public void fullElementLooksIdenticalBeforeAndAfterFirstHandleMove(){
        OcrV3ExpansionController c=controller();
        assertTrue(c.prepare(List.of(0)));
        OcrV3MlSelectionVisual.Snapshot frame=
                OcrV3MlSelectionVisual.expanded(WORDS,c);
        assertEquals(1,frame.highlights.size());
        equalBox(frame.highlights.get(0),10,10,42,36);
        equalBox(frame.start,10,10,42,36);
        equalBox(frame.end,10,10,42,36);
        assertEquals(0,frame.fallbackWords);
    }

    @Test public void partialElementHasMlSymbolWidthButOriginalMlElementHeight(){
        OcrV3ExpansionController c=controller();
        assertTrue(c.prepare(List.of(0)));
        assertTrue(c.begin(OcrV3ExpansionController.END,32,22));
        assertTrue(c.drag(17,22,6)); // shrink end to original ML symbol #0
        OcrV3MlSelectionVisual.Snapshot frame=
                OcrV3MlSelectionVisual.expanded(WORDS,c);
        assertEquals(1,frame.highlights.size());
        equalBox(frame.highlights.get(0),12,10,22,36);
        assertEquals(1,frame.partialWords);
        assertEquals(0,frame.fallbackWords);
    }

    @Test public void manyGlyphsInOneWordNeverMultiplyTranslucentBlue(){
        OcrV3ExpansionController c=controller();
        assertTrue(c.prepare(List.of(0,1)));
        OcrV3MlSelectionVisual.Snapshot frame=
                OcrV3MlSelectionVisual.expanded(WORDS,c);
        assertEquals(2,frame.highlights.size());
        equalBox(frame.highlights.get(0),10,10,42,36);
        equalBox(frame.highlights.get(1),50,10,83,36);
    }

    @Test public void crossLineExpansionOnlyPaintsItsOwnMlElements(){
        OcrV3ExpansionController c=controller();
        assertTrue(c.prepare(List.of(1)));
        assertTrue(c.begin(OcrV3ExpansionController.END,67,22));
        assertTrue(c.drag(28,62,6));
        OcrV3MlSelectionVisual.Snapshot frame=
                OcrV3MlSelectionVisual.expanded(WORDS,c);
        assertEquals(2,frame.highlights.size());
        equalBox(frame.highlights.get(0),50,10,83,36);
        equalBox(frame.highlights.get(1),10,50,47,76);
    }

    @Test public void missingMlSymbolUsesWholeMlElementNotPaddleBox(){
        OcrV3ExpansionController c=new OcrV3ExpansionController(List.of(
                new OcrV3ExpansionController.Unit(0,0,0,12,13,22,31,0,true),
                new OcrV3ExpansionController.Unit(1,0,1,10,10,42,36,0,false)),1);
        assertTrue(c.prepare(List.of(0)));
        assertTrue(c.begin(OcrV3ExpansionController.START,17,22));
        assertTrue(c.drag(38,23,6)); // select only the fallback unit
        OcrV3MlSelectionVisual.Snapshot frame=
                OcrV3MlSelectionVisual.expanded(WORDS.subList(0,1),c);
        assertEquals(1,frame.highlights.size());
        equalBox(frame.highlights.get(0),10,10,42,36);
        assertEquals(1,frame.fallbackWords);
    }

    @Test public void noValidRangeHasNoBlueGeometry(){
        OcrV3ExpansionController c=controller();
        OcrV3MlSelectionVisual.Snapshot frame=
                OcrV3MlSelectionVisual.expanded(WORDS,c);
        assertTrue(frame.highlights.isEmpty());
        assertNull(frame.start);assertNull(frame.end);
    }
}
