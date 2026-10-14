package com.yagay.YFloat;

import static org.junit.Assert.*;
import org.junit.Test;

public class NativeCircleStateTest {
    @Test public void autoSelectsTextWithoutSettingAMode() {
        NativeCircleState state=new NativeCircleState();
        assertTrue(state.begin(10,20));
        state.bounds(10,20,100,50);
        assertTrue(state.resolving());
        assertTrue(state.resolveText(state.revision,"Hello",10,20,100,50));
        assertEquals(NativeCircleState.Intent.TEXT,state.intent);
        assertEquals(NativeCircleState.Phase.TEXT_READY,state.phase);
    }
    @Test public void staleOcrCannotReplaceNewLasso() {
        NativeCircleState state=new NativeCircleState();
        state.begin(10,20);state.resolving();
        long old=state.revision;
        state.begin(30,40);state.resolving();
        assertFalse(state.resolveText(old,"old",10,20,100,50));
        assertTrue(state.resolveImage(state.revision,30,40,90,120));
        assertEquals(NativeCircleState.Intent.IMAGE,state.intent);
        assertTrue(state.canFinishImage());
    }
    @Test public void closeIsTerminal() {
        NativeCircleState state=new NativeCircleState();
        state.close();assertFalse(state.begin(1,2));
        assertFalse(state.resolving());
    }
    @Test public void closedLassoIsImageAndStrokesPreferOcr() {
        assertEquals(NativeCircleIntentRouter.Route.IMAGE,
                NativeCircleIntentRouter.classify("CIRCLE",false));
        assertEquals(NativeCircleIntentRouter.Route.NEED_OCR,
                NativeCircleIntentRouter.classify("TAP",false));
        assertEquals(NativeCircleIntentRouter.Route.NEED_OCR,
                NativeCircleIntentRouter.classify("HIGHLIGHT",true));
    }
}
