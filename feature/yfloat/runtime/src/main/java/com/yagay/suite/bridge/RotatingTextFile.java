package com.yagay.suite.api;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

/**
 * Small reusable UTF-8 text-file store with one previous generation.
 *
 * <p>Formatting, throttling and the meaning of each log line remain feature-specific. This class
 * only owns the repeated file mechanics: location, append, rotation, read and clear.</p>
 */
public final class RotatingTextFile {
    private final File directory;
    private final File current;
    private final File previous;
    private final long maxBytes;
    private final Object lock = new Object();

    public RotatingTextFile(
            Context context,
            String relativeDirectory,
            String currentName,
            String previousName,
            long maxBytes) {
        if (context == null) throw new IllegalArgumentException("context == null");
        if (currentName == null || currentName.isBlank()) {
            throw new IllegalArgumentException("currentName is blank");
        }
        Context app = context.getApplicationContext();
        Context base = app != null ? app : context;
        File files = base.getFilesDir();
        directory = relativeDirectory == null || relativeDirectory.isBlank()
                ? files
                : new File(files, relativeDirectory);
        current = new File(directory, currentName);
        previous = new File(directory,
                previousName == null || previousName.isBlank() ? currentName + ".old" : previousName);
        this.maxBytes = Math.max(1024L, maxBytes);
    }

    public File currentFile() {
        ensureDirectory();
        return current;
    }

    public File previousFile() {
        ensureDirectory();
        return previous;
    }

    public void appendLine(String line) {
        append((line == null ? "" : line) + "\n");
    }

    public void append(String text) {
        byte[] bytes = (text == null ? "" : text).getBytes(StandardCharsets.UTF_8);
        synchronized (lock) {
            try {
                ensureDirectory();
                if (current.exists() && current.length() + bytes.length > maxBytes) rotateLocked();
                try (FileOutputStream out = new FileOutputStream(current, true)) {
                    out.write(bytes);
                }
            } catch (Throwable ignored) {
            }
        }
    }

    public String read() {
        synchronized (lock) {
            if (!current.isFile()) return "";
            try {
                StringBuilder out = new StringBuilder(
                        (int) Math.min(Integer.MAX_VALUE, Math.max(0L, current.length())));
                char[] buffer = new char[8192];
                try (Reader reader = new InputStreamReader(
                        new FileInputStream(current), StandardCharsets.UTF_8)) {
                    int n;
                    while ((n = reader.read(buffer)) >= 0) {
                        if (n > 0) out.append(buffer, 0, n);
                    }
                }
                return out.toString();
            } catch (Throwable ignored) {
                return "";
            }
        }
    }

    public void clear() {
        synchronized (lock) {
            try { if (current.exists()) current.delete(); } catch (Throwable ignored) { }
            try { if (previous.exists()) previous.delete(); } catch (Throwable ignored) { }
        }
    }

    private void ensureDirectory() {
        if (!directory.exists()) directory.mkdirs();
    }

    private void rotateLocked() {
        try { if (previous.exists()) previous.delete(); } catch (Throwable ignored) { }
        if (!current.renameTo(previous)) {
            try (FileOutputStream out = new FileOutputStream(current, false)) {
                // Truncate when rename is not possible.
            } catch (Throwable ignored) {
            }
        }
    }
}
