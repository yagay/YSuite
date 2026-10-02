#!/usr/bin/env python3
from __future__ import annotations

import ast
import hashlib
import re
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CJK = re.compile(r"[\u3400-\u4dbf\u4e00-\u9fff]")
PAIR = re.compile(
    r'\b(?:t|tr)\(\s*("(?:\\.|[^"\\])*")\s*,\s*("(?:\\.|[^"\\])*")\s*\)',
    re.DOTALL,
)
LITERAL = re.compile(r'"(?:\\.|[^"\\])*"')

MODULES = {
    "apps/YPower/feature": ("ypower", "com.yagay.ypower.R"),
    "apps/YFloat/feature": ("yfloat", "com.yagay.YFloat.R"),
    "apps/YDiag/feature": ("ydiag", "com.yagay.ydiag.R"),
    "apps/YNotify/feature": ("ynotify", "com.yagay.YNotify.R"),
    "apps/YNFC/feature": ("ynfc", "com.yagay.YNFC.R"),
    "apps/YMiniGuard/feature": ("ymg", "com.yagay.YMiniGuard.R"),
    "apps/YEntryCleaner/feature": ("yentry", "com.yagay.YEntryCleaner.R"),
}

# Exact user-visible literals that were confirmed by the final repository audit.
# Technical keys, protocol values, package names and raw log tags are intentionally absent.
TRANSLATIONS = {
    # Shared/runtime states
    "未连接": "Not connected",
    "Root 操作失败": "Root operation failed",
    "Root 操作超时": "Root operation timed out",

    # YFloat common/result/OCR
    "复制": "Copy", "保存": "Save", "关闭": "Close", "已复制": "Copied",
    "识别中…": "Recognizing…", "重新识别": "Recognize again",
    "OCR失败: 图片无效": "OCR failed: invalid image",
    "未下载 PP-OCRv6 模型，暂用 ML Kit；可在设置中下载": "PP-OCRv6 model is not downloaded. ML Kit is being used temporarily; download the model in Settings.",
    "请先在设置中下载 ": "Download in Settings first: ",
    "未识别到文字": "No text recognized",
    "PP-OCRv6 失败: ": "PP-OCRv6 failed: ",
    "OCR失败: ": "OCR failed: ",
    "未启用 OCR 语言": "No OCR language is enabled",
    "OCR失败": "OCR failed",
    "整屏 View": "Full-screen View",
    "区域选择器启动失败": "Failed to start region selector",
    "拖动框选区域": "Drag to select a region",
    "拖动内部移动 · 拖边/角调整": "Drag inside to move; drag an edge or corner to resize",
    "自动识别": "Auto detect", "View文字": "View text", "取消": "Cancel",
    "选区截取失败": "Failed to capture the selected region",
    "选区内没有可提取的 View 文字": "No extractable View text was found in the selection",
    "圈选要识别的内容 · 松手开始 OCR": "Select content to recognize; release to start OCR",
    "拖动选择截图区域": "Drag to select a screenshot region",
    "区域处理失败: ": "Region processing failed: ",
    "图片 View": "Image View",
    "截图结果处理失败": "Failed to process screenshot result",
    "OCR 区域选择器启动失败": "Failed to start OCR region selector",
    "区域截图失败: 截图无效": "Region screenshot failed: invalid screenshot",
    "区域编辑器启动失败": "Failed to start region editor",
    "区域截图失败: ": "Region screenshot failed: ",
    "区域截图": "Region screenshot", "View 截图": "View screenshot",
    "失败: 裁剪失败": " failed: crop failed", "结果处理失败": " result processing failed",
    "截图失败: ": "Screenshot failed: ", "截图处理失败": "Screenshot processing failed",
    "截图无效": "Invalid screenshot", "保存失败": "Save failed",
    "PNG 写入失败": "Failed to write PNG", "已保存到 Pictures/YFloat": "Saved to Pictures/YFloat",
    "保存失败: ": "Save failed: ", "Root 命令超时": "Root command timed out",
    "OpenCV 初始化失败: ": "OpenCV initialization failed: ",
    "复制图片": "Copy image", "分享图片": "Share image", "打开方式": "Open with", "保存图片": "Save image",
    "已复制图片": "Image copied", "无法复制图片": "Unable to copy image",
    "无法分享图片": "Unable to share image", "没有可用的图片应用": "No compatible image app is available",
    "圈画识别启动失败": "Failed to start circle recognition", "圈画识别截图失败: ": "Circle recognition screenshot failed: ",
    "完成": "Done", "完成区域选择": "Finish region selection",
    "截图范围无效": "Invalid screenshot region", "圈画截图失败": "Circle screenshot failed",
    "当前位置未识别到文字": "No text was recognized at this position",
    "当前位置未识别到可选文字": "No selectable text was recognized at this position",
    "调整截图窗口 · 调好后点完成": "Adjust the screenshot window, then tap Done",
    "正在识别选区文字": "Recognizing text in the selection",
    "文字已选中 · 拖动手柄可跨行/段落调整": "Text selected; drag the handles to adjust across lines or paragraphs",
    "点击/涂抹选择文字 · 圈画截图": "Tap or brush to select text; circle to capture",
    "需要开启 YFloat 无障碍服务": "YFloat accessibility service is required",
    "请先开启 YFloat 无障碍服务": "Enable YFloat accessibility service first",
    "点击下方屏幕失败": "Failed to click the underlying screen",
    "移动图标位置已保存": "Floating icon position saved",
    "移动图标位置：拖动悬浮球后松手保存": "Move icon position: drag the floating button and release to save",
    "移动图标位置：拖动后松手保存": "Move icon position: drag and release to save",
    "图标已手动隐藏": "Icon hidden manually", "锁屏隐藏": "Hidden on lock screen",
    "全屏应用隐藏": "Hidden in full-screen apps", "当前应用按规则隐藏": "Hidden by the current app rule",
    "任务: ": "Task: ", "通知栏已展开": "Notification shade expanded", "点击进入设置": "Tap to open Settings",
    "YFloat 悬浮图标已运行": "YFloat floating icon is running", "显示图标": "Show icon", "停止": "Stop",
    "YFloat 悬浮服务": "YFloat floating service",
    "Google 圈画模式需要启用 LSPosed 增强": "Google circle mode requires LSPosed enhancement",
    "请在 LSPosed 作用域勾选 Google App": "Add the Google app to the LSPosed scope",
    "Google Hook 正在重新加载": "Google Hook is reloading",
    "检测到 Hook 更新，正在热重载 Google…": "Hook update detected; hot-reloading Google…",
    "Google 圈画启动失败": "Failed to start Google circle mode", "Google 圈画启动失败: ": "Failed to start Google circle mode: ",
    "需要同时开启“增强模式”和“使用 Root 功能”": "Enable both Enhanced mode and Use Root features",
    "未知": "Unknown", "正在运行": "Running", "已停止": "Stopped", "已冻结": "Frozen",
    "直接打开": "Open directly", "启动应用": "Launch app", "直接打开入口": "Open entry directly",
    "分享文字": "Share text", "处理文字": "Process text", "网页打开/搜索": "Open/search web",
    "网页搜索": "Web search", "拨号": "Dial", "短信": "SMS", "邮件": "Email", "地图搜索": "Search map",
    "翻译": "Translate", "打开纯文本": "Open plain text", "应用": "App",
    "无法打开 ": "Unable to open ",
    "区域截图 · OCR": "Region screenshot · OCR", "View 内容": "View content", "View / 图标": "View / icon",
    "OCR 结果": "OCR result", "View 内容 · OCR": "View content · OCR", "图标": "Icon", "图标 · ": "Icon · ",

    # YFloat menu manager
    "文字操作菜单": "Text action menu", "管理选中文字后可调用的应用和 Intent 操作。": "Manage apps and Intent actions available for selected text.",
    "添加操作": "Add action", "不需要手动填写包名、Activity、Action 或 MIME。": "No need to enter package, Activity, Action, or MIME values manually.",
    "按 App 选择": "Choose by app", "先选择应用，再查看它可用的入口": "Choose an app first, then view its available entry points",
    "按 Intent 类型选择": "Choose by Intent type", "先选择操作类型，再选择可以处理它的应用": "Choose an action type first, then choose an app that can handle it",
    "当前菜单 · ": "Current menu · ", " 项": " items", "长按 ≡ 可拖动排序，也可以使用右侧按钮精确移动。": "Long-press the drag handle to reorder, or use the controls on the right for precise movement.",
    "还没有自定义文字操作": "No custom text actions yet", "已移除": "Removed",
    "选择应用后，YFloat 会读取它可处理的标准 Intent 和可直接启动入口。": "After you choose an app, YFloat lists the standard Intents it can handle and directly launchable entry points.",
    "返回文字操作菜单": "Back to text action menu", "应用": "Apps", "搜索应用名称或包名": "Search app name or package",
    "标准 Intent 入口优先显示，后面再列出其他可直接启动的 exported Activity。": "Standard Intent entry points are shown first, followed by other exported Activities that can be launched directly.",
    "返回应用列表": "Back to app list", "直接打开入口 · ": "Direct launch entry · ", "可用入口 · ": "Available entries · ",
    " 个": "", "没有发现可从 YFloat 调用的入口": "No entry point callable from YFloat was found",
    "先确定操作类型，再从系统确认可以处理它的应用中选择。": "Choose an action type, then select from apps the system confirms can handle it.",
    "操作类型": "Action type", "只显示系统确认能处理这个 Intent 的应用入口。": "Only app entry points confirmed by the system to handle this Intent are shown.",
    "返回 Intent 类型": "Back to Intent types", "可用应用": "Available apps", "搜索应用名称、包名或 Activity": "Search app name, package, or Activity",
    "没有匹配的应用": "No matching apps", "没有找到可处理此 Intent 的应用": "No app that can handle this Intent was found",
    "没有匹配的应用或 Activity": "No matching app or Activity", "已加入": "Added", "加入": "Add",
    "没有可重新加入的系统目标": "No system target is available to add back", "没有匹配的应用或组件": "No matching app or component",
    "已加入 YFloat 菜单": "Added to the YFloat menu", "这个入口已经加入过了": "This entry is already in the menu",
    "分享菜单": "Share menu", "打开 / 处理菜单": "Open / process menu",
    "来源始终是 Android 当前可分享目标，YFloat 只保存排序和隐藏规则。": "Targets always come from Android's current share targets; YFloat only stores ordering and hide rules.",
    "来源始终是 Android 当前可处理目标，YFloat 只保存排序和隐藏规则。": "Targets always come from Android's current handlers; YFloat only stores ordering and hide rules.",
    "管理": "Manage", "添加已隐藏项": "Add hidden item", "恢复系统顺序": "Restore system order",
    "已恢复当前系统列表顺序": "Current system list order restored", "当前显示 · ": "Currently shown · ",
    "长按 ≡ 拖动排序；移除只是从 YFloat 菜单隐藏，不会修改系统应用。": "Long-press the drag handle to reorder. Removing only hides the target from YFloat and does not modify the system app.",
    "系统当前没有返回可用目标": "The system currently returns no available targets", "添加分享应用": "Add share app", "添加处理应用": "Add handler app",
    "这里只列出之前从 YFloat 菜单隐藏、但系统仍然可用的目标。": "Only targets previously hidden from YFloat but still available in the system are listed.",
    "返回当前菜单": "Back to current menu", "可重新加入": "Available to add back", "搜索应用名称、包名或组件": "Search app name, package, or component",
    "长按拖动排序": "Long-press and drag to reorder", "上移": "Move up", "下移": "Move down", "移除": "Remove",

    # YDiag runtime/issue/service
    "LSPosed 未连接": "LSPosed is not connected", "YDiag 已由 YSuite 停用": "YDiag is disabled by YSuite",
    "LSPosed 连接已断开": "LSPosed connection was lost", "Root 监控正常；LSPosed 深度追踪未连接": "Root monitoring is active; LSPosed deep tracing is not connected",
    "读取 LSPosed 状态失败": "Failed to read LSPosed status",
    "Root 日志已生效；YSuite Scope 请求失败": "Root logging is active; YSuite scope request failed",
    "Java/Kotlin 崩溃": "Java/Kotlin crash", "应用无响应 ANR": "Application not responding (ANR)", "内存不足 OOM": "Out of memory (OOM)",
    "Binder 数据过大": "Binder payload too large", "Binder 目标进程死亡": "Binder target process died", "权限或系统限制": "Permission or system restriction",
    "SELinux 拒绝": "SELinux denial", "WebView Renderer 异常": "WebView renderer failure", "方法签名不存在": "Method signature not found",
    "目标 Class 不存在": "Target class not found", "LSPosed Hook 异常": "LSPosed Hook failure",
    "监控目标已更新": "Monitoring targets updated", "正在监控": "Monitoring", "日志采集失败": "Log collection failed",
    "Perfetto 已启动": "Perfetto started", "深度性能 Trace 正在后台采集": "Deep performance trace is being collected in the background",
    "Perfetto 启动失败": "Failed to start Perfetto", "设备可能不支持当前 Perfetto 命令或 Root 调用失败": "The device may not support the current Perfetto command, or the Root call failed",
    "诊断开关已关闭": "Diagnostic option was disabled", "Perfetto 已保存": "Perfetto saved", "Perfetto 未生成 Trace": "Perfetto did not produce a trace",
    "用户标记：问题发生了": "User marker: problem occurred", "以此时间点为中心优先分析前后日志": "Prioritize logs around this timestamp",
    "用户标记问题时间点": "User-marked problem timestamp", "已标记问题时间点": "Problem timestamp marked",
    "导出诊断包": "Export diagnostic package", "停止监控": "Stop monitoring", "监控已停止": "Monitoring stopped", "服务销毁": "Service destroyed",
    "YDiag 自动诊断摘要": "YDiag automatic diagnostic summary", "目标: ": "Targets: ", "问题标记: ": "Problem markers: ", "异常: ": "Issues: ",

    # YNotify remaining model/export copy
    "自定义 Toast": "Custom Toast", "[内容已隐藏]": "[Content hidden]", "未知应用": "Unknown app", "Android 系统": "Android system",
    "修复失败：": "Repair failed: ", "扫描 ": "Scanned ", " 条。": " records. ", "类型纠正 ": "Type corrections ", "SystemUI 分类 ": "SystemUI classifications ",
    "通知子类型纠正 ": "Notification subtype corrections ", "横幅关联 ": "Heads-up links ", "跨应用横幅 ": "Cross-app heads-up links ",
    "重复通知合并 ": "Duplicate notifications merged ", "证据不足 ": "Insufficient evidence ", "重新检查旧合并 ": "Old merges rechecked ", "保留手动分类 ": "Manual classifications preserved ",
    "Root 日志未收集。\n\n": "Root logs were not collected.\n\n", "\n首次使用时请在 KernelSU/Magisk 中给 NotifyLens 授予 root 后重新导出。\n": "\nOn first use, grant root to YNotify in KernelSU/Magisk and export again.\n",
    "NotifyLens 一键诊断日志\n": "YNotify diagnostic log bundle\n", "生成时间: ": "Generated: ", "App 版本: ": "App version: ", "Root 日志: ": "Root logs: ",
    "已收集": "Collected", "未收集": "Not collected", "包含内容:\n": "Included content:\n",
    "注意: 系统 logcat、LSPosed 日志、dumpsys、ANR/tombstone 可能包含应用名、通知内容或其他个人信息。分享 ZIP 前请确认接收方可信。\n": "Note: system logcat, LSPosed logs, dumpsys, ANR/tombstone data may contain app names, notification content, or other personal information. Verify the recipient before sharing the ZIP.\n",
    "不会主动导出 NotifyLens 的 HMAC secret、数据库密钥、LSPosed 配置数据库或 root 模块私有配置文件。\n": "YNotify does not intentionally export its HMAC secret, database key, LSPosed configuration database, or private root-module configuration files.\n",

    # YNFC
    "状态": "Status", "系统": "System", "无法在 Download 创建日志文件": "Unable to create the log file in Download",
    "无法写入日志文件": "Unable to write the log file", "Root 获取失败，请在 Root 管理器中授予本应用权限": "Root access failed. Grant this app permission in your Root manager.",

    # YPower short runtime/report fragments
    "检测到": "Detected", "通过": "Pass", "异常": "Failure", "警告": "Warning", "未知": "Unknown",
    "包扫描": "Package scan", "文件/proc": "Files/proc", "命令/退出": "Commands/exits", "系统属性": "System properties", "权限查询": "Permission queries",
    "调试器检测": "Debugger detection", "异常传播": "Exception propagation", "现代安全API": "Modern security APIs", "系统身份模拟": "System identity simulation",
    "权限状态模拟": "Permission state simulation", "短调用栈": "Short call stack", "无需 LSPosed Hook": "No LSPosed Hook required",
    "抖音": "Douyin", "抖音极速版": "Douyin Lite", "红果免费短剧": "Hongguo Free Short Drama", "红果短剧（海外版）": "Hongguo Short Drama (overseas)",
    "低内存结束进程": "Process terminated for low memory", "Signal 结束进程": "Process terminated by signal", "资源使用异常结束": "Process terminated for abnormal resource use",
    "初始化失败": "Initialization failed", "应用自行退出": "App exited itself", "进程退出": "Process exited",
}

