package com.yagay.YFloat;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Restores the floating icon after reboot or app replacement when the user left it enabled. */
public final class FloatServiceBootReceiver extends BroadcastReceiver {
    private static final ScheduledExecutorService RESTORE =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "YFloat-DelayedRestore");
                t.setDaemon(true);
                return t;
            });

    @Override public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) return;
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            if (FloatServiceState.isEnabled(context)) {
                FloatServiceState.start(context);
            }
            return;
        }
        if (!Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) return;
        if (!FloatServiceState.isEnabled(context)) return;

        // Package replacement commonly happens immediately before the user opens the updated app.
        // Give MainActivity priority for its first frames instead of starting overlay/window work in
        // the same process at exactly the same time.
        PendingResult pending = goAsync();
        Context app = context.getApplicationContext();
        RESTORE.schedule(() -> {
            try {
                if (FloatServiceState.isEnabled(app)) {
                    FloatServiceState.start(app);
                }
            } finally {
                pending.finish();
            }
        }, 3, TimeUnit.SECONDS);
    }
}
