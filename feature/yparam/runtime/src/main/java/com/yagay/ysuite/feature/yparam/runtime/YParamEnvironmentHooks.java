package com.yagay.ysuite.feature.yparam.runtime;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Point;
import android.graphics.Rect;
import android.location.Location;
import android.os.Bundle;
import android.os.LocaleList;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.Window;
import android.view.WindowManager;
import android.view.WindowMetrics;
import android.webkit.WebSettings;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.TimeZone;

import io.github.libxposed.api.XposedModule;

final class YParamEnvironmentHooks {
    private static final String TAG = "YSuite/YParamHook";
    private final XposedModule module;
    private final String packageName;
    private final SharedPreferences prefs;
    private volatile YParamHookConfig config;

    YParamEnvironmentHooks(XposedModule module, String packageName) {
        this.module = module;
        this.packageName = packageName;
        this.prefs = module.getRemotePreferences("yparam");
        reload();
        prefs.registerOnSharedPreferenceChangeListener((p, key) -> {
            if (("app." + packageName).equals(key)) reload();
        });
    }

    private void reload() {
        config = YParamHookConfig.fromJson(
                prefs.getString("app." + packageName, null));
    }

    void install() {
        installDisplayHooks();
        installConfigurationHooks();
        installLocaleHooks();
        installTimeZoneHooks();
        installLocationHooks();
        installActivityHooks();
        installWindowHooks();
        installUserAgentHook();
    }

    private void safeHook(
            String name,
            Method method,
            io.github.libxposed.api.XposedInterface.Hooker hooker) {
        try {
            module.hook(method).intercept(hooker);
        } catch (Throwable error) {
            module.log(Log.WARN, TAG, "Skip hook " + name, error);
        }
    }

    private void installDisplayHooks() {
        try {
            safeHook(
                    "Resources.getDisplayMetrics",
                    Resources.class.getDeclaredMethod("getDisplayMetrics"),
                    chain -> {
                        DisplayMetrics dm = (DisplayMetrics) chain.proceed();
                        applyDisplayMetrics(dm);
                        return dm;
                    });
            safeHook(
                    "Display.getMetrics",
                    Display.class.getDeclaredMethod("getMetrics", DisplayMetrics.class),
                    chain -> {
                        Object result = chain.proceed();
                        applyDisplayMetrics((DisplayMetrics) chain.getArg(0));
                        return result;
                    });
            safeHook(
                    "Display.getRealMetrics",
                    Display.class.getDeclaredMethod("getRealMetrics", DisplayMetrics.class),
                    chain -> {
                        Object result = chain.proceed();
                        applyDisplayMetrics((DisplayMetrics) chain.getArg(0));
                        return result;
                    });
            safeHook(
                    "Display.getSize",
                    Display.class.getDeclaredMethod("getSize", Point.class),
                    chain -> {
                        Object result = chain.proceed();
                        Point point = (Point) chain.getArg(0);
                        YParamHookConfig c = config;
                        if (c.widthPixels != null) point.x = c.widthPixels;
                        if (c.heightPixels != null) point.y = c.heightPixels;
                        return result;
                    });
            safeHook(
                    "Display.getRealSize",
                    Display.class.getDeclaredMethod("getRealSize", Point.class),
                    chain -> {
                        Object result = chain.proceed();
                        Point point = (Point) chain.getArg(0);
                        YParamHookConfig c = config;
                        if (c.widthPixels != null) point.x = c.widthPixels;
                        if (c.heightPixels != null) point.y = c.heightPixels;
                        return result;
                    });
            safeHook(
                    "Display.getWidth",
                    Display.class.getDeclaredMethod("getWidth"),
                    chain -> config.widthPixels != null
                            ? config.widthPixels : chain.proceed());
            safeHook(
                    "Display.getHeight",
                    Display.class.getDeclaredMethod("getHeight"),
                    chain -> config.heightPixels != null
                            ? config.heightPixels : chain.proceed());
            safeHook(
                    "Display.getRefreshRate",
                    Display.class.getDeclaredMethod("getRefreshRate"),
                    chain -> config.refreshRate != null
                            ? config.refreshRate : chain.proceed());
        } catch (Throwable error) {
            module.log(Log.WARN, TAG, "Display hooks partial", error);
        }

        try {
            safeHook(
                    "WindowMetrics.getBounds",
                    WindowMetrics.class.getDeclaredMethod("getBounds"),
                    chain -> {
                        Rect real = (Rect) chain.proceed();
                        YParamHookConfig c = config;
                        if (c.widthPixels == null && c.heightPixels == null) return real;
                        int width = c.widthPixels != null ? c.widthPixels : real.width();
                        int height = c.heightPixels != null ? c.heightPixels : real.height();
                        return new Rect(
                                real.left,
                                real.top,
                                real.left + width,
                                real.top + height);
                    });
        } catch (Throwable error) {
            module.log(Log.WARN, TAG, "WindowMetrics hook unavailable", error);
        }
    }

