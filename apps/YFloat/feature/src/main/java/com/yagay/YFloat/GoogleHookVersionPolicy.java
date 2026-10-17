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

    // 17.60 is the selected implementation baseline; 17.58 is legacy
    // compatible. Keep the separately reported 17.64 regressions isolated.
    enum Compatibility { BASELINE_1760, LEGACY_1758, KNOWN_BAD, EXPERIMENTAL }

    static Compatibility compatibility(String versionName) {
        String version = versionName == null ? "" : versionName.trim();
        if (version.startsWith("17.60.")) return Compatibility.BASELINE_1760;
        if (version.startsWith("17.58.")) return Compatibility.LEGACY_1758;
        if (version.startsWith("17.64.")) return Compatibility.KNOWN_BAD;
        return Compatibility.EXPERIMENTAL;
    }

    private GoogleHookVersionPolicy() {}
}
