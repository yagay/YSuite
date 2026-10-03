#!/usr/bin/env python3
from __future__ import annotations

import argparse
import difflib
from pathlib import Path

from generate_feature_catalog import load_features

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "config/generated/standalone-features.tsv"
EMPTY = "-"


def clean(value: object, field: str, feature_id: str) -> str:
    text = str(value or "").strip()
    if "\t" in text or "\n" in text:
        raise SystemExit(f"standalone-catalog: {feature_id}: {field} must not contain tabs/newlines")
    return text


def optional(value: object, field: str, feature_id: str) -> str:
    return clean(value, field, feature_id) or EMPTY


def boolean(value: object, field: str, feature_id: str, default: bool = False) -> str:
    raw = value if value is not None else default
    if type(raw) is not bool:
        raise SystemExit(f"standalone-catalog: {feature_id}: {field} must be boolean")
    return "true" if raw else "false"


def render() -> str:
    lines = [
        "# Generated from config/features.toml by tools/generate_standalone_catalog.py.",
        "# feature_id<TAB>name<TAB>gradle_module<TAB>package<TAB>entry_activity<TAB>xposed_hooks<TAB>description<TAB>label<TAB>description_resource<TAB>icon<TAB>round_icon<TAB>theme<TAB>locale_config<TAB>allow_backup<TAB>uses_cleartext_traffic<TAB>nfc_required",
    ]
    for item in load_features():
        if not item.get("standalone_enabled", False):
            continue
        feature_id = clean(item["id"], "id", str(item.get("id", "?")))
        hooks = ";".join(clean(h["class"], "hook class", feature_id) for h in item.get("hooks") or []) or EMPTY
        lines.append(
            "\t".join(
                [
                    feature_id,
                    clean(item["name"], "name", feature_id),
                    clean(item["gradle_module"], "gradle_module", feature_id),
                    clean(item["standalone_package"], "standalone_package", feature_id),
                    clean(item["entry_activity"], "entry_activity", feature_id),
                    hooks,
                    clean(item["description"], "description", feature_id),
                    optional(item.get("standalone_label"), "standalone_label", feature_id),
                    optional(item.get("standalone_description_resource"), "standalone_description_resource", feature_id),
                    optional(item.get("standalone_icon"), "standalone_icon", feature_id),
                    optional(item.get("standalone_round_icon"), "standalone_round_icon", feature_id),
                    optional(item.get("standalone_theme"), "standalone_theme", feature_id),
                    optional(item.get("standalone_locale_config"), "standalone_locale_config", feature_id),
                    boolean(item.get("standalone_allow_backup"), "standalone_allow_backup", feature_id),
                    boolean(item.get("standalone_uses_cleartext_traffic"), "standalone_uses_cleartext_traffic", feature_id),
                    boolean(item.get("standalone_nfc_required"), "standalone_nfc_required", feature_id),
                ]
            )
        )
    lines.append("")
    return "\n".join(lines)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    expected = render()
    actual = OUTPUT.read_text(encoding="utf-8") if OUTPUT.is_file() else ""
    if args.check:
        if actual != expected:
            print(f"standalone-catalog: stale generated file: {OUTPUT.relative_to(ROOT)}")
            for line in list(
                difflib.unified_diff(
                    actual.splitlines(),
                    expected.splitlines(),
                    fromfile=str(OUTPUT.relative_to(ROOT)),
                    tofile=str(OUTPUT.relative_to(ROOT)) + " (generated)",
                    lineterm="",
                )
            )[:120]:
                print(line)
            raise SystemExit(1)
        print("standalone-catalog: OK")
        return

    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(expected, encoding="utf-8")
    print(f"standalone-catalog: wrote {OUTPUT.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
