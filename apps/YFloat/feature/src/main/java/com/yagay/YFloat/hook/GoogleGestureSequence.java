package com.yagay.YFloat.hook;

/**
 * Monotonic Android-uptime gesture reducer shared by every obfuscated
 * FrozenImageView touch wrapper. Pure Java so its invariants are unit tested.
 */
final class GoogleGestureSequence {
    enum Transition { IGNORE, START, MOVE, END }

    private static final long FUTURE_SKEW_MS = 5_000L;
    private static final long MAX_AGE_MS = 90_000L;
    private long activeDown = -1;
    private long lastEvent = -1;
    private int lastAction = -1;

    synchronized Transition accept(int action, long downTime, long eventTime, long uptimeNow) {
        if (downTime < 0 || eventTime < downTime
                || eventTime > uptimeNow + FUTURE_SKEW_MS
                || downTime > uptimeNow + FUTURE_SKEW_MS
                || uptimeNow - eventTime > MAX_AGE_MS) {
            return Transition.IGNORE;
        }
        if (downTime == activeDown && lastEvent == eventTime && lastAction == action) {
            return Transition.IGNORE;
        }
        // ACTION_DOWN begins a new sequence; an UP/CANCEL from an older token
        // cannot terminate a newer drag.
        if (action == 0 /* ACTION_DOWN */) {
            activeDown = downTime;
            lastEvent = eventTime;
            lastAction = action;
            return Transition.START;
        }
        if (activeDown < 0 || activeDown != downTime) return Transition.IGNORE;
        if (eventTime < lastEvent) return Transition.IGNORE;
        lastEvent = eventTime;
        lastAction = action;
        if (action == 2 /* ACTION_MOVE */) return Transition.MOVE;
        if (action == 1 /* ACTION_UP */ || action == 3 /* ACTION_CANCEL */) {
            activeDown = -1;
            return Transition.END;
        }
        return Transition.IGNORE;
    }

    synchronized void reset() {
        activeDown = -1;
        lastEvent = -1;
        lastAction = -1;
    }
}
