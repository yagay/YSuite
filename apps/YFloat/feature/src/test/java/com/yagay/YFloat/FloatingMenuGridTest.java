package com.yagay.YFloat;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class FloatingMenuGridTest {
    @Test public void shortTextActionsPreferOneHorizontalRow() {
        assertEquals(3,FloatingMenuUi.gridColumns(600,48,120,3));
    }

    @Test public void sixActionsUseTwoRowsOfThreeWhenTheyFit() {
        assertEquals(3,FloatingMenuUi.gridColumns(600,48,130,6));
    }

    @Test public void longLabelsWrapIntoTwoColumnsInsteadOfAFullVerticalList() {
        assertEquals(2,FloatingMenuUi.gridColumns(350,48,140,6));
        assertEquals(2,FloatingMenuUi.gridColumns(175,48,400,5));
    }

    @Test public void extremelyNarrowScreensHaveASafeSingleColumnFallback() {
        assertEquals(1,FloatingMenuUi.gridColumns(80,48,140,4));
        assertEquals(1,FloatingMenuUi.gridColumns(400,48,100,1));
    }
}
