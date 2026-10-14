package com.yagay.YFloat;

/** Testable Google-like gesture intent policy. */
final class NativeCircleIntentRouter {
    enum Route { NEED_OCR, IMAGE }

    static Route classify(String kind, boolean ocrReady) {
        // A closed lasso targets an image/object even when text appears inside.
        if ("CIRCLE".equals(kind)) return Route.IMAGE;
        // Taps and strokes first try OCR and may fall back to an image.
        return Route.NEED_OCR;
    }
    private NativeCircleIntentRouter(){}
}
