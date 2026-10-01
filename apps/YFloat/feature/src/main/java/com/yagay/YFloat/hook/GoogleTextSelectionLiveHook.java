package com.yagay.YFloat.hook;

import android.graphics.Rect;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;

import java.lang.reflect.Method;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;

import io.github.libxposed.api.XposedModule;

/** Reads Google's in-progress text selection geometry without changing Google's model. */
final class GoogleTextSelectionLiveHook {
    private static final String TAG = "YFloat-GoogleCTS";
    private static final String TEXT_SELECTION_VIEW =
            "com.google.android.libraries.lens.common.text.selection.ui.TextSelectionView";

    /** Collapse duplicate/near-simultaneous internal callbacks before they reach the UI layer. */
    private static final long LIVE_EMIT_MIN_INTERVAL_MS = 32L;
    /** A sudden very large expansion must persist before it replaces the visible text shell. */
    private static final long LARGE_EXPANSION_STABLE_MS = 72L;
    /** A gap this long means a new drag/session and resets the live filter automatically. */
    private static final long LIVE_FILTER_RESET_GAP_MS = 1_500L;
    private static final float LARGE_EXPANSION_AREA_RATIO = 2.6f;

    private final XposedModule module;
    private final ClassLoader classLoader;
    private final BooleanSupplier active;
    private final BiConsumer<Rect, String> sink;
    private final Profile profile;

    private Rect lastPublishedBounds;
    private long lastPublishedAt;
    private Rect pendingLargeBounds;
    private long pendingLargeSince;
    private int pendingLargeHits;

    private static final class Profile {
        final String name;
        final String updateTask;
        final String controller;
        final String state;
        final String word;
        final String range;

        Profile(String name, String updateTask, String controller,
                String state, String word, String range) {
            this.name = name;
            this.updateTask = updateTask;
            this.controller = controller;
            this.state = state;
            this.word = word;
            this.range = range;
        }
    }

    GoogleTextSelectionLiveHook(
            XposedModule module,
            ClassLoader classLoader,
            BooleanSupplier active,
            BiConsumer<Rect, String> sink) {
        this.module = module;
        this.classLoader = classLoader;
        this.active = active;
        this.sink = sink;
        this.profile = resolveProfile(classLoader);
    }

    int install() {
        if (profile == null) {
            module.log(Log.WARN, TAG,
                    "Google live text selection profile unavailable for loaded Google build");
            return 0;
        }
        int count = 0;
        count += installStateListHook();
        count += installFinalTaskHook();
        module.log(Log.INFO, TAG,
                "Google live text selection hooks=" + count
                        + " profile=" + profile.name
                        + " sources=" + profile.state + ".g," + profile.updateTask + ".run");
        return count;
    }

    private int installStateListHook() {
        try {
            Class<?> stateClass = Class.forName(profile.state, false, classLoader);
            Method target = null;
            for (Method method : GoogleReflection.declaredMethods(stateClass)) {
                if (!"g".equals(method.getName())
                        || method.getParameterCount() != 3
                        || method.getReturnType() != void.class) continue;
                Class<?>[] p = method.getParameterTypes();
                if (GoogleLens1758Profile.IMMUTABLE_LIST.equals(p[0].getName())
                        && p[1] == boolean.class && p[2] == int.class) {
                    target = method;
                    break;
                }
            }
            if (target == null) {
                module.log(Log.WARN, TAG,
                        "Google live word-list hook unavailable: " + profile.state + ".g missing");
                return 0;
            }

            final String source = profile.state + ".g";
            module.hook(target).intercept(chain -> {
                Object state = chain.getThisObject();
                Object selected = chain.getArg(0);
                Object result = chain.proceed();
                if (!active.getAsBoolean() || state == null) return result;
                try {
                    publish(readStateBounds(state, selected, source));
                } catch (Throwable t) {
                    module.log(Log.WARN, TAG,
                            "Google live " + source + " bounds read failed", t);
                }
                return result;
            });
            return 1;
        } catch (Throwable t) {
            module.log(Log.WARN, TAG, "Google live word-list hook unavailable", t);
            return 0;
        }
    }

