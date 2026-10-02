#!/usr/bin/env python3
from __future__ import annotations

import re
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def upsert(path: Path, name: str, value: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    if path.exists():
        tree = ET.parse(path)
        root = tree.getroot()
    else:
        root = ET.Element("resources")
        tree = ET.ElementTree(root)
    node = next((n for n in root.findall("string") if n.attrib.get("name") == name), None)
    if node is None:
        node = ET.SubElement(root, "string", {"name": name})
    node.text = value
    ET.indent(tree, space="    ")
    tree.write(path, encoding="utf-8", xml_declaration=True)


def patch(path: Path, transform) -> None:
    text = path.read_text(encoding="utf-8")
    new = transform(text)
    if new != text:
        path.write_text(new, encoding="utf-8")


def feature_string(app: str, name: str, english: str, chinese: str) -> None:
    base = ROOT / "apps" / app / "feature" / "src/main/res"
    upsert(base / "values/i18n_final.xml", name, english)
    upsert(base / "values-zh-rCN/i18n_final.xml", name, chinese)


def app_string(app: str, name: str, english: str, chinese: str) -> None:
    base = ROOT / "apps" / app / "app" / "src/main/res"
    upsert(base / "values/strings.xml", name, english)
    upsert(base / "values-zh-rCN/strings.xml", name, chinese)


def fix_ypower() -> None:
    status = ROOT / "apps/YPower/feature/src/main/java/com/yagay/ypower/model/DiagnosticStatus.java"

    def status_transform(text: str) -> str:
        text = text.replace("\nimport java.util.Locale;\n", "\n")
        for code, key in (
            ("DETECTED", "ypower_generated_e71233fe898b"),
            ("PASS", "ypower_generated_e0561a48579f"),
            ("FAIL", "ypower_generated_7d29ab0fc30a"),
            ("WARN", "ypower_generated_406dd7912457"),
            ("UNKNOWN", "ypower_generated_87f340e4520f"),
        ):
            text = re.sub(
                rf'{code}\("([^"]+)",\s*com\.yagay\.suite\.api\.YLocale\.text\(com\.yagay\.ypower\.R\.string\.{key}\)\)',
                rf'{code}("\1", com.yagay.ypower.R.string.{key})',
                text,
            )
        text = text.replace("    private final String zh;", "    private final int labelRes;")
        text = text.replace(
            "    DiagnosticStatus(String code, String zh) {\n        this.code = code;\n        this.zh = zh;\n    }",
            "    DiagnosticStatus(String code, int labelRes) {\n        this.code = code;\n        this.labelRes = labelRes;\n    }",
        )
        text = re.sub(
            r'    public String label\(\) \{\s*return "zh"\.equalsIgnoreCase\(Locale\.getDefault\(\)\.getLanguage\(\)\) \? zh : code;\s*\}',
            "    public String label() {\n        return com.yagay.suite.api.YLocale.text(labelRes);\n    }",
            text,
            flags=re.DOTALL,
        )
        return text

    patch(status, status_transform)

    report = ROOT / "apps/YPower/feature/src/main/java/com/yagay/ypower/model/DiagnosticReport.java"

    def report_transform(text: str) -> str:
        text = text.replace("\nimport java.util.Locale;\n", "\n")
        return re.sub(
            r'\n\s*private static String tr\(String english, String chinese\) \{\s*return "zh"\.equalsIgnoreCase\(Locale\.getDefault\(\)\.getLanguage\(\)\) \? chinese : english;\s*\}\s*',
            "\n",
            text,
            flags=re.DOTALL,
        )

    patch(report, report_transform)

    app_string("YPower", "app_name", "YPower", "应用增强 YPower")
    app_string(
        "YPower",
        "module_description",
        "App capability enhancement, Root management, and environment/crash diagnostics",
        "应用能力增强、Root 管理与环境/闪退诊断",
    )


def fix_yentry() -> None:
    feature_string("YEntryCleaner", "yentry_status_warning", "Warning", "警告")
    feature_string("YEntryCleaner", "yentry_status_status", "Status", "状态")
    feature_string("YEntryCleaner", "yentry_status_error", "Error", "错误")

    root_components = ROOT / "apps/YEntryCleaner/feature/src/main/java/com/yagay/YEntryCleaner/ui/RootComponentsScreen.kt"
    patch(root_components, lambda text: text
          .replace('YStatusRow("Warning", scan.warning, YStatusTone.Error)',
                   'YStatusRow(stringResource(R.string.yentry_status_warning), scan.warning, YStatusTone.Error)')
          .replace('message?.let { YStatusRow("Status", it, YStatusTone.Neutral) }',
                   'message?.let { YStatusRow(stringResource(R.string.yentry_status_status), it, YStatusTone.Neutral) }'))

    scope = ROOT / "apps/YEntryCleaner/feature/src/main/java/com/yagay/YEntryCleaner/ui/ScopeScreen.kt"
    patch(scope, lambda text: text
          .replace('YStatusRow("Warning", warning, YStatusTone.Error)',
                   'YStatusRow(stringResource(R.string.yentry_status_warning), warning, YStatusTone.Error)')
          .replace('status.message?.let { YStatusRow("Status", it, YStatusTone.Neutral) }',
                   'status.message?.let { YStatusRow(stringResource(R.string.yentry_status_status), it, YStatusTone.Neutral) }')
          .replace('status.error?.let { YStatusRow("Error", it, YStatusTone.Error) }',
                   'status.error?.let { YStatusRow(stringResource(R.string.yentry_status_error), it, YStatusTone.Error) }'))


def fix_ynotify() -> None:
    feature_string("YNotify", "ynotify_repair_failed", "Repair failed: %1$s", "修复失败：%1$s")
    feature_string(
        "YNotify",
        "ynotify_repair_summary",
        "Scanned %1$d records. Corrected event type for %2$d records. Classified %3$d SystemUI records. Corrected notification subtype for %4$d records. Linked %5$d heads-up records. Linked %6$d cross-app heads-up records. Merged %7$d duplicate notifications.",
        "扫描 %1$d 条记录。纠正事件类型 %2$d 条。归类 SystemUI %3$d 条。纠正通知子类型 %4$d 条。关联横幅 %5$d 条。关联跨应用横幅 %6$d 条。合并重复通知 %7$d 条。",
    )
    feature_string("YNotify", "ynotify_repair_ambiguous", "%1$d records had insufficient evidence.", "%1$d 条记录证据不足。")
    feature_string("YNotify", "ynotify_repair_rechecked", "Rechecked %1$d previous merges.", "重新检查了 %1$d 条历史合并。")
    feature_string("YNotify", "ynotify_repair_protected", "Preserved %1$d manual classifications.", "保留了 %1$d 条手动分类。")

    history = ROOT / "apps/YNotify/feature/src/main/java/com/yagay/YNotify/ui/HistoryRepairText.java"

    def history_transform(text: str) -> str:
        body = '''    static String summary(Context context, HistoryRepairEngine.RepairReport report) {
        if (report == null) return "";
        if (!report.succeeded()) {
            return context.getString(R.string.ynotify_repair_failed, report.error);
        }
        StringBuilder out = new StringBuilder(context.getString(
                R.string.ynotify_repair_summary,
                report.scanned,
                report.eventTypeChanged,
                report.systemUiClassified,
                report.notificationKindChanged,
                report.mergedUi,
                report.crossPackageHeadsUp,
                report.mergedNotifications));
        if (report.ambiguousUi > 0) {
            out.append(' ').append(context.getString(R.string.ynotify_repair_ambiguous, report.ambiguousUi));
        }
        if (report.recheckedMerged > 0) {
            out.append(' ').append(context.getString(R.string.ynotify_repair_rechecked, report.recheckedMerged));
        }
        if (report.protectedManual > 0) {
            out.append(' ').append(context.getString(R.string.ynotify_repair_protected, report.protectedManual));
        }
        return out.toString().trim();
    }
'''
        text = re.sub(
            r'    static String summary\(Context context, HistoryRepairEngine\.RepairReport report\) \{.*?\n    \}\n(?=\})',
            body,
            text,
            flags=re.DOTALL,
        )
        if "import com.yagay.YNotify.R;" not in text:
            text = text.replace("import android.content.Context;\n", "import android.content.Context;\n\nimport com.yagay.YNotify.R;\n")
        return text

    patch(history, history_transform)

    main = ROOT / "apps/YNotify/feature/src/main/java/com/yagay/YNotify/ui/MainActivity.java"
    patch(main, lambda text: text.replace('b.toolbar.setTitle("YNotify");', 'b.toolbar.setTitle(R.string.ynotify_app_name);'))

    app_string("YNotify", "xposed_description", "Notification, Toast, and UI prompt history center", "通知、Toast 与界面提示历史中心")


def fix_yfloat() -> None:
    feature_string("YFloat", "yfloat_dimension_dp", "%1$d dp", "%1$d dp")
    feature_string("YFloat", "yfloat_circle_correction_disabled", "Circle correction is disabled", "圈画纠正已关闭")
    feature_string("YFloat", "yfloat_ocr_model_missing", "%1$s is not downloaded", "%1$s 尚未下载")
    feature_string("YFloat", "yfloat_mlkit_ocr_empty", "ML Kit full-screen OCR returned no text", "ML Kit 全屏 OCR 未识别到文字")
    feature_string("YFloat", "yfloat_ppocr_empty", "PP-OCR returned no text", "PP-OCR 未识别到文字")
    feature_string("YFloat", "yfloat_ppocr_failed", "PP-OCR failed", "PP-OCR 失败")
    feature_string("YFloat", "yfloat_invalid_ocr_bitmap", "Invalid Circle OCR bitmap", "圈画 OCR 位图无效")
    feature_string("YFloat", "yfloat_ocr_off", "Off", "关闭")

    panel = ROOT / "apps/YFloat/feature/src/main/java/com/yagay/YFloat/UnifiedResultPanel.java"
    patch(panel, lambda text: text.replace('title = ResultUi.heading(context, "YFloat");',
                                           'title = ResultUi.heading(context, context.getString(R.string.yfloat_app_name));'))

    border = ROOT / "apps/YFloat/feature/src/main/java/com/yagay/YFloat/CircleBorderSettingsUi.java"
    patch(border, lambda text: text
          .replace('fs.circleBorderWidthDp() + " dp"', 'activity.getString(R.string.yfloat_dimension_dp, fs.circleBorderWidthDp())')
          .replace('value.setText(dp + " dp");', 'value.setText(activity.getString(R.string.yfloat_dimension_dp, dp));'))

    ocr = ROOT / "apps/YFloat/feature/src/main/java/com/yagay/YFloat/CircleStableOcr.java"
    patch(ocr, lambda text: text
          .replace('new IllegalStateException("Circle correction disabled")',
                   'new IllegalStateException(app.getString(R.string.yfloat_circle_correction_disabled))')
          .replace('OcrModelManager.displayName(model) + " not downloaded"',
                   'app.getString(R.string.yfloat_ocr_model_missing, OcrModelManager.displayName(model))')
          .replace('new IllegalStateException("ML Kit full-screen OCR empty")',
                   'new IllegalStateException(app.getString(R.string.yfloat_mlkit_ocr_empty))')
          .replace('new IllegalStateException("PP-OCR empty")',
                   'new IllegalStateException(app.getString(R.string.yfloat_ppocr_empty))')
          .replace('message == null || message.isBlank() ? "PP-OCR failed" : message',
                   'message == null || message.isBlank() ? app.getString(R.string.yfloat_ppocr_failed) : message')
          .replace('return mode == 0 ? "off" : modeLabel(mode);',
                   'return mode == 0 ? context.getString(R.string.yfloat_ocr_off) : modeLabel(mode);')
          .replace('new IllegalArgumentException("invalid Circle OCR bitmap")',
                   'new IllegalArgumentException(context.getString(R.string.yfloat_invalid_ocr_bitmap))'))

    app_string("YFloat", "app_description", "YFloat floating OCR, View selection and screenshot tool.", "YFloat 悬浮 OCR、View 选择与截图工具。")


def fix_ydiag() -> None:
    feature_string("YDiag", "ydiag_app_name", "YDiag", "应用故障诊断")
    main = ROOT / "apps/YDiag/feature/src/main/java/com/yagay/ydiag/ui/MainActivity.kt"
    patch(main, lambda text: text.replace('title = "YDiag",', 'title = stringResource(R.string.ydiag_app_name),'))
    app_string("YDiag", "app_name", "YDiag", "应用故障诊断")


def fix_ynfc() -> None:
    feature_string("YNFC", "ynfc_title_version", "YNFC %1$s", "YNFC %1$s")
    screen = ROOT / "apps/YNFC/feature/src/main/java/com/yagay/YNFC/ui/NfcAppScreen.kt"
    patch(screen, lambda text: text.replace('title = "YNFC ${BuildConfig.VERSION_NAME}",',
                                            'title = stringResource(R.string.ynfc_title_version, BuildConfig.VERSION_NAME),'))
    app_string("YNFC", "app_name", "YNFC", "YNFC")
    app_string("YNFC", "module_description", "Advanced NFC Door Card Simulator for OxygenOS 16", "面向 OxygenOS 16 的高级 NFC 门禁卡模拟器")


def fix_ytask() -> None:
    feature_string("YTaskManager", "ytm_root_granted", "Root granted", "已授予 Root")
    feature_string("YTaskManager", "ytm_root_denied", "Root unavailable or denied", "Root 不可用或未授权")
    feature_string("YTaskManager", "ytm_root_required", "Root permission is required", "需要 Root 权限")

    vm = ROOT / "apps/YTaskManager/feature/src/main/java/com/yagay/YTaskManager/MainViewModel.kt"
    patch(vm, lambda text: text
          .replace('message = if (rootGranted) "Root granted" else "Root unavailable or denied"',
                   'message = getApplication<Application>().getString(if (rootGranted) R.string.ytm_root_granted else R.string.ytm_root_denied)')
          .replace('error = if (rootGranted) null else "Root permission is required"',
                   'error = if (rootGranted) null else getApplication<Application>().getString(R.string.ytm_root_required)'))

    app_string("YTaskManager", "app_name", "YTaskManager", "YTaskManager")
    app_string(
        "YTaskManager",
        "app_description",
        "YTaskManager is a Root task manager with a libxposed API 102 system enhancement layer.",
        "YTaskManager 是带有 libxposed API 102 系统增强层的 Root 任务管理器。",
    )


def fix_yminiguard() -> None:
    app_string("YMiniGuard", "app_name", "YMiniGuard", "YMiniGuard")
    app_string(
        "YMiniGuard",
        "app_description",
        "Real floating mini-windows based on system_server and VirtualDisplay, with foreground lifecycle protection and complete diagnostic export.",
        "基于 system_server + VirtualDisplay 的真实悬浮小窗，并提供前台生命周期保护和完整诊断导出。",
    )


def fix_yparam() -> None:
    app_string("YParam", "app_name", "YParam", "YParam")
    app_string(
        "YParam",
        "module_description",
        "Read, override, and simulate per-app Android runtime parameters including display, language, time zone, windows, and location.",
        "按应用读取、覆盖和模拟 Android 运行环境参数：显示、语言、时区、窗口、定位等。",
    )


def main() -> None:
    fix_ypower()
    fix_yentry()
    fix_ynotify()
    fix_yfloat()
    fix_ydiag()
    fix_ynfc()
    fix_ytask()
    fix_yminiguard()
    fix_yparam()
    print("I18N_FINAL_CLEANUP=ok")


if __name__ == "__main__":
    main()
