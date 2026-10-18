package com.yagay.YFloat;

import com.yagay.yui.YViewLayout;
import com.yagay.yui.YViewSection;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.google.android.material.button.MaterialButton;

/** Screenshot, result and OCR settings page. */
final class SettingsCapturePage {
    static LinearLayout build(SettingsActivity activity, FloatSettings fs) {
        SettingsPageUi ui = new SettingsPageUi(activity, fs);
        LinearLayout root = YViewLayout.pageRoot(activity,
                activity.getString(R.string.yfloat_capture_ocr_settings),
                activity.getString(R.string.yfloat_capture_page_desc));

        YViewSection capture = YViewLayout.section(activity,
                activity.getString(R.string.yfloat_capture_section),
                activity.getString(R.string.yfloat_capture_section_desc));
        ui.check(capture.body, activity.getString(R.string.yfloat_keep_float_in_screenshot), null,
                FloatSettings.K_KEEP_IN_SCREENSHOT, fs.keepInScreenshot());
        ui.check(capture.body,
                activity.getString(R.string.yfloat_capture_status_bar),
                activity.getString(R.string.yfloat_capture_status_bar_desc),
                FloatSettings.K_KEEP_STATUS_BAR, fs.keepStatusBarInScreenshot());
        ui.check(capture.body,
                activity.getString(R.string.yfloat_capture_navigation_bar),
                activity.getString(R.string.yfloat_capture_navigation_bar_desc),
                CaptureSystemBarsPolicy.K_KEEP_NAVIGATION_BAR,
                CaptureSystemBarsPolicy.keepNavigationBar(activity));
        ui.check(capture.body,
                activity.getString(R.string.yfloat_prefer_accessibility_screenshot),
                activity.getString(R.string.yfloat_prefer_accessibility_screenshot_desc),
                FloatSettings.K_ACCESSIBILITY_SCREENSHOT, fs.accessibilityScreenshot());
        YViewLayout.addSection(root, capture);

        // Module page and global YUI editor share the same appearance tokens.
        {
            YViewSection circleBorder = YViewLayout.section(activity,
                    activity.getString(R.string.yfloat_circle_border_section),
                    activity.getString(R.string.yfloat_circle_border_desc));
            CircleBorderSettingsUi.add(activity, fs, circleBorder.body);
            YViewLayout.addSection(root, circleBorder);
        }

        YViewSection circleOcr = YViewLayout.section(activity,
                activity.getString(R.string.yfloat_native_circle_ocr_section),
                activity.getString(R.string.yfloat_native_circle_ocr_desc));
        ui.circleFullOcrEngineSpinner(circleOcr.body);
        ui.check(circleOcr.body,
                java.util.Locale.getDefault().getLanguage().startsWith("zh")
                    ? "Paddle 智能空格修正" : "Paddle spacing correction",
                java.util.Locale.getDefault().getLanguage().startsWith("zh")
                    ? "根据 CTC 字符置信度和边界框修复有依据的错误空格；保留英文正常分词"
                    : "Fix evidence-backed CTC spacing errors without losing word geometry",
                "paddle_v2_spacing",
                activity.getSharedPreferences(FloatSettings.PREF,android.content.Context.MODE_PRIVATE)
                    .getBoolean("paddle_v2_spacing",true));
        ui.circleCorrectionEngineSpinner(circleOcr.body);
        ui.check(circleOcr.body,
                java.util.Locale.getDefault().getLanguage().startsWith("zh")
                    ? "Paddle 自动检测旋转方向（较慢）" : "Paddle auto-orientation (slower)",
                java.util.Locale.getDefault().getLanguage().startsWith("zh")
                    ? "离线对比原图和 90°、180°、270° 识别置信度，回映射原图坐标"
                    : "Offline multi-pass OCR at 0/90/180/270 degrees, keeping source boxes",
                "paddle_v2_auto_orientation",
                activity.getSharedPreferences(FloatSettings.PREF,android.content.Context.MODE_PRIVATE)
                    .getBoolean("paddle_v2_auto_orientation",false));
        YViewLayout.addSection(root, circleOcr);

        YViewSection result = YViewLayout.section(activity,
                activity.getString(R.string.yfloat_ocr_result_section),
                activity.getString(R.string.yfloat_ocr_result_desc));
        ui.check(result.body, activity.getString(R.string.yfloat_show_original_selection), null,
                FloatSettings.K_OCR_SHOW_IMAGE, fs.ocrShowImage());
        ui.check(result.body, activity.getString(R.string.yfloat_show_text), null,
                FloatSettings.K_OCR_SHOW_TEXT, fs.ocrShowText());
        ui.check(result.body, activity.getString(R.string.yfloat_collapse_results), null,
                FloatSettings.K_OCR_COLLAPSE, fs.ocrCollapse());
        ui.ocrEngineSpinner(result.body);
        YViewLayout.addSection(root, result);

        YViewSection models = YViewLayout.section(activity,
                activity.getString(R.string.yfloat_local_models_section),
                activity.getString(R.string.yfloat_local_models_desc));
        try {
            ui.addOcrModelRow(models.body, OcrModelManager.TINY);
            ui.addOcrModelRow(models.body, OcrModelManager.SMALL);
            ui.addOcrModelRow(models.body, OcrModelManager.MEDIUM);
            PaddleStructureSettingsV2.add(activity,models.body);
            PaddleLocalModelSettingsV3.add(activity,models.body);
        } catch (Throwable t) {
            DiagnosticLog.i(activity, "OCR_MODEL_UI",
                    "init failure=" + t.getClass().getSimpleName() + ":" + String.valueOf(t.getMessage()));
            TextView err = YViewLayout.rowSubtitle(activity,
                    activity.getString(R.string.yfloat_local_models_unavailable));
            LinearLayout row = YViewLayout.baseRow(activity);
            row.addView(err, new LinearLayout.LayoutParams(-1, -2));
            YViewLayout.addRow(models.body, row);
        }
        YViewLayout.addSection(root, models);

        YViewSection languages = YViewLayout.section(activity,
                activity.getString(R.string.yfloat_recognition_languages_section),
                activity.getString(R.string.yfloat_recognition_languages_desc));
        ui.addOcrLanguageChecks(languages.body);
        YViewLayout.addSection(root, languages);
        return root;
    }

    private SettingsCapturePage() {}
}
