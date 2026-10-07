package com.yagay.YNFC.xposed;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

import com.yagay.YNFC.BuildConfig;

import java.util.HashMap;
import java.util.Map;

/**
 * Reads the durable YSuite NFC command snapshot.
 *
 * The rebuilt suite has one runtime owner, so the old standalone/suite handoff gate is not needed.
 * Controller-epoch repair is retained because RF proofs must never survive a controller reset
 * without a matching epoch.
 */
final class HookConfigStore {
    SimConfig read() {
        Context context = NfcHookUtils.currentContext();
        if (context == null) return SimConfig.uninitialized();

        Map<String, String> values = new HashMap<>();
        try (Cursor cursor = context.getContentResolver().query(configUri(), null, null, null, null)) {
            if (cursor == null) return SimConfig.uninitialized();
            while (cursor.moveToNext()) {
                values.put(cursor.getString(0), cursor.getString(1));
            }
        } catch (Throwable ignored) {
            return SimConfig.uninitialized();
        }

        long controllerEpoch =
                NfcHookUtils.parseLong(values.get("controller_epoch"), 0L);
        if (controllerEpoch <= 0L) {
            long rfEpoch =
                    NfcHookUtils.parseLong(values.get("rf_controller_epoch"), 0L);
            long seededEpoch =
                    rfEpoch > 0L ? rfEpoch : Math.max(1L, System.currentTimeMillis());
            try {
                ContentValues repair = new ContentValues();
                repair.put("controller_epoch", seededEpoch);
                String generation = values.get("command_generation");
                if (generation != null) {
                    repair.put(
                            "state_generation",
                            NfcHookUtils.parseLong(generation, 0L));
                }
                context.getContentResolver().insert(configUri(), repair);
            } catch (Throwable ignored) {
                // Fail open. A later read will retry.
            }
            values.put("controller_epoch", Long.toString(seededEpoch));
        }
        return decode(values);
    }

    static SimConfig decode(Map<String, String> values) {
        if (values == null) return SimConfig.uninitialized();
        boolean active =
                Boolean.parseBoolean(values.get("simulation_enabled"));
        boolean diagnostics =
                Boolean.parseBoolean(values.get("diagnostic_logging_enabled"));
        String uid = values.get("uid");
        long generation =
                NfcHookUtils.parseLong(values.get("command_generation"), 0L);
        long consumed =
                NfcHookUtils.parseLong(
                        values.get("command_consumed_generation"),
                        Long.MIN_VALUE);
        long handled =
                NfcHookUtils.parseLong(
                        values.get("command_handled_generation"),
                        Long.MIN_VALUE);
        String action = valueOrEmpty(values.get("command_action"));
        String status = valueOrEmpty(values.get("command_status"));
        int commandPid =
                (int) NfcHookUtils.parseLong(values.get("command_pid"), 0L);
        long controllerEpoch =
                NfcHookUtils.parseLong(values.get("controller_epoch"), 0L);
        if (action.isEmpty()) action = active ? "APPLY" : "STOP";
        if (controllerEpoch <= 0L) {
            long rfEpoch =
                    NfcHookUtils.parseLong(
                            values.get("rf_controller_epoch"),
                            0L);
            controllerEpoch = rfEpoch > 0L ? rfEpoch : 1L;
        }
        return new SimConfig(
                true,
                active,
                uid,
                diagnostics,
                generation,
                consumed,
                handled,
                action,
                status,
                commandPid,
                controllerEpoch);
    }

    private static Uri configUri() {
        return Uri.parse(
                "content://" +
                        BuildConfig.CONFIG_AUTHORITY +
                        "/settings");
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }
}
