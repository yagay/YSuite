#!/usr/bin/env python3
from __future__ import annotations

import argparse
import sys
from pathlib import Path

from generate_feature_catalog import load_features

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "gradle/libs.versions.toml"


def destinations() -> list[Path]:
    roots = {
        (ROOT / item["project_dir"]).parent
        for item in load_features()
    }
    return [root / "gradle/libs.versions.toml" for root in sorted(roots)]


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()

    if not SOURCE.is_file():
        raise SystemExit(f"version-catalog: missing source {SOURCE.relative_to(ROOT)}")
    expected = SOURCE.read_text(encoding="utf-8")

    stale: list[Path] = []
    for path in destinations():
        if args.check:
            actual = path.read_text(encoding="utf-8") if path.is_file() else ""
            if actual != expected:
                stale.append(path)
            continue
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(expected, encoding="utf-8")
        print(f"version-catalog: wrote {path.relative_to(ROOT)}")

    if stale:
        for path in stale:
            print(f"version-catalog: stale {path.relative_to(ROOT)}", file=sys.stderr)
        raise SystemExit(1)

    if args.check:
        print(f"version-catalog: OK source={SOURCE.relative_to(ROOT)} mirrors={len(destinations())}")


if __name__ == "__main__":
    main()