MOVE_ONLY = {
    "apps/YPower/feature/src/main/java/com/yagay/ypower/diag/DetectionRuleCatalog.java",
    "apps/YPower/feature/src/main/java/com/yagay/ypower/diag/SupplementalRuleCatalog.java",
}


def module_for(path: Path):
    rel = path.relative_to(ROOT).as_posix()
    for root, cfg in MODULES.items():
        if rel.startswith(root + "/"):
            return root, cfg
    return None


def decode(token: str) -> str:
    try:
        return ast.literal_eval(token)
    except Exception:
        return token[1:-1]


def key_for(prefix: str, en: str, zh: str) -> str:
    digest = hashlib.sha1((en + "\0" + zh).encode("utf-8")).hexdigest()[:12]
    return f"{prefix}_generated_{digest}"


def resource_expr(r_class: str, key: str) -> str:
    return f"com.yagay.suite.api.YLocale.text({r_class}.string.{key})"


def load_xml(path: Path) -> dict[str, str]:
    if not path.exists():
        return {}
    try:
        root = ET.parse(path).getroot()
    except Exception:
        return {}
    out = {}
    for item in root.findall("string"):
        name = item.attrib.get("name")
        if name:
            out[name] = item.text or ""
    return out


def xml_value(value: str) -> str:
    return value.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n")


