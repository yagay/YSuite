package com.yagay.YSuite.accessibility;

import android.content.Intent;
import android.view.accessibility.AccessibilityEvent;

import com.yagay.YFloat.LensAccessibilityService;
import com.yagay.suite.core.SuiteLog;

/**
 * Single accessibility host for the merged YSuite APK.
 *
 * YFloat remains the capability owner for gestures, screenshots, windows and accessibility
 * overlays. Additional features subscribe through SuiteAccessibilityBroker without registering
 * another Android AccessibilityService or asking the user for another grant.
 */
public final class SuiteAccessibilityService extends LensAccessibilityService {
    private static volatile boolean connected;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        connected = true;
        SuiteAccessibilityBroker.INSTANCE.onServiceConnected(this);
        SuiteLog.INSTANCE.i(this, "suite",
                "shared accessibility connected; consumers="
                        + SuiteAccessibilityBroker.INSTANCE.registeredFeatureIds());
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // YFloat first because it maintains the shared environment/window state used by overlays.
        super.onAccessibilityEvent(event);
        SuiteAccessibilityBroker.INSTANCE.onAccessibilityEvent(this, event);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        connected = false;
        SuiteAccessibilityBroker.INSTANCE.onServiceDisconnected(this);
        SuiteLog.INSTANCE.i(this, "suite", "shared accessibility unbound");
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        connected = false;
        SuiteAccessibilityBroker.INSTANCE.onServiceDisconnected(this);
        SuiteLog.INSTANCE.i(this, "suite", "shared accessibility destroyed");
        super.onDestroy();
    }

    public static boolean isConnected() {
        return connected;
    }
}
