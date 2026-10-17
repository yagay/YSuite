package com.yagay.YFloat.hook;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewTreeObserver;

import com.yagay.YFloat.GoogleCtsContract;

import java.lang.reflect.Method;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;

import io.github.libxposed.api.XposedModule;

/**
 * One V2 owner of the Google FrozenImageView viewport.
 *
 * Never clones an image into Google's layout and never touches normal Google
 * sessions. Blocks source View setters while an authenticated YFloat session
 * is active. A pre-draw reconciliation catches hardware animators that bypass
 * Java setters. Selection handles and the rest of Google's view tree remain
 * untouched.
 */
final class GoogleV2Viewport {
    private static final String FROZEN =
            "com.google.android.libraries.lens.view.frozenimage.FrozenImageView";
    private static final String[] PROPERTIES = {
            "setScaleX", "setScaleY", "setTranslationX", "setTranslationY"
    };

    private final XposedModule module;
    private final BooleanSupplier owned;
    private final BooleanSupplier textSelecting;
    private final BiConsumer<String, String> diagnostics;
    private final Handler ui = new Handler(Looper.getMainLooper());

    private View root;
    private View frozen;
    private ViewTreeObserver.OnPreDrawListener predraw;
    private long lastReport;

    GoogleV2Viewport(XposedModule module, BooleanSupplier owned,
                     BooleanSupplier textSelecting,
                     BiConsumer<String, String> diagnostics) {
        this.module = module;
        this.owned = owned;
        this.textSelecting = textSelecting;
        this.diagnostics = diagnostics;
    }

    int install() {
        int count = 0;
        for (String name : PROPERTIES) {
            try {
                Method method = View.class.getMethod(name, float.class);
                module.hook(method).intercept(chain -> {
                    Object obj = chain.getThisObject();
                    if (obj instanceof View view
                            && FROZEN.equals(view.getClass().getName())
                            && GoogleV2ScenePolicy.shouldProtectFrozenViewport(
                                    owned.getAsBoolean(),textSelecting.getAsBoolean())
                            && chain.getArg(0) instanceof Float requested
                            && GoogleV2ScenePolicy.shouldBlockTransform(
                                    true, name, requested)) {
                        trace("property blocked=" + name + " value=" + requested);
                        return null; // Android View property setters return void.
                    }
                    return chain.proceed();
                });
                count++;
            } catch (Throwable failure) {
                trace("property hook unavailable=" + name
                        + " reason=" + failure.getClass().getSimpleName());
            }
        }
        trace("installed native property guards=" + count + "/4");
        return count;
    }

    void attach(Activity owner) {
        if (owner == null) return;
        ui.post(() -> {
            if (!owned.getAsBoolean() || owner.isFinishing() || owner.isDestroyed()
                    || owner.getWindow() == null) return;
            View decor = owner.getWindow().getDecorView();
            if (decor == null) return;
            View target = GoogleLensViewIntrospection.findByClassName(decor, FROZEN);
            if (target == null) return;
            if (root == decor && frozen == target && predraw != null) return;

            detachOnMain();
            root = decor;
            frozen = target;
            predraw = () -> {
                if (!owned.getAsBoolean()) {
                    detachOnMain();
                    return true;
                }
                reconcile();
                return true;
            };
            ViewTreeObserver observer = decor.getViewTreeObserver();
            if (observer.isAlive()) observer.addOnPreDrawListener(predraw);
            reconcile();
            trace("attached frozen image owner=" + owner.getClass().getSimpleName());
        });
    }

    private void reconcile() {
        View image = frozen;
        if (image == null || !GoogleV2ScenePolicy.shouldProtectFrozenViewport(
                owned.getAsBoolean(),textSelecting.getAsBoolean())) return;
        float scaleX = image.getScaleX();
        float scaleY = image.getScaleY();
        float translationX = image.getTranslationX();
        float translationY = image.getTranslationY();
        if (!GoogleV2ScenePolicy.needsReconcile(
                true, scaleX, scaleY, translationX, translationY)) return;
        try {
            image.animate().cancel();
            image.clearAnimation();
            // These canonical values are explicitly allowed by the setter Hook.
            image.setScaleX(1f);
            image.setScaleY(1f);
            image.setTranslationX(0f);
            image.setTranslationY(0f);
            trace("reconciled scale=" + scaleX + "," + scaleY
                    + " translate=" + translationX + "," + translationY);
        } catch (Throwable t) {
            trace("reconciliation failed=" + t.getClass().getSimpleName());
        }
    }

    void reset() { ui.post(this::detachOnMain); }

    private void detachOnMain() {
        View previous = root;
        ViewTreeObserver.OnPreDrawListener listener = predraw;
        root = null;
        frozen = null;
        predraw = null;
        if (previous != null && listener != null) {
            try {
                ViewTreeObserver observer = previous.getViewTreeObserver();
                if (observer.isAlive()) observer.removeOnPreDrawListener(listener);
            } catch (Throwable ignored) { }
        }
    }

    private void trace(String message) {
        long now = SystemClock.elapsedRealtime();
        if (now - lastReport < 300) return;
        lastReport = now;
        if (diagnostics != null) diagnostics.accept("VIEWPORT", message);
    }
}
