package com.yagay.YFloat.hook;

import com.yagay.YFloat.GoogleOverlayAppearance;

import com.yagay.YFloat.CanonicalFramePolicy;
import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.ImageView;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Session-scoped immutable visual source for YFloat-owned Google CTS sessions.
 *
 * <p>Google may mutate FrozenImageView's internal viewport even while the outer View still reports
 * scale=1/translation=0. This presenter keeps a private copy of the best full-screen frame and
 * inserts it immediately above FrozenImageView but below Google's later selection/chrome siblings.
 * Google continues owning touch/selection logic; its mutable screenshot presentation is no longer
 * the pixels the user sees.</p>
 */
final class GoogleCanonicalFrameLayer {
    private static final long[] PRESENT_RETRY_MS = {0L, 16L, 64L, 160L, 320L};

    private final BooleanSupplier active;
    private final Supplier<Activity> activity;
    private final BiConsumer<String, String> reporter;
    private final Supplier<GoogleOverlayAppearance> overlayAppearance;
    private final Handler main = new Handler(Looper.getMainLooper());
    private Bitmap canonicalFrame;
    private final List<Bitmap> retiredFrames = new ArrayList<>();
    private WeakReference<ImageView> layerRef = new WeakReference<>(null);
    private WeakReference<SelectionView> selectionRef = new WeakReference<>(null);
    private WeakReference<ViewGroup> parentRef = new WeakReference<>(null);
    private final List<PointF> gesturePoints = new ArrayList<>();
    private boolean gestureInProgress;
    private Rect textSelectionBounds;

    GoogleCanonicalFrameLayer(BooleanSupplier active,
                              Supplier<Activity> activity,
                              BiConsumer<String, String> reporter,
                              Supplier<GoogleOverlayAppearance> overlayAppearance) {
        this.active = active;
        this.activity = activity;
        this.reporter = reporter;
        this.overlayAppearance = overlayAppearance;
    }

    void offer(Bitmap candidate) {
        if (!active.getAsBoolean() || !usable(candidate)) return;

        Bitmap accepted = null;
        Bitmap old = null;
        synchronized (this) {
            if (!active.getAsBoolean()) return;
            if (canonicalFrame != null && !canonicalFrame.isRecycled()
                    && !CanonicalFramePolicy.shouldReplace(
                    canonicalFrame.getWidth(), canonicalFrame.getHeight(),
                    candidate.getWidth(), candidate.getHeight())) {
                reporter.accept("GOOGLE_CANONICAL_FRAME",
                        "candidate_ignored existing=" + canonicalFrame.getWidth() + "x"
                                + canonicalFrame.getHeight()
                                + " candidate=" + candidate.getWidth() + "x"
                                + candidate.getHeight());
                return;
            }
            try {
                accepted = candidate.copy(Bitmap.Config.ARGB_8888, false);
            } catch (Throwable ignored) {
                accepted = null;
            }
            if (!usable(accepted)) return;
            old = canonicalFrame;
            canonicalFrame = accepted;
            if (usable(old)) retiredFrames.add(old);
        }

        reporter.accept("GOOGLE_CANONICAL_FRAME",
                "accepted size=" + accepted.getWidth() + "x" + accepted.getHeight());
        schedulePresent();
    }

    void updateSelection(Rect screenBounds, boolean regionSelection, String text) {
        synchronized (this) {
            if (screenBounds != null && !screenBounds.isEmpty()) {
                gesturePoints.clear();
                gestureInProgress = false;
            }

            // Never mirror a text selection into Google's RegionView state. RegionView is a real
            // screenshot-region editor; driving it makes the text box draggable and can steal text
            // gestures. Keep the text shell purely visual in our non-interactive SelectionView.
            if (!regionSelection
                    && screenBounds != null
                    && !screenBounds.isEmpty()
                    && text != null
                    && !text.isBlank()) {
                textSelectionBounds = new Rect(screenBounds);
            } else {
                textSelectionBounds = null;
            }
        }
        if (!attached()) schedulePresent(); // Render the mask even without screenshots.
        main.post(() -> {
            SelectionView view = selectionRef.get();
            if (view != null) view.invalidate();
        });
        reporter.accept("GOOGLE_CANONICAL_SELECTION",
                "screenBounds=" + String.valueOf(screenBounds)
                        + " region=" + regionSelection
                        + " textLen=" + (text == null ? 0 : text.length())
                        + " frameRenderer=yfloat_frame_mask");
    }

