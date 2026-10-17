package com.yagay.YFloat.hook;

import android.content.Context;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.DisplayMetrics;

import org.lsposed.hiddenapibypass.HiddenApiBypass;

import java.lang.reflect.Executable;
import java.lang.reflect.Method;

/** Exact-version and structural-fallback selection adapters behind one stable runtime contract. */
final class GoogleSelectionAdapter {
    interface Binding {
        boolean available();
        Method method();
        Snapshot snapshot(Object metadata, Context context);
        String detail();
        String source();
        int confidence();
    }

    static final class Snapshot {
        private final String text;
        private final Rect bounds;
        private final String selectionClass;
        private final String detail;
        private final boolean directRegionCommit;

        Snapshot(String text, Rect bounds, String selectionClass,
                 String detail, boolean directRegionCommit) {
            this.text = text == null ? "" : text;
            this.bounds = bounds == null ? null : new Rect(bounds);
            this.selectionClass = selectionClass == null ? "" : selectionClass;
            this.detail = detail == null ? "" : detail;
            this.directRegionCommit = directRegionCommit;
        }

        String text() { return text; }
        Rect bounds() { return bounds == null ? null : new Rect(bounds); }
        String selectionClass() { return selectionClass; }
        String detail() { return detail; }
        boolean directRegionCommit() { return directRegionCommit; }
    }

    // A mapping is only valid for the ClassLoader that produced its Method.
    // APK updates replace the Google process, so this cache never crosses versions.
    private static ClassLoader cachedLoader;
    private static Binding cachedBinding;

    static synchronized Binding resolve(ClassLoader loader) {
        return resolve(loader,null);
    }

    static synchronized Binding resolve(ClassLoader loader, GoogleHookProfileStore store) {
        if (loader == cachedLoader && cachedBinding != null) {
            if(store==null)return cachedBinding;
            // A manual scan in this process must bypass even the in-memory binding.
            store.read();
            if(!store.manualRequested())return cachedBinding;
        }
        Binding found = resolveUncached(loader,store);
        cachedLoader = loader;
        cachedBinding = found;
        return found;
    }

    private static Binding resolveUncached(ClassLoader loader, GoogleHookProfileStore store) {
        if (loader == null) return new MissingBinding("classLoader=null");
        // Check the one-shot manual request before selecting a fixed or cached
        // profile. Even the built-in 17.60 ABI is revalidated on demand.
        String symbols=store==null?"":store.read();
        boolean manual=store!=null && store.manualRequested();
        if(store!=null)store.reportProgress("resolving",0,0);
        String error = GoogleLens1760Profile.selectionValidationError(loader);
        if (error.isBlank()) {
            try {
                Class<?> cls = Class.forName(GoogleLens1760Profile.CONTROLLER,false,loader);
                for (Executable executable:HiddenApiBypass.getDeclaredMethods(cls)) {
                    if (executable instanceof Method method
                            && GoogleLens1760Profile.isSelectionMethod(method)) {
                        return new Profile1760Binding(method,
                                "fixed 17.60 metadata+selection contract");
                    }
                }
                error = "17.60 selection method missing";
            } catch (Throwable t) {
                error=t.getClass().getSimpleName()+":"+String.valueOf(t.getMessage());
            }
        }
        // Bind persisted symbols FIRST. No full DEX scan on an unchanged APK.
        // The resolver verifies metadata, return types and confidence again
        // inside this process's own ClassLoader.
        if(!manual && !symbols.isBlank()) {
            GoogleLensDynamicResolver.SelectionBinding remembered=
                    GoogleLensDynamicResolver.resolveCached(loader,symbols);
            if(remembered.available())
                return new DynamicBinding(remembered,"APK="+store.identity(),
                        "cached-1760-semantic");
        }
        // New APK, changed Hook matcher, or invalid cache: one bounded scan
        // per ClassLoader. A failed scan stays memoized for this process.
        GoogleLensDynamicResolver.SelectionBinding dynamic =
                GoogleLensDynamicResolver.discoverSelection(loader,
                        (phase, done, total) -> {
                            if(store!=null)store.reportProgress(phase,done,total);
                        });
        if (!dynamic.available()) return new MissingBinding(
                "1760="+error+"; semantic="+dynamic.detail());
        return new DynamicBinding(dynamic,"1760="+error,
                "scanned-1760-selection-shape");
    }

    /** Fixed 17.60 and validated cache hits do not require a scan Toast. */
    static boolean scanned(Binding binding) {
        return binding != null && (binding.source().startsWith("scanned-")
                || (!binding.available() && binding.detail().contains("; semantic=")));
    }

    /** Write only after LSPosed registered the selected Method successfully. */
    static void recordInstalled(Binding binding, GoogleHookProfileStore store) {
        if (store==null || !(binding instanceof DynamicBinding dynamic)
                || !dynamic.source().startsWith("scanned-"))return;
        store.save(GoogleLensDynamicResolver.encode(dynamic.binding));
    }

    private static final class Profile1760Binding implements Binding {
        private final Method method;
        private final String detail;

        Profile1760Binding(Method method, String detail) {
            this.method = method;
            this.detail = detail;
            try { method.setAccessible(true); } catch (Throwable ignored) { }
        }

