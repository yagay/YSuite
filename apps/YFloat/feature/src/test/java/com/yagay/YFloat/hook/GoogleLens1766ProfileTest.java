package com.yagay.YFloat.hook;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import java.util.List;

public final class GoogleLens1766ProfileTest {
    private static final class WordData {
        final String a="  Google recognized words  ";
        final List<String> b=List.of("Google","recognized","words");
        final List<Object> d=List.of();
    }
    private static final class InvalidData {
        final String a="not OCR";
    }

    @Test public void google1766NestedTextIsReadFromWordData() {
        assertEquals("Google recognized words",
                GoogleLens1766Profile.textFromWordData(new WordData()));
        assertEquals("",GoogleLens1766Profile.textFromWordData(new InvalidData()));
        assertEquals("",GoogleLens1766Profile.textFromWordData(null));
    }

    @Test public void imageRegionModelIsNeverMistakenForWordSelection() {
        assertTrue(GoogleLens1766Profile.isDirectRegionSelectionClass("dxzk"));
        assertFalse(GoogleLens1766Profile.isDirectRegionSelectionClass("dxzh"));
        assertFalse(GoogleLens1766Profile.isDirectRegionSelectionClass("dxzo"));
        assertFalse(GoogleLens1766Profile.isDirectRegionSelectionClass(null));
    }

    @Test public void unknownOrMissingAbiFailsClosed() {
        assertFalse(GoogleLens1766Profile.selectionValidationError(null).isBlank());
        assertEquals("",GoogleLens1766Profile.selection(null).text);
    }
}
