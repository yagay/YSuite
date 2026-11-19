package com.yagay.YMiniGuard;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Process;
import android.os.SystemClock;
import android.util.Log;

import com.yagay.suite.api.RuntimeHealth;

/**
 * Status protocol implementation shared by standalone YMiniGuard and the YSuite host wrapper.
 * Android registers this class only in the standalone APK; YSuite registers its own subclass.
 */
public class EngineStatusProvider extends ContentProvider {
    private static final String DEFAULT_HOST = "com.yagay.YMiniGuard";

    static volatile String AUTHORITY = DEFAULT_HOST + ".engine_status";
    static volatile Uri URI = Uri.parse("content://" + AUTHORITY);
    static final String METHOD_MARK = "markEngine";
    static final String METHOD_AUDIO = "markAudio";
    static final String KEY_AUDIO_UIDS = "audio_uids";
    static final String KEY_AUDIO_STATE = "audio_state";
    static final String KEY_AUDIO_AT = "audio_updated_at";

    static final String METHOD_MEDIA_PANEL_LAUNCH = "markMediaPanelLaunch";
    static final String KEY_MEDIA_PANEL_SEQUENCE = "media_panel_seq";
    static final String KEY_MEDIA_PANEL_AT = "media_panel_at";
    static final String KEY_MEDIA_PANEL_CONSUMED = "media_panel_consumed";
    private static final long MEDIA_PANEL_LAUNCH_VALID_MS = 5_000L;
    private static final Object MEDIA_PANEL_LOCK = new Object();

    private static final String PREFS = "engine_status";
    static final String KEY_VERSION = "version";
    static final String KEY_BOOTSTRAP_VERSION = "bootstrap_version";
    static final String KEY_HOT_RELOAD = "hot_reload";
    static final String KEY_GENERATION = "generation";
    static final String KEY_RELOAD_MESSAGE = "reload_message";
    static final String KEY_ACTIVE_SESSIONS = "active_sessions";
    static final String KEY_STARTED_AT = "started_at";
    static final String KEY_PID = "pid";
    static final String KEY_HOOKS = "hooks";

    static void updateAuthority(String hostPackage) {
        String host = hostPackage == null ? "" : hostPackage.trim();
        if (host.isEmpty()) host = DEFAULT_HOST;
        AUTHORITY = host + ".engine_status";
        URI = Uri.parse("content://" + AUTHORITY);
    }

    static final class Status {
        final long versionCode;
        final long bootstrapVersionCode;
        final boolean hotReload;
        final long generation;
        final String reloadMessage;
        final int activeSessions;
        final long startedAt;
        final int pid;
        final int hookCount;

        Status(
                long versionCode,
                long bootstrapVersionCode,
                boolean hotReload,
                long generation,
                String reloadMessage,
                int activeSessions,
                long startedAt,
                int pid,
                int hookCount
        ) {
            this.versionCode = versionCode;
            this.bootstrapVersionCode = bootstrapVersionCode;
            this.hotReload = hotReload;
            this.generation = generation;
            this.reloadMessage = reloadMessage == null ? "" : reloadMessage;
            this.activeSessions = activeSessions;
            this.startedAt = startedAt;
            this.pid = pid;
            this.hookCount = hookCount;
        }

        boolean isFromCurrentBoot() {
            if (startedAt <= 0) return false;
            // startedAt is refreshed for every 2-second heartbeat. A previous
            // system_server instance is not active merely because this boot persists.
            return RuntimeHealth.isRecentFromCurrentBoot(startedAt, 15_000L);
        }
    }

    static final class AudioState {
        final String status;
        final String uidUsages;
        final long updatedAt;

        AudioState(String status, String uidUsages, long updatedAt) {
            this.status = status == null ? "unavailable" : status;
            this.uidUsages = uidUsages == null ? "" : uidUsages;
            this.updatedAt = updatedAt;
        }

        boolean isFresh() {
            return updatedAt > 0
                    && RuntimeHealth.isRecentFromCurrentBoot(updatedAt,
                            YMiniAudioUidPolicy.MAX_AGE_MS);
        }

