#!/usr/bin/env python3
from __future__ import annotations

import re
import sys
from pathlib import Path

from generate_feature_catalog import load_features

ROOT = Path(__file__).resolve().parents[1]
APP_PLUGIN_RE = re.compile(r"(?:id\s*\(?\s*[\"']com\.android\.application[\"']|com\.android\.application)")
APP_INCLUDE_RE = re.compile(r"include\s*\(?[^\n]*[\"']?:app[\"']?", re.IGNORECASE)


def fail(message: str) -> None:
    print(f"legacy-app-shells: ERROR: {message}", file=sys.stderr)
    raise SystemExit(1)


def main() -> None:
    features = load_features()
    checked_roots: set[Path] = set()

    for feature in features:
        feature_dir = ROOT / feature["project_dir"]
        app_root = feature_dir.parent
        checked_roots.add(app_root)

        legacy_app = app_root / "app"
        if legacy_app.exists():
            fail(f"{feature['id']}: legacy per-feature app shell exists: {legacy_app.relative_to(ROOT)}")

        settings = next(
            (path for path in (app_root / "settings.gradle.kts", app_root / "settings.gradle") if path.is_file()),
            None,
        )
        if settings is not None:
            text = settings.read_text(encoding="utf-8")
            if APP_INCLUDE_RE.search(text):
                fail(f"{feature['id']}: settings still include :app: {settings.relative_to(ROOT)}")

    for app_root in sorted(checked_roots):
        for build_file in app_root.rglob("build.gradle*"):
            text = build_file.read_text(encoding="utf-8", errors="replace")
            if APP_PLUGIN_RE.search(text):
                fail(
                    "per-feature Android application plugin is forbidden; use standalone/host: "
                    f"{build_file.relative_to(ROOT)}"
                )

    standalone_host = ROOT / "standalone/host/build.gradle.kts"
    suite_host = ROOT / "suite/YSuite/build.gradle.kts"
    for host in (standalone_host, suite_host):
        if not host.is_file():
            fail(f"required application host missing: {host.relative_to(ROOT)}")
        text = host.read_text(encoding="utf-8")
        if "com.android.application" not in text and "alias(libs.plugins.android.application)" not in text:
            fail(f"required application host lost Android app plugin: {host.relative_to(ROOT)}")

    print(
        f"legacy-app-shells: OK features={len(features)} "
        "per-feature-apps=0 hosts=standalone/host,suite/YSuite"
    )


if __name__ == "__main__":
    main()
