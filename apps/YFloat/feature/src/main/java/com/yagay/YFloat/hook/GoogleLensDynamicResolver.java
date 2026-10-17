package com.yagay.YFloat.hook;

import android.graphics.PointF;
import android.graphics.RectF;
import android.os.SystemClock;

import org.lsposed.hiddenapibypass.HiddenApiBypass;

import java.lang.reflect.Array;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Enumeration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import dalvik.system.DexFile;

/**
 * Fail-soft structural resolver for Google Lens selection internals.
 *
 * <p>It runs only when the exact built-in profile no longer validates. No candidate is accepted
 * from a class/method name. A high-confidence candidate must expose the semantic shape currently
 * needed by YFloat: controller (SelectionMetadata, boolean)->void, metadata -> UserSelection,
 * and UserSelection no-arg String + RectF accessors. This makes ordinary R8 renaming survivable
 * without guessing unsafe hooks.</p>
 */
final class GoogleLensDynamicResolver {
    static final int MIN_SELECTION_CONFIDENCE = 95;
    // Only Lens-related DEX partitions are inspected; never the whole Google APK.
    private static final int MAX_SCANNED_CLASSES = 6_000;
    // Bound a cold Google DEX scan instead of blocking PackageReady indefinitely.
    private static final long MAX_SCAN_ELAPSED_MS = 7_000L;
    private static final String LENS_DEX_ANCHOR = GoogleLens1760Profile.FROZEN_IMAGE_VIEW;
    private static final String SELECTION_DEX_ANCHOR = GoogleLens1760Profile.TEXT_SELECTION_VIEW;

    interface ProgressListener {
        void onProgress(String phase,int done,int total);
    }

    static SelectionBinding discoverSelection(ClassLoader loader) {
        return discoverSelection(loader,(phase,done,total)->{});
    }

    static SelectionBinding discoverSelection(ClassLoader loader,ProgressListener progress) {
        if (loader == null) return SelectionBinding.none("classLoader=null");

        SelectionBinding best = SelectionBinding.none("no structural candidate");
        int scanned = 0;
        final long deadline = SystemClock.elapsedRealtime() + MAX_SCAN_ELAPSED_MS;
        Set<String> seen = new HashSet<>();
        boolean ambiguous = false;

        try {
            Set<String> names = dexClassNames(loader, deadline, progress);
            int total = Math.min(MAX_SCANNED_CLASSES,names.size());
            progress.onProgress("matching",0,total);
            for (String name : names) {
                if (SystemClock.elapsedRealtime() >= deadline)
                    return SelectionBinding.none("scan timeout after "
                            + MAX_SCAN_ELAPSED_MS + "ms scanned=" + scanned);
                if (scanned >= MAX_SCANNED_CLASSES) break;
                if (!eligibleClassName(name) || !seen.add(name)) continue;
                scanned++;
                if ((scanned & 63) == 0 || scanned == total)
                    progress.onProgress("matching",scanned,total);

                Class<?> controller;
                try {
                    controller = Class.forName(name, false, loader);
                } catch (Throwable ignored) {
                    continue;
                }
                if (controller.isInterface() || controller.isAnnotation()
                        || controller.isEnum() || controller.isArray()) {
                    continue;
                }

                for (Method method : declaredMethods(controller)) {
                    if (SystemClock.elapsedRealtime() >= deadline)
                        return SelectionBinding.none("scan timeout after "
                                + MAX_SCAN_ELAPSED_MS + "ms scanned=" + scanned);
                    Candidate candidate = scoreSelectionMethod(method);
                    if (candidate == null || candidate.score < MIN_SELECTION_CONFIDENCE) continue;
                    if (candidate.score == best.confidence() && best.method() != method) {
                        ambiguous = true;
                        continue;
                    }
                    if (candidate.score < best.confidence()) continue;
                    best = new SelectionBinding(
                            method,
                            candidate.userSelectionField,
                            candidate.textMethod,
                            candidate.boundsMethod,
                            candidate.pointMethod,
                            candidate.score,
                            "dynamic-structural scanned=" + scanned
                                    + " controller=" + controller.getName()
                                    + " method=" + method.getName());
                    ambiguous = false;
                }
            }
        } catch (Throwable t) {
            return SelectionBinding.none("resolver failed="
                    + t.getClass().getSimpleName() + ":" + String.valueOf(t.getMessage()));
        }

        if (ambiguous) {
            return SelectionBinding.none("multiple equally scored selection methods; refusing unsafe hook"
                    + " scanned=" + scanned);
        }
        if (best.available()) {
            return new SelectionBinding(best.method(), best.userSelectionField(),
                    best.textMethod(), best.boundsMethod(), best.pointMethod(),
                    best.confidence(), best.detail() + " scannedFinal=" + scanned);
        }
        return SelectionBinding.none("no selection Hook candidate within Lens-related DEX"
                + " scanned=" + scanned + " (other Google DEX partitions skipped)");
    }

