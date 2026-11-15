package com.yagay.YFloat;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PointF;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;

/**
 * Lightweight FL crosshair that stays in ONE fixed overlay window.
 *
 * The old 15dp moving window used updateViewLayout()/WindowManager binder
 * transactions for every pointer frame. When the window was hosted by the
 * shared AccessibilityService, those position updates competed with the icon,
 * video and SystemUI compositor. Instead, keep an always non-touchable,
 * transparent surface attached during the touch and invalidate only the
 * crosshair's previous/new dirty bounds. A high-layer accessibility token can
 * now be used without paying the per-frame accessibility-window reposition cost.
 */
public final class FlProbePointOverlay {
    private static final float FL_PROBE_SIZE_DP = 15f;
    private final Context context;
    private final FlOverlayWindowHost windowHost;
    private final int sizePx;
    private final ProbeView view;
    private final WindowManager.LayoutParams lp;
    private boolean attached;
    private boolean visible;
    private long lastMoveLogAt;
    private int targetX, targetY;
    private SelectionVisualState state = SelectionVisualState.TRACKING;

    public FlProbePointOverlay(Context c) {
        context = c.getApplicationContext();
        windowHost = new FlOverlayWindowHost(context);
        float density = Math.max(.1f, context.getResources().getDisplayMetrics().density);
        sizePx = Math.max(1, Math.round(FL_PROBE_SIZE_DP * density));
        view = new ProbeView(context);
        view.setState(state);
        lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = 0;
        lp.y = 0;
        // The coordinates supplied by SelectionPointTransformer are physical
        // screen coordinates, including status-bar and display-cutout regions.
        lp.setFitInsetsTypes(0);
        lp.setFitInsetsSides(0);
        lp.setFitInsetsIgnoringVisibility(true);
        lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
    }

    public void setTracking() { setVisualState(SelectionVisualState.TRACKING); }
    public void setReady() { setVisualState(SelectionVisualState.READY); }
    public SelectionVisualState state() { return state; }

    public void setVisualState(SelectionVisualState next) {
        if (next == null) next = SelectionVisualState.TRACKING;
        if (state == next) return;
        state = next;
        view.setState(next);
        DiagnosticLog.i(context, "FL_PROBE_VIEW", "STATE " + next);
    }

    public PointF showAt(float screenX, float screenY) {
        // Maintain the original 15dp bounding-box semantics for the separate
        // operation hint and ViewSelectionEngine. Only the actual WM surface
        // changes from a moving square into a stationary transparent canvas.
        targetX = Math.round(screenX - sizePx / 2f);
        targetY = Math.round(screenY - sizePx / 2f);
        float cx = targetX + sizePx / 2f;
        float cy = targetY + sizePx / 2f;
        view.setScreenCenter(cx, cy, sizePx);

        if (!attached) {
            attached = windowHost.addFollowingIconLayer(view, lp, "probe");
            if (attached) {
                visible = true;
                DiagnosticLog.i(context, "FL_PROBE_VIEW",
                        "ATTACH fixed_fullscreen_render_only size=" + sizePx
                                + " state=" + state
                                + " type=" + lp.type
                                + " accessibilityHost=" + windowHost.isAccessibilityHosted(view));
            }
        } else if (!visible) {
            visible = true;
            view.setVisibility(View.VISIBLE);
        }
        long now = SystemClock.uptimeMillis();
        if (now - lastMoveLogAt >= 350L && DiagnosticLog.enabled(context)) {
            lastMoveLogAt = now;
            DiagnosticLog.i(context, "FL_PROBE_VIEW",
                    "MOVE centre=" + Math.round(cx) + "," + Math.round(cy)
                            + " virtualWindow=" + targetX + "," + targetY
                            + " state=" + state + " wmUpdates=0");
        }
        return new PointF(cx, cy);
    }

    public int windowX() { return targetX; }
    public int windowY() { return targetY; }
    public int windowSizePx() { return sizePx; }
    public boolean isAttached() { return attached; }

    public void hide() {
        close();
        DiagnosticLog.i(context, "FL_PROBE_VIEW", "HIDE state=" + state);
    }

    public void close() {
        visible = false;
        if (attached) windowHost.remove(view, "probe");
        attached = false;
    }

    private static final class ProbeView extends View {
        private final Paint plus = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Rect dirty = new Rect();
        private final int[] windowOrigin = new int[2];
        private SelectionVisualState state = SelectionVisualState.TRACKING;
        private float screenCx = Float.NaN, screenCy = Float.NaN;
        private int size;

        ProbeView(Context c) {
            super(c);
            plus.setStyle(Paint.Style.STROKE);
            plus.setStrokeCap(Paint.Cap.SQUARE);
            float stroke = YFloatOverlayStyle.selectionStroke(c);
            plus.setStrokeWidth(stroke);
            glow.setStyle(Paint.Style.STROKE);
            glow.setStrokeCap(Paint.Cap.ROUND);
            glow.setStrokeWidth(stroke * 2f);
            glow.setColor(YFloatOverlayStyle.scrim(c, 0x77));
            setBackgroundColor(Color.TRANSPARENT);
            updateColor();
        }

        void setState(SelectionVisualState value) {
            if (value == null) value = SelectionVisualState.TRACKING;
            if (state == value) return;
            state = value;
            updateColor();
            invalidate();
        }

        void setScreenCenter(float x, float y, int probeSizePx) {
            if (screenCx == x && screenCy == y && size == probeSizePx) return;
            float oldX = screenCx, oldY = screenCy;
            size = probeSizePx;
            screenCx = x;
            screenCy = y;
            int radius = Math.max(8,
                    Math.round(probeSizePx * .65f + glow.getStrokeWidth() + 3f));
            if (Float.isNaN(oldX) || Float.isNaN(oldY)) {
                invalidate();
                return;
            }
            int x0 = Math.round(Math.min(oldX, x)) - windowOrigin[0] - radius;
            int y0 = Math.round(Math.min(oldY, y)) - windowOrigin[1] - radius;
            int x1 = Math.round(Math.max(oldX, x)) - windowOrigin[0] + radius;
            int y1 = Math.round(Math.max(oldY, y)) - windowOrigin[1] + radius;
            dirty.set(x0, y0, x1, y1);
            invalidate(dirty);
        }

        private void updateColor() {
            plus.setColor(SelectionVisuals.frameColor(state));
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (Float.isNaN(screenCx) || Float.isNaN(screenCy)) return;
            getLocationOnScreen(windowOrigin);
            float cx = screenCx - windowOrigin[0];
            float cy = screenCy - windowOrigin[1];
            float arm = size * .34f;
            canvas.drawLine(cx - arm, cy, cx + arm, cy, glow);
            canvas.drawLine(cx, cy - arm, cx, cy + arm, glow);
            canvas.drawLine(cx - arm, cy, cx + arm, cy, plus);
            canvas.drawLine(cx, cy - arm, cx, cy + arm, plus);
        }
    }
}
