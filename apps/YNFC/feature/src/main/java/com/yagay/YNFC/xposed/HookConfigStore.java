package com.yagay.YNFC.xposed;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

import com.yagay.suite.api.RuntimeOwnerGate;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** Reads and decodes the durable command snapshot without owning command execution. */
final class HookConfigStore {
    SimConfig read() {
        if (!RuntimeOwnerGate.shouldRun("ynfc", hostPackage())) {
            // Keep the already-loaded standalone hook in a deterministic initialized-but-inactive
            // state. This avoids retry loops while YSuite owns NFC behaviour.
            return decode(Collections.emptyMap());
        }
        Context context = NfcHookUtils.currentContext();
        if (context == null) return SimConfig.uninitialized();
        Map<String, String> values = new HashMap<>();
        try (Cursor cursor = context.getContentResolver().query(configUri(), null, null, null, null)) {
            if (cursor == null) return SimConfig.uninitialized();
            while (cursor.moveToNext()) values.put(cursor.getString(0), cursor.getString(1));
            return decode(values);
        } catch (Throwable ignored) {
            return SimConfig.uninitialized();
        }
    }

    static SimConfig decode(Map<String, String> values) {
        if (values == null) return SimConfig.uninitialized();
        boolean active = Boolean.parseBoolean(values.get("simulation_enabled"));
        boolean diagnostics = Boolean.parseBoolean(values.get("diagnostic_logging_enabled"));
        String uid = values.get("uid");
        long generation = NfcHookUtils.parseLong(values.get("command_generation"), 0L);
        long consumed = NfcHookUtils.parseLong(values.get("command_consumed_generation"), Long.MIN_VALUE);
        long handled = NfcHookUtils.parseLong(values.get("command_handled_generation"), Long.MIN_VALUE);
        String action = valueOrEmpty(values.get("command_action"));
        String status = valueOrEmpty(values.get("command_status"));
        int commandPid = (int) NfcHookUtils.parseLong(values.get("command_pid"), 0L);
        long controllerEpoch = NfcHookUtils.parseLong(values.get("controller_epoch"), 0L);
        if (action.isEmpty()) action = active ? "APPLY" : "STOP";
        // A missing epoch is seeded in memory only; verified RF writes still persist the proof.
        if (controllerEpoch <= 0L) controllerEpoch = 1L;
        return new SimConfig(true, active, uid, diagnostics, generation, consumed, handled,
                action, status, commandPid, controllerEpoch);
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private static Uri configUri() {
        return Uri.parse("content://" + com.yagay.YNFC.BuildConfig.CONFIG_AUTHORITY + "/settings");
    }

    private static String hostPackage() {
        String authority = com.yagay.YNFC.BuildConfig.CONFIG_AUTHORITY;
        return authority != null && authority.startsWith(RuntimeOwnerGate.SUITE_PACKAGE + ".")
                ? RuntimeOwnerGate.SUITE_PACKAGE
                : "com.yagay.YNFC";
    }
}
