package com.yagay.YFloat.hook;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.util.Log;

import com.yagay.YFloat.GoogleCtsContract;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

import io.github.libxposed.api.XposedModule;

/**
 * Captures the frozen Google Lens frame from Google App's own data path.
 *
 * <p>Android's stable VoiceInteractionSession screenshot callback is the primary
 * original-Bitmap source (hooked by GoogleCtsLifecycleHooks). Obfuscated Lens
 * wrappers remain optional fast paths only, never required for future versions.</p>
 */
final class GoogleLensFrameCapture {
    private static final String TAG = "YFloat-GoogleCTS";
    private static final String VOICE_SESSION_IMPL = "egzd";
    private static final String INITIAL_ACTIVITY_DATA = "drsz";
    private static final String INITIAL_ACTIVITY_PARSER = "drtc";
    private static final String IN_PROCESS_BITMAP =
            "com.google.android.libraries.lens.view.InProcessBitmap";

    private final XposedModule module;
    private final ClassLoader classLoader;
    private final BooleanSupplier active;
    private final Consumer<Bitmap> sink;
    private final BiConsumer<String, String> reporter;
    private final Supplier<Context> contextSupplier;
    private final ExecutorService sourceIo = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "YFloat-Google-OriginalSource");
        t.setDaemon(true);
        return t;
    });

    static final class Binding {
        final boolean voiceScreenshot;
        final boolean frameworkScreenshot;
        final boolean initialActivityData;
        final boolean initialActivityParser;
        final boolean inProcessBitmap;
        final int confidence;
        final String detail;

        Binding(boolean voiceScreenshot, boolean initialActivityData,
                boolean initialActivityParser, boolean inProcessBitmap,
                int confidence, String detail) {
            this(voiceScreenshot, initialActivityData, initialActivityParser,
                    inProcessBitmap, confidence, detail, false);
        }

        Binding(boolean voiceScreenshot, boolean initialActivityData,
                boolean initialActivityParser, boolean inProcessBitmap,
                int confidence, String detail, boolean frameworkScreenshot) {
            this.voiceScreenshot = voiceScreenshot;
            this.frameworkScreenshot = frameworkScreenshot;
            this.initialActivityData = initialActivityData;
            this.initialActivityParser = initialActivityParser;
            this.inProcessBitmap = inProcessBitmap;
            this.confidence = confidence;
            this.detail = detail == null ? "" : detail;
        }

        boolean available() {
            return frameworkScreenshot || voiceScreenshot || initialActivityData
                    || initialActivityParser || inProcessBitmap;
        }

        String source() {
            StringBuilder out = new StringBuilder();
            if (frameworkScreenshot) out.append("framework-voice");
            if (voiceScreenshot) append(out, "legacy-voice");
            if (initialActivityData) append(out, "initial-data");
            if (initialActivityParser) append(out, "initial-parser");
            if (inProcessBitmap) append(out, "in-process");
            return out.length() == 0 ? "none" : out.toString();
        }

        private static void append(StringBuilder out, String value) {
            if (out.length() > 0) out.append('+');
            out.append(value);
        }
    }

    static Binding resolve(ClassLoader loader) {
        if (loader == null) return new Binding(false, false, false, false, 0, "classLoader=null");
        boolean framework = hasFrameworkScreenshotCallback(loader);
        boolean voice = hasMethod(loader, VOICE_SESSION_IMPL, "onHandleScreenshot",
                Bitmap.class, Bitmap.class);
        boolean data = hasNoArgBitmapGetter(loader, INITIAL_ACTIVITY_DATA);
        boolean parser = hasReturnTypeMethod(loader, INITIAL_ACTIVITY_PARSER, INITIAL_ACTIVITY_DATA);
        boolean inProcess = hasNoArgBitmapGetter(loader, IN_PROCESS_BITMAP);
        int confidence = framework ? 50 : 0;
        if (voice) confidence += 35;
        if (data) confidence += 25;
        if (parser) confidence += 15;
        if (inProcess) confidence += 35;
        return new Binding(voice, data, parser, inProcess, confidence,
                "frameworkCallbackCandidate=" + framework + " (not runtime proof)"
                        + " voice=" + voice + " data=" + data
                        + " parser=" + parser + " inProcess=" + inProcess, framework);
    }

    GoogleLensFrameCapture(XposedModule module,
                           ClassLoader classLoader,
                           BooleanSupplier active,
                           Consumer<Bitmap> sink,
                           BiConsumer<String, String> reporter) {
        this(module, classLoader, active, sink, reporter, () -> null);
    }

    GoogleLensFrameCapture(XposedModule module,
                           ClassLoader classLoader,
                           BooleanSupplier active,
                           Consumer<Bitmap> sink,
                           BiConsumer<String, String> reporter,
                           Supplier<Context> contextSupplier) {
        this.module = module;
        this.classLoader = classLoader;
        this.active = active;
        this.sink = sink;
        this.reporter = reporter;
        this.contextSupplier = contextSupplier;
    }

    int install() {
        return install(resolve(classLoader));
    }

    int install(Binding binding) {
        if (binding == null) binding = resolve(classLoader);
        int hooks = 0;
        if (binding.voiceScreenshot) hooks += hookGoogleVoiceSessionScreenshot();
        if (binding.initialActivityData) hooks += hookInitialActivityDataBitmap();
        if (binding.initialActivityParser) hooks += hookInitialActivityParser();
        if (binding.inProcessBitmap) hooks += hookInProcessBitmap();
        module.log(Log.INFO, TAG, "Google original-frame candidate sources=" + binding.source()
                + " confidence=" + binding.confidence
                + " internalHooks=" + hooks
                + " runtimeValidated=false detail=" + binding.detail);
        return hooks;
    }

    void captureFromIntent(Intent intent) {
        if (!active.getAsBoolean() || intent == null) return;
        Bundle extras;
        try {
            extras = intent.getExtras();
        } catch (Throwable t) {
            return;
        }
        if (extras == null) return;

        captureCandidate(safeGet(extras, "injected_image_bitmap"),
                "intent.injected_image_bitmap");
        captureCandidate(safeGet(extras, "bootstrap_image"),
                "intent.bootstrap_image");
        captureCandidate(safeGet(extras, GoogleCtsContract.CONTEXTUAL_SCREENSHOT),
                "intent.contextual_screenshot");
        // 17.64 LensientActivity starts with injected_image_bitmap,
        // bootstrap_image and injected_image_uri, but on some devices none
        // are direct Bitmap parcelables. Inspect bounded, source-owned encoded
        // image/Uri types; never recapture Android's display.
        String[] keys = {"injected_image_bitmap", "bootstrap_image",
                "injected_image_payload", "injected_image_uri",
                GoogleCtsContract.CONTEXTUAL_SCREENSHOT};
        for (String key : keys) {
            Object value = safeGet(extras, key);
            if (value == null) continue;
            reporter.accept("GOOGLE_FRAME_SOURCE_PROBE",
                    "intent key=" + key + " type=" + value.getClass().getName());
            if (value instanceof byte[] encoded) {
                if (encoded.length <= 64 * 1024 * 1024 && encoded.length > 128) {
                    sourceIo.execute(() -> decodeOriginalBytes(encoded, key));
                }
            } else {
                Uri uri = value instanceof Uri u ? u : null;
                if (uri == null && "injected_image_uri".equals(key)
                        && value instanceof String raw && raw.startsWith("content://")) {
                    try { uri = Uri.parse(raw); } catch (Throwable ignored) { }
                }
                if (uri != null && "content".equals(uri.getScheme())) {
                    final Uri imageUri = uri;
                    sourceIo.execute(() -> decodeOriginalUri(imageUri, key));
                }
            }
        }
    }

    private void decodeOriginalBytes(byte[] encoded, String key) {
        if (!active.getAsBoolean()) return;
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(encoded, 0, encoded.length, options);
            if (!GoogleOriginalFramePolicy.validDecodedSize(options.outWidth,
                    options.outHeight)) return;
            options.inJustDecodeBounds = false;
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            Bitmap frame = BitmapFactory.decodeByteArray(encoded, 0, encoded.length, options);
            if (frame == null) return;
            try { captureCandidate(frame, "intent." + key + ".encoded_original"); }
            finally { if (!frame.isRecycled()) frame.recycle(); }
        } catch (Throwable t) {
            reporter.accept("GOOGLE_FRAME_SOURCE_FAILURE",
                    "encoded key=" + key + " error=" + t.getClass().getSimpleName());
        }
    }

    private void decodeOriginalUri(Uri uri, String key) {
        if (!active.getAsBoolean() || uri == null) return;
        Context app = contextSupplier.get();
        if (app == null) return;
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            try (InputStream input = app.getContentResolver().openInputStream(uri)) {
                if (input == null) return;
                BitmapFactory.decodeStream(input, null, options);
            }
            if (!GoogleOriginalFramePolicy.validDecodedSize(options.outWidth,
                    options.outHeight)) return;
            options.inJustDecodeBounds = false;
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            try (InputStream input = app.getContentResolver().openInputStream(uri)) {
                if (input == null) return;
                Bitmap frame = BitmapFactory.decodeStream(input, null, options);
                if (frame == null) return;
                try { captureCandidate(frame, "intent." + key + ".source_uri"); }
                finally { if (!frame.isRecycled()) frame.recycle(); }
            }
        } catch (Throwable t) {
            reporter.accept("GOOGLE_FRAME_SOURCE_FAILURE",
                    "uri key=" + key + " error=" + t.getClass().getSimpleName());
        }
    }

    /** Search only the actual Google frozen-image view for an already decoded
     * screenshot. BitmapDrawable/Bitmap fields are data owned by Google, not
     * PixelCopy, MediaProjection, Canvas.draw or a local screenshot.
     */
    void probeFrozenView(Activity owner, String reason) {
        if (!active.getAsBoolean() || owner == null || owner.getWindow() == null) return;
        try {
            View root = owner.getWindow().getDecorView();
            View frozen = GoogleLensViewIntrospection.findByClassName(
                    root, GoogleLens1758Profile.FROZEN_IMAGE_VIEW);
            if (frozen == null || frozen.getWidth() <= 0 || frozen.getHeight() <= 0) return;
            Bitmap result = readFrozenBitmap(frozen, frozen.getWidth(), frozen.getHeight(),
                    0, new int[]{0});
            if (result != null) {
                captureCandidate(result, "FrozenImageView.original_object_" + reason);
            } else {
                reporter.accept("GOOGLE_FRAME_SOURCE_PROBE",
                        "frozen source unavailable reason=" + reason
                        + " wh=" + frozen.getWidth() + "x" + frozen.getHeight());
            }
        } catch (Throwable t) {
            reporter.accept("GOOGLE_FRAME_SOURCE_FAILURE",
                    "frozen reason=" + reason + " error=" + t.getClass().getSimpleName());
        }
    }

    private Bitmap readFrozenBitmap(View root, int width, int height, int depth, int[] seen) {
        if (root == null || depth > 4 || ++seen[0] > 48) return null;
        Drawable drawable = root instanceof ImageView image ? image.getDrawable()
                : root.getBackground();
        Bitmap drawableBitmap = drawable instanceof BitmapDrawable bitmapDrawable
                ? bitmapDrawable.getBitmap() : null;
        if (validFrozenBitmap(drawableBitmap, width, height)) return drawableBitmap;

        // Restrict private-field reflection to the known Google FrozenImageView
        // itself. Other Android views may contain unrelated app imagery.
        if (GoogleLens1758Profile.FROZEN_IMAGE_VIEW.equals(root.getClass().getName())) {
            int scanned = 0;
            for (Field field : GoogleReflection.instanceFields(root.getClass())) {
                if (++scanned > 64) break;
                if (field.getType() != Bitmap.class) continue;
                try {
                    field.setAccessible(true);
                    Object value = field.get(root);
                    if (value instanceof Bitmap bitmap
                            && validFrozenBitmap(bitmap, width, height)) return bitmap;
                } catch (Throwable ignored) { }
            }
        }
        if (root instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount() && seen[0] < 48; i++) {
                Bitmap found = readFrozenBitmap(group.getChildAt(i), width, height,
                        depth + 1, seen);
                if (found != null) return found;
            }
        }
        return null;
    }

    private boolean validFrozenBitmap(Bitmap candidate, int width, int height) {
        return candidate != null && !candidate.isRecycled()
                && GoogleOriginalFramePolicy.matchesFrozenView(
                        candidate.getWidth(), candidate.getHeight(), width, height);
    }

    private int hookGoogleVoiceSessionScreenshot() {
        try {
            Class<?> cls = Class.forName(VOICE_SESSION_IMPL, false, classLoader);
            int count = 0;
            for (Method method : GoogleReflection.declaredMethods(cls)) {
                Class<?>[] p = method.getParameterTypes();
                if (!"onHandleScreenshot".equals(method.getName())
                        || p.length != 1 || p[0] != Bitmap.class) {
                    continue;
                }
                module.hook(method).intercept(chain -> {
                    if (active.getAsBoolean()) {
                        captureCandidate(chain.getArg(0), "egzd.onHandleScreenshot");
                    }
                    return chain.proceed();
                });
                count++;
            }
            return count;
        } catch (Throwable t) {
            module.log(Log.INFO, TAG, "Google voice screenshot implementation unavailable", t);
            return 0;
        }
    }

    private int hookInitialActivityDataBitmap() {
        try {
            Class<?> cls = Class.forName(INITIAL_ACTIVITY_DATA, false, classLoader);
            int count = 0;
            for (Method method : GoogleReflection.declaredMethods(cls)) {
                if (method.getParameterCount() != 0
                        || method.getReturnType() != Bitmap.class) {
                    continue;
                }
                module.hook(method).intercept(chain -> {
                    Object result = chain.proceed();
                    if (active.getAsBoolean()) {
                        captureCandidate(result, "InitialActivityData.Bitmap()");
                    }
                    return result;
                });
                count++;
            }
            return count;
        } catch (Throwable t) {
            module.log(Log.INFO, TAG, "InitialActivityData bitmap getter unavailable", t);
            return 0;
        }
    }

    private int hookInitialActivityParser() {
        try {
            Class<?> cls = Class.forName(INITIAL_ACTIVITY_PARSER, false, classLoader);
            int count = 0;
            for (Method method : GoogleReflection.declaredMethods(cls)) {
                if (method.getParameterCount() > 2
                        || !INITIAL_ACTIVITY_DATA.equals(method.getReturnType().getName())) {
                    continue;
                }
                module.hook(method).intercept(chain -> {
                    Object result = chain.proceed();
                    if (active.getAsBoolean()) {
                        captureCandidate(result, "InitialActivityData.parser");
                    }
                    return result;
                });
                count++;
            }
            return count;
        } catch (Throwable t) {
            module.log(Log.INFO, TAG, "InitialActivityData parser unavailable", t);
            return 0;
        }
    }

    private int hookInProcessBitmap() {
        try {
            Class<?> cls = Class.forName(IN_PROCESS_BITMAP, false, classLoader);
            int count = 0;
            for (Method method : GoogleReflection.declaredMethods(cls)) {
                if (method.getParameterCount() != 0 || method.getReturnType() != Bitmap.class) {
                    continue;
                }
                module.hook(method).intercept(chain -> {
                    Object result = chain.proceed();
                    if (active.getAsBoolean()) {
                        captureCandidate(result,
                                "InProcessBitmap." + method.getName());
                    }
                    return result;
                });
                count++;
            }
            return count;
        } catch (Throwable t) {
            module.log(Log.INFO, TAG, "InProcessBitmap getter unavailable", t);
            return 0;
        }
    }

    private void captureCandidate(Object value, String source) {
        if (!active.getAsBoolean() || value == null) return;
        Bitmap bitmap = extractBitmap(value, 0,
                Collections.newSetFromMap(new IdentityHashMap<>()));
        if (bitmap == null || bitmap.isRecycled()
                || bitmap.getWidth() <= 0 || bitmap.getHeight() <= 0) {
            return;
        }
        reporter.accept("GOOGLE_FRAME_CAPTURED",
                "source=" + source
                        + " bitmap=" + bitmap.getWidth() + "x" + bitmap.getHeight()
                        + "/" + bitmap.getConfig()
                        + " valueClass=" + value.getClass().getName());
        sink.accept(bitmap);
    }

    private Bitmap extractBitmap(Object value, int depth, Set<Object> seen) {
        if (value == null || depth > 3 || seen.contains(value)) return null;
        seen.add(value);
        if (value instanceof Bitmap bitmap) {
            return bitmap.isRecycled() ? null : bitmap;
        }

        String className = value.getClass().getName();
        boolean knownWrapper = INITIAL_ACTIVITY_DATA.equals(className)
                || IN_PROCESS_BITMAP.equals(className);
        if (!knownWrapper) return null;

        for (Method method : GoogleReflection.methodsInHierarchy(value.getClass())) {
            if (method.getParameterCount() != 0 || method.getReturnType() != Bitmap.class) {
                continue;
            }
            try {
                method.setAccessible(true);
                Object result = method.invoke(value);
                if (result instanceof Bitmap bitmap && !bitmap.isRecycled()) return bitmap;
            } catch (Throwable ignored) { }
        }

        Object field = GoogleReflection.readField(value, null, Bitmap.class.getName());
        return field instanceof Bitmap bitmap && !bitmap.isRecycled() ? bitmap : null;
    }

    private static boolean hasFrameworkScreenshotCallback(ClassLoader loader) {
        // Framework symbol, independent of Google App version and R8 renaming.
        try {
            Class<?> voice = Class.forName("android.service.voice.VoiceInteractionSession",
                    false, loader);
            for (Method method : GoogleReflection.declaredMethods(voice)) {
                if ("onHandleScreenshot".equals(method.getName())
                        && method.getReturnType() == void.class
                        && method.getParameterCount() == 1
                        && method.getParameterTypes()[0] == Bitmap.class) return true;
            }
        } catch (Throwable ignored) { }
        return false;
    }

    private static boolean hasMethod(ClassLoader loader, String className, String name,
                                     Class<?> parameterType, Class<?> expectedParameter) {
        try {
            Class<?> cls = Class.forName(className, false, loader);
            for (Method method : GoogleReflection.declaredMethods(cls)) {
                if (!name.equals(method.getName()) || method.getParameterCount() != 1) continue;
                Class<?> type = method.getParameterTypes()[0];
                if (type == parameterType || type == expectedParameter) return true;
            }
        } catch (Throwable ignored) { }
        return false;
    }

    private static boolean hasNoArgBitmapGetter(ClassLoader loader, String className) {
        try {
            Class<?> cls = Class.forName(className, false, loader);
            for (Method method : GoogleReflection.declaredMethods(cls)) {
                if (method.getParameterCount() == 0 && method.getReturnType() == Bitmap.class) {
                    return true;
                }
            }
        } catch (Throwable ignored) { }
        return false;
    }

    private static boolean hasReturnTypeMethod(
            ClassLoader loader, String className, String returnTypeName) {
        try {
            Class<?> cls = Class.forName(className, false, loader);
            for (Method method : GoogleReflection.declaredMethods(cls)) {
                if (returnTypeName.equals(method.getReturnType().getName())) return true;
            }
        } catch (Throwable ignored) { }
        return false;
    }

    private static Object safeGet(Bundle bundle, String key) {
        if (bundle == null || key == null || key.isBlank()) return null;
        try {
            return bundle.get(key);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
