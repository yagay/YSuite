#!/usr/bin/env python3
from __future__ import annotations

import ast
import hashlib
import re
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LITERAL = re.compile(r'"(?:\\.|[^"\\])*"')

MODULES = {
    "apps/YPower/feature": ("ypower", "com.yagay.ypower.R"),
    "apps/YFloat/feature": ("yfloat", "com.yagay.YFloat.R"),
    "apps/YDiag/feature": ("ydiag", "com.yagay.ydiag.R"),
    "apps/YNotify/feature": ("ynotify", "com.yagay.YNotify.R"),
    "apps/YTaskManager/feature": ("ytm", "com.yagay.YTaskManager.R"),
    "libs/ycore": ("ycore", "com.yagay.suite.core.R"),
}

# Second-wave exact literals left after the first resource migration. These are intentionally
# limited to user-readable copy. Protocol keys, package names, raw trace tokens and glyph icons
# are not translated.
STATIC_TRANSLATIONS = {
    "apps/YPower/feature": {
        "适合做环境/退出诊断；默认只记录敏感检测行为，不修改检测结果。":
            "Suitable for environment and exit diagnostics. By default only sensitive checks are recorded; results are not modified.",
        "适合分析后台、环境检测和主动退出链；默认只启用诊断型 Hook。":
            "Suitable for background, environment-check and active-exit analysis. Only diagnostic hooks are enabled by default.",
        "敏感文件 / proc 检测": "Sensitive file / proc check",
        "目标 App 实际访问了诊断规则命中的敏感路径": "The target app accessed a sensitive path matched by a diagnostic rule",
        "包/安装环境查询": "Package / installation environment query",
        "目标 App 实际查询了安装包或安装来源": "The target app queried installed packages or the installer source",
        "系统属性检测": "System property check",
        "目标 App 实际读取了环境相关系统属性": "The target app read environment-related system properties",
        "敏感命令检测": "Sensitive command check",
        "目标 App 实际执行了诊断规则命中的命令": "The target app executed a command matched by a diagnostic rule",
        "目标 App 实际查询了调试器状态": "The target app queried debugger state",
        "权限状态查询": "Permission-state query",
        "目标 App 实际查询了权限状态": "The target app queried permission state",
        "应用主动退出调用": "Active app exit call",
        "目标 App 实际调用了主动退出 API": "The target app called an active-exit API",
        "Native 敏感文件检测": "Native sensitive-file check",
        "目标 App 的 native 代码实际访问了敏感文件/路径": "Native code in the target app accessed a sensitive file or path",
        "Native 调试器检测": "Native debugger check",
        "目标 App 的 native 代码实际执行了调试器相关检查": "Native code in the target app performed a debugger-related check",
        "；仅表示 APK 中存在公开 API 引用，不表示本次运行已执行，也不参与归因。":
            "; this only means the APK contains a public API reference. It does not prove execution in this run and is not used for attribution.",
        "simpleperf 调用图命中 ": "simpleperf call graph matched ",
        "组合链：\n": "Composite chain:\n",
        "Java uncaught：": "Java uncaught: ",
        " → Java uncaught（同一 Throwable，Δ=": " → Java uncaught (same Throwable, Δ=",
        " → Java uncaught（同TID，Δ=": " → Java uncaught (same TID, Δ=",
        "，同TID": ", same TID",
        "（Native dlopen）": " (Native dlopen)",
    },
    "apps/YFloat/feature": {
        " · 只显示系统确认能处理这个 Intent 的应用入口。":
            " · Only app entry points confirmed by the system to handle this Intent are shown.",
        "需要开启 YFloat 无障碍服务才能使用安全窗口截图增强":
            "YFloat accessibility service is required for secure-window screenshot enhancement",
        "LSPosed 安全截图短时授权失败": "LSPosed temporary secure-capture authorization failed",
        "需要开启 YFloat 无障碍服务；Root 截图需同时开启增强模式、Root 功能和 Root 截图增强":
            "YFloat accessibility service is required. Root screenshots also require Enhanced mode, Root features and Root screenshot enhancement",
        "两个截图后端均失败；first=": "Both screenshot backends failed; first=",
        "等待 LSPosed 服务连接": "Waiting for the LSPosed service",
        "LSPosed 服务已断开": "LSPosed service disconnected",
        "Remote Preferences 写入失败或不可用": "Remote Preferences write failed or is unavailable",
        "当前框架不支持 Remote Preferences": "The current framework does not support Remote Preferences",
        "同步 LSPosed 配置失败：": "Failed to sync LSPosed configuration: ",
        "安全截图短时授权写入失败": "Failed to write temporary secure-capture authorization",
        "安全截图短时授权失败：": "Temporary secure-capture authorization failed: ",
        "未连接到 LSPosed 服务": "Not connected to the LSPosed service",
        "框架没有提供 Remote Preferences": "The framework does not provide Remote Preferences",
        "读取 LSPosed 状态失败：": "Failed to read LSPosed status: ",
        "Root 截图超时": "Root screenshot timed out",
        "Root 截图数据为空": "Root screenshot data is empty",
        "无法解码 Root 截图": "Unable to decode Root screenshot",
        "LSPosed 安全窗口截图自检": "LSPosed secure-window screenshot self-test",
        "这是 YFloat 自己的 FLAG_SECURE 测试窗口。正在验证 system_server Hook 是否能在短时 lease 内抓到这里的标记颜色…":
            "This is YFloat's FLAG_SECURE test window. Verifying whether the system_server hook can capture its marker colors during a short lease…",
        "重新测试": "Test again",
        "返回": "Back",
        "正在刷新 LSPosed 框架、Remote Preferences 与 system_server 加载状态…":
            "Refreshing LSPosed framework, Remote Preferences and system_server loading state…",
        "前置条件未满足。\n": "Prerequisites are not satisfied.\n",
        "无障碍服务未连接，无法执行安全截图自检。\n":
            "Accessibility service is not connected, so the secure screenshot self-test cannot run.\n",
        "前置条件已满足，正在建立短时 lease 并调用无障碍截图…":
            "Prerequisites are satisfied. Creating a short lease and requesting an accessibility screenshot…",
        "截图调用失败：": "Screenshot request failed: ",
        "无": "None",
        "本地：enhanced=": "Local: enhanced=",
        "\n框架：service=": "\nFramework: service=",
        "\n远端：enhanced=": "\nRemote: enhanced=",
        "截图返回空 Bitmap。": "Screenshot returned an empty Bitmap.",
        "通过：安全窗口内容已出现在截图 Bitmap 中。采样命中 ":
            "Passed: secure-window content is present in the screenshot Bitmap. Sample matches ",
        "，Bitmap=": ", Bitmap=",
        "未通过：截图 API 有返回，但没有抓到 FLAG_SECURE 测试页的标记颜色。采样命中 ":
            "Failed: the screenshot API returned data, but the FLAG_SECURE test-page marker colors were not captured. Sample matches ",
        "。这通常表示 system_server Hook 点与当前 OxygenOS 版本不匹配，或安全层仍在更下游被过滤。":
            ". This usually means the system_server hook point does not match the current OxygenOS version, or the secure layer is still filtered further downstream.",
        "应用主启动入口": "Main app launch entry",
        "读取日志失败: ": "Failed to read logs: ",
        "全选": "Select all",
        "分享": "Share",
        "‹   返回": "‹   Back",
        "打开 / 处理": "Open / process",
        "‹   分享到": "‹   Share to",
        "‹   打开 / 处理": "‹   Open / process",
        "当前没有已启用的分享应用": "No enabled sharing app is available",
        "当前没有已启用的处理应用": "No enabled handler app is available",
        "系统分享菜单…": "System share menu…",
        "更多处理应用…": "More handler apps…",
        "无法打开该应用": "Unable to open this app",
        "无法打开分享菜单": "Unable to open the share menu",
        "没有可用的文本处理应用": "No text-processing app is available",
    },
    "apps/YNotify/feature": {
        "自定义 Toast": "Custom Toast",
        "- NotifyLens 自身滚动日志、通知监听状态、LSPosed API 102/Hook 状态\n":
            "- YNotify rolling logs, notification-listener state, and LSPosed API 102 / Hook state\n",
        "- 数据库/分类/合并统计与最近事件元数据（不主动导出通知正文）\n":
            "- Database, classification and merge statistics plus recent event metadata (notification body text is not exported proactively)\n",
        "- App 自身 logcat\n": "- App logcat\n",
        "- Root / KernelSU/Magisk 环境与已安装 root 模块 module.prop\n":
            "- Root / KernelSU / Magisk environment and module.prop files for installed root modules\n",
        "- LSPosed 环境与近期 LSPosed 日志\n": "- LSPosed environment and recent LSPosed logs\n",
        "- Root 可用时的全缓冲区 logcat、notification/accessibility/package/activity dumpsys、dmesg、ANR 和 tombstone 摘要\n\n":
            "- When Root is available: all-buffer logcat, notification/accessibility/package/activity dumpsys, dmesg, ANR and tombstone summaries\n\n",
    },
    "apps/YDiag/feature": {},
    "apps/YTaskManager/feature": {},
    "libs/ycore": {
        "未连接": "Not connected",
        "已连接 · API ": "Connected · API ",
        "Root 操作失败": "Root operation failed",
        "Root 操作超时": "Root operation timed out",
    },
}

