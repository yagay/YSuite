package com.yagay.YFloat;

/** Pure token ownership policy: a stale asynchronous callback has no UI rights. */
final class GoogleV2SessionOwnership {
    static boolean accepts(String eventToken,String workflowToken,
            boolean sameStoredSession,boolean terminal) {
        return eventToken!=null&&!eventToken.isBlank()
                &&eventToken.equals(workflowToken)&&sameStoredSession&&!terminal;
    }
    private GoogleV2SessionOwnership() {}
}
