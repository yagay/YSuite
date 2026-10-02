package com.yagay.YNotify.util;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.provider.Settings;

import com.yagay.YNotify.BuildConfig;
import com.yagay.YNotify.YNotifyApp;
import com.yagay.YNotify.data.EventDao;
import com.yagay.YNotify.data.EventRecord;
import com.yagay.YNotify.data.EventTypes;
import com.yagay.YNotify.data.ListenerStateStore;
import com.yagay.YNotify.data.NotifyDatabase;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class DiagnosticsExporter {
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final int COMMAND_LIMIT = 6 * 1024 * 1024;
    private static final long COMMAND_TIMEOUT_MS = 15_000L;

    private DiagnosticsExporter() {}

    public interface Callback {
        void onSuccess(String fileName, String location, Uri uri, boolean rootCollected);
        void onFailure(String message);
    }

    public static void export(Context context, Callback callback) {
        Context app = context.getApplicationContext();
        WORKER.execute(() -> exportBlocking(app, callback));
    }

    private static void exportBlocking(Context context, Callback callback) {
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
        String fileName = "YNotify-diagnostic-" + stamp + ".zip";
        File temp = new File(context.getCacheDir(), fileName);
        boolean rootCollected = false;
        try {
            if (temp.exists()) temp.delete();
            DiagLog.i(context, "DiagnosticsExporter", "diagnostic export started");
            CommandResult rootProbe = runCommand(true, "id", 20_000L, 64 * 1024);
            rootCollected = rootProbe.exitCode == 0 && rootProbe.output.contains("uid=0");

            try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(temp))) {
                addText(zip, "README.txt", readme(rootCollected));
                addText(zip, "app/app_state.txt", appState(context));
                addText(zip, "app/database_summary.txt", databaseSummary(context));
                addFile(zip, "app/notifylens.log", DiagLog.currentFile(context));
                addFile(zip, "app/notifylens.previous.log", DiagLog.previousFile(context));

                addText(zip, "android/logcat_app.txt",
                        formatResult(runCommand(false,
                                "logcat -d -v threadtime --pid=" + android.os.Process.myPid() + " -t 5000",
                                COMMAND_TIMEOUT_MS, 3 * 1024 * 1024)));

                if (rootCollected) {
                    addText(zip, "root/root_environment.txt", formatResult(runCommand(true, rootEnvironmentCommand(), COMMAND_TIMEOUT_MS, 2 * 1024 * 1024)));
                    addText(zip, "root/root_modules.txt", formatResult(runCommand(true, rootModulesCommand(), COMMAND_TIMEOUT_MS, 2 * 1024 * 1024)));
                    addText(zip, "lsposed/lsposed_environment.txt", formatResult(runCommand(true, lsposedEnvironmentCommand(), COMMAND_TIMEOUT_MS, 2 * 1024 * 1024)));
                    addText(zip, "lsposed/lsposed_logs.txt", formatResult(runCommand(true, lsposedLogsCommand(), 25_000L, COMMAND_LIMIT)));
                    addText(zip, "android/logcat_system_root.txt", formatResult(runCommand(true,
                            "logcat -b all -d -v threadtime -t 15000",
                            25_000L, COMMAND_LIMIT)));
                    addText(zip, "android/dumpsys_notification.txt", formatResult(runCommand(true,
                            "dumpsys notification",
                            COMMAND_TIMEOUT_MS, 4 * 1024 * 1024)));
                    addText(zip, "android/dumpsys_accessibility.txt", formatResult(runCommand(true,
                            "dumpsys accessibility",
                            COMMAND_TIMEOUT_MS, 3 * 1024 * 1024)));
                    addText(zip, "android/dumpsys_package.txt", formatResult(runCommand(true,
                            "dumpsys package " + context.getPackageName(),
                            COMMAND_TIMEOUT_MS, 3 * 1024 * 1024)));
                    addText(zip, "android/dumpsys_activity.txt", formatResult(runCommand(true,
                            "dumpsys activity services " + context.getPackageName() + "; echo; dumpsys activity processes | grep -i -A 8 -B 4 '" + context.getPackageName() + "'",
                            COMMAND_TIMEOUT_MS, 3 * 1024 * 1024)));
                    addText(zip, "android/kernel_dmesg.txt", formatResult(runCommand(true,
                            "dmesg 2>&1 | tail -n 3000",
                            COMMAND_TIMEOUT_MS, 3 * 1024 * 1024)));
                    addText(zip, "android/anr_traces.txt", formatResult(runCommand(true,
                            anrCommand(),
                            COMMAND_TIMEOUT_MS, 3 * 1024 * 1024)));
                    addText(zip, "android/tombstones.txt", formatResult(runCommand(true,
                            tombstoneCommand(),
                            20_000L, 4 * 1024 * 1024)));
                    addText(zip, "android/system_settings.txt", formatResult(runCommand(true,
                            "echo 'enabled_notification_listeners='; settings get secure enabled_notification_listeners; echo; echo 'enabled_accessibility_services='; settings get secure enabled_accessibility_services; echo; echo 'notification_badging='; settings get secure notification_badging",
                            COMMAND_TIMEOUT_MS, 512 * 1024)));
                    addText(zip, "android/device_properties.txt", formatResult(runCommand(true,
                            "getprop | grep -E '^\\[(ro\\.(build|product|hardware|boot)|ro.boot|persist.sys)' | grep -Evi 'serial|imei|meid|android_id|mac|bluetooth.address'",
                            COMMAND_TIMEOUT_MS, 2 * 1024 * 1024)));
                } else {
                    addText(zip, "root/ROOT_NOT_AVAILABLE.txt",
                            com.yagay.suite.api.YLocale.text(com.yagay.YNotify.R.string.ynotify_generated_e1e4ae57de69) + formatResult(rootProbe)
                                    + com.yagay.suite.api.YLocale.text(com.yagay.YNotify.R.string.ynotify_generated_ccb4b64d0e71));
                }
            }

            Uri uri = publishToDownloads(context, temp, fileName);
            temp.delete();
            boolean finalRootCollected = rootCollected;
            DiagLog.i(context, "DiagnosticsExporter", "diagnostic export completed root=" + finalRootCollected + " file=" + fileName);
            MAIN.post(() -> {
                if (callback != null) callback.onSuccess(fileName,
                        "Download/YNotify/" + fileName, uri, finalRootCollected);
            });
        } catch (Throwable t) {
            temp.delete();
            DiagLog.e(context, "DiagnosticsExporter", "export failed", t);
            String message = t.getClass().getSimpleName()
                    + (t.getMessage() == null ? "" : ": " + t.getMessage());
            MAIN.post(() -> { if (callback != null) callback.onFailure(message); });
        }
    }

    private static String readme(boolean rootCollected) {
        return com.yagay.suite.api.YLocale.text(com.yagay.YNotify.R.string.ynotify_generated_342c4a97e927)
                + com.yagay.suite.api.YLocale.text(com.yagay.YNotify.R.string.ynotify_generated_77791c0c2b7a) + new Date() + "\n"
                + com.yagay.suite.api.YLocale.text(com.yagay.YNotify.R.string.ynotify_generated_dd5a3210820c) + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")\n"
                + com.yagay.suite.api.YLocale.text(com.yagay.YNotify.R.string.ynotify_generated_6dd342ab26af) + (rootCollected ? com.yagay.suite.api.YLocale.text(com.yagay.YNotify.R.string.ynotify_generated_7cad343f0ca7) : com.yagay.suite.api.YLocale.text(com.yagay.YNotify.R.string.ynotify_generated_7817c7c25cb8)) + "\n\n"
                + com.yagay.suite.api.YLocale.text(com.yagay.YNotify.R.string.ynotify_generated_11c25e70fda9)
                + com.yagay.suite.api.YLocale.text(com.yagay.YNotify.R.string.ynotify_dynamic_9f5d57d6993c)
                + com.yagay.suite.api.YLocale.text(com.yagay.YNotify.R.string.ynotify_dynamic_8c08d5292f6e)
                + com.yagay.suite.api.YLocale.text(com.yagay.YNotify.R.string.ynotify_dynamic_a75f115dc52a)
                + com.yagay.suite.api.YLocale.text(com.yagay.YNotify.R.string.ynotify_dynamic_845fc927acc8)
                + com.yagay.suite.api.YLocale.text(com.yagay.YNotify.R.string.ynotify_dynamic_833e9f9c1237)
                + com.yagay.suite.api.YLocale.text(com.yagay.YNotify.R.string.ynotify_dynamic_dcf191abdabd)
                + com.yagay.suite.api.YLocale.text(com.yagay.YNotify.R.string.ynotify_generated_15afac2c9263)
                + com.yagay.suite.api.YLocale.text(com.yagay.YNotify.R.string.ynotify_generated_6f9a23a73855);
    }

    private static String appState(Context context) {
        StringBuilder out = new StringBuilder();
        out.append("generated_at=").append(new Date()).append('\n');
        out.append("version=").append(BuildConfig.VERSION_NAME).append(" (").append(BuildConfig.VERSION_CODE).append(")\n");
        out.append("package=").append(context.getPackageName()).append('\n');
        out.append("sdk=").append(Build.VERSION.SDK_INT).append('\n');
        out.append("release=").append(Build.VERSION.RELEASE).append('\n');
        out.append("manufacturer=").append(Build.MANUFACTURER).append('\n');
        out.append("brand=").append(Build.BRAND).append('\n');
        out.append("model=").append(Build.MODEL).append('\n');
        out.append("device=").append(Build.DEVICE).append('\n');
        out.append("product=").append(Build.PRODUCT).append('\n');
        out.append("fingerprint=").append(Build.FINGERPRINT).append('\n');
        out.append("process_uptime_ms=").append(SystemClock.elapsedRealtime()).append('\n');
        out.append("\n[notification_listener]\n");
        out.append("connected=").append(ListenerStateStore.isConnected(context)).append('\n');
        out.append("last_connected=").append(ListenerStateStore.lastConnected(context)).append('\n');
        out.append("last_disconnected=").append(ListenerStateStore.lastDisconnected(context)).append('\n');
        out.append("last_event=").append(ListenerStateStore.lastEvent(context)).append('\n');
        out.append("last_notification_received=").append(ListenerStateStore.lastNotificationReceived(context)).append('\n');
        out.append("last_notification_saved=").append(ListenerStateStore.lastNotificationSaved(context)).append('\n');
        out.append("last_notification_package=").append(ListenerStateStore.lastNotificationPackage(context)).append('\n');
        out.append("last_error_time=").append(ListenerStateStore.lastErrorTime(context)).append('\n');
        out.append("last_error=").append(ListenerStateStore.lastError(context)).append('\n');
        out.append("\n[framework]\n").append(YNotifyApp.runtimeStatus()).append('\n');
        try {
            out.append("\n[secure_settings]\n");
            out.append("enabled_notification_listeners=")
                    .append(Settings.Secure.getString(context.getContentResolver(), "enabled_notification_listeners")).append('\n');
            out.append("enabled_accessibility_services=")
                    .append(Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)).append('\n');
        } catch (Throwable t) {
            out.append("secure_settings_error=").append(t).append('\n');
        }
        return out.toString();
    }

    private static String databaseSummary(Context context) {
        StringBuilder out = new StringBuilder();
        try {
            NotifyDatabase db = NotifyDatabase.get(context);
            EventDao dao = db.eventDao();
            out.append("database_path=").append(context.getDatabasePath("ynotify.db").getAbsolutePath()).append('\n');
            out.append("database_size=").append(context.getDatabasePath("ynotify.db").length()).append('\n');
            out.append("total_events=").append(dao.totalCount()).append('\n');
            out.append("visible_events=").append(dao.visibleCount()).append('\n');
            out.append("merged_events=").append(dao.mergedCount()).append('\n');
            out.append("notifications=").append(dao.countType(EventTypes.NOTIFICATION)).append('\n');
            out.append("toast=").append(dao.countType(EventTypes.TOAST)).append('\n');
            out.append("dialog=").append(dao.countType(EventTypes.DIALOG)).append('\n');
            out.append("popup=").append(dao.countType(EventTypes.POPUP)).append('\n');
            out.append("snackbar=").append(dao.countType(EventTypes.SNACKBAR)).append('\n');
            out.append("system_ui=").append(dao.countType(EventTypes.SYSTEM_UI)).append('\n');
            out.append("other_ui=").append(dao.countType(EventTypes.OTHER_UI)).append('\n');
            out.append("\n[recent_event_metadata]\n");
            out.append("id\tpostedAt\tupdatedAt\ttype\tsource\tpackage\tnotificationKey\tchannel\tkind\theadsUp\tongoing\tfgService\tprogress\tprogressMax\trevisions\tmergedInto\tclassification\n");
            List<EventRecord> recent = dao.latestForDiagnostics(300);
            for (EventRecord r : recent) {
                out.append(r.id).append('\t')
                        .append(r.postedAt).append('\t')
                        .append(r.updatedAt).append('\t')
                        .append(clean(r.eventType)).append('\t')
                        .append(clean(r.source)).append('\t')
                        .append(clean(r.packageName)).append('\t')
                        .append(clean(r.notificationKey)).append('\t')
                        .append(clean(r.channelId)).append('\t')
                        .append(clean(r.notificationKind)).append('\t')
                        .append(r.headsUp).append('\t')
                        .append(r.ongoing).append('\t')
                        .append(r.foregroundService).append('\t')
                        .append(r.progress).append('\t')
                        .append(r.progressMax).append('\t')
                        .append(r.revisionCount).append('\t')
                        .append(r.mergedIntoId == null ? "" : r.mergedIntoId).append('\t')
                        .append(clean(r.classificationSource)).append('\n');
            }
        } catch (Throwable t) {
            out.append("database_error=").append(t.getClass().getName()).append(": ").append(t.getMessage()).append('\n');
        }
        return out.toString();
    }

    private static String rootEnvironmentCommand() {
        return "echo '=== identity ==='; id; "
                + "echo '=== su ==='; su -v 2>&1; su -V 2>&1; "
                + "echo '=== kernel ==='; uname -a; cat /proc/version 2>/dev/null; "
                + "echo '=== selinux ==='; getenforce 2>&1; "
                + "echo '=== ksu/magisk binaries ==='; command -v ksud 2>&1; command -v magisk 2>&1; "
                + "[ -x /data/adb/ksu/bin/ksud ] && /data/adb/ksu/bin/ksud -V 2>&1; "
                + "[ -x /data/adb/magisk/magisk ] && /data/adb/magisk/magisk -V 2>&1; "
                + "echo '=== relevant mounts ==='; mount | grep -Ei '/data/adb|overlay|magisk|kernelsu|ksu' | head -n 300";
    }

    private static String rootModulesCommand() {
        return "echo '=== /data/adb/modules ==='; ls -la /data/adb/modules 2>&1; "
                + "for d in /data/adb/modules/*; do [ -d \"$d\" ] || continue; "
                + "echo; echo \"### $d\"; [ -f \"$d/module.prop\" ] && cat \"$d/module.prop\"; done";
    }

    private static String lsposedEnvironmentCommand() {
        return "echo '=== lspd ==='; ls -la /data/adb/lspd 2>&1; "
                + "echo; echo '=== lspd/log ==='; ls -la /data/adb/lspd/log 2>&1; "
                + "echo; echo '=== lsposed alternate paths ==='; ls -la /data/adb/lsposed 2>&1; "
                + "echo; echo '=== matching root modules ==='; "
                + "for d in /data/adb/modules/*; do [ -f \"$d/module.prop\" ] || continue; grep -Eqi 'lsposed|xposed|zygisk' \"$d/module.prop\" && { echo \"### $d\"; cat \"$d/module.prop\"; }; done";
    }

    private static String lsposedLogsCommand() {
        return "if [ -d /data/adb/lspd/log ]; then "
                + "find /data/adb/lspd/log -maxdepth 2 -type f 2>/dev/null | sort | tail -n 16 | "
                + "while IFS= read -r f; do echo; echo \"===== $f =====\"; tail -c 1048576 \"$f\" 2>&1; done; "
                + "else echo '/data/adb/lspd/log not found'; fi";
    }

    private static String anrCommand() {
        return "if [ -d /data/anr ]; then "
                + "find /data/anr -maxdepth 1 -type f 2>/dev/null | sort | tail -n 6 | "
                + "while IFS= read -r f; do echo; echo \"===== $f =====\"; tail -c 524288 \"$f\" 2>&1; done; "
                + "else echo '/data/anr not found'; fi";
    }

    private static String tombstoneCommand() {
        return "if [ -d /data/tombstones ]; then "
                + "find /data/tombstones -maxdepth 1 -type f 2>/dev/null | sort | tail -n 6 | "
                + "while IFS= read -r f; do echo; echo \"===== $f =====\"; tail -c 524288 \"$f\" 2>&1; done; "
                + "else echo '/data/tombstones not found'; fi";
    }

    private static Uri publishToDownloads(Context context, File source, String fileName) throws Exception {
        ContentResolver resolver = context.getContentResolver();
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
        values.put(MediaStore.MediaColumns.MIME_TYPE, "application/zip");
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/NotifyLens");
        values.put(MediaStore.MediaColumns.IS_PENDING, 1);
        Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        if (uri == null) throw new IllegalStateException("MediaStore insert returned null");
        boolean ok = false;
        try (InputStream in = new FileInputStream(source); OutputStream out = resolver.openOutputStream(uri, "w")) {
            if (out == null) throw new IllegalStateException("Unable to open Downloads output");
            byte[] buffer = new byte[32 * 1024];
            int n;
            while ((n = in.read(buffer)) >= 0) out.write(buffer, 0, n);
            ok = true;
        } finally {
            if (!ok) resolver.delete(uri, null, null);
        }
        ContentValues ready = new ContentValues();
        ready.put(MediaStore.MediaColumns.IS_PENDING, 0);
        resolver.update(uri, ready, null, null);
        return uri;
    }

    private static void addText(ZipOutputStream zip, String name, String text) throws Exception {
        ZipEntry entry = new ZipEntry(name);
        zip.putNextEntry(entry);
        zip.write((text == null ? "" : text).getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static void addFile(ZipOutputStream zip, String name, File file) throws Exception {
        if (file == null || !file.isFile()) {
            addText(zip, name, "<not available>\n");
            return;
        }
        ZipEntry entry = new ZipEntry(name);
        zip.putNextEntry(entry);
        try (InputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[16 * 1024];
            int n;
            while ((n = in.read(buffer)) >= 0) zip.write(buffer, 0, n);
        }
        zip.closeEntry();
    }

    private static CommandResult runCommand(boolean root, String command, long timeoutMs, int maxBytes) {
        java.lang.Process process = null;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        final boolean[] truncated = {false};
        final Throwable[] readError = {null};
        try {
            ProcessBuilder builder = root
                    ? new ProcessBuilder("su", "-c", command)
                    : new ProcessBuilder("sh", "-c", command);
            builder.redirectErrorStream(true);
            process = builder.start();
            java.lang.Process finalProcess = process;
            Thread reader = new Thread(() -> {
                try (InputStream in = finalProcess.getInputStream()) {
                    byte[] bytes = new byte[8192];
                    int total = 0;
                    int n;
                    while ((n = in.read(bytes)) >= 0) {
                        if (total < maxBytes) {
                            int keep = Math.min(n, maxBytes - total);
                            buffer.write(bytes, 0, keep);
                            total += keep;
                            if (keep < n) truncated[0] = true;
                        } else {
                            truncated[0] = true;
                        }
                    }
                } catch (Throwable t) {
                    readError[0] = t;
                }
            }, "NotifyLens-command-reader");
            reader.start();
            boolean finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroy();
                if (!process.waitFor(1_000L, TimeUnit.MILLISECONDS)) process.destroyForcibly();
            }
            reader.join(2_000L);
            int exit = finished ? process.exitValue() : -999;
            String output = buffer.toString(StandardCharsets.UTF_8);
            if (truncated[0]) output += "\n<output truncated at " + maxBytes + " bytes>\n";
            if (!finished) output += "\n<command timed out after " + timeoutMs + " ms>\n";
            if (readError[0] != null) output += "\n<reader error: " + readError[0] + ">\n";
            return new CommandResult(command, exit, output);
        } catch (Throwable t) {
            return new CommandResult(command, -998, t.getClass().getName() + ": " + t.getMessage());
        } finally {
            if (process != null) process.destroy();
        }
    }

    private static String formatResult(CommandResult result) {
        return "$ " + result.command + "\nexit=" + result.exitCode + "\n\n" + result.output;
    }

    private static String clean(String value) {
        if (value == null) return "";
        return value.replace('\t', ' ').replace('\r', ' ').replace('\n', ' ');
    }

    private static final class CommandResult {
        final String command;
        final int exitCode;
        final String output;

        CommandResult(String command, int exitCode, String output) {
            this.command = command;
            this.exitCode = exitCode;
            this.output = output == null ? "" : output;
        }
    }
}