changed: set[Path] = set()
resources: dict[tuple[str, str], dict[str, tuple[str, str]]] = {}


def module_for(path: Path):
    rel = path.relative_to(ROOT).as_posix()
    for module_root, (prefix, rclass) in MODULES.items():
        if rel == module_root or rel.startswith(module_root + "/"):
            return module_root, prefix, rclass
    return None


def key_for(prefix: str, zh: str) -> str:
    return f"{prefix}_dynamic_{hashlib.sha1(zh.encode('utf-8')).hexdigest()[:12]}"


def add_string(module_root: str, key: str, english: str, chinese: str) -> str:
    resources.setdefault((module_root, "dynamic"), {})[key] = (english, chinese)
    return key


def expr(path: Path, key: str, *args: str) -> str:
    info = module_for(path)
    if info is None:
        raise RuntimeError(f"No module mapping for {path}")
    _, _, rclass = info
    suffix = "" if not args else ", " + ", ".join(args)
    return f"com.yagay.suite.api.YLocale.text({rclass}.string.{key}{suffix})"


def replace_exact(path: Path, old: str, new: str, *, required: bool = True) -> None:
    text = path.read_text(encoding="utf-8")
    if old not in text:
        if new in text or not required:
            return
        raise RuntimeError(f"Expected snippet not found in {path}: {old[:100]!r}")
    path.write_text(text.replace(old, new), encoding="utf-8")
    changed.add(path)


