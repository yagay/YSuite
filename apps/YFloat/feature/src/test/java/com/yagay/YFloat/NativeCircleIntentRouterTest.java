package com.yagay.YFloat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Prevent a slow offline recognizer from converting highlights into crop boxes. */
public class NativeCircleIntentRouterTest {
    @Test public void highlightAlwaysRequestsTextEvenBeforeOcrIsReady(){
        assertEquals(NativeCircleIntentRouter.Route.NEED_OCR,
                NativeCircleIntentRouter.classify("HIGHLIGHT",false));
        assertEquals(NativeCircleIntentRouter.Route.NEED_OCR,
                NativeCircleIntentRouter.classify("HIGHLIGHT",true));
        assertFalse(NativeCircleIntentRouter.permitsImageFallback("HIGHLIGHT"));
    }

    @Test public void imageLassoStillUsesImageSelection(){
        assertEquals(NativeCircleIntentRouter.Route.IMAGE,
                NativeCircleIntentRouter.classify("CIRCLE",false));
        assertEquals(NativeCircleIntentRouter.Route.IMAGE,
                NativeCircleIntentRouter.classify("SCRIBBLE",true));
        assertTrue(NativeCircleIntentRouter.permitsImageFallback("CIRCLE"));
        assertTrue(NativeCircleIntentRouter.permitsImageFallback("SCRIBBLE"));
    }

    @Test public void tapKeepsOptionalImageFallback(){
        assertEquals(NativeCircleIntentRouter.Route.NEED_OCR,
                NativeCircleIntentRouter.classify("TAP",false));
        assertTrue(NativeCircleIntentRouter.permitsImageFallback("TAP"));
    }
}
