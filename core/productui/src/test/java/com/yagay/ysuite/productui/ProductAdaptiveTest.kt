package com.yagay.ysuite.productui

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class ProductAdaptiveTest {
    @Test
    fun compactBreakpointIsStable() {
        assertEquals(
            ProductWidthClass.Compact,
            classifyProductWidth(599.dp),
        )
        assertEquals(
            ProductWidthClass.Medium,
            classifyProductWidth(600.dp),
        )
    }

    @Test
    fun expandedBreakpointIsStable() {
        assertEquals(
            ProductWidthClass.Medium,
            classifyProductWidth(839.dp),
        )
        assertEquals(
            ProductWidthClass.Expanded,
            classifyProductWidth(840.dp),
        )
    }

    @Test
    fun paneClassificationCanDifferFromWindow() {
        val info =
            ProductAdaptiveInfo(
                widthClass = classifyProductWidth(620.dp),
                windowWidthClass =
                    classifyProductWidth(1000.dp),
            )

        assertEquals(
            ProductWidthClass.Medium,
            info.widthClass,
        )
        assertEquals(
            ProductWidthClass.Expanded,
            info.windowWidthClass,
        )
    }
}
