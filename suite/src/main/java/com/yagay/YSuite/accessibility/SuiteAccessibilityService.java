package com.yagay.YSuite.accessibility;

import android.content.Intent;
import android.view.accessibility.AccessibilityEvent;

import com.yagay.YFloat.LensAccessibilityService;
import com.yagay.YNotify.collector.UiAccessibilityBridge;
import com.yagay.suite.core.SuiteLog;

/**
 * Single accessibility host for the merged YSuite APK.
 *
 * YFloat remains the capability owner for gestures, screenshots, windows and accessibility
 * overlays. YNotify consumes the same event stream through UiAccessibilityBridge. Standalone
 * feature APKs keep their own AccessibilityService declarations.
 */
public final class SuiteAccessibilityService extends LensAccessibilityService {
    private static volatile boolean connected;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        connected = true;
        UiAccessibilityBridge.onServiceConnected(this);
        SuiteLog.INSTANCE.i(this, "suite", "shared accessibility connected; consumers=YFloat,YNotify");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // YFloat first because it maintains the shared environment/window state used by overlays.
        super.onAccessibilityEvent(event);
        UiAccessibilityBridge.onAccessibilityEvent(this, event);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        connected = false;
        UiAccessibilityBridge.onServiceDisconnected(this);
        SuiteLog.INSTANCE.i(this, "suite", "shared accessibility unbound");
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        connected = false;
        UiAccessibilityBridge.onServiceDisconnected(this);
        SuiteLog.INSTANCE.i(this, "suite", "shared accessibility destroyed");
        super.onDestroy();
    }

    public static boolean isConnected() {
        return connected;
    }
}
