package com.yagay.YFloat;

import static org.junit.Assert.*;
import org.junit.Test;

public class NativeCircleVisualPolicyTest {
    @Test public void enteringCircleAlwaysAppliesVisibleActivationMask() {
        assertTrue(NativeCircleVisualPolicy.baseScrimAlpha(
                NativeCircleState.Phase.IDLE) >= 60);
        assertTrue(NativeCircleVisualPolicy.baseScrimAlpha(
                NativeCircleState.Phase.DRAWING) > 0);
    }

    @Test public void resolvedImageHasOneRectangularCropAndOutsideOnlyScrim() {
        NativeCircleState state = new NativeCircleState();
        state.begin(100,200);
        state.bounds(80,150,290,440);
        assertTrue(state.resolving());
        assertTrue(NativeCircleVisualPolicy.showRectangle(state.phase));
        assertTrue(state.resolveImage(state.revision,80,150,290,440));
        assertTrue(NativeCircleVisualPolicy.showRectangle(state.phase));
        assertEquals(0,NativeCircleVisualPolicy.baseScrimAlpha(state.phase));
        assertTrue(NativeCircleVisualPolicy.SELECTION_OUTSIDE_ALPHA>=90);
        assertTrue(state.canFinishImage());
    }

    @Test public void textSelectionKeepsLightContrastMaskWithoutImageCrop() {
        NativeCircleState state = new NativeCircleState();
        state.begin(10,10);
        state.bounds(10,10,190,80);
        state.resolving();
        assertTrue(state.resolveText(state.revision,"test",10,10,190,80));
        assertFalse(NativeCircleVisualPolicy.showRectangle(state.phase));
        assertTrue(NativeCircleVisualPolicy.baseScrimAlpha(state.phase)>0);
    }
}
