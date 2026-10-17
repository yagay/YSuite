package com.yagay.YFloat;

/**
 * A Google process with an older LSPosed module generation cannot hot-load
 * the new YFloat text-selection Hook. Do not launch an OCR experiment which
 * could read the crop but cannot produce the authenticated callback.
 *
 * No Google target process yet running is NOT proof of staleness; allow Android
 * to start Google normally, then verify Hook readiness at runtime.
 */
final class GoogleLensHookReadinessPolicy {
    static boolean requiresRestart(boolean googleScoped, boolean googleLoaded,
                                   boolean googleStale) {
        return googleScoped && googleLoaded && googleStale;
    }

    private GoogleLensHookReadinessPolicy() {}
}
