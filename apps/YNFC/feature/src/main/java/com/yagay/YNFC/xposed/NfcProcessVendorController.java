package com.yagay.YNFC.xposed;

import android.os.Build;
import android.os.IBinder;
import java.util.Locale;
import android.os.Parcel;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Executes OEM RF triggers and NFC controller lifecycle operations from com.android.nfc. */
final class NfcProcessVendorController {
    private static final String NFC_SERVICE_NAME = "nfc";
    private static final String INFC_DESCRIPTOR = "android.nfc.INfcAdapter";
    private static final String INFC_STUB = "android.nfc.INfcAdapter$Stub";
    private static final String VENDOR_NAME = "vendor";
    private static final String VENDOR_DESCRIPTOR = "com.vendor.nfc.IVendorNfcAdapter";
    private static final String VENDOR_STUB = "com.vendor.nfc.IVendorNfcAdapter$Stub";
    private static final int STARTUP_RETRY_COUNT = 30;
    private static final long STARTUP_RETRY_DELAY_MS = 100L;

    static final class Result {
        final boolean success;
        final String stage;
        final String detail;
        final String vendorDescriptor;

        Result(boolean success, String stage, String detail, String vendorDescriptor) {
            this.success = success;
            this.stage = stage;
            this.detail = detail;
            this.vendorDescriptor = vendorDescriptor;
        }
    }

    /**
     * Read-only capability discovery. A vendor Binder descriptor or a known
     * NFCC string alone does NOT establish that arbitrary Type-A configuration
     * transactions are supported. Only a real callable method exposed by the
     * running OEM implementation qualifies for the direct backend.
     */
    Result probeTypeA() {
        try {
            IBinder main = serviceManagerBinder();
            if (!alive(main) || !INFC_DESCRIPTOR.equals(safeDescriptor(main))) {
                return new Result(false, "TYPE_A_NFC_UNAVAILABLE",
                        "NFC service absent or descriptor mismatch", null);
            }
            Object local = main.queryLocalInterface(INFC_DESCRIPTOR);
            IBinder vendor = getVendorBinderViaProxy(local);
            if (vendor == null) vendor = getVendorBinderViaProxy(asInterface(INFC_STUB, main));
            if (vendor == null) {
                // Avoid arbitrary Binder transactions during capability probes.
                return new Result(false, "TYPE_A_VENDOR_UNAVAILABLE",
                        "Vendor service not exposed through a callable interface", null);
            }
            String descriptor = safeDescriptor(vendor);
            if (!alive(vendor) || !VENDOR_DESCRIPTOR.equals(descriptor)) {
                return new Result(false, "TYPE_A_DESCRIPTOR_MISMATCH",
                        "Vendor Binder is not IVendorNfcAdapter", descriptor);
            }
            Object impl = vendor.queryLocalInterface(descriptor);
            if (hasTypeAApi(impl) || hasTypeAApi(asInterface(VENDOR_STUB, vendor))) {
                return new Result(true, "TYPE_A_API_PRESENT",
                        "Callable setHceTypeAConfig signature found; controller acceptance not verified",
                        descriptor);
            }
            return new Result(false, "TYPE_A_UNVERIFIED",
                    "No callable setHceTypeAConfig method; transaction number must not be guessed",
                    descriptor);
        } catch (Throwable error) {
            return new Result(false, "TYPE_A_PROBE_FAILED",
                    error.getClass().getSimpleName() + ": " + error.getMessage(), null);
        }
    }

