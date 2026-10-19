package com.yagay.YFloat;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Per-workflow ON-DEVICE Paddle stages. Detection and recognition are core,
 * always present. All other stages are individually selectable and their
 * expensive sessions must not be initialized when a stage is disabled.
 *
 * The master "extended" switch gates optional document operations without
 * erasing individual choices. Circle defaults to fast text-only OCR; regular
 * OCR retains the historical full-document behavior by default.
 */
public final class PaddleFlowOptions {
    public static final String CIRCLE = "circle";
    public static final String DOCUMENT = "document";
    private static final String PREFIX="paddle_flow_";
    public final boolean circle;
    public final boolean extended;
    public final boolean fastDetection;
    public final boolean lineOrientation;
    public final boolean documentOrientation;
    public final boolean rotationFallback;
    public final boolean layout;
    public final boolean tables;
    public final boolean uvdoc;
    public final boolean seals;
    public final boolean formulas;
    public final boolean charts;
    public final int detectionLimit;

    public static String key(boolean circle,String setting) {
        return PREFIX+(circle?CIRCLE:DOCUMENT)+"_"+setting;
    }
    public static boolean defaultValue(boolean circle,String option) {
        return switch(option) {
            case "extended" -> !circle;
            case "fast_detection" -> circle;
            // Optional stages are independently configured and gated by
            // "extended". Enabling full Circle document OCR enables installed
            // features by default; the user may selectively turn them off.
            // Full profile tries each separately installed orientation model.
            // Slow brute-force 4-way OCR remains opt-in even for document OCR.
            case "line_orientation", "document_orientation" -> true;
            case "rotation_fallback" -> false;
            case "layout", "tables", "uvdoc", "seals", "formulas", "charts" -> true;
            default -> false;
        };
    }
    public static PaddleFlowOptions read(Context context,boolean circle) {
        return new PaddleFlowOptions(
                context.getApplicationContext().getSharedPreferences(
                        FloatSettings.PREF,Context.MODE_PRIVATE),circle);
    }

    PaddleFlowOptions(SharedPreferences preferences,boolean circle) {
        this.circle=circle;
        extended=get(preferences,circle,"extended");
        fastDetection=get(preferences,circle,"fast_detection");
        lineOrientation=extended && get(preferences,circle,"line_orientation");
        documentOrientation=extended && get(preferences,circle,"document_orientation");
        rotationFallback=extended && preferences.getBoolean(key(circle,"rotation_fallback"),
                preferences.getBoolean("paddle_v2_auto_orientation",false));
        layout=extended && get(preferences,circle,"layout");
        tables=extended && get(preferences,circle,"tables");
        uvdoc=extended && get(preferences,circle,"uvdoc");
        seals=extended && get(preferences,circle,"seals");
        formulas=extended && get(preferences,circle,"formulas");
        charts=extended && get(preferences,circle,"charts");
        detectionLimit=circle?1920:2240;
    }

    private static boolean get(SharedPreferences p,boolean circle,String key) {
        return p.getBoolean(key(circle,key),defaultValue(circle,key));
    }

    public String summary() {
        return "profile="+(circle?CIRCLE:DOCUMENT)+
                " extended="+extended+" fastDet="+fastDetection+
                " lineOrientation="+lineOrientation+
                " docOrientation="+documentOrientation+
                " rotation="+rotationFallback+" layout="+layout+
                " tables="+tables+" uvdoc="+uvdoc+" seals="+seals+
                " formula="+formulas+" charts="+charts;
    }
}
