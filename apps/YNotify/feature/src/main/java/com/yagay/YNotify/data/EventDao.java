package com.yagay.YNotify.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface EventDao {
    // Keep the SQL predicate aligned with EventFilters.isNotificationRecord.
    // A manually classified UI event must not be reintroduced as a notification.
    String NOTIFICATION_MATCH =
            "(event_type = 'notification' OR (classification_locked = 0 AND ("
            + "source LIKE '%notification_listener%' "
            + "OR (notification_key IS NOT NULL AND notification_key <> '') "
            + "OR (channel_id IS NOT NULL AND channel_id <> '') "
            + "OR instance_id IS NOT NULL OR original_event_type = 'notification')))";
    String UI_MATCH =
            "(event_type IN ('toast', 'dialog', 'popup', 'snackbar', 'system_ui', 'other_ui') "
            + "AND NOT " + NOTIFICATION_MATCH + ")";

    @Insert(onConflict = OnConflictStrategy.ABORT)
    long insert(EventRecord record);

    @Update
    void update(EventRecord record);

    @Query("SELECT * FROM events WHERE event_key = :key LIMIT 1")
    EventRecord byEventKey(String key);

    @Query("SELECT * FROM events WHERE merged_into_id IS NULL ORDER BY posted_at DESC LIMIT 1000")
    LiveData<List<EventRecord>> observeAll();

    @Query("SELECT * FROM events WHERE merged_into_id IS NULL AND event_type = :type ORDER BY posted_at DESC LIMIT 1000")
    LiveData<List<EventRecord>> observeType(String type);

    @Query("SELECT * FROM events WHERE merged_into_id IS NULL AND " + NOTIFICATION_MATCH
            + " ORDER BY posted_at DESC LIMIT 1000")
    LiveData<List<EventRecord>> observeNotifications();

    @Query("SELECT * FROM events WHERE merged_into_id IS NULL AND " + UI_MATCH
            + " ORDER BY posted_at DESC LIMIT 1000")
    LiveData<List<EventRecord>> observeUiEvents();

    @Query("SELECT * FROM events WHERE merged_into_id IS NULL AND " + NOTIFICATION_MATCH
            + " AND heads_up = 1 ORDER BY posted_at DESC LIMIT 1000")
    LiveData<List<EventRecord>> observeHeadsUp();

    @Query("SELECT * FROM events WHERE merged_into_id IS NULL AND " + NOTIFICATION_MATCH
            + " AND bubble_shown = 1 ORDER BY posted_at DESC LIMIT 1000")
    LiveData<List<EventRecord>> observeBubbles();

    @Query("SELECT * FROM events WHERE merged_into_id IS NULL AND " + NOTIFICATION_MATCH
            + " AND full_screen_shown = 1 ORDER BY posted_at DESC LIMIT 1000")
    LiveData<List<EventRecord>> observeFullScreens();

    @Query("SELECT * FROM events WHERE merged_into_id IS NULL AND package_name = :packageName ORDER BY posted_at DESC LIMIT 2000")
    LiveData<List<EventRecord>> observePackage(String packageName);

    @Query("SELECT * FROM events WHERE merged_into_id IS NULL AND package_name = :packageName AND event_type = :type ORDER BY posted_at DESC LIMIT 2000")
    LiveData<List<EventRecord>> observePackageType(String packageName, String type);

    @Query("SELECT * FROM events WHERE merged_into_id IS NULL AND package_name = :packageName AND "
            + NOTIFICATION_MATCH + " ORDER BY posted_at DESC LIMIT 2000")
    LiveData<List<EventRecord>> observePackageNotifications(String packageName);

    @Query("SELECT * FROM events WHERE merged_into_id IS NULL AND package_name = :packageName AND "
            + UI_MATCH + " ORDER BY posted_at DESC LIMIT 2000")
    LiveData<List<EventRecord>> observePackageUiEvents(String packageName);

    @Query("SELECT * FROM events WHERE merged_into_id IS NULL AND package_name = :packageName AND " + NOTIFICATION_MATCH
            + " AND heads_up = 1 ORDER BY posted_at DESC LIMIT 2000")
    LiveData<List<EventRecord>> observePackageHeadsUp(String packageName);

    @Query("SELECT * FROM events WHERE merged_into_id IS NULL AND package_name = :packageName AND " + NOTIFICATION_MATCH
            + " AND bubble_shown = 1 ORDER BY posted_at DESC LIMIT 2000")
    LiveData<List<EventRecord>> observePackageBubbles(String packageName);

    @Query("SELECT * FROM events WHERE merged_into_id IS NULL AND package_name = :packageName AND " + NOTIFICATION_MATCH
            + " AND full_screen_shown = 1 ORDER BY posted_at DESC LIMIT 2000")
    LiveData<List<EventRecord>> observePackageFullScreens(String packageName);

    @Query("SELECT * FROM events WHERE merged_into_id IS NULL AND (app_label LIKE '%' || :q || '%' OR package_name LIKE '%' || :q || '%' OR title LIKE '%' || :q || '%' OR text LIKE '%' || :q || '%' OR full_text LIKE '%' || :q || '%') ORDER BY posted_at DESC LIMIT 1000")
    LiveData<List<EventRecord>> searchFallback(String q);

    @Query("SELECT package_name, MAX(app_label) AS app_label, COUNT(*) AS event_count, MAX(posted_at) AS last_time FROM events WHERE merged_into_id IS NULL GROUP BY package_name ORDER BY last_time DESC")
    LiveData<List<AppSummary>> observeApps();

    @Query("SELECT * FROM events WHERE id = :id LIMIT 1")
    EventRecord byId(long id);

    @Query("SELECT * FROM events WHERE notification_key = :key AND merged_into_id IS NULL ORDER BY updated_at DESC LIMIT 1")
    EventRecord latestByNotificationKey(String key);

    @Query("SELECT * FROM events WHERE package_name = :packageName AND event_type = :type AND source != :source AND merged_into_id IS NULL AND posted_at >= :cutoff AND (full_text = :text OR text = :text) ORDER BY posted_at DESC LIMIT 1")
    EventRecord recentEquivalentFromOtherSource(String packageName, String type, String text, String source, long cutoff);

    @Query("SELECT * FROM events ORDER BY posted_at ASC, id ASC")
    List<EventRecord> loadAllForRepair();

    @Query("SELECT COUNT(*) FROM events")
    int totalCount();

    @Query("SELECT COUNT(*) FROM events WHERE merged_into_id IS NULL")
    int visibleCount();

    @Query("SELECT COUNT(*) FROM events WHERE event_type = :type")
    int countType(String type);

    @Query("SELECT * FROM events ORDER BY posted_at DESC LIMIT :limit")
    List<EventRecord> latestForDiagnostics(int limit);

    @Query("SELECT COUNT(*) FROM events WHERE merged_into_id IS NOT NULL")
    int mergedCount();

    @Query("UPDATE events SET merged_into_id = NULL, classification_locked = 0 WHERE merged_into_id IS NOT NULL")
    void unmergeAll();

    @Query("DELETE FROM events WHERE package_name = :packageName")
    void deletePackage(String packageName);

    @Query("DELETE FROM events")
    void deleteAll();

    @Query("DELETE FROM events WHERE posted_at < :cutoff")
    void deleteOlderThan(long cutoff);
}