    /**
     * Direct Vendor Type-A backend, separate from the RF_CONFIG_WRITE hook.
     * The caller must provide explicit card parameters; never invent ATQA/SAK
     * from a UID. A true result means OEM API accepted the request, NOT that an
     * external reader or the NFCC has verified the over-the-air identity.
     */
    Result setTypeAConfig(boolean enabled, byte[] atqa, byte[] sak, byte[] uid) {
        if (enabled && (atqa == null || atqa.length != 2
                || sak == null || sak.length != 1
                || uid == null || (uid.length != 4 && uid.length != 7 && uid.length != 10))) {
            return new Result(false, "TYPE_A_INVALID_PARAMETERS",
                    "Explicit ATQA(2), SAK(1), UID(4/7/10) required", null);
        }
        try {
            IBinder main = serviceManagerBinder();
            if (!alive(main) || !INFC_DESCRIPTOR.equals(safeDescriptor(main))) {
                return new Result(false, "TYPE_A_NO_NFC", "NFC service unavailable", null);
            }
            Object mainLocal = main.queryLocalInterface(INFC_DESCRIPTOR);
            IBinder vendor = getVendorBinderViaProxy(mainLocal);
            if (vendor == null) vendor = getVendorBinderViaProxy(asInterface(INFC_STUB, main));
            if (!alive(vendor) || !VENDOR_DESCRIPTOR.equals(safeDescriptor(vendor))) {
                return new Result(false, "TYPE_A_VENDOR_UNAVAILABLE",
                        "No verified vendor Binder through Java interface", null);
            }
            Object local = vendor.queryLocalInterface(VENDOR_DESCRIPTOR);
            Object proxy = asInterface(VENDOR_STUB, vendor);
            Object[] args = new Object[]{enabled, atqa == null ? null : atqa.clone(),
                    sak == null ? null : sak.clone(), uid == null ? null : uid.clone()};
            Class<?>[] types = new Class<?>[]{boolean.class, byte[].class, byte[].class, byte[].class};
            Boolean accepted = invokeBooleanMethod(local, "setHceTypeAConfig", types, args);
            if (accepted == null) accepted = invokeBooleanMethod(proxy, "setHceTypeAConfig", types, args);
            if (accepted == null) {
                return new Result(false, "TYPE_A_API_MISSING",
                        "No callable vendor Type-A method; raw transaction 16 is deliberately disabled",
                        VENDOR_DESCRIPTOR);
            }
            return new Result(accepted, accepted ? "TYPE_A_API_ACCEPTED" : "TYPE_A_REJECTED",
                    accepted ? "OEM accepted Type-A request; RF/controller verification still required"
                            : "OEM rejected Type-A request", VENDOR_DESCRIPTOR);
        } catch (Throwable error) {
            return new Result(false, "TYPE_A_EXCEPTION",
                    error.getClass().getName() + ": " + error.getMessage(), VENDOR_DESCRIPTOR);
        }
    }

