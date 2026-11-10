package com.yagay.YFloat;

import android.graphics.Rect;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ScreenCandidateSemanticsTest {
    @Test public void semanticLabelDoesNotBecomeVisibleText() {
        ScreenCandidate candidate = new ScreenCandidate(
                new Rect(10, 20, 110, 80),
                ScreenCandidate.Type.NON_TEXT,
                "",
                "Settings",
                "android.widget.ImageView",
                "app:id/settings_icon",
                "app",
                3,
                false,
                true,
                false,
                true,
                true);

        assertFalse(candidate.hasText());
        assertTrue(candidate.hasSemanticLabel());
        assertEquals("Settings", candidate.label());
        assertFalse(candidate.toViewNodeCandidate().hasText());
        assertEquals("Settings", candidate.toViewNodeCandidate().semanticLabel());
    }

    @Test public void cachedHitTestKeepsAndroidRectBoundarySemantics() {
        // Plain JVM Android Rect methods are not implemented.
        // Verify the actual production geometry in a framework-independent test.
        assertTrue(ScreenCandidate.containsBounds(10, 20, 110, 80, 10, 20));
        assertTrue(ScreenCandidate.containsBounds(10, 20, 110, 80, 109, 79));
        assertTrue(ScreenCandidate.containsBounds(10, 20, 110, 80, 20, 30));
        assertFalse(ScreenCandidate.containsBounds(10, 20, 110, 80, 110, 80));
        assertFalse(ScreenCandidate.containsBounds(10, 20, 110, 80, 9, 30));
        assertFalse(ScreenCandidate.containsBounds(10, 20, 10, 80, 10, 30));
        assertFalse(ScreenCandidate.containsBounds(10, 20, 110, 20, 20, 20));
    }

    @Test public void visibleTextRemainsAuthoritativeLabel() {
        ScreenCandidate candidate = new ScreenCandidate(
                new Rect(0, 0, 100, 40),
                ScreenCandidate.Type.TEXT,
                "Visible",
                "Semantic",
                "android.widget.TextView",
                "app:id/title",
                "app",
                2,
                false,
                false,
                false,
                false,
                false);

        assertTrue(candidate.hasText());
        assertEquals("Visible", candidate.label());
        assertEquals("Visible", candidate.toViewNodeCandidate().text());
    }
}
