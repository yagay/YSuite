#!/usr/bin/env python3
"""Fail on per-screen button geometry that bypasses YUI intrinsic sizing.

Only high-confidence patterns are failures. Content thumbnails, editor widths,
window/dialog sizes, form-field weights and touch-area minimums are NOT buttons.
"""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
SCAN_ROOTS = ("apps", "suite", "next", "libs")
JAVA_DECL = re.compile(r"\b(?:MaterialButton|Button)\s+(\w+)\s*(?:=|;)")
WEIGHTED = re.compile(
    r"\.addView\(\s*(\w+)\s*,\s*new\s+LinearLayout\.LayoutParams\("
    r"\s*0\s*,\s*(?:-2|ViewGroup\.LayoutParams\.WRAP_CONTENT)\s*,\s*1(?:\.0)?f?\s*\)"
)
FIXED_COMPOSE_BUTTON_HEIGHT = re.compile(
    r"\.height\(\s*(?:LocalYAppearance\.current\.)?"
    r"(?:buttonHeightDp|buttonGeometry\(\)\.visualHeightDp)\.dp\s*\)"
)
FORCED_MAX_LINES = re.compile(r"\b(\w+)\.setMaxLines\(\s*[12]\s*\)")
FORCED_WIDTH = re.compile(r"\b(\w+)\.set(?:Width|Height)\(")
KOTLIN_CONTROL = re.compile(
    r"\b(?:YUi(?:Button|OutlinedButton|TextButton|ElevatedButton|FilledTonalButton|"
    r"AssistChip|SuggestionChip|InputChip)|"
    r"YPrimaryButton|YSecondaryButton|MaterialButton|OutlinedButton|TextButton|"
    r"ElevatedButton|FilledTonalButton|AssistChip|SuggestionChip|InputChip)\s*\("
)
KOTLIN_FIXED_MODIFIER = re.compile(
    r"\bmodifier\s*=\s*(?:Modifier|modifier)[^\n]*"
    r"\.(?:height|width|requiredHeight|requiredWidth)\s*\("
)

def main() -> None:
    problems = []
    checked = 0
    for root in SCAN_ROOTS:
        for path in (ROOT / root).rglob("*"):
            if path.suffix not in (".kt", ".java") or "/src/main/" not in path.as_posix():
                continue
            checked += 1
            source = path.read_text(encoding="utf-8")
            lines = source.splitlines()
            names = set(JAVA_DECL.findall(source)) if path.suffix == ".java" else set()
            for lineno, line in enumerate(lines, 1):
                if line.lstrip().startswith(("//", "*", "#")):
                    continue
                if FIXED_COMPOSE_BUTTON_HEIGHT.search(line):
                    problems.append((path, lineno, "fixed YUI button height", line.strip()))
                if names:
                    m = WEIGHTED.search(line)
                    if m and m.group(1) in names:
                        problems.append((path, lineno, "equal-width button weight", line.strip()))
                    m = FORCED_MAX_LINES.search(line)
                    if m and m.group(1) in names:
                        problems.append((path, lineno, "button label truncated to one/two lines", line.strip()))
                    m = FORCED_WIDTH.search(line)
                    if m and m.group(1) in names:
                        problems.append((path, lineno, "button width/height overridden", line.strip()))
                if path.suffix == ".kt" and KOTLIN_FIXED_MODIFIER.search(line):
                    nearby = "\n".join(lines[max(0, lineno - 12):lineno])
                    if KOTLIN_CONTROL.search(nearby):
                        problems.append((path, lineno, "local Compose button dimension", line.strip()))
    # Shared action builders must not override the same YUI properties locally.
    unified = (ROOT / "libs/yui/src/main/java/com/yagay/yui/YUnifiedDesign.kt").read_text(encoding="utf-8")
    for old in (
        "modifier = Modifier.heightIn(min = LocalYAppearance.current.buttonHeightDp.dp)",
        "maxLines = 1,\n                        overflow = TextOverflow.Ellipsis",
    ):
        if old in unified:
            problems.append((ROOT / "libs/yui/src/main/java/com/yagay/yui/YUnifiedDesign.kt",
                             0, "redundant action or tab label constraint", old))
    # The shared implementation must make minimum height intrinsic, never exact.
    controls = (ROOT / "libs/yui/src/main/java/com/yagay/yui/YActionSizing.kt").read_text(encoding="utf-8")
    if "minimumInteractiveComponentSize().heightIn(min = minimumVisualHeight)" not in controls:
        problems.append((ROOT / "libs/yui", 0, "shared intrinsic sizing contract missing", ""))
    for path, line, reason, detail in problems[:100]:
        print(f"{path.relative_to(ROOT)}:{line}: {reason}: {detail}", file=sys.stderr)
    if problems:
        raise SystemExit(f"YUI intrinsic action check failed: {len(problems)} overrides in {checked} source files")
    print(f"YUI intrinsic action check OK across {checked} source files")

if __name__ == "__main__":
    main()
