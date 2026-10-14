package com.yagay.YFloat;

import static org.junit.Assert.*;
import java.util.List;
import org.junit.Test;

public class ImageMenuSettingsTest {
    @Test public void normalizedOrderDropsInvalidAndPreservesNewActions() {
        assertEquals(List.of("share","copy","done","find","save","reset"),
                ImageMenuSettings.normalized(List.of("share","copy","share","invalid")));
    }
    @Test public void bothContentTypesUseSharedMenuCount() {
        assertEquals(2,ImageMenuSettings.primaryCount(6,2));
        assertEquals(5,ImageMenuSettings.primaryCount(6,5));
        assertEquals(6,ImageMenuSettings.primaryCount(6,32));
    }
    @Test public void callbackIndicesRemainStableWhenOrderChanges() {
        assertEquals(0,ImageMenuSettings.index(ImageMenuSettings.DONE));
        assertEquals(2,ImageMenuSettings.index(ImageMenuSettings.COPY));
        assertEquals(5,ImageMenuSettings.index(ImageMenuSettings.RESET));
    }
}
