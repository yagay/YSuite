package com.yagay.ysuite.feature.yparam.runtime;

import org.json.JSONObject;

final class YParamHookConfig {
    final Integer densityDpi;
    final Integer widthPixels;
    final Integer heightPixels;
    final Integer smallestWidthDp;
    final Integer screenWidthDp;
    final Integer screenHeightDp;
    final Float fontScale;
    final Float xdpi;
    final Float ydpi;
    final Float refreshRate;
    final String localeTag;
    final String timeZoneId;
    final String nightMode;
    final String orientation;
    final String userAgent;
    final Boolean allowScreenshots;
    final Boolean keepScreenOn;
    final String locationMode;
    final Double latitude;
    final Double longitude;
    final Double altitude;
    final Float accuracy;
    final Float speed;
    final Float bearing;
    final Float randomRadiusMeters;
    final Integer locationUpdateIntervalMs;

    private YParamHookConfig(JSONObject o) {
        densityDpi = intOrNull(o, "densityDpi");
        widthPixels = intOrNull(o, "widthPixels");
        heightPixels = intOrNull(o, "heightPixels");
        smallestWidthDp = intOrNull(o, "smallestWidthDp");
        screenWidthDp = intOrNull(o, "screenWidthDp");
        screenHeightDp = intOrNull(o, "screenHeightDp");
        fontScale = floatOrNull(o, "fontScale");
        xdpi = floatOrNull(o, "xdpi");
        ydpi = floatOrNull(o, "ydpi");
        refreshRate = floatOrNull(o, "refreshRate");
        localeTag = stringOrNull(o, "localeTag");
        timeZoneId = stringOrNull(o, "timeZoneId");
        nightMode = stringOrNull(o, "nightMode");
        orientation = stringOrNull(o, "orientation");
        userAgent = stringOrNull(o, "userAgent");
        allowScreenshots = boolOrNull(o, "allowScreenshots");
        keepScreenOn = boolOrNull(o, "keepScreenOn");
        locationMode = stringOrNull(o, "locationMode");
        latitude = doubleOrNull(o, "latitude");
        longitude = doubleOrNull(o, "longitude");
        altitude = doubleOrNull(o, "altitude");
        accuracy = floatOrNull(o, "accuracy");
        speed = floatOrNull(o, "speed");
        bearing = floatOrNull(o, "bearing");
        randomRadiusMeters = floatOrNull(o, "randomRadiusMeters");
        locationUpdateIntervalMs = intOrNull(o, "locationUpdateIntervalMs");
    }

    static YParamHookConfig fromJson(String raw) {
        try {
            return new YParamHookConfig(
                    raw == null || raw.isBlank() ? new JSONObject() : new JSONObject(raw));
        } catch (Throwable ignored) {
            return new YParamHookConfig(new JSONObject());
        }
    }

    private static Integer intOrNull(JSONObject o, String key) {
        return o.has(key) && !o.isNull(key) ? o.optInt(key) : null;
    }

    private static Float floatOrNull(JSONObject o, String key) {
        return o.has(key) && !o.isNull(key) ? (float) o.optDouble(key) : null;
    }

    private static Double doubleOrNull(JSONObject o, String key) {
        return o.has(key) && !o.isNull(key) ? o.optDouble(key) : null;
    }

    private static Boolean boolOrNull(JSONObject o, String key) {
        return o.has(key) && !o.isNull(key) ? o.optBoolean(key) : null;
    }

    private static String stringOrNull(JSONObject o, String key) {
        if (!o.has(key) || o.isNull(key)) return null;
        String value = o.optString(key, "");
        return value.isBlank() ? null : value;
    }
}
