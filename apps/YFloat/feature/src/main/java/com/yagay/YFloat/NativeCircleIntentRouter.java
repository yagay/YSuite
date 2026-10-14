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
    private NativeCircleIntentRouter(){}
}
