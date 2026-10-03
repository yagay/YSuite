package com.yagay.YNotify.util;

import android.content.Context;

import com.yagay.suite.api.FeatureServices;
import com.yagay.suite.api.HostLogLevel;
import com.yagay.suite.api.RotatingTextFile;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class DiagLog {
    private static final long MAX_BYTES = 2L * 1024L * 1024L;
    private static final String DIR = "diagnostics";
    private static final String CURRENT = "notifylens.log";
    private static final String PREVIOUS = "notifylens.previous.log";
    private static final FeatureServices SERVICES = FeatureServices.of("ynotify", "YNotify");
    private static volatile RotatingTextFile storage;

    private DiagLog() {}

    public static void i(Context context, String tag, String message) {
        write(context, "I", tag, message, null);
    }

    public static void w(Context context, String tag, String message) {
        write(context, "W", tag, message, null);
    }

    public static void e(Context context, String tag, String message, Throwable error) {
        write(context, "E", tag, message, error);
    }

    public static File currentFile(Context context) {
        return storage(context).currentFile();
    }

    public static File previousFile(Context context) {
        return storage(context).previousFile();
    }

    private static RotatingTextFile storage(Context context) {
        RotatingTextFile current = storage;
        if (current != null) return current;
        synchronized (DiagLog.class) {
            current = storage;
            if (current == null) {
                current = new RotatingTextFile(
                        context.getApplicationContext(), DIR, CURRENT, PREVIOUS, MAX_BYTES);
                storage = current;
            }
            return current;
        }
    }

    private static void write(Context context, String level, String tag, String message, Throwable error) {
        if (context == null) return;
        String safeTag = tag == null ? "YNotify" : tag;
        String safeMessage = message == null ? "" : message.replace('\n', ' ');
        String line = timestamp() + " " + level + "/" + safeTag
                + " [" + Thread.currentThread().getName() + "] " + safeMessage;
        if (error != null) {
            line += " | " + error.getClass().getName();
            if (error.getMessage() != null) line += ": " + error.getMessage().replace('\n', ' ');
        }

        HostLogLevel hostLevel = "E".equals(level)
                ? HostLogLevel.ERROR
                : "W".equals(level) ? HostLogLevel.WARN : HostLogLevel.INFO;
        SERVICES.log(hostLevel, "[" + safeTag + "] " + safeMessage, error);
        storage(context).appendLine(line);
    }

    private static String timestamp() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(new Date());
    }
}