    void updateLiveTextSelection(Rect screenBounds) {
        if (screenBounds == null || screenBounds.isEmpty()) return;
        synchronized (this) {
            textSelectionBounds = new Rect(screenBounds);
            // Text dragging must never leave a stale diagonal gesture trail.
            gesturePoints.clear();
            gestureInProgress = false;
        }
        if (!attached()) schedulePresent();
        main.post(() -> {
            SelectionView view = selectionRef.get();
            if (view != null) view.invalidate();
        });
    }

    void onGesturePoint(int action, float x, float y) {
        synchronized (this) {
            if (action == MotionEvent.ACTION_DOWN) {
                gesturePoints.clear();
                gestureInProgress = Float.isFinite(x) && Float.isFinite(y);
                if (gestureInProgress) gesturePoints.add(new PointF(x, y));
            } else if (action == MotionEvent.ACTION_MOVE && gestureInProgress
                    && Float.isFinite(x) && Float.isFinite(y)) {
                if (gesturePoints.size() >= 192) gesturePoints.remove(0);
                gesturePoints.add(new PointF(x, y));
            } else if (action == MotionEvent.ACTION_UP
                    || action == MotionEvent.ACTION_CANCEL) {
                // Never connect the next gesture to a previous endpoint, or draw
                // orphan terminal events emitted by another hooked touch method.
                gestureInProgress = false;
                gesturePoints.clear();
            }
        }
        main.post(() -> {
            SelectionView view = selectionRef.get();
            if (view != null) view.invalidate();
        });
    }

    boolean attached() {
        ImageView layer = layerRef.get();
        SelectionView selection = selectionRef.get();
        return selection != null && selection.getParent() != null;
    }

    void onActivityAvailable() {
        if (!active.getAsBoolean()) return;
        schedulePresent();
    }

    void reset() {
        Bitmap current;
        List<Bitmap> retired;
        synchronized (this) {
            current = canonicalFrame;
            canonicalFrame = null;
            retired = new ArrayList<>(retiredFrames);
            retiredFrames.clear();
            gesturePoints.clear();
            gestureInProgress = false;
            textSelectionBounds = null;
        }
        main.post(() -> {
            // Detach the ImageView before recycling any bitmap it may still reference.
            removeLayerOnMain();
            recycle(current);
            for (Bitmap bitmap : retired) recycle(bitmap);
        });
    }

    private void schedulePresent() {
        for (long delay : PRESENT_RETRY_MS) {
            if (delay == 0L) main.post(this::presentOnMain);
            else main.postDelayed(this::presentOnMain, delay);
        }
    }

    private void presentOnMain() {
        if (!active.getAsBoolean()) {
            removeLayerOnMain();
            return;
        }
        Activity owner = activity.get();
        Bitmap frame;
        synchronized (this) {
            frame = canonicalFrame;
        }
        // The selection mask and border are independent of screenshot transport.
        // Voice screenshots can arrive from a different Google process (or not at all).
        if (owner == null || owner.getWindow() == null) return;

        View root = owner.getWindow().getDecorView();
        View frozen = GoogleLensViewIntrospection.findByClassName(
                root, GoogleLens1758Profile.FROZEN_IMAGE_VIEW);
        if (frozen == null || frozen.getWidth() <= 0 || frozen.getHeight() <= 0) return;

        ViewParent rawParent = frozen.getParent();
        if (!(rawParent instanceof ViewGroup parent)) return;
        int frozenIndex = parent.indexOfChild(frozen);
        if (frozenIndex < 0) return;

        ImageView existing = layerRef.get();
        SelectionView existingSelection = selectionRef.get();
        ViewGroup previousParent = parentRef.get();
        boolean frameReady = usable(frame);
        if (existingSelection != null && previousParent == parent
                && existingSelection.getParent() == parent
                && (!frameReady || (existing != null && existing.getParent() == parent))) {
            if (frameReady && existing != null) {
                existing.setImageBitmap(frame);
                normalizeLayer(existing, frozen);
            }
            // Never let a later Google child cover the YFloat selection mask.
            existingSelection.bringToFront();
            existingSelection.invalidate();
            return;
        }

        removeLayerOnMain();
        ImageView layer = null;
        ViewGroup.LayoutParams params;
        if (parent instanceof FrameLayout) {
            params = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
        } else {
            params = new ViewGroup.LayoutParams(
                    Math.max(1, frozen.getWidth()),
                    Math.max(1, frozen.getHeight()));
        }

        if (frameReady) {
            layer = new ImageView(owner);
            layer.setTag("yfloat_google_canonical_frame");
            layer.setScaleType(ImageView.ScaleType.FIT_XY);
            layer.setImageBitmap(frame);
            layer.setClickable(false);
            layer.setFocusable(false);
            layer.setEnabled(false);
            layer.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            layer.setContentDescription(null);
            layer.setAlpha(1f);
        }

        SelectionView selectionLayer = new SelectionView(owner);
        selectionLayer.setTag("yfloat_google_selection");
        selectionLayer.setClickable(false);
        selectionLayer.setFocusable(false);
        selectionLayer.setEnabled(false);
        selectionLayer.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        try {
            if (layer != null) {
                // Keep Google's native OCR and selection handles on top of the
                // frozen bitmap. The YFloat mask itself is the highest child.
                parent.addView(layer, Math.min(parent.getChildCount(), frozenIndex + 1),
                        params);
            }
            parent.addView(selectionLayer, parent.getChildCount(), params);
        } catch (Throwable t) {
            try {
                if (layer != null && layer.getParent() instanceof ViewGroup actual) {
                    actual.removeView(layer);
                }
            } catch (Throwable ignored) { }
            reporter.accept("GOOGLE_CANONICAL_LAYER",
                    "attach_failed parent=" + parent.getClass().getName()
                            + " error=" + t.getClass().getSimpleName());
            return;
        }

        layerRef = new WeakReference<>(layer);
        selectionRef = new WeakReference<>(selectionLayer);
        parentRef = new WeakReference<>(parent);
        if (layer != null) normalizeLayer(layer, frozen);
        reporter.accept("GOOGLE_CANONICAL_LAYER",
                "attached frame=" + (frameReady
                        ? frame.getWidth() + "x" + frame.getHeight() : "none")
                        + " selectionOverlay=independent"
                        + " frozen=" + frozen.getWidth() + "x" + frozen.getHeight()
                        + " parent=" + parent.getClass().getName()
                        + " frozenIndex=" + frozenIndex
                        + " layerIndex=" + (layer == null ? -1 : parent.indexOfChild(layer))
                        + " selectionIndex=" + parent.indexOfChild(selectionLayer)
                        + " siblings=" + parent.getChildCount());
    }

