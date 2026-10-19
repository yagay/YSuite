package com.yagay.YFloat;

import static org.junit.Assert.*;
import org.junit.Test;

/** Pure numeric geometry checks: ML Kit only replaces trustworthy positions. */
public final class CircleHybridSelectionGeometryTest {
    @Test public void identicalNearbyChineseGlyphHasExcellentMatch(){
        float score=CircleHybridSelectionGeometry.matchScore(
                120f,80f,30f,34f,122f,82f,28f,33f);
        assertTrue(score<0.25f);
    }
    @Test public void sameCharacterInAnotherIconCannotHijackHighlight(){
        float score=CircleHybridSelectionGeometry.matchScore(
                120f,80f,30f,34f,440f,80f,30f,34f);
        assertTrue(Float.isInfinite(score));
    }
    @Test public void nextRowIsNeverPairedWithCurrentCharacter(){
        float score=CircleHybridSelectionGeometry.matchScore(
                120f,80f,30f,34f,121f,144f,30f,34f);
        assertTrue(Float.isInfinite(score));
    }
    @Test public void absurdlyWideMlKitBoxAlwaysFallsBack(){
        float score=CircleHybridSelectionGeometry.matchScore(
                120f,80f,30f,34f,120f,80f,180f,34f);
        assertTrue(Float.isInfinite(score));
    }
    @Test public void ordinaryCTCWidthErrorCanBeCorrected(){
        float score=CircleHybridSelectionGeometry.matchScore(
                120f,80f,17f,34f,126f,80f,31f,33f);
        assertTrue(score<5.2f);
    }
    @Test public void consecutiveGlyphsShareWordButDifferentGroupsDoNot(){
        assertFalse(CircleHybridSelectionGeometry.groupBreak(2,8,13,2,8,14));
        assertTrue(CircleHybridSelectionGeometry.groupBreak(2,8,13,2,9,14));
        assertTrue(CircleHybridSelectionGeometry.groupBreak(2,8,13,3,8,14));
        assertTrue(CircleHybridSelectionGeometry.groupBreak(2,8,13,2,8,16));
    }
    @Test public void modesAreUnambiguousAndDefaultToHybrid(){
        assertEquals(0,CircleHybridSelectionGeometry.PADDLE);
        assertEquals(1,CircleHybridSelectionGeometry.HYBRID);
        assertEquals(2,CircleHybridSelectionGeometry.COMPARE);
    }
}
