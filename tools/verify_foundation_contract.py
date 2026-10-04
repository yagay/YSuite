#!/usr/bin/env python3
from pathlib import Path
import json
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
CONTRACT = ROOT / "config/architecture-contract.json"
errors = []

contract = json.loads(CONTRACT.read_text(encoding="utf-8"))
foundation = contract["foundation_modules"]

settings_text = (ROOT / "settings.gradle.kts").read_text(
    encoding="utf-8",
)
included = set(
    re.findall(r'include\("(:core:[^"]+)"\)', settings_text)
)

expected = set(foundation)
missing = sorted(expected - included)
unexpected = sorted(included - expected)

if missing:
    errors.append(
        "Missing frozen core modules: " + ", ".join(missing)
    )
if unexpected:
    errors.append(
        "Unexpected core modules require architecture review: " +
        ", ".join(unexpected)
    )

for module, expected_dependencies in foundation.items():
    path = ROOT / module.lstrip(":").replace(":", "/")
    build_file = path / "build.gradle.kts"

    if not path.exists():
        errors.append(f"Missing foundation module path: {module}")
        continue

    if not build_file.exists():
        errors.append(f"Missing foundation build file: {module}")
        continue

    text = build_file.read_text(encoding="utf-8", errors="ignore")
    actual_dependencies = set(
        re.findall(r'project\("(:core:[^"]+)"\)', text)
    )
    expected_dependencies = set(expected_dependencies)

    if actual_dependencies != expected_dependencies:
        errors.append(
            f"{module}: frozen internal dependency graph changed; "
            f"expected={sorted(expected_dependencies)}, "
            f"actual={sorted(actual_dependencies)}"
        )

for feature in contract["framework_features"]:
    for layer in ("api", "impl"):
        path = ROOT / "feature" / feature / layer
        if not path.exists():
            errors.append(
                f"Missing framework feature layer: "
                f"feature/{feature}/{layer}"
            )

for root in contract["required_roots"]:
    if not (ROOT / root).exists():
        errors.append(f"Missing required architecture root: {root}")

for root in contract["forbidden_legacy_roots"]:
    if (ROOT / root).exists():
        errors.append(
            f"Legacy architecture root reintroduced: {root}"
        )

if errors:
    print("\n".join(errors))
    sys.exit(1)

print(
    "Foundation contract OK: "
    f"{len(foundation)} core modules frozen"
)
