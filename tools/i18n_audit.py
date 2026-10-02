#!/usr/bin/env python3
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
EXTS = {".java", ".kt", ".kts", ".xml"}
SKIP_DIRS = {".git", ".gradle", "build", "generated", ".idea", "node_modules"}

CJK = re.compile(r"[\u3400-\u4dbf\u4e00-\u9fff]")
SYMBOL_ONLY = re.compile(r'^\s*["\']\s*[≡↑↓←→×›‹•·…—–★☆✓✔✕✖●○◆◇▶◀▲▼＋−]\s*["\']\s*$')
STRING_LITERAL = re.compile(r'"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'')

# Calls/assignments whose literal arguments are very likely user-visible.
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

# Technical literals that can contain ordinary English and are not UI copy.
TECH_RE = re.compile(
    r'^(?:[a-zA-Z0-9_.:/?&=#%+@|,;\-]+|https?://\S+|[A-Z0-9_]+|\.?[a-zA-Z][\w.$]*(?:\.[\w$]+)+)$'
)


def skipped(path: Path) -> bool:
    rel = path.relative_to(ROOT)
    if any(part in SKIP_DIRS for part in rel.parts):
        return True
    s = rel.as_posix()
    # Resource files are the desired destination. Chinese translations are expected there.
    if "/res/values" in s and path.suffix == ".xml":
        return True
    return False


def literal_value(token: str) -> str:
    if len(token) < 2:
        return token
    return token[1:-1]


def looks_english_ui(value: str) -> bool:
    v = value.strip()
    if len(v) < 2 or TECH_RE.match(v):
        return False
    words = re.findall(r"[A-Za-z]{2,}", v)
    return bool(words) and (" " in v or len(words) >= 2)


def scan_source(path: Path, lines: list[str]) -> list[tuple[int, str, str]]:
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


def scan_xml(path: Path, lines: list[str]) -> list[tuple[int, str, str]]:
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
        found = scan_xml(path, lines) if path.suffix == ".xml" else scan_source(path, lines)
        rel = path.relative_to(ROOT).as_posix()
        findings.extend((rel, no, kind, snippet) for no, kind, snippet in found)

    print(f"I18N_AUDIT_FINDINGS={len(findings)}")
    for rel, no, kind, snippet in findings:
        print(f"{rel}:{no}: [{kind}] {snippet[:260]}")
    # Audit is informative during the cleanup phase; do not break builds yet.
    return 0


if __name__ == "__main__":
    sys.exit(main())
