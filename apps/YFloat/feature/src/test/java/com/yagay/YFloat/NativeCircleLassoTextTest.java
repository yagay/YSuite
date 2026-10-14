package com.yagay.YFloat;

import static org.junit.Assert.*;
import org.junit.Test;

public class NativeCircleLassoTextTest {
    private static final float[] BOX={0f,0f,100f,0f,100f,100f,0f,100f};

    @Test public void enclosedTextCanBeRecognizedInsideClosedGesture() {
        assertTrue(NativeCircleLassoText.contains(BOX,50f,50f));
        assertFalse(NativeCircleLassoText.contains(BOX,150f,50f));
        assertTrue(NativeCircleLassoText.preferText(12,0.28f));
    }

    @Test public void isolatedImageLabelDoesNotForceTextMode() {
        assertFalse(NativeCircleLassoText.preferText(2,0.7f));
        assertFalse(NativeCircleLassoText.preferText(15,0.02f));
    }
}