    private void installConfigurationHooks() {
        try {
            safeHook(
                    "Resources.getConfiguration",
                    Resources.class.getDeclaredMethod("getConfiguration"),
                    chain -> {
                        Configuration cfg = (Configuration) chain.proceed();
                        applyConfiguration(cfg);
                        return cfg;
                    });
        } catch (Throwable error) {
            module.log(Log.WARN, TAG, "Configuration hook unavailable", error);
        }
    }

    private void installLocaleHooks() {
        try {
            safeHook(
                    "Locale.getDefault",
                    Locale.class.getDeclaredMethod("getDefault"),
                    chain -> {
                        Locale locale = configuredLocale();
                        return locale != null ? locale : chain.proceed();
                    });
            safeHook(
                    "Locale.getDefault(Category)",
                    Locale.class.getDeclaredMethod("getDefault", Locale.Category.class),
                    chain -> {
                        Locale locale = configuredLocale();
                        return locale != null ? locale : chain.proceed();
                    });
            safeHook(
                    "LocaleList.getDefault",
                    LocaleList.class.getDeclaredMethod("getDefault"),
                    chain -> {
                        Locale locale = configuredLocale();
                        return locale != null
                                ? new LocaleList(locale)
                                : chain.proceed();
                    });
        } catch (Throwable error) {
            module.log(Log.WARN, TAG, "Locale hooks partial", error);
        }
    }

    private void installTimeZoneHooks() {
        try {
            safeHook(
                    "TimeZone.getDefault",
                    TimeZone.class.getDeclaredMethod("getDefault"),
                    chain -> {
                        String id = config.timeZoneId;
                        return id != null && !id.isBlank()
                                ? TimeZone.getTimeZone(id)
                                : chain.proceed();
                    });
        } catch (Throwable error) {
            module.log(Log.WARN, TAG, "TimeZone hook unavailable", error);
        }
    }

    private void installLocationHooks() {
        hookLocationGetter("getLatitude", "lat");
        hookLocationGetter("getLongitude", "lon");
        hookLocationGetter("getAltitude", "alt");
        hookLocationGetter("getAccuracy", "accuracy");
        hookLocationGetter("getSpeed", "speed");
        hookLocationGetter("getBearing", "bearing");
        hookLocationHas("hasAltitude", "alt");
        hookLocationHas("hasAccuracy", "accuracy");
        hookLocationHas("hasSpeed", "speed");
        hookLocationHas("hasBearing", "bearing");
    }

    private void hookLocationGetter(String methodName, String field) {
        try {
            safeHook(
                    "Location." + methodName,
                    Location.class.getDeclaredMethod(methodName),
                    chain -> {
                        YParamHookConfig c = config;
                        if (!locationActive(c)) return chain.proceed();
                        double[] ll = simulatedLatLon(c);
                        return switch (field) {
                            case "lat" -> ll[0];
                            case "lon" -> ll[1];
                            case "alt" -> c.altitude != null ? c.altitude : chain.proceed();
                            case "accuracy" -> c.accuracy != null ? c.accuracy : chain.proceed();
                            case "speed" -> c.speed != null ? c.speed : chain.proceed();
                            case "bearing" -> c.bearing != null ? c.bearing : chain.proceed();
                            default -> chain.proceed();
                        };
                    });
        } catch (Throwable error) {
            module.log(Log.WARN, TAG, "Location hook unavailable " + methodName, error);
        }
    }

