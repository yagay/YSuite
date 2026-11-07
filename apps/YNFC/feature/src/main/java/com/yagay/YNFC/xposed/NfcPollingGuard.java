package com.yagay.YNFC.xposed;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Objects;

/**
 * Android NFC discovery policy adapter.
 *
 * Prefer constructing a new instance through the platform Builder. If an OEM
 * removes the Builder, copy the verified object field-by-field. In both cases
 * compare ALL fields other than the mask so HCE, P2P, reader and vendor extras
 * cannot silently change when polling is disabled.
 */
final class NfcPollingGuard {
    private NfcPollingGuard() { }

    static boolean shouldReplace(boolean disabled, int techMask) {
        return disabled && techMask != 0;
    }

    /**
     * The older NfcNci.apk exposes getTechMask()/shouldEnableHostRouting().
     * OxygenOS NFC Service 17 (APK-verified) replaced these accessors with
     * PUBLIC mTechMask and mEnableHostRouting fields. Both APKs retain the
     * class name and a zero-argument constructor. Only these two explicitly
     * verified contracts may be modified. Do not guess obfuscated fields.
     */
    static boolean supportsType(Class<?> type) {
        if (type == null || !type.getName().toLowerCase(java.util.Locale.ROOT).contains("nfc")) {
            return false;
        }
        try {
            Method mask = type.getDeclaredMethod("getTechMask");
            Method host = type.getDeclaredMethod("shouldEnableHostRouting");
            if (mask.getReturnType() == int.class &&
                    host.getReturnType() == boolean.class &&
                    !Modifier.isStatic(mask.getModifiers()) &&
                    !Modifier.isStatic(host.getModifiers())) return true;
        } catch (NoSuchMethodException ignored) {
            // NFC Service 17 has fields instead of the old accessors.
        } catch (Throwable incompatible) {
            return false;
        }
        try {
            Field mask = type.getDeclaredField("mTechMask");
            Field host = type.getDeclaredField("mEnableHostRouting");
            Field reader = type.getDeclaredField("mEnableReaderMode");
            Field lowPower = type.getDeclaredField("mEnableLowPowerDiscovery");
            return mask.getType() == int.class &&
                    host.getType() == boolean.class &&
                    reader.getType() == boolean.class &&
                    lowPower.getType() == boolean.class &&
                    !Modifier.isStatic(mask.getModifiers()) &&
                    !Modifier.isStatic(host.getModifiers()) &&
                    !Modifier.isFinal(mask.getModifiers()) &&
                    type.getDeclaredConstructor().getParameterCount() == 0;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    static int mask(Object params) throws ReflectiveOperationException {
        Class<?> type = params.getClass();
        try {
            Method getter = type.getDeclaredMethod("getTechMask");
            getter.setAccessible(true);
            return (Integer) getter.invoke(params);
        } catch (NoSuchMethodException noGetterOnNfc17) {
            Field field = type.getDeclaredField("mTechMask");
            field.setAccessible(true);
            return field.getInt(params);
        }
    }

    static boolean hostRouting(Object params) throws ReflectiveOperationException {
        Class<?> type = params.getClass();
        try {
            Method getter = type.getDeclaredMethod("shouldEnableHostRouting");
            getter.setAccessible(true);
            return (Boolean) getter.invoke(params);
        } catch (NoSuchMethodException noGetterOnNfc17) {
            Field field = type.getDeclaredField("mEnableHostRouting");
            field.setAccessible(true);
            return field.getBoolean(params);
        }
    }

    static Object withoutPolling(Object original) throws ReflectiveOperationException {
        if (original == null || !supportsType(original.getClass())) {
            throw new IllegalArgumentException("Unsupported NFC discovery parameters");
        }
        if (!shouldReplace(true, mask(original))) return original;
        Object builderCopy = tryBuilder(original);
        if (builderCopy != null && hasOnlyPollingChanged(original, builderCopy)) {
            return builderCopy;
        }
        Object fallback = copyFields(original);
        if (!hasOnlyPollingChanged(original, fallback)) {
            throw new IllegalStateException("NFC discovery copy changed non-polling state");
        }
        return fallback;
    }

    private static Object tryBuilder(Object original) {
        Class<?> type = original.getClass();
        try {
            Method factory = type.getDeclaredMethod("newBuilder");
            factory.setAccessible(true);
            Object builder = factory.invoke(null);
            if (builder == null) return null;
            // Setter names are part of AOSP's discovery configuration contract;
            // optional capabilities remain unchanged only if the entire copy
            // passes the strict field-level validation below.
            set(builder, "setTechMask", int.class, 0);
            copyBoolean(builder, original, "setEnableHostRouting", "shouldEnableHostRouting");
            copyBoolean(builder, original, "setEnableLowPowerDiscovery", "shouldEnableLowPowerDiscovery");
            copyBoolean(builder, original, "setEnableReaderMode", "shouldEnableReaderMode");
            Field p2p = field(type, "mEnableP2p");
            if (p2p != null && p2p.getType() == boolean.class) {
                p2p.setAccessible(true);
                set(builder, "setEnableP2p", boolean.class, p2p.getBoolean(original));
            }
            try {
                Method annotation = type.getDeclaredMethod("techAPollingLoopAnnotation");
                annotation.setAccessible(true);
                Object value = annotation.invoke(original);
                if (value != null) set(builder, "setTechAPollingLoopAnnotation",
                        byte[].class, value);
            } catch (NoSuchMethodException ignored) { }
            Method build = builder.getClass().getDeclaredMethod("build");
            build.setAccessible(true);
            Object result = build.invoke(builder);
            return type.isInstance(result) ? result : null;
        } catch (Throwable ignored) {
            // OEM can remove or change Builder. Only the verified field-copy
            // path may run next; no blind object rewrite.
            return null;
        }
    }

    private static void copyBoolean(Object builder, Object original, String setter,
                                    String getter) throws ReflectiveOperationException {
        Method method = original.getClass().getDeclaredMethod(getter);
        method.setAccessible(true);
        set(builder, setter, boolean.class, method.invoke(original));
    }

    private static void set(Object builder, String name, Class<?> param, Object value)
            throws ReflectiveOperationException {
        Method method = builder.getClass().getDeclaredMethod(name, param);
        method.setAccessible(true);
        method.invoke(builder, value);
    }

    private static Object copyFields(Object original) throws ReflectiveOperationException {
        Class<?> type = original.getClass();
        Constructor<?> constructor = type.getDeclaredConstructor();
        constructor.setAccessible(true);
        Object result = constructor.newInstance();
        Field maskField = null;
        for (Field f : type.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers())) continue;
            f.setAccessible(true);
            f.set(result, f.get(original));
            if ("mTechMask".equals(f.getName()) && f.getType() == int.class) maskField = f;
        }
        if (maskField == null) {
            // R8 may rename the backing field. Change a field only when exactly
            // one candidate makes getTechMask() return zero without any other
            // field changing. Never guess if several candidates match.
            int candidates = 0;
            Field unique = null;
            for (Field f : type.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) || f.getType() != int.class) continue;
                f.setAccessible(true);
                int before = f.getInt(result);
                f.setInt(result, 0);
                if (mask(result) == 0) { unique = f; candidates++; }
                f.setInt(result, before);
            }
            if (candidates != 1) throw new IllegalStateException("Ambiguous NFC mask field");
            maskField = unique;
        }
        maskField.setInt(result, 0);
        return result;
    }

    private static Field field(Class<?> type, String name) {
        try { return type.getDeclaredField(name); }
        catch (NoSuchFieldException ignored) { return null; }
    }

    private static boolean hasOnlyPollingChanged(Object original, Object candidate)
            throws ReflectiveOperationException {
        if (candidate == original || candidate == null
                || candidate.getClass() != original.getClass() || mask(candidate) != 0
                || mask(original) == 0 || hostRouting(original) != hostRouting(candidate)) {
            return false;
        }
        Class<?> type = original.getClass();
        for (Field field : type.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            field.setAccessible(true);
            Object before = field.get(original);
            Object after = field.get(candidate);
            if ("mTechMask".equals(field.getName()) && field.getType() == int.class) continue;
            if (field.getType() == int.class && !Objects.deepEquals(before, after)) {
                // This might be an obfuscated tech-mask backing field.
                // Accept only when it is the SINGLE changed field.
                if (countChangedFields(original, candidate) == 1) continue;
            }
            if (!Objects.deepEquals(before, after)) return false;
        }
        return countChangedFields(original, candidate) == 1;
    }

    private static int countChangedFields(Object original, Object candidate)
            throws IllegalAccessException {
        int count = 0;
        for (Field field : original.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            field.setAccessible(true);
            if (!Objects.deepEquals(field.get(original), field.get(candidate))) count++;
        }
        return count;
    }
}
