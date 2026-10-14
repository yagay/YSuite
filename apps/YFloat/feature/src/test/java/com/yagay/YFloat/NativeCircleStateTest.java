package com.yagay.YFloat;

import static org.junit.Assert.*;
import org.junit.Test;

public class NativeCircleStateTest {
    @Test public void textSelectionRejectsObsoleteOcrResults() {
        NativeCircleState state = new NativeCircleState();
        assertTrue(state.begin(10, 20));
        state.bounds(10,20,100,50);
        assertTrue(state.finish(true));
        long token = state.revision;
        state.clear();
        state.begin(40,50);
        state.bounds(40,50,150,65);
        state.finish(true);
        assertFalse(state.textReady(token,"old",10,20,100,50));
        assertTrue(state.textReady(state.revision,"new",40,50,150,65));
        assertEquals(NativeCircleState.Phase.TEXT_READY,state.phase);
    }

    @Test public void imageModeNeverLaunchesSearchByItself() {
        NativeCircleState state = new NativeCircleState();
        state.changeMode(NativeCircleState.Mode.LASSO);
        assertTrue(state.begin(100,200));
        state.bounds(80,150,250,400);
        assertTrue(state.finish(true));
        assertTrue(state.canFinishImage());
        state.changeMode(NativeCircleState.Mode.TEXT);
        assertFalse(state.canFinishImage());
    }

    @Test public void closeIsTerminalAndCannotResurrectSelections() {
        NativeCircleState state = new NativeCircleState();
        state.close();
        assertFalse(state.begin(1,2));
        assertFalse(state.changeMode(NativeCircleState.Mode.RECTANGLE));
        assertEquals(NativeCircleState.Phase.CLOSED,state.phase);
    }
}