def write_xml(path: Path, values: dict[str, str]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    resources = ET.Element("resources")
    for name in sorted(values):
        item = ET.SubElement(resources, "string", {"name": name})
        item.text = xml_value(values[name])
    ET.indent(resources, space="    ")
    text = ET.tostring(resources, encoding="unicode")
    path.write_text('<?xml version="1.0" encoding="utf-8"?>\n' + text + '\n', encoding="utf-8")


def migrate_file(path: Path, prefix: str, r_class: str, defaults: dict[str, str], zh_values: dict[str, str]) -> bool:
    original = path.read_text(encoding="utf-8")
    text = original

    def pair_repl(match: re.Match) -> str:
        en = decode(match.group(1))
        zh = decode(match.group(2))
        if not CJK.search(zh):
            return match.group(0)
        key = key_for(prefix, en, zh)
        defaults[key] = en
        zh_values[key] = zh
        return resource_expr(r_class, key)

    text = PAIR.sub(pair_repl, text)

    rel = path.relative_to(ROOT).as_posix()
    move_only = rel in MOVE_ONLY

    def literal_repl(match: re.Match) -> str:
        token = match.group(0)
        value = decode(token)
        if not CJK.search(value):
            return token
        if "$" in value and path.suffix == ".kt":
            return token
        en = TRANSLATIONS.get(value)
        if en is None and not move_only:
            return token
        if en is None:
            en = ""
        key = key_for(prefix, en, value)
        defaults[key] = en
        zh_values[key] = value
        return resource_expr(r_class, key)

    text = LITERAL.sub(literal_repl, text)
    if text == original:
        return False
    path.write_text(text, encoding="utf-8")
    return True


def main() -> int:
    changed = []
    stores: dict[str, tuple[dict[str, str], dict[str, str], Path, Path]] = {}
    for module_root, (prefix, _) in MODULES.items():
        base = ROOT / module_root / "src/main/res"
        default_path = base / "values/generated_i18n.xml"
        zh_path = base / "values-zh-rCN/generated_i18n.xml"
        stores[module_root] = (load_xml(default_path), load_xml(zh_path), default_path, zh_path)

    for path in ROOT.rglob("*"):
        if not path.is_file() or path.suffix not in {".java", ".kt"}:
            continue
        rel = path.relative_to(ROOT).as_posix()
        if "/src/main/" not in rel or "/build/" in rel or "/generated/" in rel:
            continue
        if "/hook/" in rel or "/xposed/" in rel:
            continue
        module = module_for(path)
        if module is None:
            continue
        module_root, (prefix, r_class) = module
        defaults, zh_values, _, _ = stores[module_root]
        if migrate_file(path, prefix, r_class, defaults, zh_values):
            changed.append(rel)

    for module_root, (defaults, zh_values, default_path, zh_path) in stores.items():
        if defaults or default_path.exists():
            write_xml(default_path, defaults)
        if zh_values or zh_path.exists():
            write_xml(zh_path, zh_values)

    # Keep generated feature metadata locale-neutral. Runtime UI maps feature IDs to resources.
    config = ROOT / "config/features.toml"
    if config.exists():
        data = config.read_text(encoding="utf-8")
        replacements = {
            "分享、打开方式与组件入口清理": "Share, open-with, and component entry cleanup",
            "应用日志与故障诊断": "App logs and fault diagnostics",
            "通知 / Toast / 横幅历史": "Notification, Toast, and heads-up history",
            "应用增强、检测与运行时诊断": "App enhancements, detection, and runtime diagnostics",
            "小窗保活与后台播放守护": "Mini-window keep-alive and background playback guard",
            "NFC 门禁卡与控制器模拟": "NFC access-card and controller emulation",
            "进程、性能与系统任务管理": "Process, performance, and system task management",
            "应用 DPI、语言、定位与参数覆盖": "App DPI, language, location, and parameter overrides",
            "悬浮操作、文字选框与快捷动作": "Floating actions, text selection, and shortcuts",
        }
        updated = data
        for zh, en in replacements.items():
            updated = updated.replace(zh, en)
        if updated != data:
            config.write_text(updated, encoding="utf-8")
            changed.append("config/features.toml")

    print(f"I18N_REFACTOR_CHANGED={len(changed)}")
    for item in changed:
        print(item)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
