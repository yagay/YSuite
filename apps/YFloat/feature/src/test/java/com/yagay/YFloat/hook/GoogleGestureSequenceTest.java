package com.yagay.YFloat.hook;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public final class GoogleGestureSequenceTest {
    private final GoogleGestureSequence s = new GoogleGestureSequence();

    @Test public void duplicateWrappersDoNotEmitDuplicateTransitions() {
        assertEquals(GoogleGestureSequence.Transition.START,
                s.accept(0, 1000, 1000, 1010));
        assertEquals(GoogleGestureSequence.Transition.IGNORE,
                s.accept(0, 1000, 1000, 1011));
        assertEquals(GoogleGestureSequence.Transition.MOVE,
                s.accept(2, 1000, 1020, 1030));
        assertEquals(GoogleGestureSequence.Transition.IGNORE,
                s.accept(2, 1000, 1020, 1031));
        assertEquals(GoogleGestureSequence.Transition.END,
                s.accept(1, 1000, 1060, 1080));
        assertEquals(GoogleGestureSequence.Transition.IGNORE,
                s.accept(1, 1000, 1060, 1081));
    }

    @Test public void epochBasedSyntheticCancelCannotEndUserGesture() {
        s.accept(0, 5747385, 5747385, 5747386);
        assertEquals(GoogleGestureSequence.Transition.IGNORE,
                s.accept(3, 1791922276680L, 1791922276680L, 5747680));
        assertEquals(GoogleGestureSequence.Transition.MOVE,
                s.accept(2, 5747385, 5747682, 5747683));
        assertEquals(GoogleGestureSequence.Transition.END,
                s.accept(1, 5747385, 5747720, 5747721));
    }

    @Test public void orphanAndOutOfOrderEventsAreIgnored() {
        assertEquals(GoogleGestureSequence.Transition.IGNORE,
                s.accept(3, 100, 110, 120));
        assertEquals(GoogleGestureSequence.Transition.IGNORE,
                s.accept(2, 100, 110, 120));
        s.accept(0, 100, 100, 110);
        s.accept(2, 100, 112, 120);
        assertEquals(GoogleGestureSequence.Transition.IGNORE,
                s.accept(2, 100, 111, 120));
    }

    @Test public void rearmInvalidatesPreviousGesture() {
        s.accept(0, 100, 100, 100);
        s.accept(0, 200, 200, 200);
        assertEquals(GoogleGestureSequence.Transition.IGNORE,
                s.accept(1, 100, 220, 221));
        assertEquals(GoogleGestureSequence.Transition.END,
                s.accept(1, 200, 230, 231));
    }
}
