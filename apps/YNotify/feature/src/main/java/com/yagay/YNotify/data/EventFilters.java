package com.yagay.YNotify.data;

import java.util.Locale;

/** Shared identity and display filters for notification history and UI events. */
public final class EventFilters {
    public static final String ALL="all";
    public static final String NOTIFICATIONS="notifications";
    public static final String UI_EVENTS="ui_events";
    public static final String HEADS_UP="heads_up";
    public static final String BUBBLE="bubble";
    public static final String FULL_SCREEN="full_screen";

    public static String groupAt(int i) {
        return i==1?NOTIFICATIONS:i==2?UI_EVENTS:ALL;
    }
    public static String notificationAt(int i) {
        return switch(i) {case 1->HEADS_UP;case 2->BUBBLE;case 3->FULL_SCREEN;
            default->NOTIFICATIONS;};
    }
    public static String uiAt(int i) {
        return switch(i) {
            case 1->EventTypes.TOAST; case 2->EventTypes.DIALOG;
            case 3->EventTypes.POPUP; case 4->EventTypes.SNACKBAR;
            case 5->EventTypes.SYSTEM_UI; case 6->EventTypes.OTHER_UI;
            default->UI_EVENTS;
        };
    }
    public static String selectedTypeForGroup(String group,int notificationIndex,int uiIndex) {
        if(NOTIFICATIONS.equals(group))return notificationAt(notificationIndex);
        if(UI_EVENTS.equals(group))return uiAt(uiIndex);
        return ALL;
    }
    public static boolean isUiType(String type) {
        return EventTypes.TOAST.equals(type)||EventTypes.DIALOG.equals(type)
                ||EventTypes.POPUP.equals(type)||EventTypes.SNACKBAR.equals(type)
                ||EventTypes.SYSTEM_UI.equals(type)||EventTypes.OTHER_UI.equals(type);
    }
    /**
     * NotificationListenerService is definitive evidence, even if a past
     * classification pass changed event_type to an application UI type.
     * Explicit user corrections remain authoritative.
     */
    public static boolean isNotificationRecord(EventRecord r) {
        if(r==null)return false;
        if(EventTypes.NOTIFICATION.equals(r.eventType))return true;
        if(r.classificationLocked)return false;
        String source=r.source==null?"":r.source.toLowerCase(Locale.ROOT);
        return source.contains("notification_listener")
                || nonBlank(r.notificationKey)||nonBlank(r.channelId)
                ||r.instanceId!=null
                ||EventTypes.NOTIFICATION.equals(r.originalEventType);
    }
    public static boolean isUiEventRecord(EventRecord r) {
        return r!=null&&isUiType(r.eventType)&&!isNotificationRecord(r);
    }
    public static boolean matches(EventRecord r,String filter) {
        if(r==null)return false;
        if(filter==null||ALL.equals(filter))return true;
        if(NOTIFICATIONS.equals(filter)||EventTypes.NOTIFICATION.equals(filter))
            return isNotificationRecord(r);
        if(UI_EVENTS.equals(filter))return isUiEventRecord(r);
        if(HEADS_UP.equals(filter))return isNotificationRecord(r)&&r.headsUp;
        if(BUBBLE.equals(filter))return isNotificationRecord(r)&&r.bubbleShown;
        if(FULL_SCREEN.equals(filter))return isNotificationRecord(r)&&r.fullScreenShown;
        if(isUiType(filter))return isUiEventRecord(r)&&filter.equals(r.eventType);
        return filter.equals(r.eventType);
    }
    /** Intersect the semantic notification category with its actual presentation. */
    public static boolean matchesNotification(EventRecord event,
            NotificationFilterCatalog.Kind kind,String presentation) {
        return isNotificationRecord(event)
                && NotificationFilterCatalog.matches(event,kind)
                && (presentation==null || NOTIFICATIONS.equals(presentation)
                    || matches(event,presentation));
    }

    private static boolean nonBlank(String s){return s!=null&&!s.isBlank();}
    private EventFilters(){}
}
