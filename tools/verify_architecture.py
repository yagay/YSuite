#!/usr/bin/env python3
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
violations = []

legacy_roots = ["apps", "libs/yui", "suite", "standalone"]
for legacy in legacy_roots:
    if (ROOT / legacy).exists():
        violations.append(
            f"legacy source root must not exist in clean branch: {legacy}"
        )

pure_api_roots = (
    "core/platform/api/",
    "core/logging/api/",
    "core/permissions/api/",
    "feature/",
)

android_adapter_packages = (
    "com.yagay.ysuite.platform.android",
    "com.yagay.ysuite.logging.android",
    "com.yagay.ysuite.permissions.android",
)

for path in ROOT.rglob("*"):
    if not path.is_file():
        continue

    rel = path.relative_to(ROOT).as_posix()
    if "/build/" in f"/{rel}/":
        continue

    if path.suffix not in {".kt", ".java"}:
        continue

    text = path.read_text(encoding="utf-8", errors="ignore")

    if rel.startswith("feature/") and "/impl/" in rel:
        if "androidx.compose.material3." in text:
            violations.append(
                f"{rel}: feature imports Material3 directly; "
                "use core design system"
            )
        if "com.google.android.material." in text:
            violations.append(
                f"{rel}: feature imports Material Views directly; "
                "use core design system"
            )
        for adapter in android_adapter_packages:
            if adapter in text:
                violations.append(
                    f"{rel}: feature must depend on API, not Android adapter: "
                    f"{adapter}"
                )
        for field in ("title", "subtitle", "label", "message", "text"):
            if re.search(
                rf'\b{field}\s*=\s*"[^"]+"',
                text,
            ):
                violations.append(
                    f"{rel}: hardcoded user-visible {field}; "
                    "use string resources"
                )

    if rel.startswith("feature/") and "/api/" in rel:
        if re.search(r"\b(android|androidx)\.", text):
            violations.append(
                f"{rel}: feature API must remain framework-neutral"
            )

    if any(rel.startswith(root) for root in pure_api_roots[:3]):
        if re.search(r"\b(android|androidx)\.", text):
            violations.append(
                f"{rel}: infrastructure API must remain framework-neutral"
            )

    if rel.startswith("app/") and "androidx.compose.material3." in text:
        violations.append(
            f"{rel}: app must render through core:ui"
        )

    if rel.startswith("app/") and "com.yagay.ysuite.designsystem" in text:
        violations.append(
            f"{rel}: app must not depend directly on design system"
        )

    if rel.startswith("app/") and "com.yagay.ysuite.resources" in text:
        violations.append(
            f"{rel}: app must not depend directly on resource implementation"
        )

    if rel.startswith("core/designsystem/") and "com.yagay.ysuite.ui" in text:
        violations.append(
            f"{rel}: design system must not depend on core:ui"
        )

    if rel.startswith("core/ui/") and "com.yagay.ysuite.feature." in text:
        violations.append(
            f"{rel}: core:ui must not depend on features"
        )

for gradle in ROOT.glob("feature/*/impl/build.gradle.kts"):
    rel = gradle.relative_to(ROOT).as_posix()
    text = gradle.read_text(encoding="utf-8", errors="ignore")

    forbidden_projects = (
        ":core:platform:android",
        ":core:logging:android",
        ":core:permissions:android",
    )
    for forbidden in forbidden_projects:
        if f'project("{forbidden}")' in text:
            violations.append(
                f"{rel}: feature cannot depend on Android adapter "
                f"{forbidden}"
            )

    own_feature = gradle.parts[-3]
    for match in re.finditer(
        r'project\("(:feature:[^"]+:impl)"\)',
        text,
    ):
        dependency = match.group(1)
        if dependency != f":feature:{own_feature}:impl":
            violations.append(
                f"{rel}: feature implementation dependency is forbidden: "
                f"{dependency}"
            )

if violations:
    print("\n".join(sorted(set(violations))))
    sys.exit(1)

print("Architecture boundaries OK")
