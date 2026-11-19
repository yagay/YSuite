package com.yagay.YNFC.xposed;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Runtime NFC discovery capability detection. Vendor class names are hints, not
 * mandatory implementation choices. Only exact, validated discovery method
 * shapes are eligible for Hook installation.
 */
final class NfcDiscoveryCapabilityRegistry {
    private static final String SERVICE = "com.android.nfc.NfcService";
    private static final String PARAMS = "com.android.nfc.NfcDiscoveryParameters";

    private NfcDiscoveryCapabilityRegistry() { }

    static final class Candidate {
        final Method method;
        final String source;
        Candidate(Method method, String source) {
            this.method = method;
            this.source = source;
        }
        String fingerprint() {
            return method.getDeclaringClass().getName() + "#" + method.getName()
                    + java.util.Arrays.toString(method.getParameterTypes());
        }
    }

    static Method findCompute(Class<?> service, Class<?> params) {
        if (service == null || params == null || !NfcPollingGuard.supportsType(params)) {
            return null;
        }
        Method strong = null;
        Method structural = null;
        int matches = 0;
        for (Method method : service.getDeclaredMethods()) {
            if (Modifier.isStatic(method.getModifiers())
                    || method.getParameterCount() != 1
                    || method.getParameterTypes()[0] != int.class
                    || method.getReturnType() != params) continue;
            matches++;
            structural = method;
            if ("computeDiscoveryParameters".equals(method.getName())) strong = method;
        }
        // A renamed, uniquely shaped equivalent is safe; ambiguous shapes are not.
        return strong != null ? strong : matches == 1 ? structural : null;
    }

    static List<Candidate> findDrivers(ClassLoader loader, List<String> candidateClassNames,
                                       Class<?> params) {
        if (loader == null || params == null || !NfcPollingGuard.supportsType(params)) {
            return Collections.emptyList();
        }
        Map<String, Candidate> result = new LinkedHashMap<>();
        if (candidateClassNames == null) return Collections.emptyList();
        int classes = 0;
        for (String name : candidateClassNames) {
            if (name == null || !name.startsWith("com.android.nfc.")) continue;
            if (++classes > 32) break;
            try {
                Class<?> type = Class.forName(name, false, loader);
                for (Method method : type.getDeclaredMethods()) {
                    if (!isDiscoveryDriver(method, params)) continue;
                    method.setAccessible(true);
                    Candidate candidate = new Candidate(method,
                            "com.android.nfc.dhimpl".equals(type.getPackage() == null ? ""
                                    : type.getPackage().getName())
                                    ? "aosp-oem-discovery" : "vendor-discovery");
                    result.put(candidate.fingerprint(), candidate);
                }
            } catch (Throwable ignored) {
                // An optional vendor class must never fail NFC startup.
            }
        }
        return new ArrayList<>(result.values());
    }

    static boolean isDiscoveryDriver(Method method, Class<?> params) {
        if (method == null || params == null
                || Modifier.isStatic(method.getModifiers())
                || method.getReturnType() != void.class) return false;
        Class<?>[] args = method.getParameterTypes();
        if (args.length < 1 || args.length > 3 || args[0] != params) return false;
        for (int i = 1; i < args.length; i++) if (args[i] != boolean.class) return false;
        String name = method.getName();
        return "enableDiscovery".equals(name) || "setDiscovery".equals(name)
                || "configureDiscovery".equals(name);
    }

    static final class ServiceAdapter {
        final Method getInstance;
        final Method isEnabled;
        final Method applyRouting;
        final String detail;

        ServiceAdapter(Method getInstance, Method isEnabled, Method applyRouting,
                       String detail) {
            this.getInstance = getInstance;
            this.isEnabled = isEnabled;
            this.applyRouting = applyRouting;
            this.detail = detail;
        }

        boolean available() {
            return getInstance != null && isEnabled != null && applyRouting != null;
        }
    }

    static ServiceAdapter resolveRouting(Class<?> service) {
        if (service == null) return new ServiceAdapter(null, null, null, "service missing");
        Method instance = null, enabled = null, routing = null;
        for (Method method : service.getDeclaredMethods()) {
            if ("getInstance".equals(method.getName())
                    && Modifier.isStatic(method.getModifiers())
                    && method.getParameterCount() == 0
                    && service.isAssignableFrom(method.getReturnType())) instance = method;
            if ("isNfcEnabled".equals(method.getName())
                    && !Modifier.isStatic(method.getModifiers())
                    && method.getParameterCount() == 0
                    && method.getReturnType() == boolean.class) enabled = method;
            if ("applyRouting".equals(method.getName())
                    && !Modifier.isStatic(method.getModifiers())
                    && method.getReturnType() == void.class
                    && method.getParameterCount() == 1
                    && method.getParameterTypes()[0] == boolean.class) routing = method;
        }
        try {
            if (instance != null) instance.setAccessible(true);
            if (enabled != null) enabled.setAccessible(true);
            if (routing != null) routing.setAccessible(true);
        } catch (Throwable ignored) {
            return new ServiceAdapter(null, null, null, "reflection rejected");
        }
        return new ServiceAdapter(instance, enabled, routing,
                "instance=" + (instance != null) + " enabled=" + (enabled != null)
                        + " applyRouting=" + (routing != null));
    }
}
