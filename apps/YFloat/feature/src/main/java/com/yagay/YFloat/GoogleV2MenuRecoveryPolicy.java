package com.yagay.YFloat;

/** Pure trailing-edge menu timing; no Android UI or Google APK dependencies. */
final class GoogleV2MenuRecoveryPolicy {
    static long waitForIdle(long now,long lastContent,long lastGesture,
                            boolean dragging,long contentQuietMs,long gestureStaleMs) {
        long quiet=Math.max(0L,contentQuietMs-Math.max(0L,now-lastContent));
        long gesture=dragging
                ?Math.max(0L,gestureStaleMs-Math.max(0L,now-lastGesture)):0L;
        return Math.max(quiet,gesture);
    }
    private GoogleV2MenuRecoveryPolicy() {}
}
