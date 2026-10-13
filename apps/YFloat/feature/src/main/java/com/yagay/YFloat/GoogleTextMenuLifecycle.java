package com.yagay.YFloat;

/**
 * UI-independent text-menu lifecycle. Every changed text, bounds or drag event
 * invalidates earlier delayed presentation tasks. Only the latest revision
 * may render; dragging never creates a second toolbar.
 */
final class GoogleTextMenuLifecycle {
    private boolean dragging;
    private long revision;

    synchronized void onTextChanged() { ++revision; }
    synchronized boolean onGesture(boolean next) {
        boolean changed = dragging != next;
        dragging = next;
        ++revision;
        return changed;
    }
    synchronized boolean dragging() { return dragging; }
    synchronized long revision() { return revision; }
    synchronized boolean isCurrent(long value) { return revision == value; }
}
