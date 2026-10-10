package com.yagay.yui

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class YAppearanceStoreTest {
    private lateinit var store: YAppearanceStore

    @Before
    fun resetPreferences() {
        val context: Context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("yui_appearance_v1", Context.MODE_PRIVATE)
            .edit().clear().commit()
        store = YAppearanceStore(context)
    }

    @Test
    fun defaultsKeepStandardGeometryAndSystemTheme() {
        assertEquals("system", store.appearance().theme)
        assertEquals("standard", store.appearance().density)
        assertEquals(24, store.appearance().buttonRadiusDp)
        assertEquals(24, store.appearance().buttonPaddingHorizontalDp)
        assertEquals(12, store.appearance().effectiveGapDp)
        assertEquals(100, store.appearance().fontPercent)
        assertTrue(store.appearance().homeSwipePin)
        assertTrue(store.appearance().homeStatusVisible)
    }

    @Test
    fun moduleOverrideCanInheritAndResetWithoutChangingGlobal() {
        store.set(YSettingKey.DENSITY, "comfortable")
        assertEquals("comfortable", store.appearance("yfloat").density)
        store.set(YSettingKey.DENSITY, "compact", "yfloat")
        assertEquals("compact", store.appearance("yfloat").density)
        assertEquals("comfortable", store.appearance("ynotify").density)
        assertTrue(store.isOverridden(YSettingKey.DENSITY, "yfloat"))
        store.inherit(YSettingKey.DENSITY, "yfloat")
        assertFalse(store.isOverridden(YSettingKey.DENSITY, "yfloat"))
        assertEquals("comfortable", store.appearance("yfloat").density)
        store.set(YSettingKey.BUTTON_PADDING, "10", "yfloat")
        store.reset("yfloat")
        assertEquals(24, store.appearance("yfloat").buttonPaddingHorizontalDp)
        assertEquals("comfortable", store.appearance().density)
    }

    @Test
    fun exportImportPreservesGlobalAndModuleSettings() {
        store.set(YSettingKey.FONT_PERCENT, "115")
        store.set(YSettingKey.BUTTON_RADIUS, "8", "ynotify")
        store.set(YSettingKey.HOME_SWIPE_PIN, "false")
        val exported = store.exportJson()
        store.reset()
        store.reset("ynotify")
        store.importJson(exported)
        assertEquals(115, store.appearance().fontPercent)
        assertEquals(8, store.appearance("ynotify").buttonRadiusDp)
        assertFalse(store.appearance().homeSwipePin)
    }

    @Test
    fun malformedImportDoesNotWipeExistingSettings() {
        store.set(YSettingKey.THEME, "dark")
        val bad = """{"version":1,"global":{"theme":"light"},"modules":{"ynotify":{"button_radius":"999"}}}"""
        val failure = runCatching { store.importJson(bad) }
        assertTrue(failure.isFailure)
        assertEquals("dark", store.appearance().theme)
        assertFalse(store.isOverridden(YSettingKey.BUTTON_RADIUS, "ynotify"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidModuleIdentifier() {
        store.set(YSettingKey.THEME, "light", "../other")
    }
}
