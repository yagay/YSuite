package com.yagay.YFloat;

/**
 * Pure reducer for one YFloat-owned Google CTS session.
 *
 * App IPC, screenshot UI, accessibility navigation and LSPosed callbacks must
 * not each invent their own terminal state. Only explicit terminal actions
 * close a session, and late callbacks can never resurrect one.
 */
final class GoogleCtsSessionState {
    enum Phase { CREATED, ARMED, INTERCEPTED, SELECTING, COMMITTED, HANDOFF, CLOSED }
    private Phase phase = Phase.CREATED;
    private boolean hookAcknowledged;

    Phase phase() { return phase; }
    boolean acknowledged() { return hookAcknowledged; }
    boolean active() { return phase != Phase.CLOSED; }

    boolean arm() {
        if(phase != Phase.CREATED)return false;
        phase = Phase.ARMED;
        return true;
    }

    boolean acknowledge() {
        if(!active())return false;
        hookAcknowledged = true;
        if(phase == Phase.ARMED)phase = Phase.INTERCEPTED;
        return true;
    }

    boolean selecting() {
        if(!active()||phase == Phase.COMMITTED||phase == Phase.HANDOFF)return false;
        phase = Phase.SELECTING;
        return true;
    }

    boolean commitResult() {
        if(phase != Phase.SELECTING)return false;
        phase = Phase.COMMITTED;
        return true;
    }

    boolean handoffVisible() {
        if(phase != Phase.COMMITTED)return false;
        phase = Phase.HANDOFF;
        return true;
    }

    /** Leaving Google while result UI is opening is not user cancellation. */
    boolean mayCancelOnNavigation() {
        return active() && phase != Phase.COMMITTED && phase != Phase.HANDOFF;
    }

    boolean close() {
        if(!active())return false;
        phase = Phase.CLOSED;
        return true;
    }
}