def replace_regex(path: Path, pattern: str, replacement: str, *, count: int = 1) -> None:
    text = path.read_text(encoding="utf-8")
    new, n = re.subn(pattern, replacement, text, count=count, flags=re.DOTALL)
    if n == 0:
        if replacement in text:
            return
        raise RuntimeError(f"Expected pattern not found in {path}: {pattern[:100]!r}")
    path.write_text(new, encoding="utf-8")
    changed.add(path)


def decode_literal(token: str) -> str | None:
    try:
        value = ast.literal_eval(token)
    except Exception:
        return None
    return value if isinstance(value, str) else None


def replace_static_literals() -> None:
    for module_root, translations in STATIC_TRANSLATIONS.items():
        if not translations:
            continue
        root = ROOT / module_root
        prefix, _ = MODULES[module_root]
        for path in list(root.rglob("*.java")) + list(root.rglob("*.kt")):
            if "/build/" in path.as_posix():
                continue
            text = path.read_text(encoding="utf-8")
            original = text

            def repl(match: re.Match[str]) -> str:
                token = match.group(0)
                value = decode_literal(token)
                if value is None or value not in translations:
                    return token
                # Kotlin templates need a formatted-resource rewrite rather than replacing the
                # entire template literal. They are handled explicitly below.
                if "$" in value:
                    return token
                en = translations[value]
                key = key_for(prefix, value)
                add_string(module_root, key, en, value)
                return expr(path, key)

            text = LITERAL.sub(repl, text)
            if text != original:
                path.write_text(text, encoding="utf-8")
                changed.add(path)


