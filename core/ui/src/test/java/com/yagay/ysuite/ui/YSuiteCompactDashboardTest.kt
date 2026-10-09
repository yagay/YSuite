package com.yagay.ysuite.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class YSuiteCompactDashboardTest {
    @Test
    fun pinnedModulesMoveFirstWithoutChangingTheRegistryOrder() {
        assertEquals(
            listOf("ydownload", "ynotify", "yfiles", "yparam"),
            compactModuleOrder(
                ids = listOf("yfiles", "ydownload", "yparam", "ynotify"),
                pinned = setOf("ynotify", "ydownload"),
            ),
        )
    }

    @Test
    fun unknownPinsDoNotCreateMissingModules() {
        assertEquals(
            listOf("yfiles", "ynotify"),
            compactModuleOrder(
                ids = listOf("yfiles", "ynotify"),
                pinned = setOf("removed_module"),
            ),
        )
    }

    @Test
    fun emptyPinnedSetRetainsRegistryOrder() {
        val ids = listOf("yfiles", "ynotify", "ydiag")
        assertEquals(ids, compactModuleOrder(ids, emptySet()))
    }
}
