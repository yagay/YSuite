package com.yagay.suite.api;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.PixelFormat;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.SystemClock;
import android.util.LruCache;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Process-wide source of installed app identity, labels and icon bitmaps.
 *
 * Each module still owns its own filtering, sorting, root-only discovery and
 * feature-specific state. Use from a background thread when a query may miss
 * cache. Android package visibility and requested PackageManager flags remain
 * those of the host process.
 */
public final class InstalledAppCatalog {
    private InstalledAppCatalog() {}

    public static final class Entry {
        public final String packageName;
        public final String label;
        public final ApplicationInfo info;
        public final int uid;
        public final boolean enabled;
        public final boolean system;

        private Entry(ApplicationInfo info, String label) {
            this.info = info;
            this.packageName = info.packageName;
            this.label = label;
            this.uid = info.uid;
            this.enabled = info.enabled;
            this.system = (info.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
        }
    }

    private static final Object LOCK = new Object();
    private static final long FRESH_MS = 30_000L;
    private static final Map<String, Snapshot> CACHE = new HashMap<>();
    private static final LruCache<String, Bitmap> ICONS = new LruCache<>(128);
    private static Context observedApplication;
    private static BroadcastReceiver packageReceiver;

    private static final class Snapshot {
        final long created;
        final List<Entry> apps;

        Snapshot(long created, List<Entry> apps) {
            this.created = created;
            this.apps = Collections.unmodifiableList(apps);
        }
    }

    /** Flag-sensitive; MATCH_DISABLED_COMPONENTS callers keep their original scope. */
    public static List<Entry> list(Context context, int flags) {
        final Context app = context.getApplicationContext() != null
                ? context.getApplicationContext() : context;
        synchronized (LOCK) {
            observePackages(app);
            final String cacheKey = app.getPackageName() + "/" + flags + "/"
                    + app.getResources().getConfiguration().getLocales().toLanguageTags();
            final long now = SystemClock.elapsedRealtime();
            final Snapshot cached = CACHE.get(cacheKey);
            if (cached != null && now - cached.created < FRESH_MS) {
                return cached.apps;
            }
            final PackageManager pm = app.getPackageManager();
            final List<ApplicationInfo> installed;
            if (Build.VERSION.SDK_INT >= 33) {
                installed = pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(flags));
            } else {
                installed = pm.getInstalledApplications(flags);
            }
            final ArrayList<Entry> result = new ArrayList<>(installed.size());
            for (ApplicationInfo info : installed) {
                String label;
                try {
                    CharSequence text = pm.getApplicationLabel(info);
                    label = text == null ? info.packageName : text.toString();
                } catch (RuntimeException exception) {
                    label = info.packageName;
                }
                result.add(new Entry(info, label));
            }
            final Snapshot next = new Snapshot(now, result);
            CACHE.put(cacheKey, next);
            return next.apps;
        }
    }

    /** Lookup cached app records instead of independently querying label/package metadata. */
    public static Entry find(Context context, String packageName, int flags) {
        if (packageName == null) return null;
        for (Entry entry : list(context, flags)) {
            if (packageName.equals(entry.packageName)) return entry;
        }
        return null;
    }

    /** A shared size-aware bitmap cache for both Compose and Java/View rows. */
    public static Bitmap iconBitmap(Context context, String packageName, int sizePx) {
        if (packageName == null || sizePx <= 0) return null;
        final String key = context.getPackageName() + "/" + packageName + "/" + sizePx
                + "/" + context.getResources().getDisplayMetrics().densityDpi;
        synchronized (LOCK) {
            final Bitmap cached = ICONS.get(key);
            if (cached != null && !cached.isRecycled()) return cached;
        }
        try {
            Drawable drawable = context.getPackageManager().getApplicationIcon(packageName);
            Bitmap bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            drawable.setBounds(0, 0, sizePx, sizePx);
            drawable.draw(canvas);
            synchronized (LOCK) {
                ICONS.put(key, bitmap);
            }
            return bitmap;
        } catch (RuntimeException | OutOfMemoryError failure) {
            return null;
        }
    }

    /** Java/View adapter; shares the bitmap source instead of re-decoding icons. */
    public static Drawable icon(Context context, String packageName, int sizePx) {
        Bitmap bitmap = iconBitmap(context, packageName, sizePx);
        return bitmap == null ? null : new BitmapDrawable(context.getResources(), bitmap);
    }

    public static void invalidate() {
        synchronized (LOCK) {
            CACHE.clear();
            ICONS.evictAll();
        }
    }

    private static void observePackages(Context context) {
        if (observedApplication == context) return;
        if (packageReceiver != null && observedApplication != null) {
            observedApplication.unregisterReceiver(packageReceiver);
        }
        packageReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context ignored, Intent intent) {
                invalidate();
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_REPLACED);
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addDataScheme("package");
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(packageReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            context.registerReceiver(packageReceiver, filter);
        }
        observedApplication = context;
    }
}
