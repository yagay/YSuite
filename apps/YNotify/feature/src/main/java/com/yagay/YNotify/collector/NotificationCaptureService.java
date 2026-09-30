package com.yagay.YNotify.collector;

import android.content.ComponentName;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import com.yagay.YNotify.data.ListenerStateStore;

/** Standalone listener host. YSuite uses NotificationListenerBridge from its shared host service. */
public class NotificationCaptureService extends NotificationListenerService {
    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        NotificationListenerBridge.onListenerConnected(this);
    }

    @Override
    public void onListenerDisconnected() {
        NotificationListenerBridge.onListenerDisconnected(this);
        super.onListenerDisconnected();
        try {
            requestRebind(new ComponentName(this, NotificationCaptureService.class));
        } catch (Throwable t) {
            ListenerStateStore.markError(this, "requestRebind", getPackageName(), t);
        }
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn, RankingMap rankingMap) {
        NotificationListenerBridge.onNotificationPosted(this, sbn, rankingMap);
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn, RankingMap rankingMap, int reason) {
        NotificationListenerBridge.onNotificationRemoved(this, sbn, rankingMap, reason);
    }

    @Override
    public void onNotificationRankingUpdate(RankingMap rankingMap) {
        NotificationListenerBridge.onNotificationRankingUpdate(this, rankingMap);
    }

    @Override
    public void onDestroy() {
        NotificationListenerBridge.onListenerDisconnected(this);
        super.onDestroy();
    }
}