def refactor_history_repair() -> None:
    path = ROOT / "apps/YNotify/feature/src/main/java/com/yagay/YNotify/data/HistoryRepairEngine.java"
    module = "apps/YNotify/feature"
    add_string(module, "ynotify_repair_summary",
               "Scanned %1$d · Type corrected %2$d · SystemUI %3$d · Notification subtype corrected %4$d · Heads-up linked %5$d · Cross-app heads-up %6$d · Duplicate notifications merged %7$d",
               "扫描 %1$d 条 · 类型纠正 %2$d 条 · SystemUI %3$d 条 · 通知子类型纠正 %4$d 条 · 横幅关联 %5$d 条 · 跨应用横幅 %6$d 条 · 重复通知合并 %7$d 条")
    add_string(module, "ynotify_repair_ambiguous", " · Insufficient evidence %1$d", " · 无充分证据 %1$d 条")
    add_string(module, "ynotify_repair_rechecked", " · Rechecked old merges %1$d", " · 重新检查旧合并 %1$d 条")
    add_string(module, "ynotify_repair_manual", " · Preserved manual classifications %1$d", " · 保留手动分类 %1$d 条")
    start = "        public String summary() {"
    end = "    public static void repairAsync(Context context, Callback callback) {"
    text = path.read_text(encoding="utf-8")
    i = text.find(start)
    j = text.find(end)
    if i < 0 or j < 0 or j <= i:
        if "R.string.ynotify_repair_summary" in text:
            return
        raise RuntimeError("HistoryRepairEngine summary block not found")
    replacement = '''        public String summary() {
            if (!succeeded()) return com.yagay.suite.api.YLocale.text(com.yagay.YNotify.R.string.ynotify_generated_76e6723dc54e) + error;
            String value = com.yagay.suite.api.YLocale.text(
                    com.yagay.YNotify.R.string.ynotify_repair_summary,
                    scanned, eventTypeChanged, systemUiClassified, notificationKindChanged,
                    mergedUi, crossPackageHeadsUp, mergedNotifications);
            if (ambiguousUi > 0) {
                value += com.yagay.suite.api.YLocale.text(com.yagay.YNotify.R.string.ynotify_repair_ambiguous, ambiguousUi);
            }
            if (recheckedMerged > 0) {
                value += com.yagay.suite.api.YLocale.text(com.yagay.YNotify.R.string.ynotify_repair_rechecked, recheckedMerged);
            }
            if (protectedManual > 0) {
                value += com.yagay.suite.api.YLocale.text(com.yagay.YNotify.R.string.ynotify_repair_manual, protectedManual);
            }
            return value;
        }
    }

'''
    path.write_text(text[:i] + replacement + text[j:], encoding="utf-8")
    changed.add(path)


def refactor_fix_recommendation() -> None:
    path = ROOT / "apps/YPower/feature/src/main/java/com/yagay/ypower/diag/FixRecommendationEngine.java"
    module = "apps/YPower/feature"
    strings = {
        "ypower_attr_base": (
            "This item is marked as %1$s. Rule ID is %2$s. Representative state is %3$s. Correlation score is %4$d out of 100.",
            "本项被标记为%1$s。规则 ID 为 %2$s。代表状态为 %3$s。关联分数为 %4$d，满分 100。"),
        "ypower_attr_delta": (" The closest event for this rule occurred %1$d ms before the recorded exit.", " 离真实退出最近的一次同规则事件相隔 %1$d ms。"),
        "ypower_attr_counts": (" This run recorded %1$d events. HIT %2$d, CHECKED %3$d, NOT_HIT %4$d, UNKNOWN %5$d.", " 本次共记录 %1$d 次。HIT %2$d，CHECKED %3$d，NOT_HIT %4$d，UNKNOWN %5$d。"),
        "ypower_attr_same_exit_thread": (" The representative event and the exit occurred on the same thread.", " 代表事件与退出发生在同一线程。"),
        "ypower_attr_shared_exit": (" The detection stack and exit stack share %1$d application frames.", " 检测栈与退出栈有 %1$d 个共同业务调用帧。"),
        "ypower_attr_same_fatal_thread": (" This detection and the Java Fatal event occurred on the same thread.", " 该检测与 Java Fatal 发生在同一线程。"),
        "ypower_attr_shared_fatal": (" The detection stack and Java Fatal stack share %1$d application frames, providing intermediate evidence between the check, exception, and exit.", " 检测栈与 Java Fatal 栈有 %1$d 个共同业务调用帧，形成检测、异常和退出之间的中间证据。"),
        "ypower_attr_input": (" Representative input: %1$s.", " 代表输入：%1$s。"),
        "ypower_attr_result": (" Representative result: %1$s.", " 代表返回结果：%1$s。"),
        "ypower_attr_exception": (" Representative exception: %1$s.", " 代表调用异常：%1$s。"),
        "ypower_attr_checked_note": (" CHECKED only proves that the app performed the check. It does not prove that the result matched a risk condition.", " CHECKED 只证明应用执行了检查，并不证明检查结果命中了风险状态。"),
    }
    for key, (en, zh) in strings.items(): add_string(module, key, en, zh)
    text = path.read_text(encoding="utf-8")
    pattern = r"    private static String buildAttributionExplanation\(DiagnosticFinding f, String role\) \{.*?\n    private static boolean notBlank"
    replacement = '''    private static String buildAttributionExplanation(DiagnosticFinding f, String role) {
        StringBuilder b = new StringBuilder(com.yagay.suite.api.YLocale.text(
                com.yagay.ypower.R.string.ypower_attr_base,
                role, f.ruleId, f.representativeState, f.correlationScore));
        if (f.closestDeltaMs != Long.MAX_VALUE) {
            b.append(com.yagay.suite.api.YLocale.text(
                    com.yagay.ypower.R.string.ypower_attr_delta, f.closestDeltaMs));
        }
        b.append(com.yagay.suite.api.YLocale.text(
                com.yagay.ypower.R.string.ypower_attr_counts,
                f.totalCount, f.hitCount, f.checkedCount, f.notHitCount, f.unknownCount));
        if (f.sameThreadAsExit) b.append(com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_attr_same_exit_thread));
        if (f.sharedExitFrames > 0) b.append(com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_attr_shared_exit, f.sharedExitFrames));
        if (f.sameThreadAsFatal) b.append(com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_attr_same_fatal_thread));
        if (f.sharedFatalFrames > 0) b.append(com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_attr_shared_fatal, f.sharedFatalFrames));
        if (notBlank(f.input)) b.append(com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_attr_input, f.input));
        if (notBlank(f.result)) b.append(com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_attr_result, f.result));
        if (notBlank(f.exception)) b.append(com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_attr_exception, f.exception));
        if (f.representativeState.name().equals("CHECKED")) {
            b.append(com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_attr_checked_note));
        }
        return b.toString();
    }

    private static boolean notBlank'''
    new, n = re.subn(pattern, replacement, text, count=1, flags=re.DOTALL)
    if n == 0 and "R.string.ypower_attr_base" not in text:
        raise RuntimeError("FixRecommendationEngine dynamic method not found")
    new = new.replace("import java.util.Locale;\n\n", "")
    new = re.sub(r"\n    private static boolean isChinese\(\) \{.*?\n    \}\n", "\n", new, count=1, flags=re.DOTALL)
    if new != text:
        path.write_text(new, encoding="utf-8")
        changed.add(path)


