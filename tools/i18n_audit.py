#!/usr/bin/env python3
from __future__ import annotations

import re
import sys
import xml.etree.ElementTree as ET
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
EXTS = {".java", ".kt", ".kts", ".xml"}
SKIP_DIRS = {".git", ".gradle", "build", "generated", ".idea", "node_modules"}

CJK = re.compile(r"[\u3400-\u4dbf\u4e00-\u9fff]")
SYMBOL_ONLY = re.compile(r'^\s*["\']\s*[≡↑↓←→×›‹•·—–★☆✓✔✕✖●○◆◇▶◀▲▼＋−]\s*["\']\s*$')
STRING_LITERAL = re.compile(r'"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'')

UI_PATTERNS = (
    re.compile(r"\bset(?:Text|Title|Subtitle|Message|Hint|ContentDescription)\s*\("),
    re.compile(r"\b(?:Toast\.makeText|Snackbar\.make)\s*\("),
    re.compile(r"\bAppUi\.(?:text|caption|section|navRow|pageRoot|primaryButton|secondaryButton|compactButton|switchRow)\s*\("),
    re.compile(r"\.(?:setContentTitle|setContentText|setTicker)\s*\("),
    re.compile(r"(?<![\w.])(?:Text|Button|TextButton|OutlinedButton|AlertDialog)\s*\("),
    re.compile(r"\b(?:text|title|subtitle|label|message|hint|contentDescription)\s*="),
)

LOCALE_BRANCH_PATTERNS = (
    re.compile(r"Locale\.getDefault\(\).*getLanguage\s*\("),
    re.compile(r"getLanguage\s*\(\).*['\"]zh(?:[-_][A-Za-z]+)?['\"]"),
    re.compile(r"['\"]zh(?:[-_][A-Za-z]+)?['\"].*getLanguage\s*\("),
    re.compile(r"\bisChinese\s*\("),
)

XML_VISIBLE_ATTR = re.compile(
    r'android:(?:text|hint|contentDescription|title|summary|label)\s*=\s*"(?!@(?:string|plurals)/)([^"@][^"]*)"'
)
PACKAGE_OR_PATH = re.compile(r"^(?:https?://\S+|\.?[A-Za-z][\w$]*(?:\.[\w$]+)+|/[\w./-]+)$")
TECH_SINGLE = {
    "android", "SystemUI", "Root", "LSPosed", "KernelSU", "Magisk", "Shizuku",
    "API", "CPU", "GPU", "RAM", "ROM", "MB", "GB", "KB", "ms", "PID", "UID",
    "URL", "URI", "HTTP", "HTTPS", "JSON", "XML", "APK", "Intent", "Bitmap",
    "FLAG_SECURE", "OxygenOS", "Compose", "WebView", "JNI", "ANR", "TID",
}
INTERNAL_TEXT_HINTS = (
    "Log.d(", "Log.i(", "Log.w(", "Log.e(", "Log.v(", "Timber.", "System.out.",
    "zip.addText(", ".writeText(", "ClipData.newPlainText(",
)


def skipped(path: Path) -> bool:
    rel = path.relative_to(ROOT)
    if any(part in SKIP_DIRS for part in rel.parts):
        return True
    if "tools" in rel.parts:
        return True
    s = rel.as_posix()
    if "/src/test/" in s or "/src/androidTest/" in s or "/src/testFixtures/" in s:
        return True
    if "/res/values" in s and path.suffix == ".xml":
        return True
    return False


def literal_value(token: str) -> str:
    return token[1:-1] if len(token) >= 2 else token


def has_ui_context(line: str) -> bool:
    return any(pattern.search(line) for pattern in UI_PATTERNS)


def is_internal_text_line(line: str) -> bool:
    return any(hint in line for hint in INTERNAL_TEXT_HINTS)


def looks_english_ui(value: str) -> bool:
    v = value.strip()
    if len(v) < 2 or v in TECH_SINGLE or PACKAGE_OR_PATH.match(v):
        return False
    if re.fullmatch(r"[A-Z0-9_./:+-]+", v):
        return False
    return bool(re.search(r"[A-Za-z]{2,}", v))


def scan_source(lines: list[str]) -> list[tuple[int, str, str]]:
    out: list[tuple[int, str, str]] = []
    for no, line in enumerate(lines, 1):
        stripped = line.strip()
        if not stripped or stripped.startswith("//") or stripped.startswith("*"):
            continue

        if any(pattern.search(line) for pattern in LOCALE_BRANCH_PATTERNS):
            out.append((no, "LOCALE_BRANCH", stripped))
            continue

        literals = STRING_LITERAL.findall(line)
        if not literals:
            continue
        ui_context = has_ui_context(line)
        internal_line = is_internal_text_line(line)
        for token in literals:
            value = literal_value(token)
            if not value:
                continue
            if CJK.search(value) and not internal_line:
                out.append((no, "CJK_LITERAL", stripped))
                break
            if ui_context and SYMBOL_ONLY.match(token):
                out.append((no, "SYMBOL_UI", stripped))
                break
            if ui_context and looks_english_ui(value):
                out.append((no, "UI_LITERAL", stripped))
                break
    return out


def scan_xml(lines: list[str]) -> list[tuple[int, str, str]]:
    out: list[tuple[int, str, str]] = []
    for no, line in enumerate(lines, 1):
        stripped = line.strip()
        m = XML_VISIBLE_ATTR.search(line)
        if m:
            value = m.group(1).strip()
            if value and not value.startswith("?"):
                out.append((no, "XML_VISIBLE_LITERAL", stripped))
        elif CJK.search(line):
            out.append((no, "CJK_XML", stripped))
    return out


def module_name(rel: str) -> str:
    parts = rel.split("/")
    if len(parts) >= 2 and parts[0] == "apps":
        return parts[1]
    if len(parts) >= 2 and parts[0] == "libs":
        return "libs/" + parts[1]
    return parts[0]


def collect_strings(values_dir: Path) -> dict[str, tuple[str, Path]]:
    out: dict[str, tuple[str, Path]] = {}
    if not values_dir.exists():
        return out
    for xml in sorted(values_dir.glob("*.xml")):
        try:
            root = ET.parse(xml).getroot()
        except ET.ParseError:
            continue
        for node in root.findall("string"):
            name = node.attrib.get("name")
            if not name or node.attrib.get("translatable") == "false":
                continue
            value = "".join(node.itertext()).strip()
            out[name] = (value, xml)
    return out


def scan_resource_parity() -> list[tuple[str, int, str, str]]:
    findings: list[tuple[str, int, str, str]] = []
    for values_dir in sorted(ROOT.rglob("src/main/res/values")):
        if any(part in SKIP_DIRS for part in values_dir.relative_to(ROOT).parts):
            continue
        default = collect_strings(values_dir)
        if not default:
            continue
        zh_dir = values_dir.parent / "values-zh-rCN"
        zh = collect_strings(zh_dir)

        for name, (value, source) in sorted(default.items()):
            rel = source.relative_to(ROOT).as_posix()
            if CJK.search(value):
                findings.append((rel, 0, "DEFAULT_RESOURCE_CJK", f"{name}={value[:180]}"))
            if name not in zh:
                findings.append((rel, 0, "MISSING_ZH", name))

        for name, (_, source) in sorted(zh.items()):
            if name not in default:
                findings.append((source.relative_to(ROOT).as_posix(), 0, "ORPHAN_ZH", name))
    return findings


def main() -> int:
    findings: list[tuple[str, int, str, str]] = []
    for path in ROOT.rglob("*"):
        if not path.is_file() or path.suffix not in EXTS or skipped(path):
            continue
        try:
            text = path.read_text(encoding="utf-8")
        except UnicodeDecodeError:
            continue
        lines = text.splitlines()
        found = scan_xml(lines) if path.suffix == ".xml" else scan_source(lines)
        rel = path.relative_to(ROOT).as_posix()
        findings.extend((rel, no, kind, snippet) for no, kind, snippet in found)

    findings.extend(scan_resource_parity())

    by_module = Counter(module_name(rel) for rel, _, _, _ in findings)
    by_kind = Counter(kind for _, _, kind, _ in findings)
    print(f"I18N_AUDIT_ACTIONABLE_FINDINGS={len(findings)}")
    print("I18N_AUDIT_BY_KIND=" + ",".join(f"{k}:{v}" for k, v in sorted(by_kind.items())))
    print("I18N_AUDIT_BY_MODULE=" + ",".join(f"{k}:{v}" for k, v in by_module.most_common()))
    for rel, no, kind, snippet in findings:
        location = f"{rel}:{no}" if no else rel
        print(f"{location}: [{kind}] {snippet[:260]}")

    if findings:
        print("I18N_AUDIT_RESULT=FAIL")
        return 1
    print("I18N_AUDIT_RESULT=PASS")
    return 0


if __name__ == "__main__":
    sys.exit(main())