    private int installFinalTaskHook() {
        try {
            Class<?> taskClass = Class.forName(profile.updateTask, false, classLoader);
            Method run = null;
            for (Method method : GoogleReflection.declaredMethods(taskClass)) {
                if ("run".equals(method.getName())
                        && method.getParameterCount() == 0
                        && method.getReturnType() == void.class) {
                    run = method;
                    break;
                }
            }
            if (run == null) {
                module.log(Log.WARN, TAG,
                        "Google final text selection hook unavailable: "
                                + profile.updateTask + ".run missing");
                return 0;
            }

            module.hook(run).intercept(chain -> {
                Object task = chain.getThisObject();
                Object result = chain.proceed();
                if (!active.getAsBoolean() || task == null) return result;
                try {
                    publish(readTaskBounds(task));
                } catch (Throwable t) {
                    module.log(Log.WARN, TAG,
                            "Google final text selection bounds read failed", t);
                }
                return result;
            });
            return 1;
        } catch (Throwable t) {
            module.log(Log.WARN, TAG,
                    "Google final text selection hook unavailable", t);
            return 0;
        }
    }

    private void publish(LiveBounds live) {
        if (live == null || live.bounds == null || live.bounds.isEmpty()) return;
        long now = SystemClock.elapsedRealtime();
        Rect accepted = filterLiveBounds(live.bounds, now);
        if (accepted != null) {
            sink.accept(accepted, live.detail + " liveFilter=accepted");
        }
    }

    /**
     * Google emits transient word lists while dragging selection handles. On 17.60 those lists can
     * briefly expand from a small paragraph to almost the whole screen and collapse again a few
     * milliseconds later. Keep normal live changes responsive, but require very large expansions
     * to repeat consistently before they replace the visible YFloat selection shell.
     */
    private synchronized Rect filterLiveBounds(Rect candidate, long now) {
        if (candidate == null || candidate.isEmpty()) return null;
        Rect next = new Rect(candidate);

        if (lastPublishedAt > 0L && now - lastPublishedAt > LIVE_FILTER_RESET_GAP_MS) {
            lastPublishedBounds = null;
            pendingLargeBounds = null;
            pendingLargeSince = 0L;
            pendingLargeHits = 0;
        }

        if (lastPublishedBounds == null || lastPublishedBounds.isEmpty()) {
            lastPublishedBounds = next;
            lastPublishedAt = now;
            clearPendingLarge();
            return new Rect(next);
        }

        if (lastPublishedBounds.equals(next)) return null;

        long previousArea = area(lastPublishedBounds);
        long nextArea = area(next);
        boolean largeExpansion = previousArea > 0L
                && nextArea > previousArea * LARGE_EXPANSION_AREA_RATIO;

        if (largeExpansion) {
            if (pendingLargeBounds != null && similarBounds(pendingLargeBounds, next)) {
                pendingLargeHits++;
                if (now - pendingLargeSince < LARGE_EXPANSION_STABLE_MS
                        && pendingLargeHits < 3) {
                    return null;
                }
                lastPublishedBounds = next;
                lastPublishedAt = now;
                clearPendingLarge();
                return new Rect(next);
            }
            pendingLargeBounds = next;
            pendingLargeSince = now;
            pendingLargeHits = 1;
            return null;
        }

        clearPendingLarge();
        if (now - lastPublishedAt < LIVE_EMIT_MIN_INTERVAL_MS) return null;
        lastPublishedBounds = next;
        lastPublishedAt = now;
        return new Rect(next);
    }

    private void clearPendingLarge() {
        pendingLargeBounds = null;
        pendingLargeSince = 0L;
        pendingLargeHits = 0;
    }

    private static long area(Rect rect) {
        if (rect == null || rect.isEmpty()) return 0L;
        return (long) rect.width() * (long) rect.height();
    }