    private void hookLocationHas(String methodName, String field) {
        try {
            safeHook(
                    "Location." + methodName,
                    Location.class.getDeclaredMethod(methodName),
                    chain -> {
                        YParamHookConfig c = config;
                        if (!locationActive(c)) return chain.proceed();
                        return switch (field) {
                            case "alt" -> c.altitude != null || (Boolean) chain.proceed();
                            case "accuracy" -> c.accuracy != null || (Boolean) chain.proceed();
                            case "speed" -> c.speed != null || (Boolean) chain.proceed();
                            case "bearing" -> c.bearing != null || (Boolean) chain.proceed();
                            default -> chain.proceed();
                        };
                    });
        } catch (Throwable error) {
            module.log(Log.WARN, TAG, "Location has-hook unavailable " + methodName, error);
        }
    }

    private void installActivityHooks() {
        try {
            safeHook(
                    "Activity.onCreate",
                    Activity.class.getDeclaredMethod("onCreate", Bundle.class),
                    chain -> {
                        Object result = chain.proceed();
                        applyActivity((Activity) chain.getThisObject());
                        return result;
                    });
            safeHook(
                    "Activity.setRequestedOrientation",
                    Activity.class.getDeclaredMethod(
                            "setRequestedOrientation",
                            int.class),
                    chain -> {
                        Integer forced = orientationConstant(config.orientation);
                        return forced == null
                                ? chain.proceed()
                                : chain.proceed(new Object[] { forced });
                    });
        } catch (Throwable error) {
            module.log(Log.WARN, TAG, "Activity hooks partial", error);
        }
    }

    private void installWindowHooks() {
        try {
            safeHook(
                    "Window.addFlags",
                    Window.class.getDeclaredMethod("addFlags", int.class),
                    chain -> {
                        int flags = (Integer) chain.getArg(0);
                        return chain.proceed(new Object[] { sanitizeFlags(flags, true) });
                    });
            safeHook(
                    "Window.setFlags",
                    Window.class.getDeclaredMethod("setFlags", int.class, int.class),
                    chain -> {
                        int flags = (Integer) chain.getArg(0);
                        int mask = (Integer) chain.getArg(1);
                        YParamHookConfig c = config;
                        if (Boolean.TRUE.equals(c.allowScreenshots)) {
                            flags &= ~WindowManager.LayoutParams.FLAG_SECURE;
                        } else if (
                                Boolean.FALSE.equals(c.allowScreenshots) &&
                                (mask & WindowManager.LayoutParams.FLAG_SECURE) != 0) {
                            flags |= WindowManager.LayoutParams.FLAG_SECURE;
                        }
                        if (Boolean.TRUE.equals(c.keepScreenOn)) {
                            flags |= WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
                        } else if (Boolean.FALSE.equals(c.keepScreenOn)) {
                            flags &= ~WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
                        }
                        return chain.proceed(new Object[] { flags, mask });
                    });
        } catch (Throwable error) {
            module.log(Log.WARN, TAG, "Window hooks partial", error);
        }
    }

    private void installUserAgentHook() {
        try {
            safeHook(
                    "WebSettings.getDefaultUserAgent",
                    WebSettings.class.getDeclaredMethod(
                            "getDefaultUserAgent",
                            android.content.Context.class),
                    chain -> {
                        String ua = config.userAgent;
                        return ua != null && !ua.isBlank()
                                ? ua
                                : chain.proceed();
                    });
        } catch (Throwable error) {
            module.log(Log.WARN, TAG, "UA hook unavailable", error);
        }
    }

    private void applyDisplayMetrics(DisplayMetrics dm) {
        if (dm == null) return;
        YParamHookConfig c = config;
        float oldDensity = dm.density == 0f ? 1f : dm.density;
        float oldFontRatio = dm.scaledDensity / oldDensity;
        if (c.densityDpi != null && c.densityDpi > 0) {
            dm.densityDpi = c.densityDpi;
            dm.density = c.densityDpi / 160f;
            dm.scaledDensity =
                    dm.density *
                            (c.fontScale != null ? c.fontScale : oldFontRatio);
        } else if (c.fontScale != null) {
            dm.scaledDensity = dm.density * c.fontScale;
        }
        if (c.widthPixels != null && c.widthPixels > 0) dm.widthPixels = c.widthPixels;
        if (c.heightPixels != null && c.heightPixels > 0) dm.heightPixels = c.heightPixels;
        if (c.xdpi != null && c.xdpi > 0) dm.xdpi = c.xdpi;
        if (c.ydpi != null && c.ydpi > 0) dm.ydpi = c.ydpi;
    }

