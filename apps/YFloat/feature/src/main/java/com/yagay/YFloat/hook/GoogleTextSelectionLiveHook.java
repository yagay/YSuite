package com.yagay.YFloat.hook;

import android.app.Activity;
import android.graphics.Rect;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

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
    interface LiveSink { void accept(Rect bounds, String text, String detail); }

    private final LiveSink sink;
    private final Supplier<Activity> owner;
    private final Profile profile;

    private Rect lastPublishedBounds;
    private long lastPublishedAt;
    private String lastPublishedText = "";
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
        final String listMethod;

        Profile(String name, String updateTask, String controller,
                String state, String word, String range) {
            this(name, updateTask, controller, state, word, range, "g");
        }

        Profile(String name, String updateTask, String controller,
                String state, String word, String range, String listMethod) {
            this.name = name;
            this.updateTask = updateTask;
            this.controller = controller;
            this.state = state;
            this.word = word;
            this.range = range;
            this.listMethod = listMethod;
        }
    }

    GoogleTextSelectionLiveHook(
            XposedModule module,
            ClassLoader classLoader,
            BooleanSupplier active,
            Supplier<Activity> owner,
            LiveSink sink) {
        this.owner = owner;
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
        if (profile.updateTask != null && !profile.updateTask.isBlank()) {
            count += installFinalTaskHook();
        }
        module.log(Log.INFO, TAG,
                "Google live text selection hooks=" + count
                        + " profile=" + profile.name
                        + " sources=" + profile.state + "." + profile.listMethod
                        + (profile.updateTask == null ? "" : "," + profile.updateTask + ".run"));
        return count;
    }

    private int installStateListHook() {
        try {
            Class<?> stateClass = Class.forName(profile.state, false, classLoader);
            Method target = null;
            for (Method method : GoogleReflection.declaredMethods(stateClass)) {
                if (!profile.listMethod.equals(method.getName())
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
                        "Google live word-list hook unavailable: "
                                + profile.state + "." + profile.listMethod + " missing");
                return 0;
            }

            final String source = profile.state + "." + profile.listMethod;
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

    synchronized Rect recentBounds() {
        if (lastPublishedBounds == null || lastPublishedBounds.isEmpty()
                || SystemClock.elapsedRealtime() - lastPublishedAt > 1800L) return null;
        return new Rect(lastPublishedBounds);
    }

    synchronized String recentText() {
        if (lastPublishedText.isBlank() || lastPublishedAt == 0L
                || SystemClock.elapsedRealtime() - lastPublishedAt > 1800L) return "";
        return lastPublishedText;
    }

    private void publish(LiveBounds live) {
        if (live == null || live.bounds == null || live.bounds.isEmpty()) return;
        long now = SystemClock.elapsedRealtime();
        Rect accepted = filterLiveBounds(live.bounds, now);
        String selectedText = live.text == null ? "" : live.text;
        Rect target;
        synchronized (this) {
            // A final word-list callback can have unchanged geometry but the
            // textual selection has just become available. Do not drop it.
            boolean changed = !selectedText.isBlank()
                    && !selectedText.equals(lastPublishedText);
            if (accepted == null && changed && lastPublishedBounds != null
                    && lastPublishedBounds.equals(live.bounds)) {
                accepted = new Rect(lastPublishedBounds);
            }
            if (accepted == null) return;
            if (!selectedText.isBlank()) lastPublishedText = selectedText;
            target = accepted;
        }
        sink.accept(target, selectedText, live.detail
                + " selectedChars=" + selectedText.length() + " liveFilter=accepted");
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
            lastPublishedText = "";
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
        return new LiveBounds(live.bounds, live.text,
                live.detail + " range=" + trim(rangeText, 260));
    }

    private LiveBounds readStateBounds(Object state, Object selectedOverride, String source) {
        if (state == null || !profile.state.equals(state.getClass().getName())) {
            return LiveBounds.empty(source + " state_missing");
        }

        Object rawView = GoogleReflection.readField(state, null, TEXT_SELECTION_VIEW);
        if (!(rawView instanceof View textView)
                || textView.getWidth() <= 0 || textView.getHeight() <= 0) {
            return LiveBounds.empty(source + " text_view_missing");
        }

        Object selected = selectedOverride;
        if (!(selected instanceof Iterable<?>)) {
            selected = GoogleReflection.readField(
                    state, "c", GoogleLens1758Profile.IMMUTABLE_LIST);
            if (!(selected instanceof Iterable<?>)) {
                selected = GoogleReflection.readField(
                        state, null, GoogleLens1758Profile.IMMUTABLE_LIST);
            }
        }

        Rect union = new Rect();
        StringBuilder selectedText = new StringBuilder();
        int words = addWords(union, selected, selectedText);
        if (words == 0) {
            words += addWord(union, GoogleReflection.readField(state, "a", profile.word),
                    selectedText);
            words += addWord(union, GoogleReflection.readField(state, "b", profile.word),
                    selectedText);
        }
        if (words == 0 || union.isEmpty()) {
            return LiveBounds.empty(source + " selected_words_empty");
        }

        int[] origin = new int[2];
        textView.getLocationOnScreen(origin);
        // Google moves its TextSelectionView up by hundreds of pixels when
        // focusing text, even as OCR word Rects remain in the original frozen
        // screenshot's local coordinate system. Fullscreen OCR views must map
        // through the stable decor viewport, not their animated live origin.
        Rect viewport = null;
        Activity activity = owner.get();
        if (activity != null && activity.getWindow() != null) {
            View decor = activity.getWindow().getDecorView();
            if (decor != null && decor.getWidth() > 0 && decor.getHeight() > 0) {
                int[] stableOrigin = new int[2];
                decor.getLocationOnScreen(stableOrigin);
                viewport = new Rect(stableOrigin[0], stableOrigin[1],
                        stableOrigin[0] + decor.getWidth(),
                        stableOrigin[1] + decor.getHeight());
            }
        }
        GoogleSelectionCoordinateSpace.Result mapped = GoogleSelectionCoordinateSpace.map(
                union, textView.getWidth(), textView.getHeight(), origin[0], origin[1],
                viewport);
        Rect screen = mapped.bounds;
        if (screen == null || screen.isEmpty()) {
            return LiveBounds.empty(source + " outside_text_view");
        }
        return new LiveBounds(screen, selectedText.toString(),
                "profile=" + profile.name
                        + " source=" + source
                        + " selectedWords=" + words
                        + " selectedChars=" + selectedText.length()
                        + " space=" + mapped.space
                        + " local=" + union + " screen=" + screen);
    }

    private int addWords(Rect union, Object value, StringBuilder out) {
        if (!(value instanceof Iterable<?> items)) return 0;
        int count = 0;
        for (Object item : items) {
            if (count >= 128 || out.length() >= 8000) break;
            count += addWord(union, item, out);
        }
        return count;
    }

    private int addWord(Rect union, Object word, StringBuilder out) {
        if (word == null || !profile.word.equals(word.getClass().getName())) return 0;
        Object value = GoogleReflection.readField(word, "d", Rect.class.getName());
        if (!(value instanceof Rect)) {
            value = GoogleReflection.readField(word, null, Rect.class.getName());
        }
        if (!(value instanceof Rect rect) || rect.isEmpty()) return 0;
        if (union.isEmpty()) union.set(rect); else union.union(rect);

        // APK-verified 17.64 selected-word class dqzy owns two String
        // properties and a Rect. Unlike dwtg.k() on dwti, these strings
        // represent the live OCR word candidates. Only inspect this exact
        // verified word class, never arbitrary Google object graphs.
        String first = "", second = "";
        for (Field field : GoogleReflection.instanceFields(word.getClass())) {
            if (field.getType() != String.class) continue;
            try {
                field.setAccessible(true);
                Object raw = field.get(word);
                String candidate = raw instanceof String str ? str : "";
                if (first.isBlank()) first = candidate;
                else if (second.isBlank()) second = candidate;
            } catch (Throwable ignored) { }
        }
        String wordText = GoogleLiveWordTextPolicy.choose(first, second);
        GoogleLiveWordTextPolicy.append(out, wordText);
        return 1;
    }

    private static Profile resolveProfile(ClassLoader loader) {
        // Exact Google 17.60 word-list and final-selection update paths first.
        // These are required for selection highlights to follow handle drags.
        if (hasClass(loader, GoogleLens1760Profile.TEXT_SELECTION_STATE)
                && hasClass(loader, GoogleLens1760Profile.TEXT_SELECTION_UPDATE_TASK)) {
            return new Profile(GoogleLens1760Profile.NAME,
                    GoogleLens1760Profile.TEXT_SELECTION_UPDATE_TASK,
                    GoogleLens1760Profile.TEXT_SELECTION_CONTROLLER,
                    GoogleLens1760Profile.TEXT_SELECTION_STATE,
                    GoogleLens1760Profile.WORD,
                    GoogleLens1760Profile.TEXT_SELECTION_RANGE);
        }
        // Legacy 17.58 support; do not resolve it ahead of the 17.60 baseline.
        if (hasClass(loader, "dnrs") && hasClass(loader, "dtvm")) {
            return new Profile(GoogleLens1758Profile.NAME,
                    "dtvm", "dtvr", "dnrs", "dnrd", "dnqv");
        }
        return resolveFromStableView(loader);
    }

    /**
     * Resolve from the non-obfuscated TextSelectionView, not a version-specific
     * controller. Google 17.64.15 has TextSelectionView.q:dran, dran.g(list,Z,I)
     * and dran.a/b:dqzy where dqzy.d is the selected word's Rect.
     * This also survives future changes to dran/dqzy obfuscated names.
     */
    private static Profile resolveFromStableView(ClassLoader loader) {
        if (loader == null) return null;
        try {
            Class<?> view = Class.forName(TEXT_SELECTION_VIEW, false, loader);
            for (java.lang.reflect.Field field : GoogleReflection.instanceFields(view)) {
                Class<?> state = field.getType();
                if (state == view || state.isPrimitive()) continue;
                java.lang.reflect.Field viewField = null;
                java.lang.reflect.Field selectedList = null;
                java.lang.reflect.Field wordField = null;
                java.lang.reflect.Field[] stateFields = GoogleReflection.instanceFields(state);
                for (java.lang.reflect.Field candidate : stateFields) {
                    if (candidate.getType() == view) viewField = candidate;
                    if (GoogleLens1758Profile.IMMUTABLE_LIST
                            .equals(candidate.getType().getName())) selectedList = candidate;
                    Class<?> word = candidate.getType();
                    if (word == view || word.isPrimitive()) continue;
                    int matchingFields = 0;
                    for (java.lang.reflect.Field f : stateFields) {
                        if (f.getType() == word) matchingFields++;
                    }
                    if (matchingFields < 2) continue;
                    int stringFields = 0;
                    boolean hasWordBox = false;
                    for (java.lang.reflect.Field box : GoogleReflection.instanceFields(word)) {
                        if (box.getType() == Rect.class) hasWordBox = true;
                        if (box.getType() == String.class) stringFields++;
                    }
                    if (hasWordBox && stringFields >= 2) wordField = candidate;
                }
                if (viewField == null || selectedList == null || wordField == null) continue;
                for (Method method : GoogleReflection.declaredMethods(state)) {
                    Class<?>[] args = method.getParameterTypes();
                    if (method.getReturnType() == void.class && args.length == 3
                            && GoogleLens1758Profile.IMMUTABLE_LIST.equals(args[0].getName())
                            && args[1] == boolean.class && args[2] == int.class) {
                        return new Profile("structural-text-view",
                                null, null, state.getName(), wordField.getType().getName(),
                                null, method.getName());
                    }
                }
            }
        } catch (Throwable ignored) { }
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
        return value.length() <= max ? value : value.substring(0, max) + "...";
    }

    private static final class LiveBounds {
        final Rect bounds;
        final String text;
        final String detail;

        LiveBounds(Rect bounds, String text, String detail) {
            this.bounds = bounds == null ? null : new Rect(bounds);
            this.text = text == null ? "" : text.trim();
            this.detail = detail == null ? "" : detail;
        }

        static LiveBounds empty(String detail) {
            return new LiveBounds(null, "", detail);
        }
    }
}
