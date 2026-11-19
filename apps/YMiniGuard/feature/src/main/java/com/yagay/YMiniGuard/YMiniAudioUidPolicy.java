package com.yagay.YMiniGuard;

/** Pure, conservative decision for an app's system_server audio snapshot. */
final class YMiniAudioUidPolicy {
    static final int UNKNOWN = -1;
    static final int INACTIVE = 0;
    static final int ACTIVE = 1;
    static final long MAX_AGE_MS = 15_000L;

    private YMiniAudioUidPolicy() {}

    static int decision(String status, String uidUsages, long ageMs, int uid) {
        if (uid < 10000 || ageMs < 0 || ageMs > MAX_AGE_MS ||
                (!"ready".equals(status) && !"partial".equals(status)))
            return UNKNOWN;
        if (uidUsages != null && !uidUsages.isEmpty()) {
            String target = Integer.toString(uid);
            for (String entry : uidUsages.split(",")) {
                int colon = entry.indexOf(':');
                String number = colon < 0 ? entry : entry.substring(0, colon);
                if (target.equals(number)) return ACTIVE;
            }
        }
        // Partial means at least one active player has an anonymized UID.
        // It cannot prove that this particular app is idle.
        return "ready".equals(status) ? INACTIVE : UNKNOWN;
    }
}
