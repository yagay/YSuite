package com.yagay.YNFC.xposed;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/** Copy-only NFC discovery policy validated against OxygenOS 16 NfcNci.apk.
 * Never mutate an OEM-owned cached NfcDiscoveryParameters or its HCE routing.
 */
final class NfcPollingGuard {
    private NfcPollingGuard() { }

    static boolean shouldReplace(boolean disabled, int techMask) {
        return disabled && techMask != 0;
    }

    static int mask(Object params) throws ReflectiveOperationException {
        Method getter = params.getClass().getDeclaredMethod("getTechMask");
        getter.setAccessible(true);
        return (Integer) getter.invoke(params);
    }

    static boolean hostRouting(Object params) throws ReflectiveOperationException {
        Method getter = params.getClass().getDeclaredMethod("shouldEnableHostRouting");
        getter.setAccessible(true);
        return (Boolean) getter.invoke(params);
    }

    static Object withoutPolling(Object params) throws ReflectiveOperationException {
        if (params == null) throw new IllegalArgumentException("NFC params null");
        Class<?> type = params.getClass();
        if (!"com.android.nfc.NfcDiscoveryParameters".equals(type.getName())) {
            throw new IllegalArgumentException("Unexpected discovery params " + type.getName());
        }
        if (!shouldReplace(true, mask(params))) return params;
        boolean host = hostRouting(params);
        Constructor<?> ctor = type.getDeclaredConstructor();
        ctor.setAccessible(true);
        Object copy = ctor.newInstance();
        boolean maskPresent = false;
        for (Field field : type.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            field.setAccessible(true);
            if ("mTechMask".equals(field.getName())) {
                if (field.getType() != int.class) {
                    throw new IllegalArgumentException("Unexpected tech mask type");
                }
                field.setInt(copy, 0);
                maskPresent = true;
            } else {
                field.set(copy, field.get(params));
            }
        }
        if (!maskPresent || mask(copy) != 0 || hostRouting(copy) != host) {
            throw new IllegalStateException("NFC discovery copy validation failed");
        }
        return copy;
    }
}
