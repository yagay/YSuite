package com.yagay.YNotify.data;

import static org.junit.Assert.*;
import org.junit.Test;

public class NotificationFilterCatalogTest {
    @Test public void everyAndroidCategoryHasItsOwnContentFilter() {
        String[] categories = {
            "msg", "call", "missed_call", "voicemail", "email", "social",
            "event", "reminder", "alarm", "transport", "navigation",
            "location_sharing", "workout", "stopwatch", "progress", "service",
            "sys", "status", "err", "promo", "recommendation"
        };
        NotificationFilterCatalog.Kind[] expected = {
            NotificationFilterCatalog.Kind.MESSAGE,
            NotificationFilterCatalog.Kind.CALL,
            NotificationFilterCatalog.Kind.MISSED_CALL,
            NotificationFilterCatalog.Kind.VOICEMAIL,
            NotificationFilterCatalog.Kind.EMAIL,
            NotificationFilterCatalog.Kind.SOCIAL,
            NotificationFilterCatalog.Kind.EVENT,
            NotificationFilterCatalog.Kind.REMINDER,
            NotificationFilterCatalog.Kind.ALARM,
            NotificationFilterCatalog.Kind.MEDIA,
            NotificationFilterCatalog.Kind.NAVIGATION,
            NotificationFilterCatalog.Kind.LOCATION_SHARING,
            NotificationFilterCatalog.Kind.WORKOUT,
            NotificationFilterCatalog.Kind.STOPWATCH,
            NotificationFilterCatalog.Kind.PROGRESS,
            NotificationFilterCatalog.Kind.SERVICE,
            NotificationFilterCatalog.Kind.SYSTEM,
            NotificationFilterCatalog.Kind.STATUS,
            NotificationFilterCatalog.Kind.ERROR,
            NotificationFilterCatalog.Kind.PROMO,
            NotificationFilterCatalog.Kind.RECOMMENDATION
        };
        assertEquals(categories.length,expected.length);
        for(int i=0;i<categories.length;i++) {
            EventRecord record=new EventRecord();
            record.category=categories[i];
            assertEquals(categories[i],expected[i],NotificationFilterCatalog.classify(record));
        }
    }

    @Test public void everyCategoryIsAddressableByAFilterChip() {
        NotificationFilterCatalog.Kind[] kinds=NotificationFilterCatalog.Kind.values();
        assertEquals(27,kinds.length);
        for(int i=0;i<kinds.length;i++) {
            assertEquals(kinds[i],NotificationFilterCatalog.at(i));
            assertTrue(kinds[i].labelRes!=0);
        }
        assertEquals(NotificationFilterCatalog.Kind.ALL,NotificationFilterCatalog.at(-1));
        assertEquals(NotificationFilterCatalog.Kind.ALL,NotificationFilterCatalog.at(999));
    }

    @Test public void metadataClassifiesNotificationsWithoutExplicitCategory() {
        EventRecord message=new EventRecord();
        message.conversation=true;
        assertEquals(NotificationFilterCatalog.Kind.MESSAGE,NotificationFilterCatalog.classify(message));

        EventRecord service=new EventRecord();
        service.foregroundService=true;
        assertEquals(NotificationFilterCatalog.Kind.FOREGROUND_SERVICE,
                NotificationFilterCatalog.classify(service));

        EventRecord progress=new EventRecord();
        progress.progressIndeterminate=true;
        assertEquals(NotificationFilterCatalog.Kind.PROGRESS,
                NotificationFilterCatalog.classify(progress));

        EventRecord silent=new EventRecord();
        silent.silent=true;
        assertEquals(NotificationFilterCatalog.Kind.SILENT,NotificationFilterCatalog.classify(silent));

        EventRecord persistent=new EventRecord();
        persistent.ongoing=true;
        assertEquals(NotificationFilterCatalog.Kind.ONGOING,NotificationFilterCatalog.classify(persistent));

        EventRecord normal=new EventRecord();
        assertEquals(NotificationFilterCatalog.Kind.STANDARD,NotificationFilterCatalog.classify(normal));
    }

    @Test public void legacyPresentationKindDoesNotHideRegularNotification() {
        EventRecord record=new EventRecord();
        record.notificationKind="bubble";
        assertEquals(NotificationFilterCatalog.Kind.STANDARD,NotificationFilterCatalog.classify(record));
        record.category="msg";
        assertEquals(NotificationFilterCatalog.Kind.MESSAGE,NotificationFilterCatalog.classify(record));
    }

