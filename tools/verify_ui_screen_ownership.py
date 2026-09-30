#!/usr/bin/env python3
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

# Common screen-level ownership markers. A normal screen should delegate its shell, layout,
# search/status/settings vocabulary, or XML styling to YUI rather than maintaining another design system.
SOURCE_MARKERS = (
    "YComposeActivity",
    "YFeatureScaffold(",
    "YFeatureCustomScaffold(",
    "YViewLayout.install(",
    "YViewLayout.installFixed(",
    "YViewLayout.card(",
)
XML_MARKERS = ("Widget.YUI.", "TextAppearance.YUI.", "@dimen/yui_")

# These are interaction surfaces rather than ordinary app screens. Keep this list small and documented.
# If a new normal screen appears, it must use YUI instead of being added here.
SPECIALIZED_EXCEPTIONS: dict[str, str] = {
    # YFloat owns an OCR/capture overlay surface; its visual geometry is the product interaction itself.
    "apps/YFloat/feature/src/main/java/com/yagay/YFloat/overlay/SelectionOverlayService.java": "selection/capture overlay, not a settings screen",
}

ACTIVITY_RE = re.compile(r"\bclass\s+\w*Activity\b|\bextends\s+(?:AppCompatActivity|ComponentActivity|Activity)\b")
BINDING_RE = re.compile(r"\b([A-Z][A-Za-z0-9]+Binding)\b")
SET_CONTENT_RE = re.compile(r"setContentView\s*\(\s*R\.layout\.([A-Za-z0-9_]+)")


def rel(path: Path) -> str:
    return path.relative_to(ROOT).as_posix()


def source_has_yui(text: str) -> bool:
    return any(marker in text for marker in SOURCE_MARKERS)


def xml_has_yui(text: str) -> bool:
    return any(marker in text for marker in XML_MARKERS)


def binding_to_layout(binding: str) -> str | None:
    if not binding.endswith("Binding"):
        return None
    stem = binding[:-7]
    if not stem:
        return None
    snake = re.sub(r"(?<!^)(?=[A-Z])", "_", stem).lower()
    return f"{snake}.xml"


def related_layouts(source: Path, text: str) -> list[Path]:
    feature_root = None
    parts = source.parts
    try:
        idx = parts.index("src")
        feature_root = Path(*parts[:idx])
    except ValueError:
        return []
    layout_dir = feature_root / "src/main/res/layout"
    candidates: set[Path] = set()
    for name in SET_CONTENT_RE.findall(text):
        candidates.add(ROOT / layout_dir / f"{name}.xml")
    for binding in BINDING_RE.findall(text):
        name = binding_to_layout(binding)
        if name:
            candidates.add(ROOT / layout_dir / name)
    return [path for path in sorted(candidates) if path.is_file()]


def is_screen_source(path: Path, text: str) -> bool:
    name = path.name
    if name.endswith(("Activity.kt", "Activity.java", "Screen.kt", "Screen.java")):
        return True
    return bool(ACTIVITY_RE.search(text))


def main() -> None:
    failures: list[str] = []
    screens = 0
    yui_owned = 0
    xml_owned = 0
    exceptions = 0

    source_roots = [ROOT / "apps", ROOT / "suite"]
    for source_root in source_roots:
        if not source_root.exists():
            continue
        for path in sorted(source_root.rglob("*")):
            if path.suffix not in {".kt", ".java"} or not path.is_file():
                continue
            if "/build/" in path.as_posix() or "/test/" in path.as_posix() or "/androidTest/" in path.as_posix():
                continue
            text = path.read_text(encoding="utf-8", errors="replace")
            if not is_screen_source(path, text):
                continue
            screens += 1
            key = rel(path)
            if key in SPECIALIZED_EXCEPTIONS:
                exceptions += 1
                continue
            if source_has_yui(text):
                yui_owned += 1
                continue
            layouts = related_layouts(path, text)
            if layouts and all(xml_has_yui(layout.read_text(encoding="utf-8", errors="replace")) for layout in layouts):
                xml_owned += 1
                continue
            details = ""
            if layouts:
                details = " related=" + ",".join(rel(p) for p in layouts)
            failures.append(f"{key}{details}")

    # XML activity layouts are screen-level resources even if their Activity is thin or generated elsewhere.
    xml_screens = 0
    for path in sorted((ROOT / "apps").glob("*/feature/src/main/res/layout/activity_*.xml")):
        xml_screens += 1
        text = path.read_text(encoding="utf-8", errors="replace")
        if not xml_has_yui(text):
            failures.append(f"{rel(path)} [activity XML has no shared YUI style/dimension]")

    # Keep the exception list honest: stale exception paths are failures too.
    for key, reason in SPECIALIZED_EXCEPTIONS.items():
        if not (ROOT / key).is_file():
            failures.append(f"stale specialized exception: {key} ({reason})")

    if failures:
        print("ui-screen-ownership: ERROR unowned normal screens:", file=sys.stderr)
        for item in failures:
            print(f"  - {item}", file=sys.stderr)
        print(
            "Use shared YUI for normal screens. Add an exception only for a genuinely specialized interaction surface.",
            file=sys.stderr,
        )
        raise SystemExit(1)

    print(
        f"ui-screen-ownership: OK screens={screens} source_yui={yui_owned} xml_yui={xml_owned} "
        f"specialized={exceptions} activity_xml={xml_screens}"
    )


if __name__ == "__main__":
    main()
