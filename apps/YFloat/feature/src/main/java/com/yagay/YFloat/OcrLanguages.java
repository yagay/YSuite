package com.yagay.YFloat;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * OCR language preference shared by Settings and the existing OCR pipeline.
 *
 * PaddleOCR currently runs one local multilingual CTC recognizer. Language
 * choices are user preferences, not a promise of separate ML Kit recognizers.
 * Future downloadable Paddle language packs may use these settings for routing.
 * No legacy OCR engine selection is changed by this class.
 */
public final class OcrLanguages {
    public static final String KEY = "ocr_languages_v2";
    public static final String ZH_HANS = "zh-Hans";
    public static final String ZH_HANT = "zh-Hant";
    public static final String ENGLISH = "en";

    private static final Set<String> ALLOWED = Set.of(ZH_HANS, ZH_HANT, ENGLISH);

    public static Set<String> get(Context context) {
        SharedPreferences p = prefs(context);
        Set<String> stored = null;
        try {
            stored = p.getStringSet(KEY, null);
        } catch (ClassCastException ignored) {
            p.edit().remove(KEY).apply();
        }

        Set<String> out = sanitize(stored);
        if (!out.isEmpty()) return out;

        // Migrate the old two-state setting without changing existing behaviour.
        // old=1 meant Latin-only; old=0 meant Chinese + Latin.
        int legacy = readLegacyType(p);
        if (legacy == 1) {
            out.add(ENGLISH);
        } else {
            out.add(ZH_HANS);
            out.add(ZH_HANT);
            out.add(ENGLISH);
        }
        persist(p, out);
        return new HashSet<>(out);
    }

    public static Set<String> save(Context context, Set<String> values) {
        Set<String> out = sanitize(values);
        if (out.isEmpty()) out.add(ENGLISH);
        persist(prefs(context), out);
        return new HashSet<>(out);
    }

    public static boolean chineseEnabled(Set<String> values) {
        return values != null && (values.contains(ZH_HANS) || values.contains(ZH_HANT));
    }

    public static boolean englishEnabled(Set<String> values) {
        return values != null && values.contains(ENGLISH);
    }

    public static Set<String> all() {
        return new HashSet<>(ALLOWED);
    }

    public static Set<String> allowed() {
        return Collections.unmodifiableSet(ALLOWED);
    }

    private static Set<String> sanitize(Set<String> values) {
        Set<String> out = new HashSet<>();
        if (values == null) return out;
        for (String value : values) if (ALLOWED.contains(value)) out.add(value);
        return out;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(FloatSettings.PREF, Context.MODE_PRIVATE);
    }

    private static int readLegacyType(SharedPreferences p) {
        try {
            Object raw = p.getAll().get(FloatSettings.K_OCR_TYPE);
            if (raw instanceof Number) return ((Number) raw).intValue() == 1 ? 1 : 0;
            if (raw instanceof String) return Integer.parseInt(((String) raw).trim()) == 1 ? 1 : 0;
            if (raw instanceof Boolean) return ((Boolean) raw) ? 1 : 0;
        } catch (Throwable ignored) {
        }
        return 0;
    }

    private static void persist(SharedPreferences p, Set<String> values) {
        p.edit().putStringSet(KEY,new HashSet<>(values)).apply();
    }

    private OcrLanguages() {
    }
}
