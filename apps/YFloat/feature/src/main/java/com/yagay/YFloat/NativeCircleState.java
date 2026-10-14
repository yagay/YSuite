package com.yagay.YFloat;

/**
 * Single owner of Circle Native interaction state. No Google application,
 * framework View or screenshot lifecycle participates in this reducer.
 */
final class NativeCircleState {
    enum Mode { TEXT, LASSO, RECTANGLE }
    enum Phase { IDLE, DRAWING, TEXT_PENDING, TEXT_READY, IMAGE_READY, CLOSED }

    Mode mode = Mode.TEXT;
    Phase phase = Phase.IDLE;
    long revision;
    String selectedText = "";
    float left, top, right, bottom;

    boolean changeMode(Mode next) {
        if (phase == Phase.CLOSED || next == null) return false;
        mode = next;
        clear();
        return true;
    }

    void clear() {
        if (phase == Phase.CLOSED) return;
        phase = Phase.IDLE;
        selectedText = "";
        left = top = right = bottom = 0f;
        ++revision;
    }

    boolean begin(float x, float y) {
        if (phase == Phase.CLOSED) return false;
        clear();
        left = right = x;
        top = bottom = y;
        phase = Phase.DRAWING;
        return true;
    }

    void bounds(float l, float t, float r, float b) {
        if (phase == Phase.CLOSED) return;
        left = Math.min(l, r);
        top = Math.min(t, b);
        right = Math.max(l, r);
        bottom = Math.max(t, b);
        ++revision;
    }

    boolean finish(boolean valid) {
        if (phase != Phase.DRAWING) return false;
        phase = !valid ? Phase.IDLE
                : mode == Mode.TEXT ? Phase.TEXT_PENDING : Phase.IMAGE_READY;
        ++revision;
        return valid;
    }

    boolean textReady(long expectedRevision, String text, float l, float t,
                      float r, float b) {
        if (phase != Phase.TEXT_PENDING || expectedRevision != revision
                || text == null || text.isBlank()) return false;
        selectedText = text;
        left = l; top = t; right = r; bottom = b;
        phase = Phase.TEXT_READY;
        ++revision;
        return true;
    }

    boolean canFinishImage() {
        return phase == Phase.IMAGE_READY && right > left && bottom > top;
    }

    void close() {
        phase = Phase.CLOSED;
        selectedText = "";
        ++revision;
    }
}
