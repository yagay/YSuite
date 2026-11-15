package com.yagay.YMiniGuard;

import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

/**
 * Standalone-only listener. In YSuite, the host's one notification listener
 * forwards these same callbacks to YMiniMediaBridge instead.
 */
public final class YMiniMediaListenerService extends NotificationListenerService {
    @Override public void onListenerConnected() {
        super.onListenerConnected();
        YMiniMediaBridge.onListenerConnected(this);
    }
    @Override public void onListenerDisconnected() {
        YMiniMediaBridge.onListenerDisconnected(this);
        super.onListenerDisconnected();
    }
    @Override public void onNotificationPosted(StatusBarNotification sbn,
            RankingMap rankingMap) {
        YMiniMediaBridge.onNotificationPosted(this, sbn, rankingMap);
    }
    @Override public void onNotificationRemoved(StatusBarNotification sbn,
            RankingMap rankingMap, int reason) {
        YMiniMediaBridge.onNotificationRemoved(this, sbn, rankingMap, reason);
    }
    @Override public void onDestroy() {
        YMiniMediaBridge.onListenerDisconnected(this);
        super.onDestroy();
    }
}
