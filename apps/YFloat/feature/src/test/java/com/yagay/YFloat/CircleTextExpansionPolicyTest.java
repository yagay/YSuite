package com.yagay.YFloat;

import static org.junit.Assert.*;
import org.junit.Test;

/** Pure JVM: no Android runtime or screenshot required. */
public final class CircleTextExpansionPolicyTest {
    @Test public void startHandleCanExpandBeyondFirstOcrLine(){
        CircleTextExpansionPolicy.Range selection=CircleTextExpansionPolicy.move(
                21,23,CircleTextExpansionPolicy.START,4);
        assertEquals(4,selection.first);
        assertEquals(23,selection.last);
        assertEquals(CircleTextExpansionPolicy.START,selection.active);
    }
    @Test public void endHandleCanExpandAcrossManyLinesWithoutCap(){
        CircleTextExpansionPolicy.Range selection=CircleTextExpansionPolicy.move(
                21,23,CircleTextExpansionPolicy.END,9600);
        assertEquals(21,selection.first);
        assertEquals(9600,selection.last);
        assertEquals(CircleTextExpansionPolicy.END,selection.active);
    }
    @Test public void bothHandlesCrossAndSwapTheirActiveRole(){
        CircleTextExpansionPolicy.Range selection=CircleTextExpansionPolicy.move(
                21,23,CircleTextExpansionPolicy.START,43);
        assertEquals(23,selection.first);
        assertEquals(43,selection.last);
        assertEquals(CircleTextExpansionPolicy.END,selection.active);
        selection=CircleTextExpansionPolicy.move(
                21,23,CircleTextExpansionPolicy.END,4);
        assertEquals(4,selection.first);
        assertEquals(21,selection.last);
        assertEquals(CircleTextExpansionPolicy.START,selection.active);
    }
    @Test public void expansionCanShrinkBackToOneCharacter(){
        CircleTextExpansionPolicy.Range selection=CircleTextExpansionPolicy.move(
                4,900,CircleTextExpansionPolicy.END,4);
        assertEquals(4,selection.first);
        assertEquals(4,selection.last);
    }
    @Test public void remapPrefersScreenPositionOverStringDuplicates(){
        float sameAtAnchor=CircleTextExpansionPolicy.remapScore(2f,2f,24f,true);
        float duplicateFarAway=CircleTextExpansionPolicy.remapScore(
                650f,350f,24f,true);
        float nearbyDifferentText=CircleTextExpansionPolicy.remapScore(
                2f,2f,24f,false);
        assertTrue(sameAtAnchor<nearbyDifferentText);
        assertTrue(sameAtAnchor<duplicateFarAway);
        assertTrue(duplicateFarAway>14f);
    }
    @Test public void no96DpLimitOnDistanceFallback(){
        float far=CircleTextExpansionPolicy.boxDistance(
                1250f,1680f,10f,20f,80f,50f);
        float nearer=CircleTextExpansionPolicy.boxDistance(
                1250f,1680f,1100f,1400f,1220f,1520f);
        assertTrue(far>96f*96f);
        assertTrue(nearer<far);
        assertEquals(0f,CircleTextExpansionPolicy.boxDistance(
                12f,25f,10f,20f,80f,50f),0.001f);
    }
    @Test public void invalidSelectionsNeverChangeExistingEndpoints(){
        CircleTextExpansionPolicy.Range range=CircleTextExpansionPolicy.move(
                8,20,CircleTextExpansionPolicy.START,-1);
        assertEquals(8,range.first);
        assertEquals(20,range.last);
    }
}
