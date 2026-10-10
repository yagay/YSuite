package com.yagay.yui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class YAppearanceSettingsTest {
    private lateinit var context: Context

    @Before
    fun prepare() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("yui_appearance", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("yfloat_ui", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun legacyYFloatModeMigratesOnlyOnce() {
        context.getSharedPreferences("yfloat_ui", Context.MODE_PRIVATE)
            .edit().putInt("theme_mode", YAppearanceSettings.MODE_DARK).commit()

        assertEquals(YAppearanceSettings.MODE_DARK, YAppearanceSettings.mode(context))

        context.getSharedPreferences("yfloat_ui", Context.MODE_PRIVATE)
            .edit().putInt("theme_mode", YAppearanceSettings.MODE_LIGHT).commit()
        assertEquals(YAppearanceSettings.MODE_DARK, YAppearanceSettings.mode(context))
    }

    @Test
    fun unifiedModeOverridesSystemThemeAndClampsInvalidValues() {
        assertEquals(YAppearanceSettings.MODE_SYSTEM, YAppearanceSettings.mode(context))
        assertTrue(YAppearanceSettings.setMode(context, YAppearanceSettings.MODE_LIGHT))
        assertFalse(YAppearanceSettings.isDark(context, true))
        assertEquals(YAppearanceSettings.MODE_LIGHT, YAppearanceSettings.mode(context))

        assertTrue(YAppearanceSettings.setMode(context, YAppearanceSettings.MODE_DARK))
        assertTrue(YAppearanceSettings.isDark(context, false))

        assertTrue(YAppearanceSettings.setMode(context, 99))
        assertEquals(YAppearanceSettings.MODE_SYSTEM, YAppearanceSettings.mode(context))
        assertTrue(YAppearanceSettings.isDark(context, true))
    }
}
