package com.yagay.YFloat.hook;

/** Pure V2 scene rules: no Google obfuscated symbols or Android classes. */
final class GoogleV2ScenePolicy {
    private static final String CTS_SEARCH =
            "android.app.contextualsearch.action.LAUNCH_CONTEXTUAL_SEARCH";

    static boolean suppressNativeSearch(boolean ownedSession, String action) {
        return ownedSession && CTS_SEARCH.equals(action);
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

    static boolean needsReconcile(boolean owned, float scaleX, float scaleY,
                                  float translationX, float translationY) {
        return owned && (shouldBlockTransform(true, "setScaleX", scaleX)
                || shouldBlockTransform(true, "setScaleY", scaleY)
                || shouldBlockTransform(true, "setTranslationX", translationX)
                || shouldBlockTransform(true, "setTranslationY", translationY));
    }

    private GoogleV2ScenePolicy() { }
}
