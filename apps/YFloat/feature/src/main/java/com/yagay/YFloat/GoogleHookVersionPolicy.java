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

    private GoogleHookVersionPolicy() {}
}
