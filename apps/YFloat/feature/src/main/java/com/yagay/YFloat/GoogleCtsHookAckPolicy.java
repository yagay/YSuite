package com.yagay.YFloat;

/** Exact-token policy for non-destructive cold-start diagnostics. */
final class GoogleCtsHookAckPolicy {
    static boolean shouldReportPending(boolean sameWorkflow,
                                    String expectedToken,String acknowledgedToken) {
        return sameWorkflow && expectedToken != null && !expectedToken.isBlank()
                && !expectedToken.equals(acknowledgedToken);
    }

    private GoogleCtsHookAckPolicy() {}
}
