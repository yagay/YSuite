package com.yagay.ysuite.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class TopLevelNavigationTest {
    @Test
    fun homeContainsOnlyHome() {
        assertEquals(
            listOf("__home__"),
            topLevelDestinationStack(
                homeRoute = "__home__",
                targetRoute = null,
            ),
        )
    }

    @Test
    fun featureStackContainsHomeAndCurrentFeatureOnly() {
        assertEquals(
            listOf("__home__", "settings"),
            topLevelDestinationStack(
                homeRoute = "__home__",
                targetRoute = "settings",
            ),
        )
    }

    @Test
    fun switchingFeatureDoesNotPreservePreviousFeature() {
        val first =
            topLevelDestinationStack(
                homeRoute = "__home__",
                targetRoute = "yfiles",
            )
        val switched =
            topLevelDestinationStack(
                homeRoute = first.first(),
                targetRoute = "ydownload",
            )

        assertEquals(
            listOf("__home__", "ydownload"),
            switched,
        )
    }
}
