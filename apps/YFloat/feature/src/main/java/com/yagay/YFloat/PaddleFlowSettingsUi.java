package com.yagay.YFloat;

import android.content.Context;
import android.content.SharedPreferences;
import android.widget.LinearLayout;
import java.util.Locale;
import com.yagay.yui.YViewLayout;

/**
 * Independent Circle and document OCR execution profiles.
 * All controls persist directly to the same keys consumed at inference time.
 * Model download/availability is managed independently by the existing model UI.
 */
final class PaddleFlowSettingsUi {
    private record Stage(String key,String zh,String en,String detailZh,String detailEn) {}

    private static final Stage[] STAGES = {
        new Stage("extended","启用完整识别扩展","Enable extended OCR pipeline",
            "总开关。关闭时只执行检测、文字识别和文字空格处理；下方扩展选项保留但不执行",
            "Master switch. Off runs detection/recognition/spacing only; preserves saved choices"),
        new Stage("fast_detection","快速文字检测","Fast text detection",
            "只缩小检测张量，文字仍使用原始分辨率裁剪识别；未检出时自动提高检测精度",
            "Limit detector tensor only; recognize crops at original resolution; retry empty scans"),
        new Stage("line_orientation","文字行方向模型","Text-line orientation model",
            "需单独下载 PP-LCNet 文字行方向模型；可纠正倒置的文字行",
            "Needs separately downloaded PP-LCNet text-line model; correct inverted lines"),
        new Stage("document_orientation","文档方向模型","Document orientation model",
            "需单独下载文档方向模型；对整页方向进行预测",
            "Needs separately downloaded document orientation model"),
        new Stage("rotation_fallback","四方向 OCR 对比","OCR 4-way rotation verification",
            "没有可用文档方向预测时可运行多次 OCR，速度明显降低",
            "Extra 90/180/270-degree passes when no orientation prediction; much slower"),
        new Stage("layout","页面版面分析","Page layout analysis",
            "需单独下载 PP-DocLayout 模型；输出正文、图表等区域",
            "Needs PP-DocLayout download; detects regions and reading layout"),
        new Stage("tables","表格结构识别","Table structure recognition",
            "使用下载的 SLANet 或保守本地网格检测，支持表格结构导出",
            "Uses installed SLANet or conservative pixel-grid detection"),
        new Stage("uvdoc","UVDoc 文档展平","UVDoc page rectification",
            "需单独下载 UVDoc；生成独立的矫正文档结果，速度较慢",
            "Needs UVDoc download; generates separate rectified-page OCR output"),
        new Stage("seals","印章识别","Seal detection",
            "需单独下载印章模型；在版面识别到的印章区域进一步分析",
            "Needs seal model; operates on seal regions from layout"),
        new Stage("formulas","公式识别","Formula recognition",
            "需单独下载公式模型；处理版面中检测到的公式区域",
            "Needs formula model; processes formula regions found in layout"),
        new Stage("charts","图表可搜索文字","Chart OCR text extraction",
            "根据 OCR 和版面区域提取可见图表标签，不会猜测数据",
            "Extracts visible chart labels without inventing data series"),
        new Stage("spacing","OCR 空格修正","OCR spacing refinement",
            "修正明确的异常中文空格，保留英文分词和中英文间隔",
            "Fix CJK spacing only, preserving English and mixed-script words")
    };

    static void add(Context context,FloatSettings fs,LinearLayout parent,boolean circle){
        boolean zh=Locale.getDefault().getLanguage().startsWith("zh");
        SharedPreferences prefs=context.getSharedPreferences(FloatSettings.PREF,Context.MODE_PRIVATE);
        SettingsPageUi ui=new SettingsPageUi((SettingsActivity)context,fs);
        YViewLayout.addRow(parent,YViewLayout.rowSubtitle(context,
            circle
                ? (zh?"圈画流程：默认快速文字识别，可按需启用完整文档能力":
                      "Circle profile: fast text recognition by default; enable document stages as needed")
                : (zh?"普通图片/PDF OCR 流程：默认完整分析，也可按需关闭额外步骤":
                      "Image/PDF profile: full document stages by default; disable unused operations")));
        for(Stage stage:STAGES){
            String key=PaddleFlowOptions.key(circle,stage.key);
            boolean current=prefs.getBoolean(key,
                    ("spacing".equals(stage.key))
                        ? prefs.getBoolean("paddle_v2_spacing",true)
                        : ("rotation_fallback".equals(stage.key))
                            ? prefs.getBoolean("paddle_v2_auto_orientation",false)
                            : PaddleFlowOptions.defaultValue(circle,stage.key));
            ui.check(parent,zh?stage.zh:stage.en,
                    zh?stage.detailZh:stage.detailEn,key,current);
        }
    }
    private PaddleFlowSettingsUi(){}
}