def refactor_correlation() -> None:
    path = ROOT / "apps/YPower/feature/src/main/java/com/yagay/ypower/diag/CorrelationEngine.java"
    module = "apps/YPower/feature"
    values = {
        "ypower_corr_no_exit": ("Checks were observed in this run, but no real exit was recorded, so no cause attribution is made.", "本次运行观察到检测行为，但没有记录到真实退出，因此不做原因归因。"),
        "ypower_corr_exit": ("A real exit or crash was recorded in this run", "本次运行记录到真实退出/崩溃事件"),
        "ypower_corr_no_candidate": ("An exit was recorded, but no runtime rule met both the score and independent causal-evidence requirements. Temporal proximity alone, NOT_HIT/UNKNOWN, static evidence and derived flows are not treated as exit causes.", "记录到了退出，但没有运行时规则同时满足分数与独立因果证据要求；单纯时间接近、NOT_HIT/UNKNOWN、静态证据和派生 Flow 不作为退出原因。"),
        "ypower_corr_primary": ("Primary attribution: %1$s (%2$d/100, %3$s, %4$s)", "主要归因：%1$s（%2$d/100，%3$s，%4$s）"),
        "ypower_corr_fatal_link": (", with an exception-propagation link to Java Fatal", "，并与 Java Fatal 存在异常传播关联"),
        "ypower_corr_secondary": ("; Secondary attribution: %1$s (%2$d/100, %3$s)", "；次要归因：%1$s（%2$d/100，%3$s）"),
        "ypower_strength_high": ("High confidence", "高可信"),
        "ypower_strength_strong": ("Strong correlation", "较强相关"),
        "ypower_strength_medium": ("Moderate correlation", "中等相关"),
        "ypower_strength_possible": ("Possible correlation", "可能相关"),
    }
    for key, (en, zh) in values.items(): add_string(module, key, en, zh)
    text = path.read_text(encoding="utf-8")
    text = text.replace('report.attribution =\n                    "本次运行观察到检测行为，但没有记录到真实退出，因此不做原因归因。";',
                        'report.attribution = com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_corr_no_exit);')
    text = text.replace('report.exitSummary = "本次运行记录到真实退出/崩溃事件";',
                        'report.exitSummary = com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_corr_exit);')
    text = text.replace('report.attribution =\n                    "记录到了退出，但没有运行时规则同时满足分数与独立因果证据要求；"\n                            + "单纯时间接近、NOT_HIT/UNKNOWN、静态证据和派生 Flow 不作为退出原因。";',
                        'report.attribution = com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_corr_no_candidate);')
    pattern = r"        StringBuilder attribution = new StringBuilder\(\);.*?        report\.attribution = attribution\.toString\(\);"
    replacement = '''        String attribution = com.yagay.suite.api.YLocale.text(
                com.yagay.ypower.R.string.ypower_corr_primary,
                primary.title,
                primary.correlationScore,
                primary.representativeState,
                strength(primary.correlationScore));
        if (primary.sameThreadAsFatal || primary.sharedFatalFrames > 0) {
            attribution += com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_corr_fatal_link);
        }
        if (secondary != null) {
            attribution += com.yagay.suite.api.YLocale.text(
                    com.yagay.ypower.R.string.ypower_corr_secondary,
                    secondary.title,
                    secondary.correlationScore,
                    secondary.representativeState);
        }
        report.attribution = attribution;'''
    text, n = re.subn(pattern, replacement, text, count=1, flags=re.DOTALL)
    if n == 0 and "R.string.ypower_corr_primary" not in text:
        raise RuntimeError("CorrelationEngine attribution builder not found")
    old_strength = '''    private static String strength(int score) {
        if (score >= 85) return "高可信";
        if (score >= 70) return "较强相关";
        if (score >= 60) return "中等相关";
        return "可能相关";
    }'''
    new_strength = '''    private static String strength(int score) {
        if (score >= 85) return com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_strength_high);
        if (score >= 70) return com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_strength_strong);
        if (score >= 60) return com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_strength_medium);
        return com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_strength_possible);
    }'''
    text = text.replace(old_strength, new_strength)
    path.write_text(text, encoding="utf-8")
    changed.add(path)


