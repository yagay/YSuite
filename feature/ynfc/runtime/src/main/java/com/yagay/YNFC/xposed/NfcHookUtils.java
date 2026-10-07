package com.yagay.YNFC.xposed;

import android.app.Application;
import android.content.Context;

import java.lang.reflect.Method;
import java.util.Locale;

final class NfcHookUtils {
    private NfcHookUtils() {}

    static Context currentContext() {
        Application app = currentApplication();
        if (app != null) return app;
        try {
            Class<?> at = Class.forName("android.app.ActivityThread");
            Method current = at.getDeclaredMethod("currentActivityThread");
            current.setAccessible(true);
            Object thread = current.invoke(null);
            if (thread == null) return null;
            Method systemContext = at.getDeclaredMethod("getSystemContext");
            systemContext.setAccessible(true);
            Object value = systemContext.invoke(thread);
            return value instanceof Context ? (Context) value : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    static Application currentApplication() {
        try {
            Class<?> at = Class.forName("android.app.ActivityThread");
            Method method = at.getDeclaredMethod("currentApplication");
            method.setAccessible(true);
            return (Application) method.invoke(null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    static String normalizeUid(String uid) {
        return uid == null ? "" : uid.replaceAll("[^0-9A-Fa-f]", "").toUpperCase(Locale.ROOT);
    }

    static byte[] hexToBytes(String hex) {
        byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }
}
