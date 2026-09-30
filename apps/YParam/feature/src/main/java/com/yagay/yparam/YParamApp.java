package com.yagay.yparam;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;

public final class YParamApp extends Application implements XposedServiceHelper.OnServiceListener {
    private static final String SUITE_BROKER = "com.yagay.suite.core.SuiteXposedServiceBroker";

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
            if (!attachToSuiteBroker(listener)) XposedServiceHelper.registerListener(listener);
        }
        return listener;
    }

    private static boolean attachToSuiteBroker(YParamApp target) {
        final Class<?> broker;
        try {
            broker = Class.forName(SUITE_BROKER, false, YParamApp.class.getClassLoader());
        } catch (ClassNotFoundException absent) {
            return false;
        } catch (Throwable ignored) {
            return true;
        }
        try {
            Method attach = broker.getMethod("attachFromPlugin", String.class, Object.class);
            attach.invoke(null, "yparam", target);
        } catch (Throwable ignored) {
            // Host exists: never replace YSuite's process-global listener.
        }
        return true;
    }

    @Override public void onServiceBind(XposedService s) {
        service = s;
        notifyObservers();
    }

    @Override public void onServiceDied(XposedService s) {
        if (service == s) service = null;
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