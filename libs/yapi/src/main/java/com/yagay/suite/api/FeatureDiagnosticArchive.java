package com.yagay.suite.api;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** One ZIP/MediaStore writer; each feature still owns which evidence it collects. */
public final class FeatureDiagnosticArchive {
    private FeatureDiagnosticArchive() {}

    public static void zipDirectory(File directory, File target) throws Exception {
        if (directory == null || !directory.isDirectory()) {
            throw new IllegalArgumentException("Diagnostic input directory is missing");
        }
        try (ZipOutputStream zip = new ZipOutputStream(
                new BufferedOutputStream(new FileOutputStream(target)))) {
            append(directory, directory, zip);
        }
    }

    private static void append(File base, File entry, ZipOutputStream zip) throws Exception {
        if (entry.isDirectory()) {
            File[] children = entry.listFiles();
            if (children != null) {
                for (File child : children) append(base, child, zip);
            }
            return;
        }
        String relative = base.toPath().relativize(entry.toPath())
                .toString().replace(File.separatorChar, '/');
        zip.putNextEntry(new ZipEntry(relative));
        try (BufferedInputStream input = new BufferedInputStream(new FileInputStream(entry))) {
            byte[] buffer = new byte[32 * 1024];
            int n;
            while ((n = input.read(buffer)) >= 0) {
                if (n > 0) zip.write(buffer, 0, n);
            }
        }
        zip.closeEntry();
    }

    /** Writes a generated ZIP to Downloads/<featureFolder> with the same filename. */
    public static Uri publishToDownloads(
            Context context, File source, String filename, String featureFolder) throws Exception {
        if (context == null || source == null || !source.isFile()) {
            throw new IllegalArgumentException("Diagnostic archive not found");
        }
        if (filename == null || filename.isBlank() || filename.indexOf('/') >= 0) {
            throw new IllegalArgumentException("Invalid archive filename");
        }
        if (featureFolder == null || !featureFolder.matches("[A-Za-z0-9_.-]+")) {
            throw new IllegalArgumentException("Invalid diagnostics folder");
        }
        ContentValues values = new ContentValues();
        values.put(MediaStore.Downloads.DISPLAY_NAME, filename);
        values.put(MediaStore.Downloads.MIME_TYPE, "application/zip");
        values.put(MediaStore.Downloads.RELATIVE_PATH,
                Environment.DIRECTORY_DOWNLOADS + "/" + featureFolder);
        values.put(MediaStore.Downloads.IS_PENDING, 1);
        Uri uri = context.getContentResolver().insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        if (uri == null) return null;
        try {
            try (OutputStream out = context.getContentResolver().openOutputStream(uri);
                 BufferedInputStream in = new BufferedInputStream(new FileInputStream(source))) {
                if (out == null) throw new IllegalStateException("Cannot open download destination");
                byte[] buffer = new byte[32 * 1024];
                int n;
                while ((n = in.read(buffer)) >= 0) {
                    if (n > 0) out.write(buffer, 0, n);
                }
            }
            ContentValues done = new ContentValues();
            done.put(MediaStore.Downloads.IS_PENDING, 0);
            context.getContentResolver().update(uri, done, null, null);
            return uri;
        } catch (Exception error) {
            context.getContentResolver().delete(uri, null, null);
            throw error;
        }
    }
}
