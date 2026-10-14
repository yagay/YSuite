package com.yagay.YNotify.data;

import static org.junit.Assert.assertEquals;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Tests the actual SQLite three-valued predicate used by Room, including legacy NULLs. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class EventFilterSqlTest {
    @Test public void nullOriginalTypeMustNotHideAnOrdinaryDialog() {
        try(SQLiteDatabase db=SQLiteDatabase.create(null)) {
            makeSchema(db);
            db.execSQL("INSERT INTO events VALUES ('dialog', 0, 'accessibility', NULL, NULL, NULL, NULL)");
            assertEquals(0, count(db, EventDao.NOTIFICATION_MATCH));
            assertEquals(1, count(db, EventDao.UI_MATCH));
        }
    }

    @Test public void mislabeledListenerNotificationIsNotAnAppPopup() {
        try(SQLiteDatabase db=SQLiteDatabase.create(null)) {
            makeSchema(db);
            db.execSQL("INSERT INTO events VALUES ('popup', 0, 'notification_listener', NULL, NULL, NULL, NULL)");
            assertEquals(1, count(db, EventDao.NOTIFICATION_MATCH));
            assertEquals(0, count(db, EventDao.UI_MATCH));
        }
    }

    @Test public void manualClassificationHasPriorityOverListenerEvidence() {
        try(SQLiteDatabase db=SQLiteDatabase.create(null)) {
            makeSchema(db);
            db.execSQL("INSERT INTO events VALUES ('dialog', 1, 'notification_listener', NULL, NULL, NULL, NULL)");
            assertEquals(0, count(db, EventDao.NOTIFICATION_MATCH));
            assertEquals(1, count(db, EventDao.UI_MATCH));
        }
    }

    private static void makeSchema(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE events (event_type TEXT, classification_locked INTEGER, "
                + "source TEXT, notification_key TEXT, channel_id TEXT, "
                + "instance_id INTEGER, original_event_type TEXT)");
    }

    private static int count(SQLiteDatabase db, String predicate) {
        try (Cursor cursor=db.rawQuery("SELECT COUNT(*) FROM events WHERE "+predicate,null)) {
            cursor.moveToFirst();
            return cursor.getInt(0);
        }
    }
}
