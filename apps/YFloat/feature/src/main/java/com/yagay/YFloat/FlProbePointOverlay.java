package com.yagay.YFloat;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PointF;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;

/** YFloat probe point; uses FL selection geometry. */
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
    private long lastSlowUpdateLogAt;
    private final OverlayFrameScheduler moveUpdates;
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
                sizePx,
                sizePx,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_SPLIT_TOUCH,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = -sizePx;
        lp.y = 0;
        moveUpdates = new OverlayFrameScheduler(view, () -> {
            if (!attached) return;
            long start = SystemClock.uptimeMillis();
            windowHost.update(view, lp, "probe");
            long elapsed = SystemClock.uptimeMillis() - start;
            long now = SystemClock.uptimeMillis();
            if (elapsed >= 16L && now - lastSlowUpdateLogAt >= 1_000L
                    && DiagnosticLog.enabled(context)) {
                lastSlowUpdateLogAt = now;
                DiagnosticLog.i(context, "FL_PROBE_PERF",
                        "slow_window_update_ms=" + elapsed +
                        " host=" + (windowHost.isAccessibilityHosted(view)
                                ? "accessibility" : "application"));
            }
        });
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
        targetX = Math.round(screenX - sizePx / 2f);
        targetY = Math.round(screenY - sizePx / 2f);
        if (!attached) {
            lp.x = targetX;
            lp.y = targetY;
            // The persistent float icon is already an application overlay.
            // Prefer that host for the 60 fps probe; an Accessibility
            // overlay crosses the service bridge on every position update.
            attached = windowHost.addStable(view, lp, "probe");
            if (attached) {
                visible = true;
                DiagnosticLog.i(context, "FL_PROBE_VIEW",
                        "ATTACH size=" + sizePx + " window=" + lp.x + "," + lp.y
                                + " state=" + state
                                + " accessibilityHost=" + windowHost.isAccessibilityHosted());
            }
        } else {
            if (!visible) {
                visible = true;
                view.setVisibility(View.VISIBLE);
            }
            if (lp.x != targetX || lp.y != targetY) {
                lp.x = targetX;
                lp.y = targetY;
                moveUpdates.request();
            }
        }
        float cx = targetX + sizePx / 2f;
        float cy = targetY + sizePx / 2f;
        // Diagnostics must not format/log every high-frequency touch event.
        // Keep periodic positions without creating work on the hot path.
        long now = SystemClock.uptimeMillis();
        if (now - lastMoveLogAt >= 350L && DiagnosticLog.enabled(context)) {
            lastMoveLogAt = now;
            DiagnosticLog.i(context, "FL_PROBE_VIEW",
                    "MOVE centre=" + Math.round(cx) + "," + Math.round(cy)
                            + " window=" + targetX + "," + targetY + " state=" + state);
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
        moveUpdates.cancel();
        visible = false;
        if (attached) windowHost.remove(view, "probe");
        attached = false;
    }

    private static final class ProbeView extends View {
        private final Paint plus = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
        private SelectionVisualState state = SelectionVisualState.TRACKING;

        ProbeView(Context c) {
            super(c);
            plus.setStyle(Paint.Style.STROKE);
            plus.setStrokeCap(Paint.Cap.SQUARE);
            float stroke = YFloatOverlayStyle.selectionStroke(c);
            plus.setStrokeWidth(stroke);
            // Lightweight GPU-friendly backing stroke instead of software
            // shadow rendering on a moving video overlay.
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

        private void updateColor() {
            plus.setColor(SelectionVisuals.frameColor(state));
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            float arm = Math.min(getWidth(), getHeight()) * .34f;
            canvas.drawLine(cx - arm, cy, cx + arm, cy, glow);
            canvas.drawLine(cx, cy - arm, cx, cy + arm, glow);
            canvas.drawLine(cx - arm, cy, cx + arm, cy, plus);
            canvas.drawLine(cx, cy - arm, cx, cy + arm, plus);
        }
    }
}
