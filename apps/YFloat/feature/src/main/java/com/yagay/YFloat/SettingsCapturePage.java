package com.yagay.YFloat;

import android.widget.LinearLayout;
import android.widget.TextView;

/** Screenshot, result and OCR settings page. */
final class SettingsCapturePage {
    static LinearLayout build(SettingsActivity activity, FloatSettings fs) {
        SettingsPageUi ui = new SettingsPageUi(activity, fs);
        LinearLayout root = AppUi.pageRoot(activity,
                activity.getString(R.string.yfloat_capture_ocr_settings),
                activity.getString(R.string.yfloat_capture_page_desc));

        AppUi.Section capture = AppUi.section(activity,
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
        AppUi.addSection(root, capture);

        AppUi.Section circleBorder = AppUi.section(activity,
                activity.getString(R.string.yfloat_circle_border_section),
                activity.getString(R.string.yfloat_circle_border_desc));
        CircleBorderSettingsUi.add(activity, fs, circleBorder.body);
        AppUi.addSection(root, circleBorder);

        AppUi.Section circleOcr = AppUi.section(activity,
                activity.getString(R.string.yfloat_native_circle_ocr_section),
                activity.getString(R.string.yfloat_native_circle_ocr_desc));
        ui.circleFullOcrEngineSpinner(circleOcr.body);
        ui.circleCorrectionEngineSpinner(circleOcr.body);
        AppUi.addSection(root, circleOcr);

        AppUi.Section result = AppUi.section(activity,
                activity.getString(R.string.yfloat_ocr_result_section),
                activity.getString(R.string.yfloat_ocr_result_desc));
        ui.check(result.body, activity.getString(R.string.yfloat_show_original_selection), null,
                FloatSettings.K_OCR_SHOW_IMAGE, fs.ocrShowImage());
        ui.check(result.body, activity.getString(R.string.yfloat_show_text), null,
                FloatSettings.K_OCR_SHOW_TEXT, fs.ocrShowText());
        ui.check(result.body, activity.getString(R.string.yfloat_collapse_results), null,
                FloatSettings.K_OCR_COLLAPSE, fs.ocrCollapse());
        ui.ocrEngineSpinner(result.body);
        AppUi.addSection(root, result);

        AppUi.Section models = AppUi.section(activity,
                activity.getString(R.string.yfloat_local_models_section),
                activity.getString(R.string.yfloat_local_models_desc));
        try {
            ui.addOcrModelRow(models.body, OcrModelManager.TINY);
            ui.addOcrModelRow(models.body, OcrModelManager.SMALL);
            ui.addOcrModelRow(models.body, OcrModelManager.MEDIUM);
        } catch (Throwable t) {
            DiagnosticLog.i(activity, "OCR_MODEL_UI",
                    "init failure=" + t.getClass().getSimpleName() + ":" + String.valueOf(t.getMessage()));
            TextView err = AppUi.caption(activity,
                    activity.getString(R.string.yfloat_local_models_unavailable), 13);
            LinearLayout row = AppUi.baseRow(activity);
            row.addView(err, new LinearLayout.LayoutParams(-1, -2));
            AppUi.addRow(models.body, row);
        }
        AppUi.addSection(root, models);

        AppUi.Section languages = AppUi.section(activity,
                activity.getString(R.string.yfloat_recognition_languages_section),
                activity.getString(R.string.yfloat_recognition_languages_desc));
        ui.addOcrLanguageChecks(languages.body);
        AppUi.addSection(root, languages);
        return root;
    }

    private SettingsCapturePage() {}
}
