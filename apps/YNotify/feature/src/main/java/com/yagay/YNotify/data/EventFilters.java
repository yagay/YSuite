package com.yagay.YNotify.data;

/**
 * One shared classification contract for notification history and application UI events.
 * A notification may be shown as a heads-up, bubble or fullscreen surface, but an
 * application Dialog/Popup/Toast is not a posted Android notification.
 */
public final class EventFilters {
    public static final String ALL="all";
    public static final String NOTIFICATIONS="notifications";
    public static final String UI_EVENTS="ui_events";
    public static final String HEADS_UP="heads_up";
    public static final String BUBBLE="bubble";
    public static final String FULL_SCREEN="full_screen";

    public static boolean isUiType(String type) {
        return EventTypes.TOAST.equals(type)
                || EventTypes.DIALOG.equals(type)
                || EventTypes.POPUP.equals(type)
                || EventTypes.SNACKBAR.equals(type)
                || EventTypes.SYSTEM_UI.equals(type)
                || EventTypes.OTHER_UI.equals(type);
    }

    public static boolean matches(EventRecord item, String filter) {
        if (item==null) return false;
        if (filter==null || ALL.equals(filter)) return true;
        if (NOTIFICATIONS.equals(filter))return EventTypes.NOTIFICATION.equals(item.eventType);
        if (UI_EVENTS.equals(filter))return isUiType(item.eventType);
        if (HEADS_UP.equals(filter))return EventTypes.NOTIFICATION.equals(item.eventType)&&item.headsUp;
        if (BUBBLE.equals(filter))return EventTypes.NOTIFICATION.equals(item.eventType)&&item.bubbleShown;
        if (FULL_SCREEN.equals(filter))return EventTypes.NOTIFICATION.equals(item.eventType)&&item.fullScreenShown;
        return filter.equals(item.eventType);
    }
    private EventFilters(){}
}
