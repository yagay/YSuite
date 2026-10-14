package com.yagay.YFloat;

/**
 * One visual contract for native Circle. The base mask proves capture mode is
 * active, but finalized image selections use a single unshaded rectangle with
 * an outside-only scrim so the crop exactly matches the result.
 */
final class NativeCircleVisualPolicy {
    static final int SELECTION_OUTSIDE_ALPHA = 112;

    static int baseScrimAlpha(NativeCircleState.Phase phase) {
        if (phase == null) return 0;
        return switch (phase) {
            case IDLE -> 68;
            case DRAWING -> 49;
            case TEXT_READY -> 38;
            case RESOLVING, IMAGE_READY, CLOSED -> 0;
        };
    }

    static boolean showRectangle(NativeCircleState.Phase phase) {
        return phase == NativeCircleState.Phase.RESOLVING
                || phase == NativeCircleState.Phase.IMAGE_READY;
    }

    private NativeCircleVisualPolicy() { }
}
