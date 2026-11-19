package com.yagay.YFloat;

import static org.junit.Assert.*;
import org.junit.Test;

/** Regression contract for main-branch ML selection appearance, Paddle text owner. */
public final class CircleMlSelectionVisualTest {
    @Test public void oldMlKitBlueWashColorIsRestored() {
        assertEquals(0x884285F4,CircleMlSelectionVisual.ML_BLUE_WASH);
        assertEquals(0xFF4285F4,CircleMlSelectionVisual.ML_BLUE_HANDLE);
        assertEquals(2f,CircleMlSelectionVisual.CORNER_DP,0f);
        assertEquals(7f,CircleMlSelectionVisual.HANDLE_STEM_DP,0f);
        assertEquals(7f,CircleMlSelectionVisual.HANDLE_RADIUS_DP,0f);
    }

    @Test public void contiguousCharactersWithinAWordCanFormOneBlueSegment() {
        assertTrue(CircleMlSelectionVisual.mayMerge(
                true,true,1f,18f,22f,31f,10f,53f,30f));
    }

    @Test public void separateIconLabelsCannotBecomeOneBlueRectangle() {
        assertFalse(CircleMlSelectionVisual.mayMerge(
                true,true,115f,20f,22f,150f,10f,172f,35f));
    }

    @Test public void differentPaddleGroupsNeverMergeDespiteTouching() {
        assertFalse(CircleMlSelectionVisual.mayMerge(
                false,true,0f,22f,22f,32f,10f,54f,32f));
    }

    @Test public void excludedCharacterCannotBeRejoinedAcrossIndexGap() {
        assertFalse(CircleMlSelectionVisual.mayMerge(
                true,false,0f,22f,22f,32f,10f,54f,32f));
    }

    @Test public void unrelatedOcrLinesNeverMerge() {
        assertFalse(CircleMlSelectionVisual.mayMerge(
                false,true,0f,22f,22f,32f,10f,54f,32f));
    }

    @Test public void crookedOrMisalignedBoxCannotFloodNearbyLine() {
        assertFalse(CircleMlSelectionVisual.mayMerge(
                true,true,0f,3f,24f,31f,10f,53f,30f));
    }

    @Test public void duplicateFarBehindAnEarlierGlyphDoesNotExpandRange() {
        assertFalse(CircleMlSelectionVisual.mayMerge(
                true,true,-28f,22f,22f,0f,30f,20f,52f));
    }
}
