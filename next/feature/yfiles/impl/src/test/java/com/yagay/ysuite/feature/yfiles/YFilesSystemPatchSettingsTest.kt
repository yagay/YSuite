package com.yagay.ysuite.feature.yfiles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class YFilesSystemPatchSettingsTest {
    @Test
    fun defaultsPreserveNativePickerSorting() {
        val settings = YFilesSystemPatchSettings()

        assertEquals(YFilesSystemPatchSettings.SORT_SYSTEM, settings.defaultSort)
        assertEquals(YFilesSystemPatchSettings.DIRECTION_SYSTEM, settings.defaultDirection)
        assertTrue(settings.enabled)
    }

    @Test
    fun allSortFieldsAndDirectionsCanBeStoredIndependently() {
        YFilesSystemPatchSettings.validSorts.forEach { sort ->
            YFilesSystemPatchSettings.validDirections.forEach { direction ->
                val settings = YFilesSystemPatchSettings(
                    defaultSort = sort,
                    defaultDirection = direction,
                )
                assertEquals(sort, settings.defaultSort)
                assertEquals(direction, settings.defaultDirection)
            }
        }
    }
}
