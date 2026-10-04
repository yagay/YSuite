#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
violations = []

legacy_roots = ["apps", "libs/yui", "suite", "standalone"]
for legacy in legacy_roots:
    if (ROOT / legacy).exists():
        violations.append(f"legacy source root must not exist in clean branch: {legacy}")

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

    if rel.startswith("feature/") and "/impl/" in rel and "feature." in text and ".impl" in text:
        violations.append(f"{rel}: feature implementation must not depend on another feature implementation")

    if rel.startswith("app/") and "androidx.compose.material3." in text:
        violations.append(f"{rel}: app must render through core:ui")

    if rel.startswith("app/") and "com.yagay.ysuite.designsystem" in text:
        violations.append(f"{rel}: app must not depend directly on design system")

    if rel.startswith("app/") and "com.yagay.ysuite.resources" in text:
        violations.append(f"{rel}: app must not depend directly on resource implementation")

    if rel.startswith("core/designsystem/") and "com.yagay.ysuite.ui" in text:
        violations.append(f"{rel}: design system must not depend on core:ui")

    if rel.startswith("core/ui/") and "feature." in text:
        violations.append(f"{rel}: core:ui must not depend on features")

if violations:
    print("\n".join(violations))
    sys.exit(1)

print("Architecture boundaries OK")