    private static boolean similarBounds(Rect a, Rect b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) return false;
        Rect intersection = new Rect(a);
        if (!intersection.intersect(b) || intersection.isEmpty()) return false;
        long intersectionArea = area(intersection);
        long unionArea = area(a) + area(b) - intersectionArea;
        if (unionArea <= 0L) return false;
        return intersectionArea / (double) unionArea >= 0.82d;
    }

    private LiveBounds readTaskBounds(Object task) {
        Object controller = GoogleReflection.readField(task, "a", profile.controller);
        Object range = GoogleReflection.readField(task, "b", profile.range);
        if (controller == null) return LiveBounds.empty("controller_missing");

        Object state = GoogleReflection.invokeNoArg(controller, "c");
        if (state == null || !profile.state.equals(state.getClass().getName())) {
            return LiveBounds.empty("state_missing");
        }

        LiveBounds live = readStateBounds(state, null, profile.updateTask);
        if (live.bounds == null) return live;
        String rangeText = range == null ? "null" : safe(String.valueOf(range));
        return new LiveBounds(live.bounds,
                live.detail + " range=" + trim(rangeText, 260));
    }

    private LiveBounds readStateBounds(Object state, Object selectedOverride, String source) {
        if (state == null || !profile.state.equals(state.getClass().getName())) {
            return LiveBounds.empty(source + " state_missing");
        }

        Object rawView = GoogleReflection.readField(state, "g", TEXT_SELECTION_VIEW);
        if (!(rawView instanceof View textView)
                || textView.getWidth() <= 0 || textView.getHeight() <= 0) {
            return LiveBounds.empty(source + " text_view_missing");
        }

        Object selected = selectedOverride;
        if (!(selected instanceof Iterable<?>)) {
            selected = GoogleReflection.readField(
                    state, "c", GoogleLens1758Profile.IMMUTABLE_LIST);
        }

        Rect union = new Rect();
        int words = addWords(union, selected);
        if (words == 0) {
            words += addWord(union, GoogleReflection.readField(state, "a", profile.word));
            words += addWord(union, GoogleReflection.readField(state, "b", profile.word));
        }
        if (words == 0 || union.isEmpty()) {
            return LiveBounds.empty(source + " selected_words_empty");
        }

        int[] origin = new int[2];
        textView.getLocationOnScreen(origin);
        Rect screen = new Rect(union);
        screen.offset(origin[0], origin[1]);
        Rect viewScreen = new Rect(origin[0], origin[1],
                origin[0] + textView.getWidth(), origin[1] + textView.getHeight());
        if (!screen.intersect(viewScreen) || screen.isEmpty()) {
            return LiveBounds.empty(source + " outside_text_view");
        }

        return new LiveBounds(screen,
                "profile=" + profile.name
                        + " source=" + source
                        + " selectedWords=" + words
                        + " local=" + union + " screen=" + screen);
    }

    private int addWords(Rect union, Object value) {
        if (!(value instanceof Iterable<?> items)) return 0;
        int count = 0;
        for (Object item : items) count += addWord(union, item);
        return count;
    }

    private int addWord(Rect union, Object word) {
        if (word == null || !profile.word.equals(word.getClass().getName())) return 0;
        Object value = GoogleReflection.readField(word, "d", Rect.class.getName());
        if (!(value instanceof Rect rect) || rect.isEmpty()) return 0;
        if (union.isEmpty()) union.set(rect); else union.union(rect);
        return 1;
    }

    private static Profile resolveProfile(ClassLoader loader) {
        if (hasClass(loader, GoogleLens1760Profile.TEXT_SELECTION_STATE)
                && hasClass(loader, GoogleLens1760Profile.TEXT_SELECTION_UPDATE_TASK)) {
            return new Profile(GoogleLens1760Profile.NAME,
                    GoogleLens1760Profile.TEXT_SELECTION_UPDATE_TASK,
                    GoogleLens1760Profile.TEXT_SELECTION_CONTROLLER,
                    GoogleLens1760Profile.TEXT_SELECTION_STATE,
                    GoogleLens1760Profile.WORD,
                    GoogleLens1760Profile.TEXT_SELECTION_RANGE);
        }
        if (hasClass(loader, "dnrs") && hasClass(loader, "dtvm")) {
            return new Profile(GoogleLens1758Profile.NAME,
                    "dtvm", "dtvr", "dnrs", "dnrd", "dnqv");
        }
        return null;
    }

    private static boolean hasClass(ClassLoader loader, String name) {
        try {
            Class.forName(name, false, loader);
            return true;
        } catch (Throwable ignored) { return false; }
    }

    private static String safe(String value) {
        return value == null ? "" : value.replace("\n", " ").replace("\r", " ");
    }

    private static String trim(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max) + "…";
    }

    private static final class LiveBounds {
        final Rect bounds;
        final String detail;

        LiveBounds(Rect bounds, String detail) {
            this.bounds = bounds == null ? null : new Rect(bounds);
            this.detail = detail == null ? "" : detail;
        }

        static LiveBounds empty(String detail) { return new LiveBounds(null, detail); }
    }
}