        int stateForUid(int uid) {
            long age = isFresh() ? System.currentTimeMillis() - updatedAt
                    : YMiniAudioUidPolicy.MAX_AGE_MS + 1L;
            return YMiniAudioUidPolicy.decision(status, uidUsages, age, uid);
        }
    }

    @Override
    public boolean onCreate() {
        Context context = getContext();
        if (context != null) updateAuthority(context.getPackageName());
        return true;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        Context context = getContext();
        if (context == null) return null;

        int caller = Binder.getCallingUid();
        int ownUid = context.getApplicationInfo().uid;
        if (caller != Process.SYSTEM_UID && caller != ownUid) return null;

        if (METHOD_MEDIA_PANEL_LAUNCH.equals(method)) {
            // The event is not a generic "open an app" IPC. A system_server
            // observer verifies the genuine SystemUI UID and exact YSuite
            // launcher intent before calling here. Reject app/user callers.
            if (caller != Process.SYSTEM_UID) return null;
            boolean success;
            long seq;
            synchronized (MEDIA_PANEL_LOCK) {
                SharedPreferences p = prefs(context);
                seq = p.getLong(KEY_MEDIA_PANEL_SEQUENCE, 0L) + 1L;
                success = p.edit()
                        .putLong(KEY_MEDIA_PANEL_SEQUENCE, seq)
                        .putLong(KEY_MEDIA_PANEL_AT, System.currentTimeMillis())
                        .commit();
            }
            Bundle result = new Bundle();
            result.putBoolean("ok", success);
            if (success) {
                Log.i("YMiniMedia", "MEDIA_PANEL_LAUNCH_MARKED sequence=" + seq);
                context.getContentResolver().notifyChange(
                        Uri.parse("content://" + context.getPackageName() + ".engine_status"),
                        null);
            }
            return result;
        }

        if (METHOD_AUDIO.equals(method)) {
            // Only system_server or this APK can submit playback UID state.
            String state = extras == null ? "" :
                    extras.getString(KEY_AUDIO_STATE, "");
            if (!"ready".equals(state) && !"partial".equals(state)
                    && !"disabled".equals(state)
                    && !"unavailable".equals(state))
                return null;
            String uids = extras == null ? "" : extras.getString(KEY_AUDIO_UIDS, "");
            if (uids == null || uids.length() > 1024) return null;
            // Store only UID+audio usage for already-selected packages,
            // never information about unrelated apps or raw audio data.
            if (!uids.matches("(?:[0-9]{5,8}:[0-9]{1,3}:[0-9]{1,3})(?:,[0-9]{5,8}:[0-9]{1,3}:[0-9]{1,3})*")
                    && !uids.isEmpty()) return null;
            boolean saved = prefs(context).edit()
                    .putString(KEY_AUDIO_STATE, state)
                    .putString(KEY_AUDIO_UIDS, uids)
                    .putLong(KEY_AUDIO_AT, System.currentTimeMillis())
                    .commit();
            Bundle result = new Bundle();
            result.putBoolean("ok", saved);
            return result;
        }

        if (METHOD_MARK.equals(method)) {
            long version = extras == null ? -1L : extras.getLong(KEY_VERSION, -1L);
            long bootstrapVersion = extras == null ? -1L : extras.getLong(KEY_BOOTSTRAP_VERSION, -1L);
            boolean hotReload = extras != null && extras.getBoolean(KEY_HOT_RELOAD, false);
            long generation = extras == null ? 0L : extras.getLong(KEY_GENERATION, 0L);
            String reloadMessage = extras == null ? "" : extras.getString(KEY_RELOAD_MESSAGE, "");
            int activeSessions = extras == null ? -1 : extras.getInt(KEY_ACTIVE_SESSIONS, -1);
            int pid = extras == null ? -1 : extras.getInt(KEY_PID, -1);
            int hooks = extras == null ? -1 : extras.getInt(KEY_HOOKS, -1);
            long now = System.currentTimeMillis();

            boolean saved = prefs(context).edit()
                    .putLong(KEY_VERSION, version)
                    .putLong(KEY_BOOTSTRAP_VERSION, bootstrapVersion)
                    .putBoolean(KEY_HOT_RELOAD, hotReload)
                    .putLong(KEY_GENERATION, generation)
                    .putString(KEY_RELOAD_MESSAGE, reloadMessage)
                    .putInt(KEY_ACTIVE_SESSIONS, activeSessions)
                    .putLong(KEY_STARTED_AT, now)
                    .putInt(KEY_PID, pid)
                    .putInt(KEY_HOOKS, hooks)
                    .commit();

            Bundle result = new Bundle();
            result.putBoolean("ok", saved);
            result.putLong(KEY_STARTED_AT, now);
            return result;
        }

        return super.call(method, arg, extras);
    }

