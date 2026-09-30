package com.yagay.ypower.hook;

import android.util.Log;

import com.bytedance.android.bytehook.ByteHook;

public final class NativeSocketTraceBridge {
    private static final String TAG = "YPowerSocketTrace";
    private static boolean loaded;

    private NativeSocketTraceBridge() {}

    public static synchronized boolean enable(String packageName, String sessionId) {
        try {
            if (!loaded) {
                ByteHook.init();
                System.loadLibrary("ypower_socket_trace");
                loaded = true;
            }
            nativeEnable(
                    packageName == null ? "" : packageName,
                    sessionId == null ? "" : sessionId
            );
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "Native socket tracer unavailable", t);
            return false;
        }
    }

    public static synchronized void disable() {
        if (!loaded) return;
        try {
            nativeDisable();
        } catch (Throwable t) {
            Log.w(TAG, "Native socket tracer disable failed", t);
        }
    }

    private static native void nativeEnable(String packageName, String sessionId);
    private static native void nativeDisable();
}
