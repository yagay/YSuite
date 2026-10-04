#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
violations = []

for path in ROOT.rglob("*"):
    if not path.is_file():
        continue
    rel = path.relative_to(ROOT).as_posix()
    if "/build/" in f"/{rel}/":
        continue
    if path.suffix not in {".kt", ".java"}:
        continue

    text = path.read_text(encoding="utf-8", errors="ignore")

    if rel.startswith("feature/") and "androidx.compose.material3." in text:
        violations.append(f"{rel}: feature imports Material3 directly; use core design system")

    if rel.startswith("feature/") and "com.google.android.material." in text:
        violations.append(f"{rel}: feature imports Material Views directly; use core design system")

    if rel.startswith("app/") and "androidx.compose.material3." in text:
        violations.append(f"{rel}: app must render through core:ui/designsystem")

if violations:
    print("\n".join(violations))
    sys.exit(1)

print("Architecture boundaries OK")