    static Status read(Context context) {
        if (context == null) {
            return new Status(-1L, -1L, false, 0L, "", -1, 0L, -1, -1);
        }

        SharedPreferences p = prefs(context);
        return new Status(
                p.getLong(KEY_VERSION, -1L),
                p.getLong(KEY_BOOTSTRAP_VERSION, -1L),
                p.getBoolean(KEY_HOT_RELOAD, false),
                p.getLong(KEY_GENERATION, 0L),
                p.getString(KEY_RELOAD_MESSAGE, ""),
                p.getInt(KEY_ACTIVE_SESSIONS, -1),
                p.getLong(KEY_STARTED_AT, 0L),
                p.getInt(KEY_PID, -1),
                p.getInt(KEY_HOOKS, -1));
    }

    /**
     * Consume at most once, and only within a short interval of the
     * system_server-verified SystemUI request. Normal YSuite launcher,
     * Recents, lockscreen wake, and other notifications do not mark an event.
     */
    public static boolean consumeMediaPanelLaunch(Context context) {
        if (context == null) return false;
        synchronized (MEDIA_PANEL_LOCK) {
            SharedPreferences p = prefs(context);
            long seq = p.getLong(KEY_MEDIA_PANEL_SEQUENCE, 0L);
            long consumed = p.getLong(KEY_MEDIA_PANEL_CONSUMED, 0L);
            long age = System.currentTimeMillis()
                    - p.getLong(KEY_MEDIA_PANEL_AT, 0L);
            if (seq <= consumed || age < 0 || age > MEDIA_PANEL_LAUNCH_VALID_MS)
                return false;
            return p.edit().putLong(KEY_MEDIA_PANEL_CONSUMED, seq).commit();
        }
    }

    public static boolean hasPendingMediaPanelLaunch(Context context) {
        return pendingMediaPanelLaunchSequence(context) > 0L;
    }

    static long pendingMediaPanelLaunchSequence(Context context) {
        if (context == null) return 0L;
        SharedPreferences p = prefs(context);
        long seq = p.getLong(KEY_MEDIA_PANEL_SEQUENCE, 0L);
        long consumed = p.getLong(KEY_MEDIA_PANEL_CONSUMED, 0L);
        long age = System.currentTimeMillis() - p.getLong(KEY_MEDIA_PANEL_AT, 0L);
        return seq > consumed && age >= 0 && age <= MEDIA_PANEL_LAUNCH_VALID_MS
                ? seq : 0L;
    }

    static String mediaPanelDiagnostic(Context context) {
        if (context == null) return "unavailable";
        SharedPreferences p = prefs(context);
        long at = p.getLong(KEY_MEDIA_PANEL_AT, 0L);
        return "lastSeq=" + p.getLong(KEY_MEDIA_PANEL_SEQUENCE, 0L)
                + ", consumedSeq=" + p.getLong(KEY_MEDIA_PANEL_CONSUMED, 0L)
                + ", lastAtMs=" + at
                + ", ageMs=" + (at > 0 ? System.currentTimeMillis() - at : -1);
    }

    static AudioState readAudio(Context context) {
        if (context == null) return new AudioState("unavailable", "", 0);
        SharedPreferences p = prefs(context);
        return new AudioState(
                p.getString(KEY_AUDIO_STATE, "unavailable"),
                p.getString(KEY_AUDIO_UIDS, ""),
                p.getLong(KEY_AUDIO_AT, 0L));
    }

    private static SharedPreferences prefs(Context context) {
        Context dp = context.createDeviceProtectedStorageContext();
        return dp.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection,
            String[] selectionArgs, String sortOrder) { return null; }
    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection,
            String[] selectionArgs) { return 0; }
}
