package com.yagay.ypower.xposed;

import android.content.SharedPreferences;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.List;

import io.github.libxposed.service.XposedService;

public final class XposedBridgeManager {
    private static final String TAG = "YPowerXposed";
    private static final String SUITE_BROKER = "com.yagay.suite.core.SuiteXposedServiceBroker";
    private static volatile XposedService service;
    private static volatile boolean sharedHost;

    private XposedBridgeManager() {}

    public static void setService(XposedService value) { service = value; }
    public static void setSharedHost(boolean value) { sharedHost = value; }
    public static boolean isReady() { return service != null; }

    public static SharedPreferences remotePreferences() {
        XposedService s = service;
        if (s == null) return null;
        try { return s.getRemotePreferences("ypower"); }
        catch (Throwable t) { Log.w(TAG, "Remote preferences unavailable", t); return null; }
    }

    public static void requestScope(String packageName) {
        if (packageName == null || packageName.isBlank()) return;
        if (sharedHost && invokeSuiteScope("requestScopeFromPlugin", packageName)) return;

        XposedService s = service;
        if (s == null || sharedHost) return;
        try {
            s.requestScope(List.of(packageName), new XposedService.OnScopeEventListener() {
                @Override public void onScopeRequestApproved(List<String> approved) {
                    Log.i(TAG, "Scope approved: " + approved);
                }
                @Override public void onScopeRequestFailed(String message) {
                    Log.w(TAG, "Scope request failed: " + message);
                }
            });
        } catch (Throwable t) {
            Log.w(TAG, "requestScope failed: " + packageName, t);
        }
    }

    public static void removeScope(String packageName) {
        if (packageName == null || packageName.isBlank()) return;
        if (sharedHost) {
            // YSuite is the only scope owner. This request is routed to the host instead of touching
            // the shared XposedService directly. The host can later add cross-plugin ownership
            // accounting without changing feature code.
            invokeSuiteScope("removeScopeFromPlugin", packageName);
            return;
        }

        XposedService s = service;
        if (s == null) return;
        try { s.removeScope(List.of(packageName)); }
        catch (Throwable t) { Log.w(TAG, "removeScope failed: " + packageName, t); }
    }

    private static boolean invokeSuiteScope(String methodName, String packageName) {
        try {
            Class<?> broker = Class.forName(SUITE_BROKER, false, XposedBridgeManager.class.getClassLoader());
            Method method = broker.getMethod(methodName, String.class, String[].class);
            Object result = method.invoke(null, "ypower", new String[]{packageName});
            if (!Boolean.TRUE.equals(result)) {
                Log.w(TAG, "YSuite scope operation not accepted: " + methodName + " " + packageName);
            }
            return true;
        } catch (ClassNotFoundException absent) {
            return false;
        } catch (Throwable error) {
            Log.w(TAG, "YSuite scope operation failed: " + methodName + " " + packageName, error);
            // Host is present/sharedHost; caller must not bypass it.
            return true;
        }
    }
}
