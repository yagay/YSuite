package com.yagay.yparam.data;

import org.json.JSONException;
import org.json.JSONObject;

/** Per-package overrides. Null means "do not override; return the real/default value". */
public final class AppConfig {
    public Integer densityDpi;
    public Integer widthPixels;
    public Integer heightPixels;
    public Integer smallestWidthDp;
    public Integer screenWidthDp;
    public Integer screenHeightDp;
    public Float fontScale;
    public Float xdpi;
    public Float ydpi;
    public Float refreshRate;

    public String localeTag;
    public String timeZoneId;
    /** default, light, dark; null is default. */
    public String nightMode;
    /** default, portrait, landscape, sensor, locked; null is default. */
    public String orientation;
    public String userAgent;

    /** null = app default, true = force allow, false = force secure. */
    public Boolean allowScreenshots;
    /** null = app default, true = keep on, false = don't force keep-on. */
    public Boolean keepScreenOn;

    /** null/default, fixed or random. */
    public String locationMode;
    public Double latitude;
    public Double longitude;
    public Double altitude;
    public Float accuracy;
    public Float speed;
    public Float bearing;
    public Float randomRadiusMeters;
    public Integer locationUpdateIntervalMs;

    public static AppConfig fromJson(String raw) {
        AppConfig c = new AppConfig();
        if (raw == null || raw.isBlank()) return c;
        try {
            JSONObject j = new JSONObject(raw);
            c.densityDpi = intOrNull(j, "densityDpi");
            c.widthPixels = intOrNull(j, "widthPixels");
            c.heightPixels = intOrNull(j, "heightPixels");
            c.smallestWidthDp = intOrNull(j, "smallestWidthDp");
            c.screenWidthDp = intOrNull(j, "screenWidthDp");
            c.screenHeightDp = intOrNull(j, "screenHeightDp");
            c.fontScale = floatOrNull(j, "fontScale");
            c.xdpi = floatOrNull(j, "xdpi");
            c.ydpi = floatOrNull(j, "ydpi");
            c.refreshRate = floatOrNull(j, "refreshRate");
            c.localeTag = stringOrNull(j, "localeTag");
            c.timeZoneId = stringOrNull(j, "timeZoneId");
            c.nightMode = stringOrNull(j, "nightMode");
            c.orientation = stringOrNull(j, "orientation");
            c.userAgent = stringOrNull(j, "userAgent");
            c.allowScreenshots = boolOrNull(j, "allowScreenshots");
            c.keepScreenOn = boolOrNull(j, "keepScreenOn");
            c.locationMode = stringOrNull(j, "locationMode");
            c.latitude = doubleOrNull(j, "latitude");
            c.longitude = doubleOrNull(j, "longitude");
            c.altitude = doubleOrNull(j, "altitude");
            c.accuracy = floatOrNull(j, "accuracy");
            c.speed = floatOrNull(j, "speed");
            c.bearing = floatOrNull(j, "bearing");
            c.randomRadiusMeters = floatOrNull(j, "randomRadiusMeters");
            c.locationUpdateIntervalMs = intOrNull(j, "locationUpdateIntervalMs");
        } catch (JSONException ignored) {
        }
        return c;
    }

    public String toJson() {
        JSONObject j = new JSONObject();
        try {
            put(j, "densityDpi", densityDpi);
            put(j, "widthPixels", widthPixels);
            put(j, "heightPixels", heightPixels);
            put(j, "smallestWidthDp", smallestWidthDp);
            put(j, "screenWidthDp", screenWidthDp);
            put(j, "screenHeightDp", screenHeightDp);
            put(j, "fontScale", fontScale);
            put(j, "xdpi", xdpi);
            put(j, "ydpi", ydpi);
            put(j, "refreshRate", refreshRate);
            put(j, "localeTag", localeTag);
            put(j, "timeZoneId", timeZoneId);
            put(j, "nightMode", nightMode);
            put(j, "orientation", orientation);
            put(j, "userAgent", userAgent);
            put(j, "allowScreenshots", allowScreenshots);
            put(j, "keepScreenOn", keepScreenOn);
            put(j, "locationMode", locationMode);
            put(j, "latitude", latitude);
            put(j, "longitude", longitude);
            put(j, "altitude", altitude);
            put(j, "accuracy", accuracy);
            put(j, "speed", speed);
            put(j, "bearing", bearing);
            put(j, "randomRadiusMeters", randomRadiusMeters);
            put(j, "locationUpdateIntervalMs", locationUpdateIntervalMs);
        } catch (JSONException ignored) {
        }
        return j.toString();
    }

    public boolean isEmpty() { return overrideCount() == 0; }

    public int overrideCount() {
        try { return new JSONObject(toJson()).length(); }
        catch (JSONException ignored) { return 0; }
    }

    private static void put(JSONObject j, String key, Object value) throws JSONException {
        if (value != null && (!(value instanceof String) || !((String) value).isBlank())) j.put(key, value);
    }
    private static String stringOrNull(JSONObject j, String key) { return j.has(key) && !j.isNull(key) ? j.optString(key, null) : null; }
    private static Integer intOrNull(JSONObject j, String key) { return j.has(key) && !j.isNull(key) ? j.optInt(key) : null; }
    private static Float floatOrNull(JSONObject j, String key) { return j.has(key) && !j.isNull(key) ? (float) j.optDouble(key) : null; }
    private static Double doubleOrNull(JSONObject j, String key) { return j.has(key) && !j.isNull(key) ? j.optDouble(key) : null; }
    private static Boolean boolOrNull(JSONObject j, String key) { return j.has(key) && !j.isNull(key) ? j.optBoolean(key) : null; }
}