    private void applyConfiguration(Configuration cfg) {
        if (cfg == null) return;
        YParamHookConfig c = config;
        if (c.densityDpi != null && c.densityDpi > 0) cfg.densityDpi = c.densityDpi;
        if (c.fontScale != null && c.fontScale > 0) cfg.fontScale = c.fontScale;
        if (c.smallestWidthDp != null && c.smallestWidthDp > 0) {
            cfg.smallestScreenWidthDp = c.smallestWidthDp;
        }
        if (c.screenWidthDp != null && c.screenWidthDp > 0) cfg.screenWidthDp = c.screenWidthDp;
        if (c.screenHeightDp != null && c.screenHeightDp > 0) cfg.screenHeightDp = c.screenHeightDp;
        Locale locale = configuredLocale();
        if (locale != null) cfg.setLocales(new LocaleList(locale));
        if ("dark".equals(c.nightMode)) {
            cfg.uiMode =
                    (cfg.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) |
                            Configuration.UI_MODE_NIGHT_YES;
        } else if ("light".equals(c.nightMode)) {
            cfg.uiMode =
                    (cfg.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) |
                            Configuration.UI_MODE_NIGHT_NO;
        }
    }

    private Locale configuredLocale() {
        String tag = config.localeTag;
        if (tag == null || tag.isBlank()) return null;
        Locale locale = Locale.forLanguageTag(tag);
        return locale.getLanguage().isBlank() ? null : locale;
    }

    private void applyActivity(Activity activity) {
        if (activity == null) return;
        Integer orientation = orientationConstant(config.orientation);
        if (orientation != null) activity.setRequestedOrientation(orientation);
        Window window = activity.getWindow();
        if (window == null) return;
        if (Boolean.TRUE.equals(config.allowScreenshots)) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
        } else if (Boolean.FALSE.equals(config.allowScreenshots)) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        }
        if (Boolean.TRUE.equals(config.keepScreenOn)) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else if (Boolean.FALSE.equals(config.keepScreenOn)) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    private int sanitizeFlags(int flags, boolean adding) {
        YParamHookConfig c = config;
        if (Boolean.TRUE.equals(c.allowScreenshots)) {
            flags &= ~WindowManager.LayoutParams.FLAG_SECURE;
        } else if (Boolean.FALSE.equals(c.allowScreenshots) && adding) {
            flags |= WindowManager.LayoutParams.FLAG_SECURE;
        }
        if (Boolean.TRUE.equals(c.keepScreenOn) && adding) {
            flags |= WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
        } else if (Boolean.FALSE.equals(c.keepScreenOn)) {
            flags &= ~WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
        }
        return flags;
    }

    private static Integer orientationConstant(String mode) {
        if (mode == null || mode.isBlank() || "default".equals(mode)) return null;
        return switch (mode) {
            case "portrait" -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
            case "landscape" -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
            case "sensor" -> ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR;
            case "locked" -> ActivityInfo.SCREEN_ORIENTATION_LOCKED;
            default -> null;
        };
    }

    private static boolean locationActive(YParamHookConfig c) {
        return c != null &&
                c.latitude != null &&
                c.longitude != null &&
                ("fixed".equals(c.locationMode) || "random".equals(c.locationMode));
    }

    private static double[] simulatedLatLon(YParamHookConfig c) {
        double lat = c.latitude;
        double lon = c.longitude;
        if (!"random".equals(c.locationMode) ||
                c.randomRadiusMeters == null ||
                c.randomRadiusMeters <= 0) {
            return new double[] { lat, lon };
        }
        long interval =
                c.locationUpdateIntervalMs != null &&
                        c.locationUpdateIntervalMs >= 1000
                        ? c.locationUpdateIntervalMs
                        : 5000L;
        long bucket = System.currentTimeMillis() / interval;
        long seed = bucket * 1103515245L + 12345L;
        double angle =
                ((seed >>> 16) & 0xffff) /
                        65535.0 *
                        Math.PI *
                        2.0;
        double radius =
                Math.sqrt(((seed >>> 32) & 0xffff) / 65535.0) *
                        c.randomRadiusMeters;
        double dLat =
                (radius * Math.cos(angle)) /
                        111_320.0;
        double dLon =
                (radius * Math.sin(angle)) /
                        (111_320.0 *
                                Math.max(
                                        0.1,
                                        Math.cos(
                                                Math.toRadians(lat))));
        return new double[] { lat + dLat, lon + dLon };
    }
}
