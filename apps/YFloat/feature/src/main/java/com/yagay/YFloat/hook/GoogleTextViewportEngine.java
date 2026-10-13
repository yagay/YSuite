package com.yagay.YFloat.hook;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewTreeObserver;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import io.github.libxposed.api.XposedModule;

/**
 * Sole transform owner of Google's native FrozenImageView while the user edits
 * OCR text. Intercepts source View property setters *before* they animate the
 * screenshot, with a single pre-draw guard for RenderNode animations that
 * bypass Java setters. The screenshot/selection presenter never writes these
 * properties. Region selection and normal Google launches are untouched.
 */
final class GoogleTextViewportEngine {
    private static final String FROZEN = GoogleLens1758Profile.FROZEN_IMAGE_VIEW;
    private final XposedModule module;
    private final BooleanSupplier active;
    private final BooleanSupplier textActive;
    private final Supplier<Activity> activity;
    private final BiConsumer<String,String> report;
    private final Handler main = new Handler(Looper.getMainLooper());
    private View watched;
    private ViewTreeObserver.OnPreDrawListener guard;
    private long lastReportMs;

    GoogleTextViewportEngine(XposedModule module, BooleanSupplier active,
                             BooleanSupplier textActive, Supplier<Activity> activity,
                             BiConsumer<String,String> report) {
        this.module = module;
        this.active = active;
        this.textActive = textActive;
        this.activity = activity;
        this.report = report;
    }

    /** Install once per target Google process, not on every session start. */
    int install() {
        int count = 0;
        for (String name : new String[]{"setScaleX", "setScaleY",
                "setTranslationX", "setTranslationY"}) {
            try {
                Method method = View.class.getMethod(name, float.class);
                module.hook(method).intercept(chain -> {
                    Object receiver = chain.getThisObject();
                    if (receiver instanceof View view && eligible(view)) {
                        Object parameter = chain.getArg(0);
                        if (parameter instanceof Float requested) {
                            // Guard only the single, verified FrozenImageView.
                            // Never suppress the rest of Google's View hierarchy.
                            if (Math.abs(requested - expected(name)) > 0.0005f) {
                                throttled("setter_blocked=" + name
                                        + " requested=" + requested);
                                return null;
                            }
                        }
                    }
                    return chain.proceed();
                });
                count++;
            } catch (Throwable t) {
                report.accept("GOOGLE_TEXT_VIEWPORT_CAPABILITY",
                        "setter=" + name + " unavailable="
                                + t.getClass().getSimpleName());
            }
        }
        report.accept("GOOGLE_TEXT_VIEWPORT_CAPABILITY",
                "stable_java_setters=" + count + "/4 predraw_fallback=true");
        return count;
    }

    void onOwnerAvailable() { main.post(this::ensureAttached); }

    void onTextSelection() { main.post(this::ensureAttached); }

    void reset() { main.post(this::detach); }

    private boolean eligible(View view) {
        return active.getAsBoolean() && textActive.getAsBoolean()
                && FROZEN.equals(view.getClass().getName())
                && watched == view;
    }

    private static float expected(String name) {
        return name.startsWith("setScale") ? 1f : 0f;
    }

    private void ensureAttached() {
        if (!active.getAsBoolean()) { detach(); return; }
        Activity owner = activity.get();
        if (owner == null || owner.getWindow() == null) return;
        View target = GoogleLensViewIntrospection.findByClassName(
                owner.getWindow().getDecorView(), FROZEN);
        if (target == null) return;
        if (watched == target && guard != null) return;
        detach();
        watched = target;
        guard = () -> {
            if (!active.getAsBoolean()) {
                detach();
                return true;
            }
            if (eligible(target)) normalize(target);
            return true;
        };
        ViewTreeObserver tree = target.getViewTreeObserver();
        if (tree.isAlive()) tree.addOnPreDrawListener(guard);
        report.accept("GOOGLE_TEXT_VIEWPORT_OWNER",
                "attached frozen=" + target.getClass().getName());
    }

    private void normalize(View target) {
        float sx = target.getScaleX(), sy = target.getScaleY();
        float tx = target.getTranslationX(), ty = target.getTranslationY();
        if (!GoogleTextViewportPolicy.shouldNormalize(true, sx, sy, tx, ty)) return;
        try {
            target.animate().cancel();
            target.clearAnimation();
            // The direct setters with canonical values are allowed by the Hook.
            target.setScaleX(1f);
            target.setScaleY(1f);
            target.setTranslationX(0f);
            target.setTranslationY(0f);
            throttled("fallback normalized scale=" + sx + "," + sy
                    + " translation=" + tx + "," + ty);
        } catch (Throwable t) {
            throttled("normalize_exception=" + t.getClass().getSimpleName());
        }
    }

    private void throttled(String detail) {
        long now = SystemClock.elapsedRealtime();
        if (now - lastReportMs < 320L) return;
        lastReportMs = now;
        report.accept("GOOGLE_TEXT_VIEWPORT_STABLE", detail);
    }

    private void detach() {
        View previous = watched;
        ViewTreeObserver.OnPreDrawListener listener = guard;
        watched = null;
        guard = null;
        if (previous != null && listener != null) {
            try {
                ViewTreeObserver tree = previous.getViewTreeObserver();
                if (tree.isAlive()) tree.removeOnPreDrawListener(listener);
            } catch (Throwable ignored) { }
        }
    }
}
