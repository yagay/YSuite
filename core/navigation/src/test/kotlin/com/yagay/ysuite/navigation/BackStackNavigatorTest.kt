package com.yagay.ysuite.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackStackNavigatorTest {
    @Test
    fun navigateBackAndReplaceAreDeterministic() {
        val home = NavigationEntry(RouteId("home"))
        val settings = NavigationEntry(RouteId("settings"))
        val detail = NavigationEntry(RouteId("detail"))
        val navigator = BackStackNavigator(home)

        navigator.navigate(settings)
        assertEquals(settings, navigator.current)
        assertEquals(listOf(home, settings), navigator.backStack)

        navigator.navigate(settings)
        assertEquals(2, navigator.backStack.size)

        navigator.replace(detail)
        assertEquals(detail, navigator.current)

        assertTrue(navigator.back())
        assertEquals(home, navigator.current)
        assertFalse(navigator.back())
    }
}