def refactor_supplemental() -> None:
    path = ROOT / "apps/YPower/feature/src/main/java/com/yagay/ypower/diag/SupplementalReportProcessor.java"
    module = "apps/YPower/feature"
    values = {
        "ypower_supp_primary": ("Primary attribution", "主要归因"),
        "ypower_supp_secondary": ("Secondary attribution", "次要归因"),
        "ypower_supp_why": ("This item is marked as %1$s, rule ID=%2$s, representative state=%3$s, correlation score=%4$d/100.", "本项被标记为%1$s，规则 ID=%2$s，代表状态=%3$s，关联分数=%4$d/100。"),
        "ypower_supp_delta": (" Closest to exit: %1$d ms.", " 与退出最近相隔 %1$d ms。"),
        "ypower_supp_same_thread": (" The representative event and exit occurred on the same thread.", " 代表事件与退出发生在同一线程。"),
        "ypower_supp_shared_frames": (" The exit stack shares %1$d application frames.", " 与退出栈共享 %1$d 个业务帧。"),
        "ypower_supp_title": ("%1$s: %2$s", "%1$s：%2$s"),
        "ypower_run_counts": ("; this run %1$d times (HIT %2$d / CHECKED %3$d / NOT_HIT %4$d / UNKNOWN %5$d)", "；本次运行 %1$d 次（HIT %2$d / CHECKED %3$d / NOT_HIT %4$d / UNKNOWN %5$d）"),
    }
    for key, (en, zh) in values.items(): add_string(module, key, en, zh)
    text = path.read_text(encoding="utf-8")
    pattern = r"            String role = finding\.attributionRank == 1 \? \"主要归因\" : \"次要归因\";.*?\n\n            finding\.recommendations\.add\(new FixRecommendation\(\n                    role \+ \"：\" \+ def\.title,"
    replacement = '''            String role = finding.attributionRank == 1
                    ? com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_supp_primary)
                    : com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_supp_secondary);
            String why = com.yagay.suite.api.YLocale.text(
                    com.yagay.ypower.R.string.ypower_supp_why,
                    role, finding.ruleId, finding.representativeState, finding.correlationScore);
            if (finding.closestDeltaMs != Long.MAX_VALUE) {
                why += com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_supp_delta, finding.closestDeltaMs);
            }
            if (finding.sameThreadAsExit) {
                why += com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_supp_same_thread);
            }
            if (finding.sharedExitFrames > 0) {
                why += com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_supp_shared_frames, finding.sharedExitFrames);
            }

            finding.recommendations.add(new FixRecommendation(
                    com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_supp_title, role, def.title),'''
    text, n = re.subn(pattern, replacement, text, count=1, flags=re.DOTALL)
    if n == 0 and "R.string.ypower_supp_why" not in text:
        raise RuntimeError("SupplementalReportProcessor recommendation block not found")
    old_suffix = '''        return "；本次运行 " + finding.totalCount + " 次"
                + "（HIT " + finding.hitCount
                + " / CHECKED " + finding.checkedCount
                + " / NOT_HIT " + finding.notHitCount
                + " / UNKNOWN " + finding.unknownCount + "）";'''
    new_suffix = '''        return com.yagay.suite.api.YLocale.text(
                com.yagay.ypower.R.string.ypower_run_counts,
                finding.totalCount, finding.hitCount, finding.checkedCount,
                finding.notHitCount, finding.unknownCount);'''
    text = text.replace(old_suffix, new_suffix)
    path.write_text(text, encoding="utf-8")
    changed.add(path)


