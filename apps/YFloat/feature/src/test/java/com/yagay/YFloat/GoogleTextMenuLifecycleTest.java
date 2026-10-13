package com.yagay.YFloat;

import static org.junit.Assert.*;
import org.junit.Test;

public class GoogleTextMenuLifecycleTest {
    @Test public void oldPresentationNeverWinsAfterHandleDrag() {
        GoogleTextMenuLifecycle state = new GoogleTextMenuLifecycle();
        state.onTextChanged();
        long initial = state.revision();
        assertTrue(state.onGesture(true));
        assertTrue(state.dragging());
        assertFalse(state.isCurrent(initial));
        long during = state.revision();
        state.onTextChanged();
        assertFalse(state.isCurrent(during));
        assertTrue(state.onGesture(false));
        assertFalse(state.dragging());
    }
    @Test public void newTextInvalidatesPendingMenuButNotDrag() {
        GoogleTextMenuLifecycle state = new GoogleTextMenuLifecycle();
        state.onGesture(true);
        state.onTextChanged();
        long rev = state.revision();
        assertTrue(state.isCurrent(rev));
        assertTrue(state.dragging());
        state.onGesture(false);
        assertFalse(state.isCurrent(rev));
        assertFalse(state.onGesture(false));
    }
}
