package com.yagay.YNotify.collector;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.view.accessibility.AccessibilityEvent;

/** Standalone accessibility host. YSuite uses UiAccessibilityBridge from its shared host service. */
public class UiAccessibilityService extends AccessibilityService {
    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        UiAccessibilityBridge.onServiceConnected(this);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        UiAccessibilityBridge.onServiceDisconnected(this);
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        UiAccessibilityBridge.onServiceDisconnected(this);
        super.onDestroy();
    }

    public static boolean isConnected() {
        return UiAccessibilityBridge.isConnected();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        UiAccessibilityBridge.onAccessibilityEvent(this, event);
    }

    @Override public void onInterrupt() {}
}
