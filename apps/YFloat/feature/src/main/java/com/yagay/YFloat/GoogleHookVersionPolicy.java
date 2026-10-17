package com.yagay.YFloat;

/** Pure policy for determining when a Google APK profile must be revisited. */
final class GoogleHookVersionPolicy {
    static String identity(String versionName, long versionCode, long lastUpdateTime) {
        String name = versionName == null || versionName.isBlank() ? "unknown" : versionName;
        return name + "/" + versionCode + "@" + lastUpdateTime;
    }

    static boolean changed(String previous, String current) {
        return current != null && !current.isBlank()
                && previous != null && !previous.isBlank()
                && !previous.equals(current);
    }

    // One 17.60 symbol baseline. Other builds require semantic discovery.
    enum Compatibility { BASELINE_1760, EXPERIMENTAL }
    static Compatibility compatibility(String versionName) {
        return versionName!=null && versionName.trim().startsWith("17.60.")
                ? Compatibility.BASELINE_1760 : Compatibility.EXPERIMENTAL;
    }

    /** Missing timestamps and monotonic-clock reset also expire pending requests. */
    static boolean scanTimedOut(long started,long now,long timeoutMillis) {
        return started<=0 || now<started || timeoutMillis<=0
                || now-started>=timeoutMillis;
    }

    private GoogleHookVersionPolicy() {}
}
