package com.yagay.YNotify.data;

import android.content.Context;
import androidx.annotation.StringRes;
import com.yagay.YNotify.R;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Notification content categories. Android's Notification.category and the
 * existing event flags are used; display surface (heads-up/bubble/fullscreen)
 * is an independent dimension. No history migration or additional Hook needed.
 */
public final class NotificationFilterCatalog {
    public enum Kind {
        ALL("all", R.string.ynotify_filter_kind_all),
        STANDARD("standard", R.string.ynotify_filter_kind_standard),
        MESSAGE("message", R.string.ynotify_filter_kind_message),
        CALL("call", R.string.ynotify_filter_kind_call),
        MISSED_CALL("missed_call", R.string.ynotify_filter_kind_missed_call),
        VOICEMAIL("voicemail", R.string.ynotify_filter_kind_voicemail),
        EMAIL("email", R.string.ynotify_filter_kind_email),
        SOCIAL("social", R.string.ynotify_filter_kind_social),
        EVENT("event", R.string.ynotify_filter_kind_event),
        REMINDER("reminder", R.string.ynotify_filter_kind_reminder),
        ALARM("alarm", R.string.ynotify_filter_kind_alarm),
        MEDIA("media", R.string.ynotify_filter_kind_media),
        NAVIGATION("navigation", R.string.ynotify_filter_kind_navigation),
        LOCATION_SHARING("location_sharing", R.string.ynotify_filter_kind_location_sharing),
        WORKOUT("workout", R.string.ynotify_filter_kind_workout),
        STOPWATCH("stopwatch", R.string.ynotify_filter_kind_stopwatch),
        PROGRESS("progress", R.string.ynotify_filter_kind_progress),
        FOREGROUND_SERVICE("foreground_service", R.string.ynotify_filter_kind_foreground_service),
        SERVICE("service", R.string.ynotify_filter_kind_service),
        SYSTEM("system", R.string.ynotify_filter_kind_system),
        STATUS("status", R.string.ynotify_filter_kind_status),
        ERROR("error", R.string.ynotify_filter_kind_error),
        PROMO("promo", R.string.ynotify_filter_kind_promo),
        RECOMMENDATION("recommendation", R.string.ynotify_filter_kind_recommendation),
        ONGOING("ongoing", R.string.ynotify_filter_kind_ongoing),
        SILENT("silent", R.string.ynotify_filter_kind_silent),
        OTHER("other", R.string.ynotify_filter_kind_other);
        public final String id;
        @StringRes public final int labelRes;
        Kind(String id, @StringRes int labelRes) {
            this.id=id;
            this.labelRes=labelRes;
        }
    }

    private static final Kind[] VALUES=Kind.values();
    public static Kind at(int index) {
        return index>=0&&index<VALUES.length?VALUES[index]:Kind.ALL;
    }

    public static List<String> labels(Context context) {
        List<String> result=new ArrayList<>(VALUES.length);
        for(Kind kind:VALUES)result.add(context.getString(kind.labelRes));
        return result;
    }

    /** Exhaustive classification for the known Android Notification categories,
     * with metadata-based fallback for apps that omit Notification.category.
     */
    public static Kind classify(EventRecord record) {
        if(record==null)return Kind.OTHER;
        String category=normalize(record.category);
        if(!category.isEmpty()) {
            return switch(category) {
                case "msg", "message" -> Kind.MESSAGE;
                case "call" -> Kind.CALL;
                case "missed_call" -> Kind.MISSED_CALL;
                case "voicemail" -> Kind.VOICEMAIL;
                case "email" -> Kind.EMAIL;
                case "social" -> Kind.SOCIAL;
                case "event" -> Kind.EVENT;
                case "reminder" -> Kind.REMINDER;
                case "alarm" -> Kind.ALARM;
                case "transport", "media" -> Kind.MEDIA;
                case "navigation" -> Kind.NAVIGATION;
                case "location_sharing" -> Kind.LOCATION_SHARING;
                case "workout" -> Kind.WORKOUT;
                case "stopwatch", "stop_watch" -> Kind.STOPWATCH;
                case "progress" -> Kind.PROGRESS;
                case "service" -> record.foregroundService
                        ? Kind.FOREGROUND_SERVICE : Kind.SERVICE;
                case "sys", "system" -> Kind.SYSTEM;
                case "status" -> Kind.STATUS;
                case "err", "error" -> Kind.ERROR;
                case "promo" -> Kind.PROMO;
                case "recommendation" -> Kind.RECOMMENDATION;
                default -> Kind.OTHER;
            };
        }
        // Data saved by prior versions can have a category-kind even when the
        // originating app omitted Notification.category. Do not let historical
        // "bubble" / "full_screen" labels mask the content category.
        if(record.conversation)return Kind.MESSAGE;
        if(record.rawExtras!=null&&record.rawExtras.contains("android.mediaSession"))return Kind.MEDIA;
        if(record.progressMax>0||record.progressIndeterminate)return Kind.PROGRESS;
        if(record.foregroundService)return Kind.FOREGROUND_SERVICE;
        String stored=normalize(record.notificationKind);
        if(!stored.isEmpty()) {
            return switch(stored) {
                case "message" -> Kind.MESSAGE;
                case "call" -> Kind.CALL;
                case "missed_call" -> Kind.MISSED_CALL;
                case "voicemail" -> Kind.VOICEMAIL;
                case "email" -> Kind.EMAIL;
                case "social" -> Kind.SOCIAL;
                case "event" -> Kind.EVENT;
                case "reminder" -> Kind.REMINDER;
                case "alarm" -> Kind.ALARM;
                case "media" -> Kind.MEDIA;
                case "navigation" -> Kind.NAVIGATION;
                case "location_sharing" -> Kind.LOCATION_SHARING;
                case "workout" -> Kind.WORKOUT;
                case "stopwatch" -> Kind.STOPWATCH;
                case "progress" -> Kind.PROGRESS;
                case "foreground_service" -> Kind.FOREGROUND_SERVICE;
                case "service" -> Kind.SERVICE;
                case "system" -> Kind.SYSTEM;
                case "status" -> Kind.STATUS;
                case "error" -> Kind.ERROR;
                case "promo" -> Kind.PROMO;
                case "recommendation" -> Kind.RECOMMENDATION;
                case "ongoing" -> Kind.ONGOING;
                case "silent" -> Kind.SILENT;
                case "standard", "bubble", "full_screen" ->
                        record.ongoing?Kind.ONGOING:record.silent?Kind.SILENT:Kind.STANDARD;
                default -> Kind.OTHER;
            };
        }
        if(record.ongoing)return Kind.ONGOING;
        if(record.silent)return Kind.SILENT;
        return Kind.STANDARD;
    }

    public static boolean matches(EventRecord record,Kind kind) {
        return kind==null || kind==Kind.ALL || classify(record)==kind;
    }

    private static String normalize(String value) {
        return value==null?"":value.trim().toLowerCase(Locale.ROOT);
    }

    private NotificationFilterCatalog(){}
}
