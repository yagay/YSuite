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

    /** Short, stable primary groups; the full Kind catalog remains in advanced filters. */
    public enum Group {
        ALL(R.string.ynotify_filter_all_notifications),
        COMMUNICATION(R.string.ynotify_group_communication),
        REMINDERS(R.string.ynotify_group_reminders),
        MEDIA_TRAVEL(R.string.ynotify_group_media_travel),
        DOWNLOADS(R.string.ynotify_group_downloads),
        SYSTEM_SERVICES(R.string.ynotify_group_system_services),
        OTHER(R.string.ynotify_group_other);
        @StringRes public final int labelRes;
        Group(@StringRes int labelRes){this.labelRes=labelRes;}
    }

    public enum Status {
        ANY(R.string.ynotify_filter_state_any),
        SILENT(R.string.ynotify_filter_state_silent),
        ONGOING(R.string.ynotify_filter_state_ongoing),
        FOREGROUND_SERVICE(R.string.ynotify_filter_state_foreground);
        @StringRes public final int labelRes;
        Status(@StringRes int labelRes){this.labelRes=labelRes;}
    }

    public static Group groupAt(int index) {
        Group[] groups=Group.values();
        return index>=0&&index<groups.length?groups[index]:Group.ALL;
    }

    public static List<String> groupLabels(Context context) {
        List<String> labels=new ArrayList<>();
        for(Group group:Group.values())labels.add(context.getString(group.labelRes));
        return labels;
    }

    public static Group groupOf(Kind kind) {
        if(kind==null||kind==Kind.ALL)return Group.ALL;
        return switch(kind){
            case MESSAGE,CALL,MISSED_CALL,VOICEMAIL,EMAIL,SOCIAL -> Group.COMMUNICATION;
            case EVENT,REMINDER,ALARM,STOPWATCH -> Group.REMINDERS;
            case MEDIA,NAVIGATION,LOCATION_SHARING,WORKOUT -> Group.MEDIA_TRAVEL;
            case PROGRESS -> Group.DOWNLOADS;
            case FOREGROUND_SERVICE,SERVICE,SYSTEM,STATUS,ERROR -> Group.SYSTEM_SERVICES;
            default -> Group.OTHER;
        };
    }

    public static List<Kind> kindsFor(Group group) {
        List<Kind> selected=new ArrayList<>();
        selected.add(Kind.ALL);
        for(Kind kind:Kind.values()) {
            if(kind!=Kind.ALL && (group==null||group==Group.ALL||groupOf(kind)==group))
                selected.add(kind);
        }
        return selected;
    }

    public static String[] kindLabels(Context context, List<Kind> kinds) {
        String[] labels=new String[kinds.size()];
        for(int i=0;i<kinds.size();i++)labels[i]=context.getString(kinds.get(i).labelRes);
        return labels;
    }

    public static String[] statusLabels(Context context) {
        Status[] states=Status.values();
        String[] labels=new String[states.length];
        for(int i=0;i<states.length;i++)labels[i]=context.getString(states[i].labelRes);
        return labels;
    }

    public static boolean matchesStatus(EventRecord record,Status status) {
        if(record==null)return false;
        if(status==null||status==Status.ANY)return true;
        return switch(status) {
            case SILENT -> record.silent;
            case ONGOING -> record.ongoing;
            case FOREGROUND_SERVICE -> record.foregroundService;
            default -> true;
        };
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