    @Test public void categoryAndPresentationCombineWithoutTreatingDialogAsNotification() {
        EventRecord message=new EventRecord();
        message.category="msg";
        message.headsUp=true;
        assertTrue(EventFilters.matchesNotification(message,
                NotificationFilterCatalog.Kind.MESSAGE, EventFilters.NOTIFICATIONS));
        assertTrue(EventFilters.matchesNotification(message,
                NotificationFilterCatalog.Kind.MESSAGE, EventFilters.HEADS_UP));
        assertFalse(EventFilters.matchesNotification(message,
                NotificationFilterCatalog.Kind.CALL, EventFilters.HEADS_UP));
        assertFalse(EventFilters.matchesNotification(message,
                NotificationFilterCatalog.Kind.MESSAGE, EventFilters.BUBBLE));

        EventRecord dialog=new EventRecord();
        dialog.eventType=EventTypes.DIALOG;
        dialog.category="msg";
        dialog.headsUp=true;
        assertFalse(EventFilters.matchesNotification(dialog,
                NotificationFilterCatalog.Kind.MESSAGE, EventFilters.HEADS_UP));
    }

    @Test public void primaryGroupsCoverEveryDetailedNotificationType() {
        assertEquals(7,NotificationFilterCatalog.Group.values().length);
        for(NotificationFilterCatalog.Kind kind:NotificationFilterCatalog.Kind.values()) {
            if(kind==NotificationFilterCatalog.Kind.ALL)continue;
            NotificationFilterCatalog.Group group=NotificationFilterCatalog.groupOf(kind);
            assertNotEquals(NotificationFilterCatalog.Group.ALL,group);
            assertTrue("Missing "+kind,NotificationFilterCatalog.kindsFor(group).contains(kind));
            assertTrue(NotificationFilterCatalog.kindsFor(NotificationFilterCatalog.Group.ALL)
                    .contains(kind));
        }
    }

    @Test public void basicCategoriesCannotDisappearBehindAdvancedConditions() {
        EventRecord normal=new EventRecord();
        assertTrue(EventFilters.matchesNotification(normal,
                NotificationFilterCatalog.Group.OTHER,
                NotificationFilterCatalog.Kind.ALL,
                EventFilters.NOTIFICATIONS,
                NotificationFilterCatalog.Status.ANY));
        assertFalse(EventFilters.matchesNotification(normal,
                NotificationFilterCatalog.Group.COMMUNICATION,
                NotificationFilterCatalog.Kind.ALL,
                EventFilters.NOTIFICATIONS,
                NotificationFilterCatalog.Status.ANY));

        EventRecord msg=new EventRecord();
        msg.category="msg";
        msg.headsUp=true;
        assertTrue(EventFilters.matchesNotification(msg,
                NotificationFilterCatalog.Group.COMMUNICATION,
                NotificationFilterCatalog.Kind.MESSAGE,
                EventFilters.HEADS_UP,
                NotificationFilterCatalog.Status.ANY));
        assertFalse(EventFilters.matchesNotification(msg,
                NotificationFilterCatalog.Group.MEDIA_TRAVEL,
                NotificationFilterCatalog.Kind.MESSAGE,
                EventFilters.HEADS_UP,
                NotificationFilterCatalog.Status.ANY));
    }

    @Test public void notificationStateFiltersAreIndependentOfContent() {
        EventRecord message=new EventRecord();
        message.category="msg";
        message.ongoing=true;
        message.silent=true;
        message.foregroundService=true;
        for(NotificationFilterCatalog.Status state: new NotificationFilterCatalog.Status[]{
                NotificationFilterCatalog.Status.SILENT,
                NotificationFilterCatalog.Status.ONGOING,
                NotificationFilterCatalog.Status.FOREGROUND_SERVICE}) {
            assertTrue(EventFilters.matchesNotification(message,
                    NotificationFilterCatalog.Group.COMMUNICATION,
                    NotificationFilterCatalog.Kind.ALL,
                    EventFilters.NOTIFICATIONS,state));
        }
        EventRecord other=new EventRecord();
        assertFalse(EventFilters.matchesNotification(other,
                NotificationFilterCatalog.Group.OTHER,
                NotificationFilterCatalog.Kind.ALL,
                EventFilters.NOTIFICATIONS,
                NotificationFilterCatalog.Status.SILENT));
    }

    @Test public void advancedKindChoicesStayWithinSelectedPrimaryGroup() {
        for(NotificationFilterCatalog.Group group:NotificationFilterCatalog.Group.values()){
            assertEquals(NotificationFilterCatalog.Kind.ALL,
                    NotificationFilterCatalog.kindsFor(group).get(0));
            for(NotificationFilterCatalog.Kind kind:NotificationFilterCatalog.kindsFor(group))
                if(group!=NotificationFilterCatalog.Group.ALL && kind!=NotificationFilterCatalog.Kind.ALL)
                    assertEquals(group,NotificationFilterCatalog.groupOf(kind));
        }
    }

    @Test public void unknownCategoryIsVisibleUnderOtherNotLost() {
        EventRecord record=new EventRecord();
        record.category="new_oem_category";
        assertEquals(NotificationFilterCatalog.Kind.OTHER,NotificationFilterCatalog.classify(record));
        assertTrue(EventFilters.matchesNotification(record,
                NotificationFilterCatalog.Kind.OTHER,EventFilters.NOTIFICATIONS));
    }
}