        @Override public boolean available() { return method != null; }
        @Override public Method method() { return method; }
        @Override public String source() { return "baseline-1760"; }
        @Override public String detail() { return detail; }
        @Override public int confidence() { return 100; }

        @Override public Snapshot snapshot(Object metadata, Context context) {
            GoogleLens1760Profile.SelectionSnapshot selected =
                    GoogleLens1760Profile.selection(metadata, context);
            Rect bounds = selected.bounds();
            boolean region = GoogleLens1760Profile.isDirectRegionSelectionClass(
                    selected.selectionClass()) && bounds != null && !bounds.isEmpty();
            return new Snapshot(selected.text(), bounds, selected.selectionClass(),
                    selected.detail() + " adapter=baseline-1760", region);
        }
    }

    private static final class ExactBinding implements Binding {
        private final Method method;
        private final String source;
        private final int confidence;
        private final String detail;

        ExactBinding(Method method, String source, int confidence, String detail) {
            this.method = method;
            this.source = source == null ? "exact" : source;
            this.confidence = confidence;
            this.detail = detail == null ? "" : detail;
            try { if (this.method != null) this.method.setAccessible(true); } catch (Throwable ignored) {}
        }

        @Override public boolean available() { return method != null; }
        @Override public Method method() { return method; }
        @Override public String detail() { return detail; }
        @Override public String source() { return source; }
        @Override public int confidence() { return confidence; }

        @Override public Snapshot snapshot(Object metadata, Context context) {
            GoogleLens1758Profile.SelectionSnapshot selected =
                    GoogleLens1758Profile.selection(metadata);
            Rect bounds = exactBounds(selected, context);
            return new Snapshot(selected.text(), bounds, selected.userSelectionClass(),
                    selected.detail() + " adapter=" + source,
                    selected.isDirectRegionSelection() && bounds != null && !bounds.isEmpty());
        }
    }

    private static final class DynamicBinding implements Binding {
        private final GoogleLensDynamicResolver.SelectionBinding binding;
        private final String profileError;
        private final String source;

        DynamicBinding(GoogleLensDynamicResolver.SelectionBinding binding,
                       String profileError, String source) {
            this.binding = binding;
            this.profileError = profileError == null ? "" : profileError;
            this.source = source;
        }

        @Override public boolean available() { return binding.available(); }
        @Override public Method method() { return binding.method(); }
        @Override public String source() { return source; }
        @Override public int confidence() { return binding.confidence(); }
        @Override public String detail() {
            return binding.detail() + " profileError=" + profileError;
        }

        @Override public Snapshot snapshot(Object metadata, Context context) {
            GoogleLensDynamicResolver.DynamicSelectionSnapshot selected =
                    binding.snapshot(metadata);
            Rect bounds = dynamicBounds(selected.rawBounds(), context);
            String detail = selected.detail() + " resolver=" + binding.detail()
                    + " adapter=dynamic";
            return new Snapshot(selected.text(), bounds, selected.selectionClass(),
                    detail, selected.directRegionLike() && bounds != null && !bounds.isEmpty());
        }
    }

    private static final class MissingBinding implements Binding {
        private final String detail;
        MissingBinding(String detail) { this.detail = detail == null ? "" : detail; }
        @Override public boolean available() { return false; }
        @Override public Method method() { return null; }
        @Override public Snapshot snapshot(Object metadata, Context context) {
            return new Snapshot("", null, "", detail, false);
        }
        @Override public String detail() { return detail; }
        @Override public String source() { return "none"; }
        @Override public int confidence() { return 0; }
    }

    private static Rect exactBounds(GoogleLens1758Profile.SelectionSnapshot selected,
                                    Context context) {
        if (selected == null) return null;
        Rect direct = selected.bounds();
        if (direct != null && !direct.isEmpty()) return direct;
        if (context == null) return null;
        try {
            DisplayMetrics metrics = context.getResources().getDisplayMetrics();
            return selected.boundsForFrame(metrics.widthPixels, metrics.heightPixels);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Rect dynamicBounds(RectF rawBounds, Context context) {
        if (rawBounds == null || rawBounds.width() <= 0f || rawBounds.height() <= 0f) return null;
        RectF working = new RectF(rawBounds);
        boolean normalized = working.left >= -0.05f && working.top >= -0.05f
                && working.right <= 1.05f && working.bottom <= 1.05f;
        if (normalized) {
            if (context == null) return null;
            try {
                DisplayMetrics metrics = context.getResources().getDisplayMetrics();
                working.set(
                        working.left * metrics.widthPixels,
                        working.top * metrics.heightPixels,
                        working.right * metrics.widthPixels,
                        working.bottom * metrics.heightPixels);
            } catch (Throwable ignored) {
                return null;
            }
        }
        Rect out = new Rect();
        working.roundOut(out);
        return out.isEmpty() ? null : out;
    }

    private static String safe(String value) {
        if (value == null) return "";
        String out = value.replace('\n', ' ').replace('\r', ' ');
        return out.length() <= 300 ? out : out.substring(0, 300) + "...";
    }

    private GoogleSelectionAdapter() {}
}
