package com.yagay.ypower.xposed;

import android.content.SharedPreferences;

import com.yagay.suite.api.FeatureServices;
import com.yagay.suite.api.XposedHostBridge;

import java.util.List;

import io.github.libxposed.service.XposedService;

public final class XposedBridgeManager {
    private static final String FEATURE_ID = "ypower";
    private static final FeatureServices SERVICES = FeatureServices.of(FEATURE_ID, "YPowerXposed");
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
        catch (Throwable t) { SERVICES.warn("Remote preferences unavailable", t); return null; }
    }

    public static void requestScope(String packageName) {
        if (packageName == null || packageName.isBlank()) return;
        if (sharedHost) {
            if (!XposedHostBridge.requestScope(FEATURE_ID, List.of(packageName))) {
                SERVICES.warn("Managed host scope request not accepted: " + packageName);
            }
            return;
        }

        XposedService s = service;
        if (s == null) return;
        try {
            s.requestScope(List.of(packageName), new XposedService.OnScopeEventListener() {
                @Override public void onScopeRequestApproved(List<String> approved) {
                    SERVICES.info("Scope approved: " + approved);
                }
                @Override public void onScopeRequestFailed(String message) {
                    SERVICES.warn("Scope request failed: " + message);
                }
            });
        } catch (Throwable t) {
            SERVICES.warn("requestScope failed: " + packageName, t);
        }
    }

    public static void removeScope(String packageName) {
        if (packageName == null || packageName.isBlank()) return;
        if (sharedHost) {
            if (!XposedHostBridge.removeScope(FEATURE_ID, List.of(packageName))) {
                SERVICES.warn("Managed host scope removal not accepted: " + packageName);
            }
            return;
        }

        XposedService s = service;
        if (s == null) return;
        try { s.removeScope(List.of(packageName)); }
        catch (Throwable t) { SERVICES.warn("removeScope failed: " + packageName, t); }
    }
}
