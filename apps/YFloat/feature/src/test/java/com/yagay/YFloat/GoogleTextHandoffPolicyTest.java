package com.yagay.YFloat;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class GoogleTextHandoffPolicyTest {
    @Test public void lastValidSelectionSurvivesTransientEmptyTerminalEvent() {
        assertTrue(GoogleTextHandoffPolicy.canRecover("hello", 1000L, 1450L));
        assertTrue(GoogleTextHandoffPolicy.canRecover("selected text", 1500L, 3499L));
    }

    @Test public void staleAndMissingSelectionsNeverBecomeResults() {
        assertFalse(GoogleTextHandoffPolicy.canRecover("", 1000L, 1200L));
        assertFalse(GoogleTextHandoffPolicy.canRecover("word", 0L, 1000L));
        assertFalse(GoogleTextHandoffPolicy.canRecover("word", 1000L, 3001L));
        assertFalse(GoogleTextHandoffPolicy.canRecover("word", 3000L, 2000L));
    }
}
