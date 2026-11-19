package com.yagay.YNFC.xposed;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.util.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Preserves ordered asynchronous writes and explicit synchronous lifecycle barriers. */
final class HookStateWriter {
    private static final String TAG = "NfcUIDSim";
    private final ExecutorService executor = Executors.newSingleThreadExecutor(
            runnable -> NfcHookUtils.daemon(runnable, "NfcUIDSim-StateSync"));

    void writeAsync(ContentValues values, int attempts, long delayMs) {
        if (HookConfigStore.isPassive()) return;
        final ContentValues copy = new ContentValues(values);
        final Uri destination = HookConfigStore.activeUri();
        executor.execute(() -> {
            for (int i = 0; i < attempts; i++) {
                if (HookConfigStore.isPassive()
                        || !destination.equals(HookConfigStore.activeUri())) return;
                Context context = NfcHookUtils.currentContext();
                if (context != null) {
                    try {
                        context.getContentResolver().insert(destination, copy);
                        return;
                    } catch (Throwable error) {
                        Log.w(TAG, "status write attempt " + (i + 1) + " failed: " + error.getMessage());
                    }
                }
                NfcHookUtils.sleep(delayMs);
            }
        });
    }

    boolean writeSynchronously(ContentValues values, int attempts, long delayMs) {
        if (HookConfigStore.isPassive()) return false;
        final Uri destination = HookConfigStore.activeUri();
        ContentValues copy = new ContentValues(values);
        for (int i = 0; i < attempts; i++) {
            if (HookConfigStore.isPassive()
                    || !destination.equals(HookConfigStore.activeUri())) return false;
            Context context = NfcHookUtils.currentContext();
            if (context != null) {
                try {
                    context.getContentResolver().insert(destination, copy);
                    return true;
                } catch (Throwable error) {
                    Log.w(TAG, "sync state write attempt " + (i + 1) + " failed: " + error.getMessage());
                }
            }
            if (i + 1 < attempts) NfcHookUtils.sleep(delayMs);
        }
        return false;
    }
}
