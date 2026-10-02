#!/usr/bin/env python3
from __future__ import annotations

import re
import sys
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
EXTS = {".java", ".kt", ".kts", ".xml"}
SKIP_DIRS = {".git", ".gradle", "build", "generated", ".idea", "node_modules"}

CJK = re.compile(r"[\u3400-\u4dbf\u4e00-\u9fff]")
SYMBOL_ONLY = re.compile(r'^\s*["\']\s*[≡↑↓←→×›‹•·…—–★☆✓✔✕✖●○◆◇▶◀▲▼＋−]\s*["\']\s*$')
STRING_LITERAL = re.compile(r'"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'')

UI_HINTS = (
    "setText(", "setTitle(", "setSubtitle(", "setMessage(", "setHint(",
    "setContentDescription(", "Toast.makeText(", "Snackbar.make(",
    "AppUi.text(", "AppUi.caption(", "AppUi.section(", "AppUi.navRow(",
    "AppUi.pageRoot(", "AppUi.primaryButton(", "AppUi.secondaryButton(",
    "AppUi.compactButton(", "AppUi.switchRow(", "NotificationCompat.Builder",
    ".setContentTitle(", ".setContentText(", ".setTicker(",
    "Text(", "Button(", "TextButton(", "OutlinedButton(", "AlertDialog(",
)

XML_VISIBLE_ATTR = re.compile(
    r'android:(?:text|hint|contentDescription|title|summary|label)\s*=\s*"(?!@(?:string|plurals)/)([^"@][^"]*)"'
)
TECH_RE = re.compile(
    r'^(?:[a-zA-Z0-9_.:/?&=#%+@|,;\-]+|https?://\S+|[A-Z0-9_]+|\.?[a-zA-Z][\w.$]*(?:\.[\w$]+)+)$'
)


def skipped(path: Path) -> bool:
    rel = path.relative_to(ROOT)
    if any(part in SKIP_DIRS for part in rel.parts):
        return True
    s = rel.as_posix()
    if "/src/test/" in s or "/src/androidTest/" in s or "/src/testFixtures/" in s:
        return True
    if s.startswith("tools/"):
        return True
    if "/res/values" in s and path.suffix == ".xml":
        return True
    return False


def literal_value(token: str) -> str:
    return token[1:-1] if len(token) >= 2 else token


def looks_english_ui(value: str) -> bool:
    v = value.strip()
    if len(v) < 2 or TECH_RE.match(v):
        return False
    words = re.findall(r"[A-Za-z]{2,}", v)
    return bool(words) and (" " in v or len(words) >= 2)


def scan_source(lines: list[str]) -> list[tuple[int, str, str]]:
    out: list[tuple[int, str, str]] = []
    for no, line in enumerate(lines, 1):
        stripped = line.strip()
        if not stripped or stripped.startswith("//") or stripped.startswith("*"):
            continue
        literals = STRING_LITERAL.findall(line)
        if not literals:
            continue
        ui_context = any(h in line for h in UI_HINTS)
        for token in literals:
            value = literal_value(token)
            if not value:
                continue
            if CJK.search(value):
                out.append((no, "CJK_LITERAL", stripped))
                break
            if SYMBOL_ONLY.match(token):
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

    by_module = Counter(module_name(rel) for rel, _, _, _ in findings)
    by_kind = Counter(kind for _, _, kind, _ in findings)
    print(f"I18N_AUDIT_RUNTIME_FINDINGS={len(findings)}")
    print("I18N_AUDIT_BY_KIND=" + ",".join(f"{k}:{v}" for k, v in sorted(by_kind.items())))
    print("I18N_AUDIT_BY_MODULE=" + ",".join(f"{k}:{v}" for k, v in by_module.most_common()))
    for rel, no, kind, snippet in findings:
        print(f"{rel}:{no}: [{kind}] {snippet[:260]}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
