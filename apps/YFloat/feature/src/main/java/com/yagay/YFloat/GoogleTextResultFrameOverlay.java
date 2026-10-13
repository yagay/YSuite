package com.yagay.YFloat;

import android.content.Context;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;

/**
 * Persistent, non-touchable final Google text selection shell.
 *
 * Google's in-activity selection outline disappears when Lens closes. The YFloat
 * result menu survives its workflow, so its last confirmed bounds must also be
 * owned by YFloat. Unlike the live Google layer, this window is never used to
 * handle the user's selection drag.
 */
final class GoogleTextResultFrameOverlay {
    private static final Object LOCK = new Object();
    private static FrameView frame;
    private static FlOverlayWindowHost host;
    private static String token = "";

    static boolean hasSelection() {
        synchronized (LOCK) {
            return frame != null && frame.getParent() != null;
        }
    }

    static void show(Context context, String sessionToken, Rect bounds) {
        if (context == null || sessionToken == null || sessionToken.isBlank()
                || bounds == null || bounds.isEmpty()) return;
        synchronized (LOCK) {
            dismissLocked();
            Context app = context.getApplicationContext();
            FlOverlayWindowHost nextHost = new FlOverlayWindowHost(app);
            FrameView next = new FrameView(app, bounds);
            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                            | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                    PixelFormat.TRANSLUCENT);
            params.gravity = Gravity.TOP | Gravity.START;
            if (!nextHost.addStable(next, params, "google_text_result_frame")) {
                DiagnosticLog.i(app, "GOOGLE_TEXT_FRAME",
                        "final frame not hosted token=" + shortToken(sessionToken));
                return;
            }
            frame = next;
            host = nextHost;
            token = sessionToken;
            DiagnosticLog.i(app, "GOOGLE_TEXT_FRAME",
                    "final selection hosted token=" + shortToken(token)
                            + " bounds=" + bounds);
        }
    }

    static void dismiss() {
        synchronized (LOCK) { dismissLocked(); }
    }

    private static void dismissLocked() {
        FrameView old = frame;
        FlOverlayWindowHost oldHost = host;
        frame = null;
        host = null;
        token = "";
        if (old != null && oldHost != null) oldHost.remove(old, "google_text_result_frame");
    }

    private static String shortToken(String value) {
        return value.substring(0, Math.min(8, value.length()));
    }

    private static final class FrameView extends View {
        private final Rect bounds;
        private final Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint dim = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF local = new RectF();
        private final int[] origin = new int[2];
        private final float radius;

        FrameView(Context app, Rect screenBounds) {
            super(app);
            bounds = new Rect(screenBounds);
            float density = Math.max(1f, getResources().getDisplayMetrics().density);
            radius = 10f * density;
            border.setStyle(Paint.Style.STROKE);
            border.setStrokeWidth(Math.max(1f, YFloatOverlayStyle.selectionStroke(app)));
            border.setColor(YFloatOverlayStyle.selectionColor(app));
            glow.setStyle(Paint.Style.STROKE);
            glow.setStrokeWidth(7f * density);
            glow.setColor(YFloatOverlayStyle.selectionColor(app));
            glow.setAlpha(175);
            glow.setMaskFilter(new BlurMaskFilter(5f * density, BlurMaskFilter.Blur.OUTER));
            dim.setColor(Color.argb(45, 0, 0, 0));
            setLayerType(LAYER_TYPE_SOFTWARE, null);
            setBackgroundColor(Color.TRANSPARENT);
            setClickable(false);
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (bounds.isEmpty()) return;
            getLocationOnScreen(origin);
            local.set(bounds);
            local.offset(-origin[0], -origin[1]);
            local.left = Math.max(0f, local.left);
            local.top = Math.max(0f, local.top);
            local.right = Math.min(getWidth(), local.right);
            local.bottom = Math.min(getHeight(), local.bottom);
            if (local.isEmpty()) return;
            canvas.drawRoundRect(local, radius, radius, glow);
            canvas.drawRoundRect(local, radius, radius, border);
        }
    }

    private GoogleTextResultFrameOverlay() { }
}
