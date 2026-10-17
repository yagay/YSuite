package com.yagay.YFloat;

import static org.junit.Assert.*;
import org.junit.Test;

public class GoogleCtsSessionStateTest {
    @Test public void coldStartRemainsActiveWithoutFixedTimeoutAck() {
        GoogleCtsSessionState state=new GoogleCtsSessionState();
        assertTrue(state.arm());
        assertFalse(state.commitResult()); // no screen selection yet
        assertTrue(state.active());
        assertFalse(state.acknowledged());
        assertTrue(state.acknowledge());
        assertEquals(GoogleCtsSessionState.Phase.INTERCEPTED,state.phase());
        assertTrue(state.selecting());
    }

    @Test public void completedScreenshotIsTerminalForNewSelectionEvents() {
        GoogleCtsSessionState state=new GoogleCtsSessionState();
        state.arm();state.acknowledge();state.selecting();
        assertTrue(state.commitResult());
        assertFalse(state.mayCancelOnNavigation());
        assertFalse(state.selecting());
        assertTrue(state.handoffVisible());
        assertFalse(state.commitResult());
        assertTrue(state.close());
        assertFalse(state.close());
        assertFalse(state.acknowledge());
        assertFalse(state.selecting());
        assertFalse(state.commitResult());
    }

    @Test public void newGestureHasIndependentStateAfterClose() {
        GoogleCtsSessionState old=new GoogleCtsSessionState();
        old.arm();old.selecting();old.close();
        GoogleCtsSessionState next=new GoogleCtsSessionState();
        assertTrue(next.arm());
        assertTrue(next.selecting());
        assertTrue(next.mayCancelOnNavigation());
        assertTrue(next.close());
    }
}
