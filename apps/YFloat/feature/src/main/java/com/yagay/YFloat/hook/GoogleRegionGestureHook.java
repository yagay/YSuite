package com.yagay.YFloat.hook;

import android.os.SystemClock;
import android.util.Log;
import android.view.MotionEvent;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;

import io.github.libxposed.api.XposedModule;

/** Tracks real FrozenImageView touch lifecycle so YFloat confirm UI never covers refinements. */
final class GoogleRegionGestureHook {
    interface GesturePointSink {
        void onGesturePoint(int action, float x, float y, String detail);
    }

    private static final String TAG = "YFloat-GoogleCTS";

    private final XposedModule module;
    private final ClassLoader classLoader;
    private final BooleanSupplier active;
    private final BiConsumer<Boolean, String> stateSink;
    private final GesturePointSink pointSink;

    private static final long MOVE_HEARTBEAT_MS = 120L;

    static final class Binding {
        final Class<?> viewClass;
        final Method[] touchMethods;
        final int confidence;
        final String source;
        final String detail;

        Binding(Class<?> viewClass, Method[] touchMethods,
                int confidence, String source, String detail) {
            this.viewClass = viewClass;
            this.touchMethods = touchMethods == null ? new Method[0] : touchMethods;
            this.confidence = confidence;
            this.source = source == null ? "none" : source;
            this.detail = detail == null ? "" : detail;
        }

        boolean available() { return viewClass != null && touchMethods.length > 0; }
    }

    static Binding resolve(ClassLoader loader) {
        if (loader == null) return new Binding(null, null, 0, "none", "classLoader=null");
        try {
            Class<?> cls = Class.forName(
                    GoogleLens1758Profile.FROZEN_IMAGE_VIEW, false, loader);
            List<Method> methods = new ArrayList<>();
            for (Method method : GoogleReflection.declaredMethods(cls)) {
                if (motionEventIndex(method.getParameterTypes()) >= 0
                        && !java.lang.reflect.Modifier.isStatic(method.getModifiers()))
                    methods.add(method);
            }
            int frozenCount = methods.size();
            // FrozenImageView receives the initial circle gesture, but handle
            // drags are dispatched by the separate native TextSelectionView.
            // Observe its concrete touch entrypoints too; never intercept or
            // consume Google's MotionEvents.
            int textCount = 0;
            try {
                Class<?> textView = Class.forName(
                        "com.google.android.libraries.lens.common.text.selection.ui.TextSelectionView",
                        false, loader);
                for (Method method : GoogleReflection.declaredMethods(textView)) {
                    if (motionEventIndex(method.getParameterTypes()) >= 0
                            && !java.lang.reflect.Modifier.isStatic(method.getModifiers())) {
                        methods.add(method);
                        textCount++;
                    }
                }
            } catch (Throwable ignored) {
                // Later Google releases may move the text view class.
            }
            int confidence = methods.isEmpty() ? 0 : 95;
            return new Binding(cls, methods.toArray(new Method[0]), confidence,
                    "frozen-and-text-selection-views",
                    "frozenTouchMethods=" + frozenCount + " textTouchMethods=" + textCount);
        } catch (Throwable t) {
            return new Binding(null, null, 0, "none",
                    "resolve=" + t.getClass().getSimpleName());
        }
    }

    /**
     * One stream per gesture, not one state transition per obfuscated method
     * callback. Google 17.64 invokes multiple wrappers for one MotionEvent;
     * it also creates epoch-based synthetic ACTION_CANCEL events unrelated to
     * the Android uptime-based user gesture.
     */
    private final GoogleGestureSequence sequence = new GoogleGestureSequence();
    private long lastHeartbeatElapsed;

    GoogleRegionGestureHook(XposedModule module,
                            ClassLoader classLoader,
                            BooleanSupplier active,
                            BiConsumer<Boolean, String> stateSink,
                            GesturePointSink pointSink) {
        this.module = module;
        this.classLoader = classLoader;
        this.active = active;
        this.stateSink = stateSink;
        this.pointSink = pointSink;
    }

    int install() {
        return install(resolve(classLoader));
    }

    int install(Binding binding) {
        if (binding == null) binding = resolve(classLoader);
        if (!binding.available()) {
            module.log(Log.WARN, TAG,
                    "Google gesture capability unavailable: " + binding.detail);
            return 0;
        }
        int count = 0;
        for (Method method : binding.touchMethods) {
            int index = motionEventIndex(method.getParameterTypes());
            if (index < 0) continue;
            try {
                final int argIndex = index;
                module.hook(method).intercept(chain -> {
                    MotionEvent event = chain.getArg(argIndex) instanceof MotionEvent motion
                            ? motion : null;
                    if (active.getAsBoolean() && event != null) {
                        dispatchGestureState(event, method.getName());
                    }
                    return chain.proceed();
                });
                count++;
            } catch (Throwable t) {
                module.log(Log.WARN, TAG,
                        "Google gesture binding skipped method=" + method.getName(), t);
            }
        }
        module.log(Log.INFO, TAG,
                "Google gesture single-state source=" + binding.source
                        + " hooks=" + count + " detail=" + binding.detail);
        return count;
    }

    synchronized void reset() {
        sequence.reset();
        lastHeartbeatElapsed = 0L;
    }

    private synchronized void dispatchGestureState(MotionEvent event, String methodName) {
        int action = event.getActionMasked();
        long now = SystemClock.uptimeMillis();
        GoogleGestureSequence.Transition result = sequence.accept(
                action, event.getDownTime(), event.getEventTime(), now);
        if (result == GoogleGestureSequence.Transition.IGNORE) return;
        String phase = switch (result) {
            case START -> "start";
            case MOVE -> "heartbeat";
            case END -> "end";
            default -> "ignored";
        };
        String detail = describe(event, methodName, action, phase);
        if (pointSink != null) {
            try {
                pointSink.onGesturePoint(action, event.getRawX(), event.getRawY(), detail);
            } catch (Throwable ignored) { }
        }
        if (result == GoogleGestureSequence.Transition.START) {
            lastHeartbeatElapsed = now;
            stateSink.accept(true, detail);
        } else if (result == GoogleGestureSequence.Transition.MOVE) {
            if (now - lastHeartbeatElapsed >= MOVE_HEARTBEAT_MS) {
                lastHeartbeatElapsed = now;
                stateSink.accept(true, detail);
            }
        } else if (result == GoogleGestureSequence.Transition.END) {
            lastHeartbeatElapsed = 0L;
            stateSink.accept(false, detail);
        }
    }

    private static String describe(
            MotionEvent event, String methodName, int action, String phase) {
        return "phase=" + phase
                + " action=" + actionName(action)
                + " method=" + methodName
                + " pointers=" + event.getPointerCount()
                + " downTime=" + event.getDownTime()
                + " eventTime=" + event.getEventTime();
    }

    private static int motionEventIndex(Class<?>[] parameters) {
        if (parameters == null) return -1;
        for (int i = 0; i < parameters.length; i++) {
            if (MotionEvent.class.isAssignableFrom(parameters[i])) return i;
        }
        return -1;
    }

    private static String actionName(int action) {
        return switch (action) {
            case MotionEvent.ACTION_DOWN -> "DOWN";
            case MotionEvent.ACTION_MOVE -> "MOVE";
            case MotionEvent.ACTION_UP -> "UP";
            case MotionEvent.ACTION_CANCEL -> "CANCEL";
            case MotionEvent.ACTION_POINTER_DOWN -> "POINTER_DOWN";
            case MotionEvent.ACTION_POINTER_UP -> "POINTER_UP";
            default -> String.valueOf(action);
        };
    }
}
