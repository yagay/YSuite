package com.yagay.suite.api;

import android.app.Application;
import android.content.Context;

import java.lang.reflect.Method;

/** One best-effort Android process Context resolver for hook/runtime infrastructure. */
public final class AndroidRuntimeContext {
    private AndroidRuntimeContext() {}

    public static Context current() {
        try {
            Class<?> activityThread = Class.forName("android.app.ActivityThread");
            Method currentApplication = activityThread.getDeclaredMethod("currentApplication");
            currentApplication.setAccessible(true);
            Object app = currentApplication.invoke(null);
            if (app instanceof Application) {
                return ((Application) app).getApplicationContext();
            }

            Method currentThread = activityThread.getDeclaredMethod("currentActivityThread");
            currentThread.setAccessible(true);
            Object thread = currentThread.invoke(null);
            if (thread != null) {
                Method getSystemContext = activityThread.getDeclaredMethod("getSystemContext");
                getSystemContext.setAccessible(true);
                Object system = getSystemContext.invoke(thread);
                if (system instanceof Context) return (Context) system;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }
}
