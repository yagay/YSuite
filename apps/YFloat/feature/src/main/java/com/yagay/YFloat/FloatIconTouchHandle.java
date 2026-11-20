package com.yagay.YFloat;

import android.content.Context;
import android.view.MotionEvent;
import android.view.View;

/**
 * Invisible FL-style edge handle that enlarges the touch target of a partially hidden float icon.
 *
 * FL keeps a separate invisible edge handle beside the visible icon. The current geometry uses
 * that helper at roughly 0.5x icon width and 1.16x icon height. This view mirrors that role without
 * changing the visible icon size: it forwards the touch stream to the real FloatIconView while
 * remapping local coordinates to the real owner's current window origin.
 */
final class FloatIconTouchHandle extends View {
    private final FloatIconView target;
    private final int[] targetLocation = new int[2];
    private boolean blockedMismatchedGesture;

    FloatIconTouchHandle(Context context, FloatIconView target) {
        super(context);
        this.target = target;
        setClickable(true);
        setFocusable(false);
        setWillNotDraw(true);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (target == null || event == null) return false;
        // Rehosting can detach before UP/CANCEL; never block the next gesture.
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            blockedMismatchedGesture = false;
        }
        if (blockedMismatchedGesture) {
            if (event.getActionMasked() == MotionEvent.ACTION_UP
                    || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                blockedMismatchedGesture = false;
            }
            return true;
        }
        // Only DOWN needs local coordinates relative to the real icon:
        // SelectionPointTransformer captures the initial window origin
        // once. Every subsequent MOVE/UP is processed using RAW screen
        // coordinates. Repeated getLocationOnScreen() while that icon is
        // being moved forces needless view-transform work on the UI thread.
        if (event.getActionMasked() != MotionEvent.ACTION_DOWN) {
            return target.onTouchEvent(event);
        }
        MotionEvent forwarded = MotionEvent.obtainNoHistory(event);
        try {
            target.getLocationOnScreen(targetLocation);
            FloatService service = FloatService.get();
            if (service != null && service.isAccessibilityIconHosted(target)
                    && FlOverlayLayerPolicy.touchOutsideIconBounds(
                        event.getRawX(), event.getRawY(),
                        targetLocation[0], targetLocation[1],
                        target.getWidth(), target.getHeight())) {
                // A bad accessibility touch hit must not start OCR/drag.
                // Restore the stable window host instead.
                service.onIconTouchOriginMismatch(target, event.getRawX(),
                        event.getRawY(), targetLocation[0], targetLocation[1]);
                blockedMismatchedGesture = true;
                return true;
            }
            forwarded.setLocation(
                    event.getRawX() - targetLocation[0],
                    event.getRawY() - targetLocation[1]);
            DiagnosticLog.i(getContext(), "FL_HANDLE", "DOWN raw="
                    + Math.round(event.getRawX()) + "," + Math.round(event.getRawY())
                    + " owner=" + targetLocation[0] + "," + targetLocation[1]
                    + " local=" + Math.round(forwarded.getX()) + "," + Math.round(forwarded.getY()));
            return target.onTouchEvent(forwarded);
        } finally {
            forwarded.recycle();
        }
    }
}
