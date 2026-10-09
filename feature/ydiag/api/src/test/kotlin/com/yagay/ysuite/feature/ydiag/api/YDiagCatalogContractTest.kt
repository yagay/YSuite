package com.yagay.ysuite.feature.ydiag.api
import org.junit.Assert.*
import org.junit.Test
class YDiagCatalogContractTest {
    @Test fun everyPresetRefersToRealOptionAndCompleteHasEveryOption() {
        val ids = YDiagCatalog.options.map { it.id }.toSet()
        assertEquals(ids.size, YDiagCatalog.options.size)
        for (preset in YDiagCatalog.presets) {
            assertTrue(ids.containsAll(preset.options))
        }
        assertEquals(ids, YDiagCatalog.presets.first { it.id == "complete" }.options)
    }
    @Test fun quickPresetDoesNotEnableDeepPerfettoCapture() {
        assertFalse(YDiagCatalog.presets.first { it.id == "quick" }.options.contains("perfetto"))
    }
}
