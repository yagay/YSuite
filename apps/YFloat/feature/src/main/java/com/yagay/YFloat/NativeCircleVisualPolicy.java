package com.yagay.YFloat;

/**
 * One visual contract for native Circle. The base mask proves capture mode is
 * active, but finalized image selections use a single unshaded rectangle with
 * an outside-only scrim so the crop exactly matches the result.
 */
final class NativeCircleVisualPolicy {
    static final int SELECTION_OUTSIDE_ALPHA = 112;

    // Use a thin, high-contrast border instead of an opaque text-colored
    // rectangle; the original captured glyph pixels remain unchanged.
    static final int TEXT_HIGHLIGHT_FILL_ALPHA = 48;
    static final int TEXT_HIGHLIGHT_BORDER_ALPHA = 214;

    /** Handle stems are fully outside the text glyph band. */
    static float leadingHandleX(float bandLeft,float density){
        return bandLeft-5f*density;
    }
    static float trailingHandleX(float bandRight,float density){
        return bandRight+5f*density;
    }
    static float handleStemTop(float bandBottom,float density){
        return bandBottom+1f*density;
    }
    static float handleCenterY(float bandBottom,float density){
        return bandBottom+9f*density;
    }

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
