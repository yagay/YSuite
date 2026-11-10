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

    @Test public void cachedHitTestKeepsRectGeometryAndDefensiveCopy() {
        ScreenCandidate candidate = new ScreenCandidate(
                new Rect(10, 20, 110, 80), ScreenCandidate.Type.TEXT,
                "Visible", "", "TextView", "title", "app",
                1, false, false, false, false, false);
        assertTrue(candidate.contains(10, 20));
        assertTrue(candidate.contains(109, 79));
        assertFalse(candidate.contains(110, 80));
        Rect mutableCopy = candidate.bounds();
        mutableCopy.setEmpty();
        assertTrue(candidate.contains(20, 30));
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
