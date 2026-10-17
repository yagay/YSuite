package com.yagay.YFloat.hook;

/** Pure V2 scene rules: no Google obfuscated symbols or Android classes. */
final class GoogleV2ScenePolicy {
    private static final String CTS_SEARCH =
            "android.app.contextualsearch.action.LAUNCH_CONTEXTUAL_SEARCH";

    static boolean suppressNativeSearch(boolean ownedSession, String action) {
        return suppressNativeSearch(ownedSession,action,null,null);
    }

    static boolean suppressNativeSearch(boolean ownedSession, String action,
                                        String host, String path) {
        if (!ownedSession) return false;
        if (CTS_SEARCH.equals(action)
                || "android.intent.action.WEB_SEARCH".equals(action)
                || "android.intent.action.SEARCH".equals(action)) return true;
        if (!"android.intent.action.VIEW".equals(action) || host==null || path==null)
            return false;
        String h=host.toLowerCase(java.util.Locale.ROOT);
        if (!(h.equals("google.com") || h.endsWith(".google.com")
                || h.equals("lens.google")))return false;
        return path.equals("/search") || path.startsWith("/search/")
                || path.equals("/lens") || path.startsWith("/lens/");
    }

    static boolean shouldBlockTransform(boolean ownedSession, String property,
                                        float requested) {
        if (!ownedSession || property == null) return false;
        float expected;
        float tolerance;
        if ("setScaleX".equals(property) || "setScaleY".equals(property)) {
            expected = 1f;
            tolerance = 0.0005f;
        } else if ("setTranslationX".equals(property)
                || "setTranslationY".equals(property)) {
            expected = 0f;
            tolerance = 0.5f;
        } else {
            return false;
        }
        return Math.abs(requested - expected) > tolerance;
    }

    /** Google's text-handle hit testing needs its own viewport transitions.
     * Only freeze the image before text mode and while drawing a region. */
    static boolean shouldProtectFrozenViewport(boolean owned, boolean textSelecting) {
        return owned && !textSelecting;
    }

    static boolean needsReconcile(boolean owned, float scaleX, float scaleY,
                                  float translationX, float translationY) {
        return owned && (shouldBlockTransform(true, "setScaleX", scaleX)
                || shouldBlockTransform(true, "setScaleY", scaleY)
                || shouldBlockTransform(true, "setTranslationX", translationX)
                || shouldBlockTransform(true, "setTranslationY", translationY));
    }

    /**
     * A YFloat-owned Google LensientActivity may outlive YFloat's result
     * dialog. Once its specific token has been revoked, it must no longer
     * accept drawing/search gestures. Never close a normal Google session.
     */
    static boolean shouldFinishStaleOwnedLens(boolean markedOwner,
            boolean sessionTokenPresent,boolean leaseStillOwned,
            boolean lensientActivity,boolean alreadyFinishing) {
        return markedOwner && sessionTokenPresent && !leaseStillOwned
                && lensientActivity && !alreadyFinishing;
    }

    /**
     * A previously owned YFloat CTS surface must be closed after its lease
     * expires, unless a replacement session is already armed in Google.
     */
    static boolean shouldCloseRevokedOwnedSession(boolean owned,
            boolean tokenPresent,boolean leaseStillOwned,boolean replacementArmed) {
        return owned && tokenPresent && !leaseStillOwned && !replacementArmed;
    }

    private GoogleV2ScenePolicy() { }
}
