package com.yagay.YFloat;

import static org.junit.Assert.*;
import org.junit.Test;

public final class SelectionDragControllerTest {
    @Test public void leadingCaretCrossesTrailingCaretAndSwitchesRole(){
        SelectionDragController.Range moved=SelectionDragController.move(
                4,6,SelectionDragController.START,9);
        assertEquals(6,moved.first);
        assertEquals(9,moved.last);
        assertEquals(SelectionDragController.END,moved.active);
        SelectionDragController.Range resumed=SelectionDragController.move(
                moved.first,moved.last,moved.active,10);
        assertEquals(6,resumed.first);
        assertEquals(10,resumed.last);
    }
    @Test public void trailingCaretCrossesLeadingCaretAndSwitchesRole(){
        SelectionDragController.Range moved=SelectionDragController.move(
                4,6,SelectionDragController.END,2);
        assertEquals(2,moved.first);
        assertEquals(4,moved.last);
        assertEquals(SelectionDragController.START,moved.active);
    }
    @Test public void draggingOneChineseGlyphDoesNotLoseSelection(){
        SelectionDragController.Range pair=SelectionDragController.move(
                2,3,SelectionDragController.START,3);
        assertEquals(3,pair.first);
        assertEquals(3,pair.last);
        pair=SelectionDragController.move(pair.first,pair.last,pair.active,4);
        assertEquals(3,pair.first);
        assertEquals(4,pair.last);
    }
    @Test public void invalidCandidateKeepsCurrentSelection(){
        SelectionDragController.Range unchanged=SelectionDragController.move(
                3,7,SelectionDragController.END,-1);
        assertEquals(3,unchanged.first);
        assertEquals(7,unchanged.last);
        assertEquals(SelectionDragController.END,unchanged.active);
    }
}