def refactor_diagnostic_engine() -> None:
    path = ROOT / "apps/YPower/feature/src/main/java/com/yagay/ypower/diag/DiagnosticEngine.java"
    module = "apps/YPower/feature"
    add_string(module, "ypower_run_counts", "; this run %1$d times (HIT %2$d / CHECKED %3$d / NOT_HIT %4$d / UNKNOWN %5$d)", "；本次运行 %1$d 次（HIT %2$d / CHECKED %3$d / NOT_HIT %4$d / UNKNOWN %5$d）")
    text = path.read_text(encoding="utf-8")
    old = '''            finding.summary = finding.summary
                    + "；本次运行 " + finding.totalCount + " 次"
                    + "（HIT " + finding.hitCount
                    + " / CHECKED " + finding.checkedCount
                    + " / NOT_HIT " + finding.notHitCount
                    + " / UNKNOWN " + finding.unknownCount + "）";'''
    new = '''            finding.summary = finding.summary
                    + com.yagay.suite.api.YLocale.text(
                    com.yagay.ypower.R.string.ypower_run_counts,
                    finding.totalCount, finding.hitCount, finding.checkedCount,
                    finding.notHitCount, finding.unknownCount);'''
    text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
    changed.add(path)


def refactor_diagnostic_styling() -> None:
    path = ROOT / "apps/YPower/feature/src/main/java/com/yagay/ypower/ui/DiagnosticActivity.java"
    text = path.read_text(encoding="utf-8")
    old_simple = '            Pattern linePattern = Pattern.compile("(?m)^• .*?检测状态=([^\\\\s\\\\n]+).*$");'
    new_simple = '''            String detectionLabel = getString(com.yagay.ypower.R.string.ypower_generated_81c4e4e7a4c8);
            Pattern linePattern = Pattern.compile(
                    "(?m)^\\\\s*" + Pattern.quote(detectionLabel) + "\\\\s*[:：]\\\\s*([^\\\\s\\\\n]+).*$");'''
    text = text.replace(old_simple, new_simple)
    old_state = '            Pattern statePattern = Pattern.compile("应用检测状态：([^\\\\s\\\\n]+)");'
    new_state = '''            String appStateLabel = getString(com.yagay.ypower.R.string.ypower_generated_3057e103ffb7);
            Pattern statePattern = Pattern.compile(
                    Pattern.quote(appStateLabel) + "\\\\s*[:：]\\\\s*([^\\\\s\\\\n]+)");'''
    text = text.replace(old_state, new_state)
    path.write_text(text, encoding="utf-8")
    changed.add(path)


def refactor_ydiag() -> None:
    module = "apps/YDiag/feature"
    vals = {
        "ydiag_export_targets": ("Targets: %1$s", "目标: %1$s"),
        "ydiag_export_marks": ("Problem marks: %1$s", "问题标记: %1$s"),
        "ydiag_export_issues": ("Issues: %1$d", "异常: %1$d"),
        "ydiag_root_unavailable": ("Root was not available for this session.\n", "本次会话 Root 不可用。\n"),
        "ydiag_size_mb": ("%1$d MB", "%1$d MB"),
        "ydiag_opencv_failed": ("OpenCV initialization failed: %1$s", "OpenCV 初始化失败: %1$s"),
    }
    for key, (en, zh) in vals.items(): add_string(module, key, en, zh)
    p = ROOT / "apps/YDiag/feature/src/main/java/com/yagay/ydiag/export/DiagnosticExporter.kt"
    text = p.read_text(encoding="utf-8")
    text = text.replace('appendLine("目标: ${meta.targetPackages.joinToString()}")', 'appendLine(com.yagay.suite.api.YLocale.text(com.yagay.ydiag.R.string.ydiag_export_targets, meta.targetPackages.joinToString()))')
    text = text.replace('appendLine("问题标记: ${meta.problemMarks.joinToString()}")', 'appendLine(com.yagay.suite.api.YLocale.text(com.yagay.ydiag.R.string.ydiag_export_marks, meta.problemMarks.joinToString()))')
    text = text.replace('appendLine("异常: ${issues.size}")', 'appendLine(com.yagay.suite.api.YLocale.text(com.yagay.ydiag.R.string.ydiag_export_issues, issues.size))')
    p.write_text(text, encoding="utf-8"); changed.add(p)
    p = ROOT / "apps/YDiag/feature/src/main/java/com/yagay/ydiag/export/EvidenceCollector.kt"
    text = p.read_text(encoding="utf-8").replace('"Root was not available for this session.\\n"', 'com.yagay.suite.api.YLocale.text(com.yagay.ydiag.R.string.ydiag_root_unavailable)')
    p.write_text(text, encoding="utf-8"); changed.add(p)
    p = ROOT / "apps/YDiag/feature/src/main/java/com/yagay/ydiag/ui/MainActivity.kt"
    text = p.read_text(encoding="utf-8").replace('Text("${localLimit} MB", modifier = Modifier.width(90.dp))', 'Text(stringResource(R.string.ydiag_size_mb, localLimit), modifier = Modifier.width(90.dp))')
    p.write_text(text, encoding="utf-8"); changed.add(p)


