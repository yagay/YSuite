package com.yagay.YNFC.xposed;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.os.Process;
import android.util.Log;

import com.yagay.YNFC.BuildConfig;
import com.yagay.YNFC.ConfigProvider;
import com.yagay.YNFC.xposed.discovery.HookDiscoveryEngine;
import com.yagay.YNFC.xposed.discovery.HookTarget;
import com.yagay.YNFC.xposed.payload.RewriteResult;
import com.yagay.YNFC.xposed.payload.RfPayloadEngine;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

public final class NfcInjectionModule extends XposedModule {
    private static final String TAG = "NfcUIDSim";
    private final HookDiscoveryEngine discovery = new HookDiscoveryEngine();
    private final RfPayloadEngine payloads = new RfPayloadEngine();
    private final ConcurrentHashMap<String, Boolean> installed = new ConcurrentHashMap<>();

    @Override
    public void onModuleLoaded(XposedModuleInterface.ModuleLoadedParam param) {
        super.onModuleLoaded(param);
        log(Log.INFO, TAG, "YSuite NFC Hook build=" + BuildConfig.HOOK_BUILD + " process=" + param.getProcessName());
    }

    @Override
    public void onPackageLoaded(XposedModuleInterface.PackageLoadedParam param) {
        super.onPackageLoaded(param);
        if (!"com.android.nfc".equals(param.getPackageName())) return;
        ClassLoader loader = param.getDefaultClassLoader();
        String key = Integer.toString(Process.myPid());
        if (installed.putIfAbsent(key, Boolean.TRUE) != null) return;

        List<HookTarget> targets = discovery.discoverRfCandidates(loader);
        int count = 0;
        for (HookTarget target : targets) {
            if (count >= 4) break;
            try {
                installTarget(target.resolve(loader));
                count++;
            } catch (Throwable error) {
                log(Log.WARN, TAG, "Skip target " + target + ": " + error.getMessage());
            }
        }
        writeStatus(false, "", "HOOK_READY", count > 0, count, null, false, null);
    }

    private void installTarget(Method method) {
        hook(method).intercept(chain -> {
            Map<String, String> config = readConfig();
            boolean active = Boolean.parseBoolean(config.get("simulation_enabled"));
            String uidText = NfcHookUtils.normalizeUid(config.get("uid"));
            long generation = parseLong(config.get("command_generation"), 0L);

            if (!active || !(uidText.length() == 8 || uidText.length() == 14 || uidText.length() == 20)) {
                return chain.proceed();
            }

            byte[] uid = NfcHookUtils.hexToBytes(uidText);
            Object[] args = new Object[method.getParameterCount()];
            int byteIndex = -1;
            for (int i = 0; i < args.length; i++) {
                args[i] = chain.getArg(i);
                if (args[i] instanceof byte[]) {
                    if (byteIndex >= 0) return chain.proceed();
                    byteIndex = i;
                }
            }
            if (byteIndex < 0) return chain.proceed();

            RewriteResult rewrite = payloads.rewrite((byte[]) args[byteIndex], uid);
            if (!rewrite.changed) {
                writeStatus(true, uidText, "REWRITE_SKIPPED", true, 1, rewrite.reason, false, generation);
                return chain.proceed();
            }

            args[byteIndex] = rewrite.data;
            Object result = chain.proceed(args);
            boolean accepted = nativeAccepted(method, result);
            writeStatus(
                    true,
                    uidText,
                    accepted ? "RF_APPLIED" : "RF_NATIVE_REJECTED",
                    true,
                    1,
                    rewrite.codecId + ":" + rewrite.reason,
                    accepted,
                    generation);
            return result;
        });
    }

    private boolean nativeAccepted(Method method, Object result) {
        Class<?> type = method.getReturnType();
        if (type == Void.TYPE) return true;
        if (type == Boolean.TYPE || type == Boolean.class) return Boolean.TRUE.equals(result);
        if (result instanceof Number) return ((Number) result).longValue() == 0L;
        return false;
    }

    private Map<String, String> readConfig() {
        Map<String, String> values = new HashMap<>();
        Context context = NfcHookUtils.currentContext();
        if (context == null) return values;
        try (Cursor cursor = context.getContentResolver().query(ConfigProvider.URI, null, null, null, null)) {
            if (cursor != null) {
                while (cursor.moveToNext()) values.put(cursor.getString(0), cursor.getString(1));
            }
        } catch (Throwable error) {
            log(Log.WARN, TAG, "Config read failed: " + error.getMessage());
        }
        return values;
    }

    private void writeStatus(
            boolean active,
            String uid,
            String rfStatus,
            boolean hookReady,
            int hookCount,
            String detail,
            boolean accepted,
            Long generation) {
        Context context = NfcHookUtils.currentContext();
        if (context == null) return;
        int pid = Process.myPid();
        long epoch = System.currentTimeMillis();
        try {
            ContentValues values = new ContentValues();
            values.put(ConfigProvider.KEY_HOOK_BUILD, BuildConfig.HOOK_BUILD);
            values.put(ConfigProvider.KEY_HOOK_INSTALLED, hookReady);
            values.put(ConfigProvider.KEY_HOOK_PID, pid);
            values.put(ConfigProvider.KEY_SCOPE_OK, true);
            values.put(ConfigProvider.KEY_SCOPE_PID, pid);
            values.put(ConfigProvider.KEY_RUNTIME_PID, pid);
            values.put(ConfigProvider.KEY_RF_STATUS, rfStatus);
            values.put(ConfigProvider.KEY_RF_UID, uid == null ? "" : uid);
            values.put(ConfigProvider.KEY_RF_SOURCE, "api102:" + hookCount);
            values.put(ConfigProvider.KEY_RF_ACCEPTED, accepted);
            values.put(ConfigProvider.KEY_RF_PID, pid);
            values.put(ConfigProvider.KEY_CONTROLLER_EPOCH, epoch);
            if (generation != null && generation > 0L) {
                values.put(ConfigProvider.KEY_RF_GENERATION, generation);
                values.put(ConfigProvider.KEY_COMMAND_HANDLED_GENERATION, generation);
                values.put(ConfigProvider.KEY_COMMAND_PID, pid);
                values.put(ConfigProvider.KEY_COMMAND_STATUS, accepted ? "SUCCESS" : "FAILED");
                values.put(ConfigProvider.KEY_COMMAND_DETAIL, detail == null ? "" : detail);
                values.put(ConfigProvider.KEY_OPERATION_STATE, accepted ? "IDLE" : "FAILED");
                values.put(ConfigProvider.KEY_EFFECTIVE_STATE, accepted ? "ACTIVE" : "UNKNOWN");
                values.put(ConfigProvider.KEY_VERIFICATION_CONFIDENCE, accepted ? "VERIFIED" : "FAILED");
                if (accepted) values.put(ConfigProvider.KEY_RF_CONTROLLER_EPOCH, epoch);
            }
            if (detail != null && !accepted) values.put(ConfigProvider.KEY_RF_ERROR, detail);
            context.getContentResolver().insert(ConfigProvider.URI, values);
        } catch (Throwable error) {
            log(Log.WARN, TAG, "State write failed: " + error.getMessage());
        }
    }

    private static long parseLong(String value, long fallback) {
        try {
            return Long.parseLong(value);
        } catch (Throwable ignored) {
            return fallback;
        }
    }
}
