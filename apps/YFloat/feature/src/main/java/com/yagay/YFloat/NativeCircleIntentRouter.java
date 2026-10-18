package com.yagay.YFloat;

/** Testable Google-like gesture intent policy. */
final class NativeCircleIntentRouter {
    enum Route { NEED_OCR, IMAGE }

    static Route classify(String kind, boolean ocrReady) {
        // Closed or free-form region strokes target the cropped image immediately,
        // without waiting for screen OCR or checking recognized text coverage.
        if ("CIRCLE".equals(kind) || "SCRIBBLE".equals(kind)) return Route.IMAGE;
        // Taps and horizontal text highlights first try OCR.
        return Route.NEED_OCR;
    }
    /**
     * A horizontal text highlight must never silently become a rectangular
     * screenshot, including when offline recognition is slow or returns no
     * matching characters. Taps may still open a crop as the last resort.
     */
    static boolean permitsImageFallback(String kind) {
        return !"HIGHLIGHT".equals(kind);
    }

    private NativeCircleIntentRouter(){}
}
