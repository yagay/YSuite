package com.yagay.YFloat;

import com.yagay.yui.YViewLayout;
import com.yagay.yui.YViewSection;
import android.widget.LinearLayout;
import android.widget.TextView;

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

        // Active selection-border visuals now belong to the YSuite appearance editor.
        if (!"com.yagay.YSuite".equals(activity.getPackageName())) {
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
        ui.circleCorrectionEngineSpinner(circleOcr.body);
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
        } catch (Throwable t) {
            DiagnosticLog.i(activity, "OCR_MODEL_UI",
                    "init failure=" + t.getClass().getSimpleName() + ":" + String.valueOf(t.getMessage()));
            TextView err = YViewLayout.caption(activity,
                    activity.getString(R.string.yfloat_local_models_unavailable), 13);
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
