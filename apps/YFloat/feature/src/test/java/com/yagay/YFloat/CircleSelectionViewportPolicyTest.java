package com.yagay.YFloat;

import static org.junit.Assert.*;
import org.junit.Test;
import java.util.List;

/**
 * Pure JVM regressions: Blue pixels, copied characters and handles share
 * the same visual membership, independent of Paddle/ML Kit recognizers.
 */
public final class CircleSelectionViewportPolicyTest {
    private static CircleSelectionViewportPolicy.Glyph g(
            int line,float l,float t,float r,float b){
        return new CircleSelectionViewportPolicy.Glyph(line,l,t,r,b);
    }

    @Test public void excludesInterleavedNeighboringLauncherIconLabels(){
        // Paddle order is row-major: two icon columns are interleaved.
        // Drag the first icon down to the third row in the same column.
        var chars=List.of(
                g(0,22,25,72,47), g(1,210,25,262,47),
                g(2,24,90,77,113),g(3,211,90,266,113),
                g(4,25,157,79,181),g(5,214,157,263,181));
        var range=CircleSelectionViewportPolicy.select(chars,0,4,-1,false);
        assertTrue(range.contains(0));
        assertTrue(range.contains(2));
        assertTrue(range.contains(4));
        assertFalse("Unselected icon outside handles must not be blue",
                range.contains(1));
        assertFalse("Middle-row second-column icon must not be blue",
                range.contains(3));
        assertEquals(3,range.selectedCount);
    }

    @Test public void sameOcrLineCannotPaintASeparateFarWord(){
        var chars=List.of(
                g(0,10,20,25,43),g(0,190,20,214,43),
                g(0,26,20,46,43));
        var range=CircleSelectionViewportPolicy.select(chars,0,2,0,false);
        assertTrue(range.contains(0));assertTrue(range.contains(2));
        assertFalse("Ordinal between handles != visual selection",
                range.contains(1));
    }

    @Test public void fullWidthParagraphDoesNotLoseMiddleRowEndings(){
        var chars=List.of(
                g(0,10,10,34,32),g(0,34,10,58,32),
                g(0,58,10,82,32),g(0,82,10,106,32),
                g(1,10,47,35,70),g(1,75,47,98,70),
                g(2,10,85,33,107),g(2,34,85,58,107));
        var range=CircleSelectionViewportPolicy.select(chars,0,7,-1,false);
        assertTrue(range.contains(5));
        assertEquals(8,range.selectedCount);
    }

    @Test public void partialLastLineStopsAtRightGrip(){
        var chars=List.of(
                g(0,10,10,30,30),g(0,31,10,54,30),
                g(1,10,50,32,70),g(1,34,50,60,70),
                g(1,62,50,88,70));
        var range=CircleSelectionViewportPolicy.select(chars,0,3,-1,false);
        assertTrue(range.contains(0));
        assertTrue(range.contains(3));
        assertFalse(range.contains(4));
    }

    @Test public void explicitSelectAllIsNotRestrictedToOneColumn(){
        var chars=List.of(g(0,10,15,35,35),g(1,240,15,280,35),
                g(2,11,90,36,115));
        var all=CircleSelectionViewportPolicy.select(chars,0,2,-1,true);
        assertEquals(3,all.selectedCount);
        assertTrue(all.contains(1));
    }

    @Test public void lockedInitialLineStillRejectsOtherRows(){
        var chars=List.of(g(3,10,10,30,30),g(4,12,60,33,80),
                g(3,31,10,50,30));
        var initial=CircleSelectionViewportPolicy.select(chars,0,2,3,false);
        assertTrue(initial.contains(0));assertTrue(initial.contains(2));
        assertFalse(initial.contains(1));
    }

    @Test public void invalidGeometryDoesNotBecomeBlue(){
        var chars=List.of(g(0,20,20,40,40),g(0,0,0,0,0),
                g(0,42,20,60,40));
        var range=CircleSelectionViewportPolicy.select(chars,0,2,0,false);
        assertEquals(2,range.selectedCount);
        assertFalse(range.contains(1));
    }
}
