package com.yagay.YFloat.hook;

import static org.junit.Assert.*;
import org.junit.Test;

public class GoogleWordLineGeometryTest {
    @Test public void multiLineSelectionDoesNotDrawOneGiantRectangle() {
        int[] rows=GoogleWordLineGeometry.mergeProjected(new int[]{
                100,100,160,120, 168,101,210,121,
                110,142,240,163});
        assertArrayEquals(new int[]{
                100,100,210,121, 110,142,240,163},rows);
    }

    @Test public void separateColumnsOnSameLineAreNotFalselyJoined() {
        assertArrayEquals(new int[]{
                100,100,120,120, 400,100,420,120},
                GoogleWordLineGeometry.mergeProjected(new int[]{
                        400,100,420,120,100,100,120,120}));
    }

    @Test public void invalidWordsAreRejected() {
        assertArrayEquals(new int[]{10,20,60,40},
                GoogleWordLineGeometry.mergeProjected(new int[]{
                        10,20,60,40, 55,10,54,30}));
        assertNull(GoogleWordLineGeometry.mergeProjected(null));
    }
}
