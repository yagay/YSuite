#!/usr/bin/env python3
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
CONFIG = ROOT / "config/standalone-features.properties"
errors = []
ids = set()

if not CONFIG.exists():
    errors.append("Missing config/standalone-features.properties")
else:
    for number, raw in enumerate(
        CONFIG.read_text(encoding="utf-8").splitlines(),
        start=1,
    ):
        line = raw.strip()
        if not line or line.startswith("#"):
            continue

        parts = line.split("|")
        if len(parts) != 4:
            errors.append(f"line {number}: expected 4 fields")
            continue

        feature_id, module, registration_class, application_id = parts

        if feature_id in ids:
            errors.append(f"line {number}: duplicate id {feature_id}")
        ids.add(feature_id)

        if not module.startswith(":feature:") or not module.endswith(":impl"):
            errors.append(f"line {number}: invalid impl module {module}")

        module_path = ROOT / module.lstrip(":").replace(":", "/")
        if not module_path.exists():
            errors.append(
                f"line {number}: module path does not exist: "
                f"{module_path.relative_to(ROOT)}"
            )
            continue

        if not registration_class.startswith(
            "com.yagay.ysuite.feature."
        ):
            errors.append(
                f"line {number}: invalid registration class "
                f"{registration_class}"
            )
        else:
            package_parts = registration_class.split(".")
            simple_name = package_parts[-1]
            relative_package = Path(*package_parts)
            source_candidates = [
                module_path / "src/main/java" /
                relative_package.with_suffix(".kt"),
                module_path / "src/main/kotlin" /
                relative_package.with_suffix(".kt"),
            ]

            source = next(
                (candidate for candidate in source_candidates if candidate.exists()),
                None,
            )
            if source is None:
                errors.append(
                    f"line {number}: registration source not found: "
                    f"{registration_class}"
                )
            else:
                source_text = source.read_text(
                    encoding="utf-8",
                    errors="ignore",
                )
                if not re.search(
                    rf"\bobject\s+{re.escape(simple_name)}\b",
                    source_text,
                ):
                    errors.append(
                        f"line {number}: standalone registration must be "
                        f"a Kotlin object: {registration_class}"
                    )

        if not application_id.endswith(".standalone"):
            errors.append(
                f"line {number}: application id must end in .standalone"
            )

if errors:
    print("\n".join(errors))
    sys.exit(1)

print(f"Standalone configuration OK: {len(ids)} features")