    private void normalizeLayer(ImageView layer, View frozen) {
        if (layer == null || frozen == null) return;
        layer.setScaleX(1f);
        layer.setScaleY(1f);
        layer.setTranslationX(0f);
        layer.setTranslationY(0f);
        ViewGroup.LayoutParams lp = layer.getLayoutParams();
        if (lp != null && (lp.width != ViewGroup.LayoutParams.MATCH_PARENT
                || lp.height != ViewGroup.LayoutParams.MATCH_PARENT)
                && frozen.getWidth() > 0 && frozen.getHeight() > 0) {
            lp.width = frozen.getWidth();
            lp.height = frozen.getHeight();
            layer.setLayoutParams(lp);
        }
    }

    private void removeLayerOnMain() {
        ImageView layer = layerRef.get();
        SelectionView selection = selectionRef.get();
        ViewGroup parent = parentRef.get();
        layerRef = new WeakReference<>(null);
        selectionRef = new WeakReference<>(null);
        parentRef = new WeakReference<>(null);
        try {
            if (selection != null && selection.getParent() instanceof ViewGroup actual) {
                actual.removeView(selection);
            } else if (selection != null && parent != null) {
                parent.removeView(selection);
            }
        } catch (Throwable ignored) { }
        try {
            if (layer != null && layer.getParent() instanceof ViewGroup actual) {
                actual.removeView(layer);
            } else if (layer != null && parent != null) {
                parent.removeView(layer);
            }
        } catch (Throwable ignored) { }
        try { if (layer != null) layer.setImageDrawable(null); } catch (Throwable ignored) { }
    }

    private final class SelectionView extends View {
        private final Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint colorGlow = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint scrim = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint trail = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF localSelection = new RectF();
        private final float cornerRadius;
        private final GoogleOverlayAppearance appearance;

