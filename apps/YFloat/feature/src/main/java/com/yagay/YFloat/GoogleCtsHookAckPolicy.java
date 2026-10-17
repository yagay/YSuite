package com.yagay.YFloat;

/** Exact-token policy for a fail-closed, YFloat-owned CTS takeover. */
final class GoogleCtsHookAckPolicy {
    static boolean shouldFailClosed(boolean sameWorkflow,
                                    String expectedToken,String acknowledgedToken) {
        return sameWorkflow && expectedToken != null && !expectedToken.isBlank()
                && !expectedToken.equals(acknowledgedToken);
    }

    private GoogleCtsHookAckPolicy() {}
}
