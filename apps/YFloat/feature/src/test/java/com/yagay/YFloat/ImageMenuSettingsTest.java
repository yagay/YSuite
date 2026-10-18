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
    @Test public void customImageActionsAreRetainedAndDeduplicated() {
        String custom="imageapp:valid";
        assertEquals(List.of("share",custom,"done","find","copy","save","reset"),
                ImageMenuSettings.normalized(List.of("share",custom,custom,"unknown"),
                        List.of(custom)));
        assertTrue(ImageCustomMenuStore.supportedMode(ImageCustomMenuStore.SEND));
        assertTrue(ImageCustomMenuStore.supportedMode(ImageCustomMenuStore.VIEW));
        assertFalse(ImageCustomMenuStore.supportedMode("process_text"));
    }

    @Test public void editableActionsStayInsideCircleCrop() {
        assertFalse(ImageMenuSettings.availableForSelection(false, ImageMenuSettings.DONE));
        assertFalse(ImageMenuSettings.availableForSelection(false, ImageMenuSettings.RESET));
        assertTrue(ImageMenuSettings.availableForSelection(true, ImageMenuSettings.DONE));
        assertTrue(ImageMenuSettings.availableForSelection(false, ImageMenuSettings.COPY));
    }

    @Test public void callbackIndicesRemainStableWhenOrderChanges() {
        assertEquals(0,ImageMenuSettings.index(ImageMenuSettings.DONE));
        assertEquals(2,ImageMenuSettings.index(ImageMenuSettings.COPY));
        assertEquals(5,ImageMenuSettings.index(ImageMenuSettings.RESET));
    }
}
