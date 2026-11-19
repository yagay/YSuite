package com.yagay.YFloat;

import static org.junit.Assert.*;
import org.junit.Test;

/** The YFloat gesture must resolve on its original frozen frame. */
public class NativeCircleInPlaceRoutingTest {
    @Test public void tapAndHighlightUseInPlaceTextOcr() {
        assertEquals(NativeCircleIntentRouter.Route.NEED_OCR,
                NativeCircleIntentRouter.classify("TAP",false));
        assertEquals(NativeCircleIntentRouter.Route.NEED_OCR,
                NativeCircleIntentRouter.classify("HIGHLIGHT",false));
        assertEquals(NativeCircleIntentRouter.Route.NEED_OCR,
                NativeCircleIntentRouter.classify("HIGHLIGHT",true));
    }

    @Test public void circleAndScribbleRemainLocalScreenshots() {
        for(boolean ocrReady:new boolean[]{false,true}) {
            assertEquals(NativeCircleIntentRouter.Route.IMAGE,
                    NativeCircleIntentRouter.classify("CIRCLE",ocrReady));
            assertEquals(NativeCircleIntentRouter.Route.IMAGE,
                    NativeCircleIntentRouter.classify("SCRIBBLE",ocrReady));
        }
    }

    @Test public void recognitionResultRemainsInNativeSelectionState() {
        NativeCircleState state=new NativeCircleState();
        assertTrue(state.begin(120,240));
        state.bounds(120,240,420,280);
        assertTrue(state.resolving());
        long revision=state.revision;
        assertTrue(state.resolveText(revision,"Selected on original image",
                120,240,420,280));
        assertEquals(NativeCircleState.Phase.TEXT_READY,state.phase);
        assertEquals("Selected on original image",state.selectedText);
    }

    @Test public void screenshotResultCannotBecomeTextDueToLateOcr() {
        NativeCircleState state=new NativeCircleState();
        assertTrue(state.begin(120,240));
        state.bounds(120,240,620,860);
        assertTrue(state.resolving());
        long revision=state.revision;
        assertTrue(state.resolveImage(revision,120,240,620,860));
        assertFalse(state.resolveText(revision,"late",120,240,620,860));
        assertEquals(NativeCircleState.Phase.IMAGE_READY,state.phase);
    }
}