    private static boolean hasTypeAApi(Object receiver) {
        if (receiver == null) return false;
        try {
            Method m = receiver.getClass().getMethod("setHceTypeAConfig",
                    boolean.class, byte[].class, byte[].class, byte[].class);
            return m.getReturnType() == boolean.class
                    || m.getReturnType() == Boolean.class;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    Result setShareMode(boolean enabled) {
        Result last = null;
        for (int attempt = 0; attempt < STARTUP_RETRY_COUNT; attempt++) {
            last = setShareModeOnce(enabled);
            if (last.success || !isTransient(last.stage)) return last;
            if (attempt + 1 < STARTUP_RETRY_COUNT) sleep(STARTUP_RETRY_DELAY_MS);
        }
        if (last == null) return new Result(false, "UNKNOWN", "No vendor result", null);
        return new Result(false, last.stage,
                last.detail + " after " + STARTUP_RETRY_COUNT + " attempts/~" +
                        ((STARTUP_RETRY_COUNT - 1) * STARTUP_RETRY_DELAY_MS) + "ms",
                last.vendorDescriptor);
    }

    private Result setShareModeOnce(boolean enabled) {
        try {
            IBinder main = serviceManagerBinder();
            if (!alive(main)) return new Result(false, "MAIN_BINDER", "NFC binder unavailable", null);
            String mainDescriptor = safeDescriptor(main);
            if (!INFC_DESCRIPTOR.equals(mainDescriptor)) return new Result(false, "MAIN_DESCRIPTOR", "Unexpected descriptor=" + mainDescriptor, null);

            // Preferred path: generated AIDL proxies. This follows the runtime method ABI and does
            // not depend on transaction numbering. If the OEM interface cannot be reflected, use
            // only transaction IDs reflected from the current Stub; never guess a numeric ID.
            Object mainProxy = asInterface(INFC_STUB, main);
            // ColorOS may expose the NFC service as a local Binder implementation
            // while its framework AIDL Stub differs from the SDK-facing class.
            // Probe the in-process interface first, without hardcoding an OEM tx ID.
            Object localMain = null;
            try { localMain = main.queryLocalInterface(mainDescriptor); }
            catch (Throwable ignored) { }
            IBinder vendor = getVendorBinderViaProxy(localMain);
            if (vendor == null) vendor = getVendorBinderViaProxy(mainProxy);
            String path = "aidl-proxy";
            if (vendor == null) {
                int txGetVendor = resolveRequiredTransaction(INFC_STUB, "TRANSACTION_getNfcAdapterVendorInterface");
                if (txGetVendor > 0) {
                    vendor = getVendorBinderTransact(main, txGetVendor);
                    path = "reflected-transaction";
                } else if (isOplusHardware()) {
                    // OplusNfcTool documents transaction 6 on its verified NXP SN220T
                    // stack. The platform SDK Stub may not expose the OEM method.
                    // Restrict this fallback to OPlus devices and VERIFY the returned
                    // binder's descriptor before attempting any vendor operation.
                    vendor = getVendorBinderTransact(main, 6);
                    path = "oplus-nxp-vendor-tx6";
                    if (vendor != null && !VENDOR_DESCRIPTOR.equals(safeDescriptor(vendor))) {
                        return new Result(false, "GET_VENDOR_DESCRIPTOR",
                                "OPlus transaction 6 did not return IVendorNfcAdapter; refusing vendor calls",
                                safeDescriptor(vendor));
                    }
                } else {
                    return new Result(false, "GET_VENDOR_CAPABILITY",
                            "No reflected vendor transaction and device is not OPlus", null);
                }
            }
            if (!alive(vendor)) return new Result(false, "GET_VENDOR_BINDER",
                    "No vendor binder available via " + path, null);

            String vendorDescriptor = safeDescriptor(vendor);
            if (!VENDOR_DESCRIPTOR.equals(vendorDescriptor)) {
                return new Result(false, "VENDOR_DESCRIPTOR", "Unexpected descriptor=" + vendorDescriptor, vendorDescriptor);
            }

            // In com.android.nfc the returned vendor binder can be the actual local Stub/service.
            // Prefer that real Java object first: it follows the OEM implementation directly and
            // avoids both transaction-number coupling and generated Stub class visibility issues.
            Object localInterface = null;
            try { localInterface = vendor.queryLocalInterface(vendorDescriptor); } catch (Throwable ignored) { }
            Boolean directAccepted = invokeBooleanMethod(localInterface, "enableNfcShareMode",
                    new Class<?>[]{boolean.class}, new Object[]{enabled});
            if (directAccepted == null) {
                directAccepted = invokeBooleanMethod(vendor, "enableNfcShareMode",
                        new Class<?>[]{boolean.class}, new Object[]{enabled});
            }
            if (directAccepted != null) {
                return new Result(directAccepted, directAccepted ? "TRIGGERED" : "SHARE_MODE",
                        "enableNfcShareMode(" + enabled + ") via in-process vendor service", vendorDescriptor);
            }

            Object vendorProxy = asInterface(VENDOR_STUB, vendor);
            Boolean proxyAccepted = invokeBooleanMethod(vendorProxy, "enableNfcShareMode",
                    new Class<?>[]{boolean.class}, new Object[]{enabled});
            if (proxyAccepted != null) {
                return new Result(proxyAccepted, proxyAccepted ? "TRIGGERED" : "SHARE_MODE",
                        "enableNfcShareMode(" + enabled + ") via AIDL proxy", vendorDescriptor);
            }

            // A controller name and Binder descriptor do not prove the entire
            // AIDL transaction table. Do not assume txn 15 is share mode on
            // every ColorOS revision; accept only the current Stub's value.
            int txShare = resolveRequiredTransaction(VENDOR_STUB, "TRANSACTION_enableNfcShareMode");
            if (txShare <= 0) {
                return new Result(false, "SHARE_MODE_CAPABILITY",
                        "No callable or reflected share-mode method; unsafe raw transaction disabled",
                        vendorDescriptor);
            }
            boolean accepted = transactShareMode(vendor, enabled, txShare);
            return new Result(accepted, accepted ? "TRIGGERED" : "SHARE_MODE",
                    "enableNfcShareMode(" + enabled + ") via " + path + "/reflected-tx=" + txShare,
                    vendorDescriptor);
        } catch (Throwable t) {
            return new Result(false, "EXCEPTION", t.getClass().getName() + ": " + String.valueOf(t.getMessage()), null);
        }
    }

    private static boolean isOplusHardware() {
        String brand = Build.BRAND == null ? "" : Build.BRAND.toLowerCase(Locale.ROOT);
        String manufacturer = Build.MANUFACTURER == null
                ? "" : Build.MANUFACTURER.toLowerCase(Locale.ROOT);
        return brand.contains("oppo") || brand.contains("oneplus") || brand.contains("realme")
                || manufacturer.contains("oppo") || manufacturer.contains("oneplus")
                || manufacturer.contains("realme");
    }

    private static boolean isTransient(String stage) {
        return "MAIN_BINDER".equals(stage) || "GET_VENDOR_BINDER".equals(stage) || "EXCEPTION".equals(stage);
    }

    private static IBinder serviceManagerBinder() {
        try {
            Class<?> sm = Class.forName("android.os.ServiceManager");
            Method method = sm.getDeclaredMethod("getService", String.class);
            method.setAccessible(true);
            return (IBinder) method.invoke(null, NFC_SERVICE_NAME);
        } catch (Throwable ignored) { return null; }
    }

    private static Object asInterface(String stubClass, IBinder binder) {
        if (binder == null) return null;
        try {
            Class<?> stub = Class.forName(stubClass);
            Method m = stub.getDeclaredMethod("asInterface", IBinder.class);
            m.setAccessible(true);
            return m.invoke(null, binder);
        } catch (Throwable ignored) { return null; }
    }

    private static IBinder getVendorBinderViaProxy(Object proxy) {
        if (proxy == null) return null;
        try {
            for (Method m : proxy.getClass().getMethods()) {
                if (!"getNfcAdapterVendorInterface".equals(m.getName())) continue;
                Class<?>[] p = m.getParameterTypes();
                // OEM/AIDL revisions expose either a named vendor or a no-argument
                // accessor. Never guess Binder transaction codes when neither exists.
                if (p.length == 0 || (p.length == 1 && p[0] == String.class)) {
                    m.setAccessible(true);
                    Object result = p.length == 0 ? m.invoke(proxy) : m.invoke(proxy, VENDOR_NAME);
                    if (result instanceof IBinder) return (IBinder) result;
                    if (result != null) {
                        try {
                            Method asBinder = result.getClass().getMethod("asBinder");
                            Object b = asBinder.invoke(result);
                            if (b instanceof IBinder) return (IBinder) b;
                        } catch (Throwable ignored) { }
                    }
                }
            }
        } catch (Throwable ignored) { }
        return null;
    }

    /** Returns null when the capability/signature is absent, otherwise the boolean result. */
    private static Boolean invokeBooleanMethod(Object proxy, String name, Class<?>[] params, Object[] args) {
        if (proxy == null) return null;
        try {
            Method m = proxy.getClass().getMethod(name, params);
            m.setAccessible(true);
            Object result = m.invoke(proxy, args);
            if (m.getReturnType() == Void.TYPE) return Boolean.TRUE;
            return result instanceof Boolean ? (Boolean) result : null;
        } catch (NoSuchMethodException ignored) {
            return null;
        } catch (Throwable t) {
            Throwable cause = t.getCause();
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            throw new RuntimeException(cause == null ? t : cause);
        }
    }

    private static int resolveRequiredTransaction(String stubClass, String fieldName) {
        try {
            Class<?> c = Class.forName(stubClass);
            Field f = c.getDeclaredField(fieldName);
            f.setAccessible(true);
            return f.getInt(null);
        } catch (Throwable ignored) { return -1; }
    }

    private static IBinder getVendorBinderTransact(IBinder mainBinder, int transaction) throws Exception {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(INFC_DESCRIPTOR);
            data.writeString(VENDOR_NAME);
            if (!mainBinder.transact(transaction, data, reply, 0)) return null;
            reply.setDataPosition(0);
            reply.readException();
            return reply.readStrongBinder();
        } finally {
            reply.recycle();
            data.recycle();
        }
    }

    private static String queryNfccIdentity(IBinder vendor) {
        if (vendor == null || !VENDOR_DESCRIPTOR.equals(safeDescriptor(vendor))) return null;
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(VENDOR_DESCRIPTOR);
            // Read-only IVendorNfcAdapter.getNfcc transaction from OplusNfcTool.
            if (!vendor.transact(2, data, reply, 0)) return null;
            reply.setDataPosition(0);
            reply.readException();
            return reply.readString();
        } catch (Throwable ignored) {
            return null;
        } finally {
            reply.recycle();
            data.recycle();
        }
    }

    private static boolean isRecognizedOplusNfcc(String identity) {
        if (identity == null || identity.length() < 3 || identity.length() > 100) return false;
        String name = identity.toUpperCase(Locale.ROOT);
        return name.contains("SN100") || name.contains("SN110")
                || name.contains("SN220") || name.contains("NQ330")
                || name.contains("NXP") || name.contains("ST54");
    }

    private static boolean transactShareMode(IBinder vendor, boolean enabled, int transaction) throws Exception {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(VENDOR_DESCRIPTOR);
            data.writeBoolean(enabled);
            if (!vendor.transact(transaction, data, reply, 0)) return false;
            reply.setDataPosition(0);
            reply.readException();
            return reply.dataAvail() >= 4 && reply.readBoolean();
        } finally {
            reply.recycle();
            data.recycle();
        }
    }

    private static boolean alive(IBinder binder) {
        return binder != null && binder.isBinderAlive() && binder.pingBinder();
    }

    private static String safeDescriptor(IBinder binder) {
        try {
            String value = binder.getInterfaceDescriptor();
            return value == null ? "" : value;
        } catch (Throwable ignored) { return ""; }
    }

    private static void sleep(long millis) {
        try { Thread.sleep(millis); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