def refactor_yfloat_templates() -> None:
    module = "apps/YFloat/feature"
    add_string(module, "yfloat_opencv_failed", "OpenCV initialization failed: %1$s", "OpenCV 初始化失败: %1$s")
    p = ROOT / "apps/YFloat/feature/src/main/java/com/yagay/YFloat/PaddleOcrBridge.kt"
    text = p.read_text(encoding="utf-8").replace('throw IllegalStateException("OpenCV 初始化失败: $detail")', 'throw IllegalStateException(com.yagay.suite.api.YLocale.text(com.yagay.YFloat.R.string.yfloat_opencv_failed, detail))')
    p.write_text(text, encoding="utf-8"); changed.add(p)


def refactor_ytask() -> None:
    module = "apps/YTaskManager/feature"
    add_string(module, "ytm_cpu_core_label", "CPU %1$d", "CPU %1$d")
    add_string(module, "ytm_milliseconds", "%1$d ms", "%1$d ms")
    p = ROOT / "apps/YTaskManager/feature/src/main/java/com/yagay/YTaskManager/ui/TaskManagerScreen.kt"
    text = p.read_text(encoding="utf-8")
    text = text.replace('Text(" ${stringResource(R.string.ytm_foreground_short)}", style = MaterialTheme.typography.labelSmall)', 'Text(" " + stringResource(R.string.ytm_foreground_short), style = MaterialTheme.typography.labelSmall)')
    text = text.replace('Text("CPU ${core.core}", style = MaterialTheme.typography.labelMedium)', 'Text(stringResource(R.string.ytm_cpu_core_label, core.core), style = MaterialTheme.typography.labelMedium)')
    text = text.replace('label = { Text("${value} ms") },', 'label = { Text(stringResource(R.string.ytm_milliseconds, value)) },')
    p.write_text(text, encoding="utf-8"); changed.add(p)


def write_resources() -> None:
    for (module_root, _), entries in resources.items():
        for locale, idx in (("values", 0), ("values-zh-rCN", 1)):
            path = ROOT / module_root / "src/main/res" / locale / "dynamic_i18n.xml"
            path.parent.mkdir(parents=True, exist_ok=True)
            existing: dict[str, str] = {}
            if path.exists():
                try:
                    root = ET.parse(path).getroot()
                    for node in root.findall("string"):
                        name = node.attrib.get("name")
                        if name: existing[name] = node.text or ""
                except ET.ParseError:
                    existing = {}
            for key, pair in entries.items():
                existing[key] = pair[idx]
            root = ET.Element("resources")
            for key in sorted(existing):
                node = ET.SubElement(root, "string", {"name": key})
                node.text = existing[key]
            tree = ET.ElementTree(root)
            ET.indent(tree, space="    ")
            tree.write(path, encoding="utf-8", xml_declaration=True)
            changed.add(path)


def main() -> None:
    refactor_history_repair()
    refactor_fix_recommendation()
    refactor_correlation()
    refactor_supplemental()
    refactor_diagnostic_engine()
    refactor_diagnostic_styling()
    refactor_ydiag()
    refactor_yfloat_templates()
    refactor_ytask()
    replace_static_literals()
    write_resources()
    print(f"I18N_DYNAMIC_CHANGED={len(changed)}")
    for path in sorted(changed):
        print(path.relative_to(ROOT))


if __name__ == "__main__":
    main()
