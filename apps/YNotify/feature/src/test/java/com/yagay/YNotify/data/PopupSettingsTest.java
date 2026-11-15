package com.yagay.YNotify.data;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class PopupSettingsTest {
    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences(PopupSettings.PREFS, Context.MODE_PRIVATE)
                .edit().clear().commit();
    }

    @Test
    public void monitorDefaultsOnAndClipboardSuppressionDefaultsOff() {
        assertTrue(PopupSettings.captureEnabled(context));
        assertFalse(PopupSettings.hideClipboardEnabled(context));
    }

    @Test
    public void clipboardSuppressionIsIndependentOfMonitoring() {
        PopupSettings.setHideClipboardEnabled(context, true);
        assertTrue(PopupSettings.hideClipboardEnabled(context));
        PopupSettings.setCaptureEnabled(context, false);
        assertFalse(PopupSettings.captureEnabled(context));
        assertTrue(PopupSettings.hideClipboardEnabled(context));
        PopupSettings.setHideClipboardEnabled(context, false);
        assertFalse(PopupSettings.hideClipboardEnabled(context));
    }
}