    /**
     * Serialize one discovered semantic selection role for the installed APK.
     * No Java reflective handle crosses process restarts.
     */
    static String encode(SelectionBinding binding) {
        if(binding==null || !binding.available()) return "";
        return String.join("|",
                binding.method().getDeclaringClass().getName(),
                binding.method().getName(),
                binding.method().getParameterTypes()[0].getName(),
                binding.userSelectionField().getName(),
                binding.userSelectionField().getType().getName(),
                binding.textMethod().getName(),
                binding.boundsMethod().getName(),
                binding.pointMethod()==null?"":binding.pointMethod().getName());
    }

    /**
     * Fast cached binding. Always resolve in the NEW process ClassLoader and
     * score the semantic contract again. Any changed signature fails closed
     * and calls the full scanner exactly once for this ClassLoader.
     */
    static SelectionBinding resolveCached(ClassLoader loader, String encoded) {
        if(loader==null || encoded==null || encoded.isBlank() || encoded.length()>2048)
            return SelectionBinding.none("empty/invalid cached match");
        String[] s=encoded.split("\\|",-1);
        if(s.length!=8) return SelectionBinding.none("cached match field count mismatch");
        try {
            Class<?> cls=Class.forName(s[0],false,loader);
            for(Method method:declaredMethods(cls)) {
                if(!method.getName().equals(s[1])
                        || !method.getReturnType().equals(void.class)
                        || method.getParameterCount()!=2
                        || !method.getParameterTypes()[0].getName().equals(s[2])
                        || method.getParameterTypes()[1]!=boolean.class)continue;
                Candidate candidate=scoreSelectionMethod(method);
                if(candidate==null || candidate.score<MIN_SELECTION_CONFIDENCE)continue;
                if(!candidate.userSelectionField.getName().equals(s[3])
                        || !candidate.userSelectionField.getType().getName().equals(s[4])
                        || !candidate.textMethod.getName().equals(s[5])
                        || !candidate.boundsMethod.getName().equals(s[6])
                        || !(candidate.pointMethod==null?s[7].isEmpty()
                              :candidate.pointMethod.getName().equals(s[7])))continue;
                return new SelectionBinding(method,candidate.userSelectionField,
                        candidate.textMethod,candidate.boundsMethod,
                        candidate.pointMethod,candidate.score,
                        "cached-1760-semantic validated class="+s[0]);
            }
        } catch(Throwable ignored) { }
        return SelectionBinding.none("cached selection contract mismatch");
    }

    static Candidate scoreSelectionMethod(Method method) {
        if (method == null || method.getReturnType() != void.class) return null;
        Class<?>[] params = method.getParameterTypes();
        if (params.length != 2 || params[1] != boolean.class) return null;
        if (isPlatformType(params[0])) return null;

        int base = 20;
        Candidate best = null;
        for (Field field : instanceFields(params[0])) {
            Class<?> userSelection = field.getType();
            if (isPlatformType(userSelection) || userSelection.isPrimitive()) continue;

            Method text = null;
            Method bounds = null;
            Method point = null;
            for (Method semantic : declaredMethods(userSelection)) {
                if (semantic.getParameterCount() != 0) continue;
                if (semantic.getReturnType() == String.class && text == null) {
                    text = semantic;
                } else if (semantic.getReturnType() == RectF.class && bounds == null) {
                    bounds = semantic;
                } else if (semantic.getReturnType() == PointF.class && point == null) {
                    point = semantic;
                }
            }

            int score = base;
            if (text != null) score += 40;
            if (bounds != null) score += 35;
            if (point != null) score += 10;
            if (Modifier.isFinal(field.getModifiers())) score += 2;

            if (text != null && bounds != null
                    && (best == null || score > best.score)) {
                best = new Candidate(field, text, bounds, point, score);
            }
        }
        return best;
    }

