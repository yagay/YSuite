package com.yagay.YFloat;

/**
 * A process version label is only a hint, not proof that live LSPosed callbacks
 * are unavailable. Always try the Google Lens experiment normally, even after
 * upgrading YFloat. Suggest a restart only after an actual Hook handshake
 * timeout while Google is demonstrably running an older module generation.
 */
final class GoogleLensHookReadinessPolicy {
    static boolean shouldSuggestRestartAfterTimeout(boolean googleScoped,
            boolean googleLoaded, boolean googleStale, boolean hookAcknowledged) {
        return googleScoped && googleLoaded && googleStale && !hookAcknowledged;
    }

    private GoogleLensHookReadinessPolicy() {}
}
