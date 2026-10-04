#!/usr/bin/env python3
from pathlib import Path
import argparse
import re
import shutil

ROOT = Path(__file__).resolve().parents[1]

def normalize(raw: str) -> str:
    value = re.sub(r"[^a-z0-9]+", "", raw.lower())
    if not value:
        raise ValueError("Feature name must contain letters or numbers")
    return value

def main():
    parser = argparse.ArgumentParser(
        description="Remove a generated YSuite feature."
    )
    parser.add_argument("name")
    parser.add_argument("--yes", action="store_true")
    args = parser.parse_args()

    feature = normalize(args.name)
    if feature in {"template", "settings", "system"}:
        raise SystemExit(f"Protected framework feature: {feature}")

    base = ROOT / "feature" / feature
    if not base.exists():
        raise SystemExit(f"Feature not found: {feature}")

    if not args.yes:
        raise SystemExit("Refusing to remove without --yes")

    shutil.rmtree(base)

    migration_doc = ROOT / "docs/migrations" / f"{feature}.md"
    if migration_doc.exists():
        migration_doc.unlink()

    settings = ROOT / "settings.gradle.kts"
    settings_text = settings.read_text(encoding="utf-8")
    for suffix in ("api", "impl"):
        settings_text = settings_text.replace(
            f'include(":feature:{feature}:{suffix}")\n',
            "",
        )
    settings.write_text(settings_text, encoding="utf-8")

    standalone = ROOT / "config/standalone-features.properties"
    lines = standalone.read_text(encoding="utf-8").splitlines()
    standalone.write_text(
        "\n".join(
            line for line in lines
            if not line.startswith(f"{feature}|")
        ) + "\n",
        encoding="utf-8",
    )

    print(f"Removed feature:{feature}")

if __name__ == "__main__":
    main()
