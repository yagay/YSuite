package com.yagay.YFloat;

import android.widget.LinearLayout;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.yagay.yui.YViewLayout;

/**
 * PP-StructureV3 COMPLETE remote pipeline is explicit opt-in. No screenshot
 * leaves the phone when normal ML Kit, local Paddle, or Google modes are used.
 */
final class PaddleStructureFullSettingsV3 {
    static void add(SettingsActivity activity,LinearLayout parent,FloatSettings fs) {
        boolean zh=java.util.Locale.getDefault().getLanguage().startsWith("zh");
        LinearLayout block=YViewLayout.settingBlock(activity);
        block.addView(YViewLayout.text(activity,
            zh?"PP-StructureV3 完整文档解析":"PP-StructureV3 full document parsing"));
        block.addView(YViewLayout.rowSubtitle(activity,
            zh?"使用官方独立推理服务，可处理表格、公式、印章、图表、版面和阅读顺序。" +
              "仅选择完整模式时才上传截图；需要自行配置 HTTPS 服务。":
               "Uses an official PaddleOCR service for tables, formulas, seals, charts, " +
               "layout and reading order. Images upload only in Full mode. HTTPS is required."));

        TextInputLayout field=new TextInputLayout(activity);
        field.setHint(zh?"服务 HTTPS 地址":"HTTPS service address");
        TextInputEditText input=new TextInputEditText(activity);
        input.setSingleLine(true);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT |
            android.text.InputType.TYPE_TEXT_VARIATION_URI);
        input.setText(PaddleStructureFullV3.configuredEndpoint(activity));
        field.addView(input);
        block.addView(field);

        MaterialButton save=YViewLayout.compactButton(activity,
            zh?"保存服务地址":"Save service address");
        save.setOnClickListener(v->{
            String entered=input.getText()==null?"":input.getText().toString().trim();
            String validation=PaddleStructureFullV3.validateEndpoint(entered);
            if(!validation.isEmpty()){
                field.setError(validation);
                return;
            }
            field.setError(null);
            fs.setString(PaddleStructureFullV3.K_ENDPOINT,entered);
            Toast.makeText(activity,zh?"服务地址已保存":"Service address saved",
                Toast.LENGTH_SHORT).show();
        });
        LinearLayout actions=YViewLayout.buttonRow(activity);
        YViewLayout.addAction(actions,save);
        block.addView(actions);
        YViewLayout.addRow(parent,block);

        YViewLayout.addRow(parent,YViewLayout.switchContainer(YViewLayout.switchRow(
            activity,zh?"包含 Markdown 图片":"Include Markdown images",
            zh?"服务响应可能更大":"Response may be much larger",
            activity.getSharedPreferences(FloatSettings.PREF,0).getBoolean(
                PaddleStructureFullV3.K_MARKDOWN_IMAGES,false),
            (button,checked)->fs.setBoolean(
                PaddleStructureFullV3.K_MARKDOWN_IMAGES,checked))));
        YViewLayout.addRow(parent,YViewLayout.switchContainer(YViewLayout.switchRow(
            activity,zh?"返回 Word 文档":"Request Word export",
            zh?"要求服务返回 DOCX；会增加数据量":"Request DOCX; increases response size",
            activity.getSharedPreferences(FloatSettings.PREF,0).getBoolean(
                PaddleStructureFullV3.K_DOCX,false),
            (button,checked)->fs.setBoolean(
                PaddleStructureFullV3.K_DOCX,checked))));
        YViewLayout.addRow(parent,YViewLayout.rowSubtitle(activity,
            zh?"详细结果的 JSON 保存在应用私有目录的 ocr_results/ppstructure_full_v3/。" +
               "识别引擎选择“PP-StructureV3 完整”后才会请求服务。":
               "Full JSON is saved privately in ocr_results/ppstructure_full_v3/. " +
               "Only choosing the PP-StructureV3 Full OCR engine sends images."));
    }
    private PaddleStructureFullSettingsV3(){}
}
