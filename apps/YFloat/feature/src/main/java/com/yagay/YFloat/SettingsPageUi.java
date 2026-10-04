package com.yagay.YFloat;

import com.yagay.yui.YViewLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.HashSet;
import java.util.Set;
import java.util.function.IntConsumer;

/** Shared widgets for the focused settings pages. Keeps persistence behavior in one place. */
final class SettingsPageUi {
    private final SettingsActivity activity;
    private final FloatSettings fs;
    private final String[] actionIds = ActionId.availableIds();

    SettingsPageUi(SettingsActivity activity, FloatSettings fs) {
        this.activity = activity;
        this.fs = fs;
    }

    void check(LinearLayout parent, String title, String subtitle,
               String key, boolean current) {
        SwitchMaterial toggle = YViewLayout.switchRow(activity, title, subtitle, current,
                (button, checked) -> fs.setBoolean(key, checked));
        YViewLayout.addRow(parent, YViewLayout.switchContainer(toggle));
    }

    void fullscreenModeCheck(LinearLayout parent) {
        SwitchMaterial toggle = YViewLayout.switchRow(activity,
                activity.getString(R.string.yfloat_fullscreen_auto_hide),
                activity.getString(R.string.yfloat_fullscreen_auto_hide_desc),
                fs.fullscreenHideMode() != 0,
                (button, checked) -> fs.setInt(FloatSettings.K_HIDE_FULLSCREEN, checked ? 2 : 0));
        YViewLayout.addRow(parent, YViewLayout.switchContainer(toggle));
    }

    void seek(LinearLayout parent, String label, String key,
              int min, int max, int current, String suffix) {
        YViewLayout.addRow(parent, YViewLayout.sliderSetting(
                activity,
                label,
                min,
                max,
                current,
                value -> value + suffix,
                value -> fs.setInt(key, value)));
    }

    void actionSpinner(LinearLayout parent, String label, String key, String def) {
        String[] labels = new String[actionIds.length];
        int selected = 0;
        String now = fs.action(key, def);
        for (int i = 0; i < actionIds.length; i++) {
            labels[i] = ActionId.label(activity, actionIds[i]);
            if (actionIds[i].equals(now)) selected = i;
        }
        addPreferenceSpinner(parent, label, labels, selected, false,
                position -> fs.setString(key, actionIds[position]));
    }

    void styleSpinner(LinearLayout parent) {
        addIntPreferenceSpinner(parent, activity.getString(R.string.yfloat_icon_style),
                new String[]{
                        activity.getString(R.string.yfloat_icon_style_blue),
                        activity.getString(R.string.yfloat_icon_style_dark),
                        activity.getString(R.string.yfloat_icon_style_light),
                        activity.getString(R.string.yfloat_icon_style_custom),
                        activity.getString(R.string.yfloat_icon_style_slideshow)
                }, fs.style(), FloatSettings.K_STYLE, false);
    }

    void lineStyleSpinner(LinearLayout parent) {
        addIntPreferenceSpinner(parent, activity.getString(R.string.yfloat_trail_style),
                new String[]{
                        activity.getString(R.string.yfloat_trail_style_round),
                        activity.getString(R.string.yfloat_trail_style_square),
                        activity.getString(R.string.yfloat_trail_style_enhanced)
                }, fs.lineStyle(), FloatSettings.K_LINE_STYLE, false);
    }

    void ocrEngineSpinner(LinearLayout parent) {
        addIntPreferenceSpinner(parent, activity.getString(R.string.yfloat_ocr_engine), new String[]{
                activity.getString(R.string.yfloat_ocr_auto),
                activity.getString(R.string.yfloat_ocr_medium),
                activity.getString(R.string.yfloat_ocr_small),
                activity.getString(R.string.yfloat_ocr_mlkit)
        }, fs.ocrEngineMode(), FloatSettings.K_OCR_ENGINE, true);
    }

    void circleFullOcrEngineSpinner(LinearLayout parent) {
        addIntPreferenceSpinner(parent, activity.getString(R.string.yfloat_fullscreen_ocr_engine), new String[]{
                activity.getString(R.string.yfloat_ocr_mlkit),
                activity.getString(R.string.yfloat_ocr_tiny),
                activity.getString(R.string.yfloat_ocr_small),
                activity.getString(R.string.yfloat_ocr_medium)
        }, fs.circleFullOcrEngine(), FloatSettings.K_CIRCLE_FULL_OCR_ENGINE, true);
    }

    void circleCorrectionEngineSpinner(LinearLayout parent) {
        addIntPreferenceSpinner(parent, activity.getString(R.string.yfloat_correction_engine), new String[]{
                activity.getString(R.string.yfloat_correction_off),
                activity.getString(R.string.yfloat_ocr_tiny),
                activity.getString(R.string.yfloat_ocr_small),
                activity.getString(R.string.yfloat_ocr_medium)
        }, fs.circleCorrectionEngine(), FloatSettings.K_CIRCLE_CORRECTION_ENGINE, true);
    }

    private void addIntPreferenceSpinner(LinearLayout parent, String label, String[] labels,
                                         int selected, String key, boolean vertical) {
        addPreferenceSpinner(parent, label, labels, selected, vertical,
                position -> fs.setInt(key, position));
    }

