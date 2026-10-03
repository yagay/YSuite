package com.yagay.yparam;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

import com.yagay.suite.api.FeatureServices;
import com.yagay.suite.api.XposedHostBridge;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;

public final class YParamApp extends Application implements XposedServiceHelper.OnServiceListener {
    private static final String FEATURE_ID = "yparam";
    private static final FeatureServices SERVICES = FeatureServices.of(FEATURE_ID, "YParam");

    public interface ServiceObserver { void onServiceChanged(); }
    private static volatile XposedService service;
    private static final List<ServiceObserver> observers = new CopyOnWriteArrayList<>();
    private static YParamApp listener;

    @Override public void onCreate() {
        super.onCreate();
        initialize(this);
    }

    public static synchronized Object initialize(Context context) {
        if (listener == null) {
            listener = context instanceof YParamApp ? (YParamApp) context : new YParamApp();
            XposedHostBridge.AttachResult result =
                    XposedHostBridge.attachListener(context, FEATURE_ID, listener);
            if (result == XposedHostBridge.AttachResult.NOT_SUITE_HOST) {
                XposedServiceHelper.registerListener(listener);
            } else if (result == XposedHostBridge.AttachResult.HOST_PRESENT_BUT_FAILED) {
                SERVICES.error("Managed host LSPosed broker attach failed");
            }
        }
        return listener;
    }

    @Override public void onServiceBind(XposedService s) {
        service = s;
        notifyObservers();
    }

    @Override public void onServiceDied(XposedService s) {
        if (service == s) service = null;
        notifyObservers();
    }

    static void onSuiteRuntimeDisabled() {
        service = null;
        notifyObservers();
    }

    public static XposedService getService() { return service; }
    public static SharedPreferences remotePrefs() {
        XposedService s = service;
        if (s == null) return null;
        try { return s.getRemotePreferences("yparam"); }
        catch (Throwable ignored) { return null; }
    }
    public static void addObserver(ServiceObserver o) { observers.add(o); }
    public static void removeObserver(ServiceObserver o) { observers.remove(o); }
    private static void notifyObservers() { for (ServiceObserver o : observers) o.onServiceChanged(); }
}
