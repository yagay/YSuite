package com.yagay.suite.core

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SuiteCommonSettingsTest {
    private lateinit var store: SuiteCommonSettings

    @Before
    fun setup() {
        val context: Context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("ysuite_common_settings_v1", Context.MODE_PRIVATE)
            .edit().clear().commit()
        store = SuiteCommonSettings(context)
    }

    @Test
    fun safeDefaultsMatchExistingLogger() {
        assertEquals("debug", store.logging().level)
        assertEquals(1024L * 1024L, store.logging().maxFileBytes)
        assertTrue(store.logging().keepPrevious)
        assertTrue(store.logging().accepts("D"))
        assertTrue(store.logging().accepts("I"))
        assertTrue(store.logging().accepts("W"))
        assertTrue(store.logging().accepts("E"))
    }

    @Test
    fun logLevelAlwaysPreservesErrors() {
        store.set(SuiteCommonSetting.LOG_LEVEL, "error")
        val options = store.logging("ynotify")
        assertFalse(options.accepts("D"))
        assertFalse(options.accepts("I"))
        assertFalse(options.accepts("W"))
        assertTrue(options.accepts("E"))
        assertTrue(options.accepts("ERROR"))
    }

    @Test
    fun moduleOverrideFallsBackAndResets() {
        store.set(SuiteCommonSetting.LOG_LEVEL, "warning")
        assertEquals("warning", store.logging("yfloat").level)
        store.set(SuiteCommonSetting.LOG_LEVEL, "debug", "yfloat")
        store.set(SuiteCommonSetting.LOG_MAX_FILE_MB, "6", "yfloat")
        assertEquals("debug", store.logging("yfloat").level)
        assertEquals(6L * 1024L * 1024L, store.logging("yfloat").maxFileBytes)
        assertEquals("warning", store.logging("ynotify").level)
        store.inherit(SuiteCommonSetting.LOG_LEVEL, "yfloat")
        assertFalse(store.isOverridden(SuiteCommonSetting.LOG_LEVEL, "yfloat"))
        assertEquals("warning", store.logging("yfloat").level)
        store.reset("yfloat")
        assertEquals(1024L * 1024L, store.logging("yfloat").maxFileBytes)
        assertEquals("warning", store.logging().level)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnsafeSize() {
        store.set(SuiteCommonSetting.LOG_MAX_FILE_MB, "999")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidModuleId() {
        store.set(SuiteCommonSetting.LOG_LEVEL, "info", "../other")
    }
}
