package com.yagay.YFloat;

import static org.junit.Assert.*;
import org.junit.Test;
import java.util.List;

/** Regression tests for one-session ML Kit visual selection state. */
public final class CircleMlSelectionSessionTest {
    private static CircleMlBlockSelection.Entry e(int i,String block,
            int line,float x,float y){
        return new CircleMlBlockSelection.Entry(i,line,block,0,0,i,
                x,y,x+20,y+24);
    }
    private static CircleMlBlockSelection icons(){
        return CircleMlBlockSelection.of(List.of(
                e(0,"a",0,10,10),e(1,"a",0,31,10),
                e(2,"b",0,110,10),e(3,"b",0,131,10),
                e(4,"c",0,220,10),e(5,"c",0,241,10)));
    }
    @Test public void reverseDragRemovesNoLongerVisitedAppLabels(){
        CircleMlBlockSelection t=icons();
        CircleMlSelectionSession s=new CircleMlSelectionSession();
        s.bind(t);s.initialize(0,1,0);s.beginDrag(2);
        assertTrue(s.moveTo(3));
        assertTrue(s.moveTo(5));
        assertTrue(s.mask().contains(4));
        assertTrue(s.moveTo(3));
        assertEquals(2,s.routeLength());
        assertFalse("Reversing must unselect the third app",
                s.mask().contains(4));
        assertFalse(s.mask().contains(5));
        assertTrue(s.mask().contains(2));
    }

    @Test public void invertedPaddleOrdinalsNormalizeByMlVisualOrder(){
        CircleMlBlockSelection t=CircleMlBlockSelection.of(List.of(
                e(0,"right",0,250,10),
                e(1,"left",0,10,10),
                e(2,"left",0,31,10),
                e(3,"further",0,360,10)));
        CircleMlSelectionSession s=new CircleMlSelectionSession();
        s.bind(t);s.initialize(0,1,-1);
        assertEquals("ML leftmost is selection start",1,s.first);
        assertEquals(0,s.last);
        s.beginDrag(1);
        assertTrue(s.moveTo(3));
        assertEquals(0,s.first);
        assertEquals(3,s.last);
        assertEquals("Crossover swaps active grip",2,s.movingHandle);
    }

    @Test public void rebuildingMlBlocksReplaysThePhysicalDragPath(){
        CircleMlBlockSelection t=icons();
        CircleMlSelectionSession s=new CircleMlSelectionSession();
        s.bind(t);s.initialize(0,1,0);s.beginDrag(2);
        s.moveTo(3);s.moveTo(5);
        assertEquals(3,s.routeLength());
        CircleMlBlockSelection refreshed=CircleMlBlockSelection.of(List.of(
                e(0,"fresh-a",0,10,10),e(1,"fresh-a",0,31,10),
                e(2,"fresh-b",0,110,10),e(3,"fresh-b",0,131,10),
                e(4,"fresh-c",0,220,10),e(5,"fresh-c",0,241,10)));
        s.bind(refreshed);s.rebase(0,5);
        assertTrue(s.mask().contains(3));
        assertTrue(s.mask().contains(5));
        assertEquals(3,s.routeLength());
    }

    @Test public void explicitSelectAllIgnoresDisconnectedBlocks(){
        CircleMlSelectionSession s=new CircleMlSelectionSession();
        s.bind(icons());s.chooseAll(6);
        assertEquals(6,s.mask().selectedCount);
        assertEquals(6,s.mask().orderedIndices.size());
    }

    @Test public void immutableReturnedSelectionFollowsMlReadingOrder(){
        CircleMlBlockSelection t=CircleMlBlockSelection.of(List.of(
                e(0,"right",0,250,10),
                e(1,"left",0,10,10),e(2,"left",0,31,10)));
        CircleMlSelectionSession s=new CircleMlSelectionSession();
        s.bind(t);s.initialize(1,0,-1);
        var mask=s.mask();
        assertEquals(List.of(1,2,0),mask.orderedIndices);
        try{
            mask.orderedIndices.add(0);
            fail("Selection view must be immutable");
        }catch(UnsupportedOperationException expected){}
    }

    @Test public void sessionResetClearsOldSelectionsAndDragRoutes(){
        CircleMlSelectionSession s=new CircleMlSelectionSession();
        s.bind(icons());s.initialize(0,1,0);s.beginDrag(2);s.moveTo(3);
        s.reset();
        assertEquals(-1,s.first);
        assertEquals(-1,s.last);
        assertEquals(0,s.movingHandle);
        assertEquals(0,s.routeLength());
        assertEquals(0,s.mask().selectedCount);
    }
}
