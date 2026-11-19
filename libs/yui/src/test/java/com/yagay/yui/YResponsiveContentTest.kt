package com.yagay.yui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

/** Verifies content geometry, not just the presence of shared YUI component imports. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h700dp-xxhdpi")
@LooperMode(LooperMode.Mode.PAUSED)
class YResponsiveContentTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Composable
    private fun Pair() {
        YTheme(dynamicColor = false, darkTheme = false) {
            YResponsiveFieldPair(
                first = { field -> Box(field.height(48.dp).testTag("first")) },
                second = { field -> Box(field.height(48.dp).testTag("second")) },
            )
        }
    }

    @Test
    fun narrowScreenStacksBothInputs() {
        composeRule.setContent { Pair() }
        val first = composeRule.onNodeWithTag("first").fetchSemanticsNode().boundsInRoot
        val second = composeRule.onNodeWithTag("second").fetchSemanticsNode().boundsInRoot
        assertTrue("Second field must be below first on narrow content", second.top >= first.bottom)
    }

    @Test
    @Config(sdk = [35], qualifiers = "w720dp-h850dp-xxhdpi")
    fun expandedContentPlacesInputsSideBySide() {
        composeRule.setContent { Pair() }
        val first = composeRule.onNodeWithTag("first").fetchSemanticsNode().boundsInRoot
        val second = composeRule.onNodeWithTag("second").fetchSemanticsNode().boundsInRoot
        assertTrue("Second field must be to the right on wide content", second.left >= first.right)
        assertTrue("Wide fields must start at a matching Y", kotlin.math.abs(second.top - first.top) < 2f)
    }

    @Test
    fun narrowContentStacksActionBelowField() {
        composeRule.setContent {
            YTheme(dynamicColor = false, darkTheme = false) {
                YResponsiveFieldAction(
                    field = { field -> Box(field.height(48.dp).testTag("field")) },
                    action = { Box(Modifier.size(48.dp).testTag("action")) },
                )
            }
        }
        val field = composeRule.onNodeWithTag("field").fetchSemanticsNode().boundsInRoot
        val action = composeRule.onNodeWithTag("action").fetchSemanticsNode().boundsInRoot
        assertTrue("Action must remain visible below input instead of squeezing the field", action.top >= field.bottom)
    }
}
