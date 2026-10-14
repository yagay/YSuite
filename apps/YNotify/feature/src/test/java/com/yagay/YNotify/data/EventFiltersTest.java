package com.yagay.YNotify.data;

import static org.junit.Assert.*;
import org.junit.Test;

public class EventFiltersTest {
    @Test public void appPopupsAreNotNotificationRecords() {
        EventRecord popup=new EventRecord();
        popup.eventType=EventTypes.POPUP;
        assertTrue(EventFilters.matches(popup,EventFilters.ALL));
        assertTrue(EventFilters.matches(popup,EventFilters.UI_EVENTS));
        assertFalse(EventFilters.matches(popup,EventFilters.NOTIFICATIONS));
        assertFalse(EventFilters.matches(popup,EventFilters.HEADS_UP));
    }

    @Test public void headsUpBubbleAndFullScreenRemainNotificationSurfaces() {
        EventRecord notification=new EventRecord();
        notification.eventType=EventTypes.NOTIFICATION;
        notification.headsUp=true;
        notification.bubbleShown=true;
        notification.fullScreenShown=true;
        assertTrue(EventFilters.matches(notification,EventFilters.NOTIFICATIONS));
        assertTrue(EventFilters.matches(notification,EventFilters.HEADS_UP));
        assertTrue(EventFilters.matches(notification,EventFilters.BUBBLE));
        assertTrue(EventFilters.matches(notification,EventFilters.FULL_SCREEN));
        assertFalse(EventFilters.matches(notification,EventFilters.UI_EVENTS));
    }

    @Test public void allExplicitUiTypesUseUiGroup() {
        for(String type:new String[] {
                EventTypes.TOAST,EventTypes.DIALOG,EventTypes.POPUP,
                EventTypes.SNACKBAR,EventTypes.SYSTEM_UI,EventTypes.OTHER_UI}) {
            EventRecord record=new EventRecord();
            record.eventType=type;
            assertTrue(type,EventFilters.isUiType(type));
            assertTrue(type,EventFilters.matches(record,EventFilters.UI_EVENTS));
            assertFalse(type,EventFilters.matches(record,EventFilters.NOTIFICATIONS));
        }
    }
}
