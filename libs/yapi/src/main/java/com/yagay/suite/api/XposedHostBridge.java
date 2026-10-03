package com.yagay.suite.api;

import android.content.Context;

import java.lang.reflect.Method;
import java.util.Collection;

/**
 * Reflection boundary between reusable Feature code and YSuite Core's process-wide LSPosed broker.
 *
 * <p>Feature modules must not know Core class names or reflection signatures. Standalone APKs use
 * their normal XposedServiceHelper listener; only the combined YSuite package routes through this
 * bridge.</p>
 */
public final class XposedHostBridge {
    private static final String BROKER_CLASS = "com.yagay.suite.core.SuiteXposedServiceBroker";

    public enum AttachResult {
        NOT_SUITE_HOST,
        ATTACHED,
        HOST_PRESENT_BUT_FAILED
    }

    private XposedHostBridge() {}

    public static boolean isSuiteHost(Context context) {
        return context != null
                && RuntimeOwnerGate.SUITE_PACKAGE.equals(context.getApplicationContext().getPackageName());
    }

    /**
     * Attach an app-side LSPosed listener to YSuite's single broker.
     * Standalone hosts deliberately return NOT_SUITE_HOST even though ycore classes are packaged.
     */
    public static AttachResult attachListener(Context context, String featureId, Object listener) {
        if (!isSuiteHost(context)) return AttachResult.NOT_SUITE_HOST;
        try {
            Class<?> broker = Class.forName(BROKER_CLASS, false, listener.getClass().getClassLoader());
            Method attach = broker.getMethod("attachFromPlugin", String.class, Object.class);
            Object result = attach.invoke(null, FeatureIds.normalize(featureId), listener);
            return Boolean.TRUE.equals(result)
                    ? AttachResult.ATTACHED
                    : AttachResult.HOST_PRESENT_BUT_FAILED;
        } catch (Throwable ignored) {
            return AttachResult.HOST_PRESENT_BUT_FAILED;
        }
    }

    public static boolean requestScope(String featureId, Collection<String> packages) {
        return invokeScope("requestScopeFromPlugin", featureId, packages);
    }

    public static boolean removeScope(String featureId, Collection<String> packages) {
        return invokeScope("removeScopeFromPlugin", featureId, packages);
    }

    private static boolean invokeScope(String methodName, String featureId, Collection<String> packages) {
        Context context = AndroidRuntimeContext.current();
        if (!isSuiteHost(context)) return false;
        try {
            ClassLoader loader = context != null ? context.getClassLoader() : XposedHostBridge.class.getClassLoader();
            Class<?> broker = Class.forName(BROKER_CLASS, false, loader);
            Method method = broker.getMethod(methodName, String.class, String[].class);
            String[] values = packages == null
                    ? new String[0]
                    : packages.stream()
                        .filter(value -> value != null && !value.trim().isEmpty())
                        .map(String::trim)
                        .distinct()
                        .toArray(String[]::new);
            Object result = method.invoke(null, FeatureIds.normalize(featureId), values);
            return Boolean.TRUE.equals(result);
        } catch (Throwable ignored) {
            return false;
        }
    }
}
