#!/usr/bin/env python3
"""Prevent ordinary feature UI from silently introducing a second set of Material3 controls."""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCES = (
    ROOT / "apps",
    ROOT / "suite",
    ROOT / "next/core/ui",
    ROOT / "next/core/productui",
    ROOT / "next/feature",
)
# Navigation structures and custom product workspaces may compose other Material3 primitives.
# Common controls instead pass through libs/yui to share geometry, defaults and accessibility.
CONTROL_IMPORT = re.compile(
    r"^import\s+androidx\.compose\.material3\."
    r"(Button|OutlinedButton|TextButton|IconButton|Checkbox|OutlinedTextField)\b",
    re.MULTILINE,
)
CONTROL_FQCN = re.compile(
    r"\bandroidx\.compose\.material3\."
    r"(Button|OutlinedButton|TextButton|IconButton|Checkbox|OutlinedTextField)\s*\("
)

def main() -> None:
    failures: list[str] = []
    scanned = 0
    for directory in SOURCES:
        if not directory.is_dir():
            continue
        for path in directory.rglob("*"):
            if path.suffix not in (".kt", ".java") or not path.is_file():
                continue
            if any(x in path.parts for x in ("build", "test", "androidTest")):
                continue
            source = path.read_text(encoding="utf-8", errors="replace")
            scanned += 1
            if path.suffix == ".kt":
                bad = sorted(set(CONTROL_IMPORT.findall(source) + CONTROL_FQCN.findall(source)))
                if "import androidx.compose.material3.*" in source:
                    bad.append("material3 wildcard import")
                if bad:
                    failures.append(f"{path.relative_to(ROOT)}: {', '.join(bad)}")
            elif (
                "import androidx.appcompat.app.AlertDialog;" in source
                and "new AlertDialog.Builder(" in source
            ):
                failures.append(f"{path.relative_to(ROOT)}: use YViewDialogs.builder()")
    if failures:
        print("yui-controls: independent Material3 controls found:", file=sys.stderr)
        for failure in failures:
            print(" - " + failure, file=sys.stderr)
        return 1
    print(f"yui-controls: OK scanned={scanned} shared controls=YUI")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