        SelectionView(Activity context) {
            super(context);
            float density = Math.max(1f, getResources().getDisplayMetrics().density);
            GoogleOverlayAppearance loaded = GoogleOverlayAppearance.defaults();
            try {
                if (overlayAppearance != null) loaded = overlayAppearance.get();
            } catch (Throwable ignored) { }
            appearance = loaded == null ? GoogleOverlayAppearance.defaults() : loaded;

            // YFloat selection frame: 1dp white rounded outline with a soft colored outer glow.
            cornerRadius = appearance.radiusDp * density;
            border.setStyle(Paint.Style.STROKE);
            border.setStrokeWidth(appearance.outlineDp * density);
            border.setColor(appearance.yuiColor ? appearance.accentColor : Color.WHITE);
            border.clearShadowLayer();

            // Colored shadow only. Blur.OUTER suppresses the source stroke itself so this paint
            // behaves like a halo rather than becoming a second colored border.
            colorGlow.setStyle(Paint.Style.STROKE);
            colorGlow.setStrokeWidth(appearance.glowWidthDp * density);
            colorGlow.setStrokeCap(Paint.Cap.ROUND);
            colorGlow.setStrokeJoin(Paint.Join.ROUND);
            colorGlow.setAlpha(appearance.glowAlpha);
            colorGlow.setMaskFilter(
                    new BlurMaskFilter(Math.max(0.01f, appearance.glowBlurDp * density), BlurMaskFilter.Blur.OUTER));

            // Dim only outside the YFloat frame. The selected text area stays untouched.
            scrim.setStyle(Paint.Style.FILL);
            scrim.setColor(Color.argb(appearance.scrimAlpha, 0, 0, 0));

            trail.setStyle(Paint.Style.STROKE);
            trail.setStrokeCap(Paint.Cap.ROUND);
            trail.setStrokeJoin(Paint.Join.ROUND);
            trail.setStrokeWidth(appearance.trailWidthDp * density);
            trail.setColor(appearance.yuiColor ? appearance.accentColor : Color.WHITE);
            trail.setShadowLayer(1.5f * density, 0f, 0f, 0xAA000000);

            setLayerType(LAYER_TYPE_SOFTWARE, null);
            setBackgroundColor(Color.TRANSPARENT);
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            Rect bounds;
            List<PointF> points;
            synchronized (GoogleCanonicalFrameLayer.this) {
                bounds = textSelectionBounds == null
                        ? null : new Rect(textSelectionBounds);
                points = new ArrayList<>(gesturePoints);
            }

            int[] origin = new int[2];
            try { getLocationOnScreen(origin); } catch (Throwable ignored) { }

            if (bounds != null && !bounds.isEmpty()) {
                localSelection.set(
                        bounds.left - origin[0],
                        bounds.top - origin[1],
                        bounds.right - origin[0],
                        bounds.bottom - origin[1]);

                localSelection.left = Math.max(0f, localSelection.left);
                localSelection.top = Math.max(0f, localSelection.top);
                localSelection.right = Math.min(getWidth(), localSelection.right);
                localSelection.bottom = Math.min(getHeight(), localSelection.bottom);

                if (!localSelection.isEmpty()) {
                    int save = canvas.save();
                    Path hole = new Path();
                    hole.addRoundRect(
                            localSelection,
                            cornerRadius,
                            cornerRadius,
                            Path.Direction.CW);
                    canvas.clipOutPath(hole);
                    canvas.drawRect(0f, 0f, getWidth(), getHeight(), scrim);
                    canvas.restoreToCount(save);

                    int[] glowColors = appearance.yuiColor
                            ? new int[]{appearance.accentColor, appearance.accentColor,
                                    appearance.accentColor, appearance.accentColor, appearance.accentColor}
                            : new int[]{0xFF4285F4, 0xFFEA4335, 0xFFFBBC04,
                                    0xFF34A853, 0xFF4285F4};
                    float[] glowStops = {0f, 0.28f, 0.52f, 0.76f, 1f};
                    Shader glowShader = new SweepGradient(
                            localSelection.centerX(),
                            localSelection.centerY(),
                            glowColors,
                            glowStops);
                    colorGlow.setShader(glowShader);
                    canvas.drawRoundRect(
                            localSelection,
                            cornerRadius,
                            cornerRadius,
                            colorGlow);
                    colorGlow.setShader(null);

                    canvas.drawRoundRect(
                            localSelection,
                            cornerRadius,
                            cornerRadius,
                            border);
                }
            }

            if (bounds == null && points.size() >= 2) {
                // Gesture callbacks provide raw screen coordinates; account for
                // the layer's actual offset in Google's view hierarchy.
                Path path = new Path();
                PointF first = points.get(0);
                path.moveTo(first.x - origin[0], first.y - origin[1]);
                for (int i = 1; i < points.size(); i++) {
                    PointF point = points.get(i);
                    path.lineTo(point.x - origin[0], point.y - origin[1]);
                }
                canvas.drawPath(path, trail);
            }
        }
    }

    private static boolean usable(Bitmap bitmap) {
        return bitmap != null && !bitmap.isRecycled()
                && bitmap.getWidth() > 0 && bitmap.getHeight() > 0;
    }

    private static void recycle(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) return;
        try { bitmap.recycle(); } catch (Throwable ignored) { }
    }
}
