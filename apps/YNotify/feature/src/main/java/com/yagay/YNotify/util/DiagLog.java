package com.yagay.YNotify.util;

import android.content.Context;

import com.yagay.suite.api.FeatureServices;
import com.yagay.suite.api.HostLogLevel;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class DiagLog {
    private static final Object LOCK = new Object();
    private static final long MAX_BYTES = 2L * 1024L * 1024L;
    private static final String DIR = "diagnostics";
    private static final String CURRENT = "notifylens.log";
    private static final String PREVIOUS = "notifylens.previous.log";
    private static final FeatureServices SERVICES = FeatureServices.of("ynotify", "YNotify");

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
        return new File(dir(context), CURRENT);
    }

    public static File previousFile(Context context) {
        return new File(dir(context), PREVIOUS);
    }

    private static File dir(Context context) {
        File dir = new File(context.getFilesDir(), DIR);
        if (!dir.exists()) dir.mkdirs();
        return dir;
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

        byte[] bytes = (line + "\n").getBytes(StandardCharsets.UTF_8);
        synchronized (LOCK) {
            try {
                File current = currentFile(context.getApplicationContext());
                if (current.exists() && current.length() + bytes.length > MAX_BYTES) {
                    File previous = previousFile(context.getApplicationContext());
                    if (previous.exists()) previous.delete();
                    current.renameTo(previous);
                }
                try (FileOutputStream out = new FileOutputStream(current, true)) {
                    out.write(bytes);
                }
            } catch (Throwable ignored) {}
        }
    }

    private static String timestamp() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(new Date());
    }
}