    /**
     * Scan only the FrozenImageView / TextSelectionView DEX partitions.
     * Unrelated Google DEX partitions are excluded even if they contain
     * plausible (Metadata, boolean) methods. No APK-wide fallback.
     *
     * Class-name enumeration is necessary to locate the stable anchors;
     * Java reflection is limited to eligible names in those partitions.
     */
    private static Set<String> dexClassNames(ClassLoader loader,long deadline,
            ProgressListener progress) {
        Set<String> related = new LinkedHashSet<>();
        int indexedPartitions=0;
        progress.onProgress("indexing",0,0);
        for (ClassLoader current = loader; current != null; current = current.getParent()) {
            if (SystemClock.elapsedRealtime() >= deadline) return related;
            Object pathList = readNamedField(current, "pathList");
            Object elements = readNamedField(pathList, "dexElements");
            if (elements == null || !elements.getClass().isArray()) continue;
            int length = Array.getLength(elements);
            for (int i = 0; i < length; i++) {
                if (SystemClock.elapsedRealtime() >= deadline) return related;
                Object element = Array.get(elements, i);
                Object dex = readNamedField(element, "dexFile");
                if (!(dex instanceof DexFile dexFile)) continue;
                try {
                    Set<String> partition = new LinkedHashSet<>();
                    boolean relevant = false;
                    Enumeration<String> entries = dexFile.entries();
                    int enumerated = 0;
                    while (entries.hasMoreElements()) {
                        if ((++enumerated & 127) == 0
                                && SystemClock.elapsedRealtime() >= deadline)
                            return related;
                        String name = entries.nextElement();
                        if (isRelevantDexAnchor(name)) relevant = true;
                        if (eligibleClassName(name)) partition.add(name);
                    }
                    if (relevant) related.addAll(partition);
                } catch (Throwable ignored) { }
                indexedPartitions++;
                progress.onProgress("indexing",indexedPartitions,0);
            }
        }
        return related;
    }

    static boolean isRelevantDexAnchor(String name) {
        return LENS_DEX_ANCHOR.equals(name) || SELECTION_DEX_ANCHOR.equals(name);
    }

    private static boolean eligibleClassName(String name) {
        if (name == null || name.isBlank() || name.contains("$")) return false;
        // Also consider named Lens selection classes in the related DEX.
        if (name.startsWith("com.google.android.libraries.lens.common.text.selection.")
                || name.startsWith("com.google.android.libraries.lens.view.frozenimage."))
            return true;
        int dot = name.lastIndexOf('.');
        String simple = dot < 0 ? name : name.substring(dot + 1);
        if (simple.length() < 2 || simple.length() > 8) return false;
        char first = simple.charAt(0);
        if (first < 'a' || first > 'z') return false;
        if (name.startsWith("android.") || name.startsWith("java.")
                || name.startsWith("kotlin.") || name.startsWith("androidx.")) {
            return false;
        }
        return true;
    }

    private static boolean isPlatformType(Class<?> type) {
        if (type == null) return true;
        String name = type.getName();
        return name.startsWith("java.") || name.startsWith("android.")
                || name.startsWith("androidx.") || name.startsWith("kotlin.");
    }

    private static Method[] declaredMethods(Class<?> cls) {
        return GoogleReflection.declaredMethods(cls);
    }

    private static Field[] instanceFields(Class<?> cls) {
        return GoogleReflection.instanceFields(cls);
    }

    private static Object readNamedField(Object target, String name) {
        return GoogleReflection.readNamedField(target, name);
    }

    static final class SelectionBinding {
        private final Method method;
        private final Field userSelectionField;
        private final Method textMethod;
        private final Method boundsMethod;
        private final Method pointMethod;
        private final int confidence;
        private final String detail;

