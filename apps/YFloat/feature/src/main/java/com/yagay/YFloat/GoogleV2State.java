package com.yagay.YFloat;

/**
 * V2 Google CTS reducer. Only this state machine owns the user intent and
 * lifecycle; timestamps, obsolete callbacks and background frame delivery
 * cannot replace a committed result.
 */
final class GoogleV2State {
    enum Phase { SELECTING, TEXT, REGION, AWAITING_FRAME, DELIVERED, CLOSED }
    Phase phase = Phase.SELECTING;
    int revision;
    boolean dragging;
    String text = "";
    int left, top, right, bottom;
    boolean frameReady;

    boolean text(String value, int l, int t, int r, int b) {
        // A committed region intent owns this session. Google may still emit
        // late OCR word callbacks during region drawing; they must never
        // reopen the text menu or steal the screenshot confirmation.
        if (terminal() || phase == Phase.AWAITING_FRAME || phase == Phase.REGION
                || value == null || value.isBlank()) return false;
        if (phase != Phase.TEXT) dragging = false;
        phase = Phase.TEXT;
        text = value.trim();
        // New text without measured bounds must not inherit a stale selection.
        if(r>l&&b>t)setBounds(l,t,r,b);
        else left=top=right=bottom=0;
        ++revision;
        return true;
    }
    boolean region(int l,int t,int r,int b) {
        if (terminal() || phase == Phase.AWAITING_FRAME || r <= l || b <= t)
            return false;
        if (phase != Phase.REGION) dragging = false;
        phase = Phase.REGION;
        text = "";
        setBounds(l,t,r,b);
        ++revision;
        return true;
    }
    void bounds(int l,int t,int r,int b) {
        if (terminal() || r <= l || b <= t) return;
        setBounds(l,t,r,b);
        ++revision;
    }
    boolean drag(boolean value) {
        if (terminal() || phase == Phase.AWAITING_FRAME) return false;
        boolean changed = dragging != value;
        dragging = value;
        if (changed) ++revision;
        return changed;
    }
    boolean confirm() {
        if (phase != Phase.REGION || !validBounds()) return false;
        phase = Phase.AWAITING_FRAME;
        dragging = false;
        ++revision;
        return true;
    }
    boolean canDeliver() { return phase == Phase.AWAITING_FRAME && frameReady; }
    boolean delivered() {
        if (!canDeliver()) return false;
        phase = Phase.DELIVERED; ++revision;
        return true;
    }
    void close() { phase = Phase.CLOSED; ++revision; }
    boolean terminal() { return phase == Phase.DELIVERED || phase == Phase.CLOSED; }
    boolean validBounds() { return right > left && bottom > top; }
    void setBounds(int l,int t,int r,int b) {
        if (r > l && b > t) { left=l;top=t;right=r;bottom=b; }
    }
}
