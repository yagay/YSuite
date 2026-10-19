package com.yagay.YFloat;

import static org.junit.Assert.*;
import org.junit.Test;
import java.util.List;
import java.util.Set;

/** ML structural selection, no real device or ML recognizer required. */
public final class CircleMlBlockSelectionTest {
    private static CircleMlBlockSelection.Entry g(int i,String ml,int line,
            float left,float top,float right,float bottom){
        return new CircleMlBlockSelection.Entry(i,line,ml,0,0,i,
                left,top,right,bottom);
    }

    @Test public void adjacentMlSymbolsFormOneTextSelectionBlock(){
        var engine=CircleMlBlockSelection.of(List.of(
                g(0,"zh:block:2",1,20,20,40,45),
                g(1,"zh:block:2",1,41,20,61,45),
                g(2,"zh:block:2",1,62,20,82,45)));
        assertEquals(engine.blockOf(0),engine.blockOf(2));
        var selected=engine.select(0,2,1,false,Set.of());
        assertEquals(3,selected.selectedCount);
    }

    @Test public void desktopLauncherCaptionInAnotherColumnNeverAutoSelects(){
        var engine=CircleMlBlockSelection.of(List.of(
                g(0,"zh:block:rednote",0,25,90,49,113),
                g(1,"zh:block:tools",1,235,90,259,113),
                g(2,"zh:block:rednote",0,50,90,73,113),
                g(3,"zh:block:tools",1,260,90,285,113),
                g(4,"zh:block:douyin",2,25,155,49,178),
                g(5,"zh:block:douyin",2,50,155,73,178)));
        assertNotEquals(engine.blockOf(0),engine.blockOf(1));
        assertNotEquals(engine.blockOf(0),engine.blockOf(4));
        var selection=engine.select(0,4,-1,false,Set.of());
        assertTrue(selection.contains(0));
        assertTrue(selection.contains(2));
        assertTrue(selection.contains(4));
        assertFalse("Unvisited Tools caption cannot turn blue",selection.contains(1));
        assertFalse("Unvisited Tools caption cannot turn blue",selection.contains(3));
        assertEquals(3,selection.selectedCount);
    }

    @Test public void touchedAdditionalBlockCanJoinWithoutOthers(){
        var engine=CircleMlBlockSelection.of(List.of(
                g(0,"ml:a",0,10,10,28,30),
                g(1,"ml:a",0,29,10,47,30),
                g(2,"ml:b",0,220,10,238,30),
                g(3,"ml:b",0,239,10,257,30),
                g(4,"ml:c",1,10,95,28,115)));
        var visited=Set.of(engine.blockOf(1),engine.blockOf(4));
        var selection=engine.select(0,4,-1,false,visited);
        assertTrue(selection.contains(0));
        assertTrue(selection.contains(1));
        assertFalse(selection.contains(2));
        assertFalse(selection.contains(3));
        assertTrue(selection.contains(4));
    }

    @Test public void erroneousMlTextBlockWithTwoDistantIconsIsSplit(){
        var engine=CircleMlBlockSelection.of(List.of(
                g(0,"same-ml-block",0,10,100,30,120),
                g(1,"same-ml-block",0,31,100,51,120),
                g(2,"same-ml-block",0,230,100,250,120),
                g(3,"same-ml-block",0,251,100,271,120)));
        assertEquals(engine.blockOf(0),engine.blockOf(1));
        assertEquals(engine.blockOf(2),engine.blockOf(3));
        assertNotEquals(engine.blockOf(1),engine.blockOf(2));
        var mask=engine.select(0,3,-1,false,Set.of());
        assertEquals(4,mask.selectedCount); // endpoints were deliberately touched
        assertEquals(2,mask.touchedBlocks);
    }

    @Test public void physicalTextLinesWithinSameMlBlockCanExtend(){
        var engine=CircleMlBlockSelection.of(List.of(
                g(0,"paragraph",0,15,10,35,34),
                g(1,"paragraph",0,36,10,56,34),
                g(2,"paragraph",1,15,47,35,71),
                g(3,"paragraph",1,36,47,56,71)));
        assertEquals(engine.blockOf(0),engine.blockOf(3));
        assertEquals(4,engine.select(0,3,-1,false,Set.of()).selectedCount);
    }

    @Test public void firstTapRemainsOnInitialLine(){
        var engine=CircleMlBlockSelection.of(List.of(
                g(0,"paragraph",0,15,10,35,34),
                g(1,"paragraph",0,36,10,56,34),
                g(2,"paragraph",1,15,47,35,71)));
        var mask=engine.select(0,2,0,false,Set.of());
        assertTrue(mask.contains(0));
        assertTrue(mask.contains(1));
        assertFalse(mask.contains(2));
    }

    @Test public void emptyGapDoesNotSnapToFarAwayIcon(){
        var engine=CircleMlBlockSelection.of(List.of(
                g(0,"app-a",0,10,10,30,35),
                g(1,"app-b",0,310,10,330,35)));
        assertEquals(-1,engine.hit(165,20,18f));
        assertEquals(0,engine.hit(15,20,18f));
        assertEquals(1,engine.hit(320,20,18f));
    }

    @Test public void explicitSelectAllStillSelectsAllBlocks(){
        var engine=CircleMlBlockSelection.of(List.of(
                g(0,"a",0,10,10,30,30),
                g(1,"b",0,180,10,200,30),
                g(2,"c",1,10,110,30,130)));
        assertEquals(3,engine.select(0,2,-1,true,Set.of()).selectedCount);
    }

    @Test public void fallbackWithoutMlTextStillUsesSpatialGrouping(){
        var engine=CircleMlBlockSelection.of(List.of(
                g(0,"",0,10,10,30,33),g(1,"",0,32,10,52,33),
                g(2,"",0,220,10,240,33)));
        assertEquals(engine.blockOf(0),engine.blockOf(1));
        assertNotEquals(engine.blockOf(0),engine.blockOf(2));
    }
}
