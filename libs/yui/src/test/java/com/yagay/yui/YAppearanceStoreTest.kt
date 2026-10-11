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
    fun buttonAppearanceSeparatesVisualSizeFromTouchSize() {
        val defaults = store.appearance().buttonGeometry()
        assertEquals(48, defaults.visualHeightDp)
        assertEquals(48, defaults.touchHeightDp)
        assertEquals(0f, defaults.backgroundInsetDp)
        assertEquals(24, defaults.iconVisualSizeDp)
        store.set(YSettingKey.BUTTON_HEIGHT, "24")
        store.set(YSettingKey.BUTTON_PADDING, "7")
        store.set(YSettingKey.BUTTON_RADIUS, "4")
        store.set(YSettingKey.ICON_VISUAL_SIZE, "17")
        val compact = store.appearance().buttonGeometry()
        assertEquals(24, compact.visualHeightDp)
        assertEquals(48, compact.touchHeightDp)
        assertEquals(12f, compact.backgroundInsetDp)
        assertEquals(7, compact.horizontalPaddingDp)
        assertEquals(4, compact.cornerRadiusDp)
        assertEquals(17, compact.iconVisualSizeDp)
        store.set(YSettingKey.BUTTON_HEIGHT, "68", "ypower")
        assertEquals(68, store.appearance("ypower").buttonGeometry().touchHeightDp)
        assertEquals(24, store.appearance("ynotify").buttonGeometry().visualHeightDp)
        val serialized = store.exportJson()
        store.reset()
        store.reset("ypower")
        store.importJson(serialized)
        assertEquals(68, store.appearance("ypower").buttonGeometry().visualHeightDp)
        assertEquals(24, store.appearance().buttonGeometry().visualHeightDp)
    }

    @Test
    fun switchAppearanceHasIndependentGeometryAndAccessibleTouchTarget() {
        val defaults = store.appearance()
        assertEquals(100, defaults.switchScalePercent)
        assertEquals(52, defaults.switchTrackWidthDp)
        assertEquals(32, defaults.switchTrackHeightDp)
        assertEquals(24, defaults.switchThumbSizeDp)
        assertEquals(0, defaults.switchLabelGapDp)
        store.set(YSettingKey.SWITCH_SCALE_PERCENT, "70")
        store.set(YSettingKey.SWITCH_TRACK_WIDTH, "40")
        store.set(YSettingKey.SWITCH_TRACK_HEIGHT, "25")
        store.set(YSettingKey.SWITCH_THUMB_SIZE, "18")
        store.set(YSettingKey.SWITCH_LABEL_GAP, "6")
        assertEquals(70, store.appearance("yfloat").switchScalePercent)
        assertEquals(40, store.appearance("ynotify").switchTrackWidthDp)
        assertEquals(25, store.appearance().switchTrackHeightDp)
        assertEquals(18, store.appearance().switchThumbSizeDp)
        assertEquals(6, store.appearance().switchLabelGapDp)
        val shape = store.appearance().switchGeometry()
        assertEquals(28f, shape.trackWidthDp)
        assertTrue(shape.touchHeightDp >= 48f)
        assertTrue(shape.touchWidthDp >= 48f)
        store.set(YSettingKey.SWITCH_THUMB_SIZE, "30", "ynotify")
        assertEquals(30, store.appearance("ynotify").switchThumbSizeDp)
        assertEquals(18, store.appearance("yfloat").switchThumbSizeDp)
        val exported = store.exportJson()
        store.reset()
        store.reset("ynotify")
        store.importJson(exported)
        assertEquals(30, store.appearance("ynotify").switchThumbSizeDp)
        assertEquals(70, store.appearance().switchScalePercent)
        assertTrue(runCatching { store.set(YSettingKey.SWITCH_SCALE_PERCENT, "151") }.isFailure)
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
    fun overlayNativeScopedContextTracksModuleOverrides() {
        val app = RuntimeEnvironment.getApplication() as Context
        val base = YAppearanceStore.moduleIdFor(app)
        val scoped = YOverlayUi.scopedContext(app, "yfloat")
        assertEquals("yfloat", YAppearanceStore.moduleIdFor(scoped))
        assertTrue(scoped === YOverlayUi.scopedContext(app, "yfloat"))
        store.set(YSettingKey.ACCENT, "purple", "yfloat")
        assertEquals("purple", store.appearance(YAppearanceStore.moduleIdFor(scoped)).accent)
        assertEquals(base, YAppearanceStore.moduleIdFor(app))
    }

    @Test
    fun overlayAppearanceFlowsFromGlobalIntoModuleAndExports() {
        assertEquals(22, store.appearance("yfloat").overlayPanelRadiusDp)
        assertEquals(100, store.appearance("yfloat").overlaySurfaceAlpha)
        assertEquals(10, store.appearance("yfloat").overlayShadowElevationDp)
        store.set(YSettingKey.OVERLAY_PANEL_RADIUS, "40")
        store.set(YSettingKey.OVERLAY_SURFACE_ALPHA, "75")
        store.set(YSettingKey.OVERLAY_CONTENT_PADDING, "18", "yfloat")
        store.set(YSettingKey.OVERLAY_OUTLINE_WIDTH, "2", "yfloat")
        store.set(YSettingKey.OVERLAY_SCRIM_SCALE, "55", "yfloat")
        store.set(YSettingKey.OVERLAY_SHADOW_ELEVATION, "18", "yfloat")
        assertEquals(40, store.appearance("yfloat").overlayPanelRadiusDp)
        assertEquals(75, store.appearance("yfloat").overlaySurfaceAlpha)
        assertEquals(18, store.appearance("yfloat").overlayContentPaddingDp)
        assertEquals(2, store.appearance("yfloat").overlayOutlineWidthDp)
        assertEquals(55, store.appearance("yfloat").overlayScrimScale)
        assertEquals(18, store.appearance("yfloat").overlayShadowElevationDp)
        store.set(YSettingKey.CARD_OUTLINE_WIDTH, "3", "ynotify")
        assertEquals(3, store.appearance("ynotify").cardOutlineWidthDp)
        assertEquals(12, store.appearance("ynotify").overlayContentPaddingDp)
        val json = store.exportJson()
        store.reset()
        store.reset("yfloat")
        store.importJson(json)
        assertEquals(18, store.appearance("yfloat").overlayContentPaddingDp)
        store.inherit(YSettingKey.OVERLAY_CONTENT_PADDING, "yfloat")
        assertEquals(12, store.appearance("yfloat").overlayContentPaddingDp)
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
    fun applyGlobalAppearanceRemovesEveryModuleOverrideWithoutChangingGlobal() {
        store.set(YSettingKey.BUTTON_RADIUS, "21")
        store.set(YSettingKey.BUTTON_RADIUS, "48", "yfloat")
        store.set(YSettingKey.BUTTON_RADIUS, "60", "yfiles")
        store.set(YSettingKey.PANE_WIDTH_SCALE, "180", "ydownload")
        assertEquals(48, store.appearance("yfloat").buttonRadiusDp)
        assertEquals(60, store.appearance("yfiles").buttonRadiusDp)
        store.inheritGlobalAppearanceForAllModules()
        assertEquals(21, store.appearance("yfloat").buttonRadiusDp)
        assertEquals(21, store.appearance("yfiles").buttonRadiusDp)
        assertEquals(100, store.appearance("ydownload").paneWidthPercent)
        assertFalse(store.isOverridden(YSettingKey.BUTTON_RADIUS, "yfloat"))
        assertFalse(store.isOverridden(YSettingKey.BUTTON_RADIUS, "yfiles"))
        assertFalse(store.isOverridden(YSettingKey.PANE_WIDTH_SCALE, "ydownload"))
        assertEquals(21, store.appearance().buttonRadiusDp)
    }

    @Test
    fun productLayoutGeometryReadsSingleStoreAndInheritsGlobalChanges() {
        assertEquals(100, store.appearance("yfiles").contentWidthPercent)
        assertEquals(100, store.appearance("ydownload").paneWidthPercent)
        store.set(YSettingKey.CONTENT_WIDTH_SCALE, "145")
        store.set(YSettingKey.PANE_WIDTH_SCALE, "65")
        assertEquals(145, store.appearance("ydownload").contentWidthPercent)
        assertEquals(145, store.appearance("yfiles").contentWidthPercent)
        assertEquals(65, store.appearance("yfiles").paneWidthPercent)
        store.set(YSettingKey.PANE_WIDTH_SCALE, "120", "yfiles")
        assertEquals(120, store.appearance("yfiles").paneWidthPercent)
        assertEquals(65, store.appearance("ydownload").paneWidthPercent)
        val json = store.exportJson()
        store.reset()
        store.reset("yfiles")
        store.importJson(json)
        assertEquals(145, store.appearance("yfiles").contentWidthPercent)
        assertEquals(120, store.appearance("yfiles").paneWidthPercent)
        store.inherit(YSettingKey.PANE_WIDTH_SCALE, "yfiles")
        assertEquals(65, store.appearance("yfiles").paneWidthPercent)
        assertTrue(runCatching {
            store.set(YSettingKey.PANE_WIDTH_SCALE, "251")
        }.isFailure)
    }

    @Test
    fun overlayAppearancesAreGlobalByDefaultAndOverridePerModule() {
        val keys = listOf(
            YSettingKey.OVERLAY_PANEL_RADIUS, YSettingKey.OVERLAY_SURFACE_ALPHA,
            YSettingKey.OVERLAY_CONTENT_PADDING, YSettingKey.OVERLAY_OUTLINE_WIDTH,
            YSettingKey.OVERLAY_SCRIM_SCALE, YSettingKey.OVERLAY_SHADOW_ELEVATION,
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