        SelectionBinding(Method method, Field userSelectionField, Method textMethod,
                         Method boundsMethod, Method pointMethod, int confidence, String detail) {
            this.method = method;
            this.userSelectionField = userSelectionField;
            this.textMethod = textMethod;
            this.boundsMethod = boundsMethod;
            this.pointMethod = pointMethod;
            this.confidence = confidence;
            this.detail = detail == null ? "" : detail;
            try { if (this.method != null) this.method.setAccessible(true); } catch (Throwable ignored) {}
            try { if (this.userSelectionField != null) this.userSelectionField.setAccessible(true); } catch (Throwable ignored) {}
            try { if (this.textMethod != null) this.textMethod.setAccessible(true); } catch (Throwable ignored) {}
            try { if (this.boundsMethod != null) this.boundsMethod.setAccessible(true); } catch (Throwable ignored) {}
            try { if (this.pointMethod != null) this.pointMethod.setAccessible(true); } catch (Throwable ignored) {}
        }

        static SelectionBinding none(String detail) {
            return new SelectionBinding(null, null, null, null, null, 0, detail);
        }

        boolean available() {
            return method != null && userSelectionField != null
                    && textMethod != null && boundsMethod != null
                    && confidence >= MIN_SELECTION_CONFIDENCE;
        }

        Method method() { return method; }
        Field userSelectionField() { return userSelectionField; }
        Method textMethod() { return textMethod; }
        Method boundsMethod() { return boundsMethod; }
        Method pointMethod() { return pointMethod; }
        int confidence() { return confidence; }
        String detail() { return detail; }

        DynamicSelectionSnapshot snapshot(Object metadata) {
            if (metadata == null || !available()) {
                return new DynamicSelectionSnapshot("", null, "", "metadata/binding unavailable");
            }
            try {
                Object selection = userSelectionField.get(metadata);
                if (selection == null) {
                    return new DynamicSelectionSnapshot("", null, "",
                            "userSelection=null metadata=" + metadata.getClass().getName());
                }
                Object rawText = textMethod.invoke(selection);
                Object rawBounds = boundsMethod.invoke(selection);
                String text = rawText instanceof String value ? value.trim() : "";
                // WordSelection.k() may intentionally return empty while the
                // completed Google OCR text lives in the nested TextSelection.
                // Recognize its (String a, List b) data shape, not R8 names.
                if (text.isBlank()) text = nestedSelectedText(selection);
                RectF bounds = rawBounds instanceof RectF value ? new RectF(value) : null;
                boolean directRegion = hasGoogleRegion(selection);
                if (directRegion) {
                    // The Region parcelable carries the drawn rectangle directly.
                    // It is more authoritative than UserSelection.b(), which can
                    // describe the viewport while the rectangle is being edited.
                    RectF selectedRegion = googleRegionBounds(selection);
                    if (selectedRegion != null && !selectedRegion.isEmpty()) {
                        bounds = selectedRegion;
                    }
                }
                StringBuilder detail = new StringBuilder();
                detail.append("dynamicSelectionClass=").append(selection.getClass().getName())
                        .append(" confidence=").append(confidence);
                if (pointMethod != null) {
                    try {
                        Object point = pointMethod.invoke(selection);
                        if (point instanceof PointF p) {
                            detail.append(" point=").append(p.x).append(",").append(p.y);
                        }
                    } catch (Throwable ignored) {
                    }
                }
                if (bounds != null) detail.append(" rawBounds=").append(bounds);
                if (!text.isBlank()) detail.append(" textLen=").append(text.length());
                return new DynamicSelectionSnapshot(text, bounds,
                        selection.getClass().getName(), detail.toString(), directRegion);
            } catch (Throwable t) {
                return new DynamicSelectionSnapshot("", null, "",
                        "dynamic snapshot failed=" + t.getClass().getSimpleName());
            }
        }
    }

    /**
     * Google 17.60 and 17.64 store OCR text inside a TextSelection value
     * whose first field is String and second field is an ImmutableList.
     * The wrapper is owned by a concrete UserSelection and is not itself OCR.
     */
    static String nestedSelectedText(Object selection) {
        if (selection == null) return "";
        for (Field field : instanceFields(selection.getClass())) {
            if (field.getType().isPrimitive() || isPlatformType(field.getType())) continue;
            // Resolve Google's nested OCR text by value shape, not the R8 field names
            // "a"/"b": require exactly one String field and one word-list field.
            Field text = null;
            int textCount = 0;
            int listCount = 0;
            for (Field nested : instanceFields(field.getType())) {
                if (nested.getType() == String.class) {
                    text = nested;
                    textCount++;
                } else if (java.util.List.class.isAssignableFrom(nested.getType())) {
                    listCount++;
                }
            }
            if (textCount != 1 || listCount != 1) continue;
            try {
                field.setAccessible(true);
                Object wrapper = field.get(selection);
                if (wrapper == null) continue;
                text.setAccessible(true);
                Object value = text.get(wrapper);
                if (value instanceof String selected && !selected.isBlank()) {
                    return selected.trim();
                }
            } catch (Throwable ignored) { }
        }
        return "";
    }

