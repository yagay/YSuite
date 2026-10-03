package com.yagay.yui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w390dp-h844dp-xxhdpi")
@LooperMode(LooperMode.Mode.PAUSED)
class YUiScreenshotTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun catalog_compact_light() {
        composeRule.setContent {
            YTheme(dynamicColor = false, darkTheme = false) {
                YComponentCatalogScreen()
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/yui_catalog_compact_light.png")
    }

    @Test
    @Config(sdk = [35], qualifiers = "w840dp-h900dp-xxhdpi")
    fun catalog_expanded_dark() {
        composeRule.setContent {
            YTheme(dynamicColor = false, darkTheme = true) {
                YComponentCatalogScreen()
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/yui_catalog_expanded_dark.png")
    }

    @Test
    fun catalog_large_font() {
        composeRule.setContent {
            val currentDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(currentDensity.density, fontScale = 2f),
            ) {
                YTheme(dynamicColor = false, darkTheme = false) {
                    YComponentCatalogScreen()
                }
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/yui_catalog_large_font.png")
    }
}
