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
        assertEquals(12, store.appearance().buttonRadiusDp)
        assertEquals(16, store.appearance().buttonPaddingHorizontalDp)
        assertEquals(12, store.appearance().effectiveGapDp)
        assertEquals(48, store.appearance().buttonHeightDp)
        assertEquals(4, store.appearance().buttonVerticalPaddingDp)
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
        assertEquals(16, store.appearance("yfloat").buttonPaddingHorizontalDp)
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


    @Test
    fun catalogContainsEachAppearanceControlExactlyOnce() {
        val definitions = YUiControlRegistry.definitions
        assertEquals(YUiControlRegistry.groups.toSet(), definitions.map { it.group }.toSet())
        assertEquals(definitions.size, definitions.map { it.key }.toSet().size)
        assertFalse(definitions.any { it.key == YSettingKey.HOME_STATUS || it.key == YSettingKey.HOME_SWIPE_PIN })
        definitions.forEach { definition ->
            assertEquals(definition.key.default, definition.key.validate(definition.key.default))
            if (definition.kind == YControlKind.RANGE) {
                assertTrue(definition.key.default.toInt() in definition.minimum..definition.maximum)
            }
        }
    }

    @Test
    fun controlAppearanceSettingsApplyGloballyAndCanBeOverridden() {
        store.set(YSettingKey.BUTTON_HEIGHT, "58")
        store.set(YSettingKey.CARD_RADIUS, "6")
        store.set(YSettingKey.ACCENT, "teal")
        assertEquals(58, store.appearance("yfloat").buttonHeightDp)
        assertEquals(6, store.appearance("ynotify").cardRadiusDp)
        assertEquals("teal", store.appearance("yentrycleaner").accent)
        store.set(YSettingKey.BUTTON_HEIGHT, "64", "ynotify")
        assertEquals(64, store.appearance("ynotify").buttonHeightDp)
        assertEquals(58, store.appearance("yfloat").buttonHeightDp)
        val serialized = store.exportJson()
        store.importJson(serialized)
        assertEquals(64, store.appearance("ynotify").buttonHeightDp)
    }

    @Test
    fun fullRangeAllowsExactOneDpStepsWithSharedValidation() {
        val controls = YUiControlRegistry.definitions.filter { it.kind == YControlKind.RANGE }
        assertTrue(controls.isNotEmpty())
        controls.forEach { definition ->
            assertEquals(1, definition.step)
            val key = definition.key
            assertEquals(key.minimum, definition.minimum)
            assertEquals(key.maximum, definition.maximum)
            store.set(key, definition.minimum.toString())
            assertEquals(definition.minimum.toString(), store.value(key))
            store.set(key, definition.maximum.toString())
            assertEquals(definition.maximum.toString(), store.value(key))
            assertTrue(runCatching { store.set(key, (definition.maximum + 1).toString()) }.isFailure)
            assertEquals(definition.maximum.toString(), store.value(key))
        }
        store.set(YSettingKey.BUTTON_RADIUS, "1")
        store.set(YSettingKey.BUTTON_HEIGHT, "27")
        store.set(YSettingKey.BUTTON_PADDING, "0")
        assertEquals(1, store.appearance().buttonRadiusDp)
        assertEquals(27, store.appearance().buttonHeightDp)
        assertEquals(0, store.appearance().buttonPaddingHorizontalDp)
        store.set(YSettingKey.DENSITY, "comfortable")
        store.set(YSettingKey.CONTROL_GAP, "96")
        assertEquals(128, store.appearance().effectiveGapDp)
        store.set(YSettingKey.BUTTON_RADIUS, "500")
        store.set(YSettingKey.BUTTON_PADDING, "500")
        store.set(YSettingKey.BUTTON_HEIGHT, "16")
        store.set(YSettingKey.ROW_HEIGHT, "0")
        store.set(YSettingKey.FONT_PERCENT, "399")
        store.set(YSettingKey.ICON_TOUCH_TARGET, "16")
        store.set(YSettingKey.ICON_VISUAL_SIZE, "140")
        store.set(YSettingKey.LIST_ICON_SIZE, "132")
        store.set(YSettingKey.TOOLBAR_HEIGHT, "180")
        store.set(YSettingKey.PAGE_VERTICAL_PADDING, "0")
        assertEquals(500, store.appearance().buttonRadiusDp)
        assertEquals(500, store.appearance().buttonPaddingHorizontalDp)
        assertEquals(16, store.appearance().buttonHeightDp)
        assertEquals(0, store.appearance().rowHeightDp)
        assertEquals(399, store.appearance().fontPercent)
        assertEquals(16, store.appearance().iconTouchTargetDp)
        assertEquals(140, store.appearance().iconVisualSizeDp)
        assertEquals(132, store.appearance().listIconSizeDp)
        assertEquals(180, store.appearance().toolbarHeightDp)
        assertEquals(0, store.appearance().pageVerticalPaddingDp)
        val json = store.exportJson()
        store.reset()
        store.importJson(json)
        assertEquals(500, store.appearance().buttonRadiusDp)
        assertEquals(140, store.appearance().iconVisualSizeDp)
        assertEquals(0, store.appearance().pageVerticalPaddingDp)
    }

    @Test
    fun overlayAppearancesAreGlobalByDefaultAndOverridePerModule() {
        val keys = listOf(
            YSettingKey.FLOAT_ICON_ALPHA, YSettingKey.FLOAT_ICON_SIZE,
            YSettingKey.FLOAT_EDGE_VISIBLE, YSettingKey.FLOAT_BORDER_WIDTH,
            YSettingKey.FLOAT_TRAIL_ALPHA, YSettingKey.FLOAT_TRAIL_WIDTH,
            YSettingKey.FLOAT_MENU_COUNT, YSettingKey.FLOAT_BORDER_COLOR,
            YSettingKey.FLOAT_ICON_STYLE, YSettingKey.FLOAT_TRAIL_STYLE,
            YSettingKey.FLOAT_TRAIL_GRADIENT, YSettingKey.FLOAT_TRAIL_COLORS,
            YSettingKey.FLOAT_BORDER_VISIBLE, YSettingKey.FLOAT_TRAIL_VISIBLE,
        )
        val controls = YUiControlRegistry.forGroup(YControlGroup.OVERLAYS)
        assertEquals(keys.toSet(), controls.map { it.key }.toSet())
        assertEquals("62", store.value(YSettingKey.FLOAT_ICON_ALPHA, "yfloat"))
        store.set(YSettingKey.FLOAT_ICON_ALPHA, "19")
        store.set(YSettingKey.FLOAT_TRAIL_COLORS, "#123456,#ABCDEF")
        store.set(YSettingKey.FLOAT_BORDER_VISIBLE, "false")
        assertEquals(19, store.appearance("yfloat").floatIconAlpha)
        assertEquals("#123456,#ABCDEF", store.appearance("yfloat").floatTrailColors)
        assertFalse(store.appearance("yfloat").floatBorderVisible)

        store.set(YSettingKey.FLOAT_ICON_ALPHA, "87", "yfloat")
        assertEquals(87, store.appearance("yfloat").floatIconAlpha)
        assertEquals(19, store.appearance("ynotify").floatIconAlpha)
        store.inherit(YSettingKey.FLOAT_ICON_ALPHA, "yfloat")
        assertEquals(19, store.appearance("yfloat").floatIconAlpha)
        val exported = store.exportJson()
        store.reset()
        store.importJson(exported)
        assertEquals(19, store.appearance("yfloat").floatIconAlpha)
        assertFalse(store.appearance("yfloat").floatBorderVisible)
        assertEquals("#123456,#ABCDEF", store.appearance("yfloat").floatTrailColors)
    }

    @Test
    fun appearanceInputRejectsInvalidColorsAndOutOfRangeValues() {
        assertTrue(runCatching {
            store.set(YSettingKey.FLOAT_ICON_ALPHA, "101")
        }.isFailure)
        assertTrue(runCatching {
            store.set(YSettingKey.FLOAT_BORDER_COLOR, "invalid")
        }.isFailure)
        assertTrue(runCatching {
            store.set(YSettingKey.FLOAT_TRAIL_COLORS, "a".repeat(257))
        }.isFailure)
        store.set(YSettingKey.FLOAT_TRAIL_GRADIENT, "true")
        store.set(YSettingKey.FLOAT_TRAIL_VISIBLE, "true")
        assertTrue(store.appearance("yfloat").floatTrailGradient)
        assertTrue(store.appearance("yfloat").floatTrailVisible)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidModuleIdentifier() {
        store.set(YSettingKey.THEME, "light", "../other")
    }
}