    /** A real Lens Region object is required; an empty OCR string alone is insufficient. */
    static boolean hasGoogleRegion(Object selection) {
        if (selection == null) return false;
        for (Field field : instanceFields(selection.getClass())) {
            if (!isLensRegionType(field.getType())) continue;
            try {
                field.setAccessible(true);
                if (field.get(selection) != null) return true;
            } catch (Throwable ignored) { }
        }
        return false;
    }

    private static boolean isLensRegionType(Class<?> type) {
        if (type == null) return false;
        String name = type.getName();
        // Region's public namespace is stable across 17.58/17.60/17.64. Never
        // reinterpret arbitrary nested RectF fields as a region selection.
        return "com.google.android.libraries.lens.view.region.Region".equals(name);
    }

    static RectF googleRegionBounds(Object selection) {
        if (selection == null) return null;
        for (Field field : instanceFields(selection.getClass())) {
            if (!isLensRegionType(field.getType())) continue;
            try {
                field.setAccessible(true);
                Object region = field.get(selection);
                if (region == null) continue;
                RectF found = null;
                for (Field valueField : instanceFields(region.getClass())) {
                    if (valueField.getType() != RectF.class) continue;
                    // Multiple RectF candidates are ambiguous: fail closed rather than
                    // hand a viewport or editing handle to the screenshot cropper.
                    if (found != null) return null;
                    valueField.setAccessible(true);
                    Object candidate = valueField.get(region);
                    if (!(candidate instanceof RectF rect) || rect.isEmpty()
                            || !Float.isFinite(rect.left) || !Float.isFinite(rect.top)
                            || !Float.isFinite(rect.right) || !Float.isFinite(rect.bottom)) {
                        return null;
                    }
                    found = new RectF(rect);
                }
                if (found != null) return found;
            } catch (Throwable ignored) { }
        }
        return null;
    }

    static final class DynamicSelectionSnapshot {
        private final String text;
        private final RectF rawBounds;
        private final String selectionClass;
        private final String detail;
        private final boolean directRegion;

        DynamicSelectionSnapshot(String text, RectF rawBounds,
                                 String selectionClass, String detail) {
            this(text, rawBounds, selectionClass, detail, false);
        }

        DynamicSelectionSnapshot(String text, RectF rawBounds,
                                 String selectionClass, String detail, boolean directRegion) {
            this.text = text == null ? "" : text;
            this.rawBounds = rawBounds == null ? null : new RectF(rawBounds);
            this.selectionClass = selectionClass == null ? "" : selectionClass;
            this.detail = detail == null ? "" : detail;
            this.directRegion = directRegion;
        }

        String text() { return text; }
        RectF rawBounds() { return rawBounds == null ? null : new RectF(rawBounds); }
        String selectionClass() { return selectionClass; }
        String detail() { return detail; }

        boolean directRegionLike() {
            return shouldCommitRegion(directRegion, text, rawBounds != null
                    && rawBounds.width() > 0f && rawBounds.height() > 0f);
        }
    }

    static boolean shouldCommitRegion(boolean regionModelPresent,
                                      String selectedText, boolean validBounds) {
        return regionModelPresent && (selectedText == null || selectedText.isBlank())
                && validBounds;
    }

    static final class Candidate {
        final Field userSelectionField;
        final Method textMethod;
        final Method boundsMethod;
        final Method pointMethod;
        final int score;

        Candidate(Field userSelectionField, Method textMethod, Method boundsMethod,
                  Method pointMethod, int score) {
            this.userSelectionField = userSelectionField;
            this.textMethod = textMethod;
            this.boundsMethod = boundsMethod;
            this.pointMethod = pointMethod;
            this.score = score;
        }
    }

    private GoogleLensDynamicResolver() {}
}
