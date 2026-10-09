package com.yagay.YSuite

import org.junit.Assert.assertEquals
import org.junit.Test

class CompactSuiteHomeTest {
    @Test
    fun pinningPreservesRelativeRegistryOrder() {
        assertEquals(
            listOf("ynotify", "ynfc", "yentrycleaner", "ypower"),
            orderedHomeIds(
                ids = listOf("yentrycleaner", "ynotify", "ypower", "ynfc"),
                pinned = setOf("ynfc", "ynotify"),
            ),
        )
    }

    @Test
    fun obsoletePinsNeverCreatePhantomRows() {
        assertEquals(
            listOf("ydiag", "yfiles"),
            orderedHomeIds(listOf("ydiag", "yfiles"), setOf("removed_feature")),
        )
    }

    @Test
    fun defaultOrderRemainsUnchanged() {
        val ids = listOf("ydiag", "ynotify", "yparam")
        assertEquals(ids, orderedHomeIds(ids, emptySet()))
    }
}
