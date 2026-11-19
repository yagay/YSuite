package com.yagay.YMiniGuard;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Private media PendingIntent receiver; commands are revalidated by the bridge. */
public final class YMiniMediaActionReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null || !"com.yagay.YMiniGuard.MEDIA_ACTION".equals(intent.getAction()))
            return;
        String target = intent.getStringExtra("target");
        int command = intent.getIntExtra("command", -1);
        if (target != null && command >= 1 && command <= 4)
            YMiniMediaBridge.command(context, target, command);
    }
}
