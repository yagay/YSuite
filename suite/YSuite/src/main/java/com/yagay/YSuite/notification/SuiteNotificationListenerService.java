package com.yagay.YSuite.notification;

import android.content.ComponentName;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import com.yagay.suite.core.SuiteLog;

/** One NotificationListenerService for the merged YSuite host. */
public final class SuiteNotificationListenerService extends NotificationListenerService {
    private static volatile boolean connected;

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        connected = true;
        SuiteNotificationListenerBroker.INSTANCE.onListenerConnected(this);
        SuiteLog.INSTANCE.i(this, "suite",
                "shared notification listener connected; consumers="
                        + SuiteNotificationListenerBroker.INSTANCE.registeredFeatureIds());
    }

    @Override
    public void onListenerDisconnected() {
        connected = false;
        SuiteNotificationListenerBroker.INSTANCE.onListenerDisconnected(this);
        SuiteLog.INSTANCE.i(this, "suite", "shared notification listener disconnected");
        super.onListenerDisconnected();
        try {
            requestRebind(new ComponentName(this, SuiteNotificationListenerService.class));
        } catch (Throwable t) {
            SuiteLog.INSTANCE.e(this, "suite", "shared notification listener rebind failed", t);
        }
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn, RankingMap rankingMap) {
        SuiteNotificationListenerBroker.INSTANCE.onNotificationPosted(this, sbn, rankingMap);
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn, RankingMap rankingMap, int reason) {
        SuiteNotificationListenerBroker.INSTANCE.onNotificationRemoved(this, sbn, rankingMap, reason);
    }

    @Override
    public void onNotificationRankingUpdate(RankingMap rankingMap) {
        SuiteNotificationListenerBroker.INSTANCE.onNotificationRankingUpdate(this, rankingMap);
    }

    @Override
    public void onDestroy() {
        connected = false;
        SuiteNotificationListenerBroker.INSTANCE.onListenerDisconnected(this);
        super.onDestroy();
    }

    public static boolean isConnected() {
        return connected;
    }
}
