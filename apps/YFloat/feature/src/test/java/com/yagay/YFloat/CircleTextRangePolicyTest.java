package com.yagay.YFloat;

import static org.junit.Assert.*;
import org.junit.Test;

/** No Android graphics/runtime needed: verify the screen-selection rules. */
public final class CircleTextRangePolicyTest {
    @Test public void underlinePrefersNearbyApplicationCaptionOverDistantIconGlyphs(){
        // Screenshot case: 小红书 text in an icon and "rednote" caption below
        // are two OCR lines; proximity to the actual swipe wins.
        float icon=CircleTextRangePolicy.lineScore(3,83f,26f);
        float caption=CircleTextRangePolicy.lineScore(2,3f,32f);
        assertTrue(caption>icon);
    }

    @Test public void nearbyLatinWordsStayTogetherAcrossOrdinarySpaces(){
        assertFalse(CircleTextRangePolicy.splitsLabel(10,18));
        assertFalse(CircleTextRangePolicy.splitsLabel(28,16));
    }

    @Test public void launcherGridGapCannotJoinUnrelatedAppLabels(){
        assertTrue(CircleTextRangePolicy.splitsLabel(148,28));
        assertTrue(CircleTextRangePolicy.splitsLabel(80,19));
    }

    @Test public void normalSelectionIsLineLockedWhileExplicitSelectAllMayCross(){
        assertTrue(CircleTextRangePolicy.sameLine(3,3));
        assertFalse(CircleTextRangePolicy.sameLine(4,3));
        assertTrue(CircleTextRangePolicy.sameLine(4,-1));
    }
}