    /** Shared YUI spinner row; feature code only binds the selected value. */
    private void addPreferenceSpinner(LinearLayout parent, String label, String[] labels,
                                      int selected, boolean vertical, IntConsumer onSelected) {
        String[] safeLabels = labels == null ? new String[0] : labels;
        if (safeLabels.length == 0) return;
        YViewLayout.addRow(parent, YViewLayout.spinnerSetting(
                activity,
                label,
                safeLabels,
                selected,
                vertical,
                onSelected));
    }

    void addOcrModelRow(LinearLayout parent, int model) {
        LinearLayout block = YViewLayout.settingBlock(activity);
        TextView status = YViewLayout.text(activity, OcrModelManager.displayName(model), 14, false);
        block.addView(status);

        LinearLayout buttons = YViewLayout.buttonRow(activity);
        MaterialButton download = YViewLayout.compactButton(activity,
                activity.getString(R.string.yfloat_model_download_update));
        MaterialButton remove = YViewLayout.compactButton(activity,
                activity.getString(R.string.yfloat_model_delete));
        YViewLayout.addAction(buttons, download);
        YViewLayout.addAction(buttons, remove);
        block.addView(buttons);
        YViewLayout.addRow(parent, block);

        Runnable refresh = () -> {
            try {
                boolean ready = OcrModelManager.isReady(activity, model);
                long mb = OcrModelManager.installedBytes(activity, model) / (1024 * 1024);
                status.setText(ready
                        ? activity.getString(R.string.yfloat_model_ready,
                                OcrModelManager.displayName(model), mb)
                        : activity.getString(R.string.yfloat_model_missing,
                                OcrModelManager.displayName(model)));
                status.setTextColor(ready ? YViewLayout.success(activity) : YViewLayout.textPrimary(activity));
                remove.setEnabled(ready && !OcrModelManager.isDownloading(model));
                download.setEnabled(!OcrModelManager.isDownloading(model));
            } catch (Throwable t) {
                status.setText(R.string.yfloat_model_status_failed);
                status.setTextColor(YViewLayout.warning(activity));
                remove.setEnabled(false);
                download.setEnabled(false);
                DiagnosticLog.i(activity, "OCR_MODEL_UI",
                        "refresh failure=" + t.getClass().getSimpleName());
            }
        };
        refresh.run();

        download.setOnClickListener(v -> {
            download.setEnabled(false);
            remove.setEnabled(false);
            try {
                OcrModelManager.download(activity, model, new OcrModelManager.Callback() {
                    @Override public void onProgress(String stage, int percent) {
                        status.setText(activity.getString(R.string.yfloat_model_progress,
                                OcrModelManager.displayName(model), stage, percent));
                    }
                    @Override public void onSuccess() {
                        Toast.makeText(activity,
                                R.string.yfloat_model_download_complete,
                                Toast.LENGTH_SHORT).show();
                        refresh.run();
                    }
                    @Override public void onFailure(String message) {
                        Toast.makeText(activity,
                                activity.getString(R.string.yfloat_model_download_failed, message),
                                Toast.LENGTH_LONG).show();
                        refresh.run();
                    }
                });
            } catch (Throwable t) {
                Toast.makeText(activity,
                        R.string.yfloat_model_download_init_failed,
                        Toast.LENGTH_LONG).show();
                DiagnosticLog.i(activity, "OCR_MODEL_UI",
                        "download launch failure=" + t.getClass().getSimpleName());
                refresh.run();
            }
        });
        remove.setOnClickListener(v -> {
            try { OcrModelManager.delete(activity, model); } catch (Throwable ignored) {}
            refresh.run();
        });
    }

    void addOcrLanguageChecks(LinearLayout parent) {
        Set<String> selected = new HashSet<>(OcrLanguages.get(activity));
        YViewLayout.addRow(parent, ocrLanguageCheck(
                activity.getString(R.string.yfloat_ocr_language_simplified_chinese),
                OcrLanguages.ZH_HANS, selected));
        YViewLayout.addRow(parent, ocrLanguageCheck(
                activity.getString(R.string.yfloat_ocr_language_traditional_chinese),
                OcrLanguages.ZH_HANT, selected));
        YViewLayout.addRow(parent, ocrLanguageCheck(
                activity.getString(R.string.yfloat_ocr_language_english),
                OcrLanguages.ENGLISH, selected));
    }

    private MaterialCheckBox ocrLanguageCheck(String label, String code, Set<String> selected) {
        MaterialCheckBox box = YViewLayout.checkBoxRow(
                activity,
                label,
                selected.contains(code),
                null);
        box.setTag(code);
        box.setOnCheckedChangeListener((button, checked) -> {
            String lang = String.valueOf(button.getTag());
            if (checked) selected.add(lang); else selected.remove(lang);
            if (selected.isEmpty()) {
                selected.add(lang);
                button.setChecked(true);
                Toast.makeText(activity,
                        R.string.yfloat_ocr_language_required,
                        Toast.LENGTH_SHORT).show();
                return;
            }
            OcrLanguages.save(activity, selected);
            DiagnosticLog.i(activity, "OCR_LANG", "selected=" + selected);
        });
        return box;
    }
}
