# YFloat OCR V3 — fresh runtime

Branch: `experiment/yfloat-ocr-v3`.

## Active entry point

`FLCircleController` -> `FLCircleCapture` -> **`OcrV3Workspace`**.

The old `NativeCircleWorkspace`, `CircleRawPaddleOcr`,
`CircleMlBlockSelection`, `CircleMlSelectionSession`, custom image-pixel
selection geometry and OCR correction pipeline are **not referenced by
the V3 runtime**. They remain in the source tree solely as a reference
for other existing YFloat entrypoints; V3 cannot call into them.

## Recognition and ownership

1. **`OcrV3Paddle`** runs one local `PaddleOCR.recognize(Bitmap)` from
   the installed/downloaded ONNX detector and recognizer. It uses only
   the stable model-file manager and Paddle SDK. No retouching spaces,
   multiple orientation OCR passes, layout postprocessing, glyph
   fabrication, grouping heuristics or second text-decoding engine.
2. **`OcrV3Page`** stores untouched `OCRResult.text`, original line
   ordering, SDK character bounding boxes and UTF-16 positions in the
   recognized source line. `OcrV3TextSlice` extracts verbatim
   Paddle substrings for selected characters, preserving existing
   whitespace and excluding nonselected characters.
3. **`OcrV3MlLayout`** invokes on-device Chinese/Latin ML Kit text
   recognition to obtain TextBlock, Line, Element and Symbol geometry.
   Its decoded text is used only to align each symbol to a Paddle
   glyph. It is **never** copied, translated or shown as the OCR result.
4. **`OcrV3Selection`** maintains two separate interaction modes:
   taps and freehand strokes still select original ML Kit Elements; the
   glyph-level expansion mode starts only after a blue handle moves.
   `OcrV3StrokeRange` remains the independent stable freehand engine.
5. **`OcrV3ExpansionController`** owns the only draggable selection range.
   It sorts real Paddle glyph source spans in reading order, uses matched
   real ML Symbol boxes when available, and falls back only to genuine
   SDK geometry (Paddle glyph or ML Element). Touch-to-glyph offsets are
   captured on DOWN; MOVE and UP use the same coordinate mapping.
   The non-moving endpoint is preserved; crossing swaps grip roles,
   cross-line/cross-block selection is continuous, and remote blank
   wallpaper does not arbitrarily snap to text.
6. **`OcrV3Workspace`** renders a frozen screenshot and ML-style blue
   highlights/handles. The existing `FloatActionMenu` follows the final
   selection, including across grip movement and finger release.
   Screenshot crops use the same configurable image menu. Paddle is the
   only source for copied/searched/shared text.

## Lifecycle

Screenshot bitmap is owned by `FLCircleCapture.Frame` and recycled by
the controller on close. V3 Paddle receives its own disposable bitmap
copy; ML Kit also clones its own input for recognition and closes both
recognizers after completion. Each workspace has a monotonically
incrementing generation, so late asynchronous responses cannot alter
a closed or replaced overlay.

## Verification

- `OcrV3TextSliceTest`: original whitespace, missing/unselected
  middle labels, punctuation, overlapping character offsets.
- `OcrV3ExpansionControllerTest`: glyph-level expansion, shrinking,
  crossing, reading order, finger offset and distant-blank isolation.
- `OcrV3StrokeSelectionTest`: unchanged freehand stroke behavior.
- `tools/verify_paddle_independent_models.py`: checks the V3
  entry point, standalone engines and tests, and prohibits V3 runtime
  references to old Circle/OCR correction classes.
- ARM64 Debug CI builds the actual Suite APK.

ML Kit does not expose the private Google Circle-to-Search selection
controller. V3 uses the PUBLIC recognition hierarchy and locally draws
the familiar blue selection UI. The screenshot overlay is a real
device feature, so final visual/touch accuracy still requires testing
on an Android device after CI.


## 2026-10-20 — recognition-versus-selection diagnosis

The on-device diagnostic at 06:59–07:01 records four completed
Paddle runs (43–52 lines, 242–251 source glyphs) and concurrent
ML Kit runs (98–101 word Elements), without a reported model failure.
It did NOT report how many words remained selectable after alignment.

V3 originally required a verbatim ML Symbol-to-Paddle CTC character
match. OCR engines may independently tokenize words differently or
recognize different Unicode characters, causing otherwise correct ML
Element boxes to disappear from the selection UI.

**Updated V3-only architecture:**
- `OcrV3GeometryMatch`: pure geometry overlap/coverage for true
  Paddle character boxes and ML Element boxes; no text equality gate
  or snapping across blank wallpaper
- `OcrV3WordMapper`: assigns each real Paddle glyph at most once,
  resolves overlapping Chinese/Latin ML detection passes using
  coverage and tighter real boxes; preserves ML TextBlocks/Lines
- `OcrV3Selection`: hit-testing and selection still use only ML
  Element coordinates; new direct text strokes select Elements inside
  the same TextBlock without turning a text stroke into an image crop
- `OcrV3Workspace`: emits `OCR_V3_ALIGNMENT` with selectable
  elements, source glyph matches/misses, and `OCR_V3_TAP` and
  `OCR_V3_STROKE` events without logging private OCR text
- `OcrV3Paddle`: records genuine CTC characters reported by SDK,
  source-substring hits, unaligned source characters, invalid
  source geometry, and whole-line-only fallbacks

ML recognition strings remain **non-copyable**; there is still no
post-CTC whitespace correction, fabricated glyph, or old V2 mapping
dependency. A wrong Paddle transcription must be diagnosed
separately with an actual screenshot/output comparison because
aggregate log counts cannot reveal textual accuracy.

## 2026-10-20 — glyph-level expansion refactor

The previous `OcrV3HandleRange` rejected movement outside one ML TextBlock
and relied on direct hit testing against full ML Elements. Its range reset
when crossing blocks, and the terminal ACTION_UP point was not consumed.
It has been retired, rather than supplemented with more special cases.

The new `OcrV3ExpansionController` is platform-independent and is directly
covered by plain JVM tests. Real character rectangles come from ML Symbol
matches whenever possible. If absent, the renderer uses a real Paddle glyph
rectangle, or the existing ML Element rectangle; character widths are not
fabricated. The original Paddle UTF-16 spans remain authoritative. Existing
tap/stroke word selection is unaffected until the user actively expands.

Important: screen-layout reading order depends on Paddle's original line
sequence. Highly complex multiple-column/icon layouts may still require
device-level validation; geometry ordering alone cannot infer semantics.

## 2026-10-20 — unified ML-only blue selection renderer

The 17:03 diagnostic confirmed successful handle expansion (ranges such as
104..120 to 120..223) and matching text action popup. However, the earlier
expanded() path painted an independent rounded rectangle for EVERY selected
Paddle glyph. When a glyph lacked ML Symbol correspondence, the code passed
the Paddle glyph box to the UI. That caused visibly different blue heights,
shape, and stacked alpha immediately after touching a selection handle.

**Architecture invariant**: the OCR visual ALWAYS uses genuine ML Kit geometry,
before AND after drag. The new Android-free `OcrV3MlSelectionVisual` groups
selected Paddle **source indexes** by their mapped ML Element, and paints
exactly one rounded rectangle per selected Element, retaining the exact ML
Element vertical position and height. A fully selected word looks identical
in tap/stroke and expanded states. A partially selected word can horizontally
clip to a real ML Symbol span; absent exact ML Symbol alignment it highlights
the entire real ML Element rather than drawing a Paddle glyph box. Neither
made-up widths nor Paddle CTC boxes can enter the UI renderer.

The `OcrV3ExpansionController` provides finger targets from real ML Symbols,
falling back to real ML Element boxes. Paddle's glyph boxes are used for
alignment only; Paddle original text and spacing remain the sole clipboard
source. Regression tests enforce ML-only geometry, no multiplied translucent
blue for multi-glyph words, partial symbol boundaries and ML Element fallback.

## 2026-10-20 — original Latin label prefix lost during ML alignment

A device report showed a selected app label fragment (“atsApp” rather
than “WhatsApp”). The source diagnostic logged 241 raw Paddle glyphs,
but only 228 were mapped into selectable ML Kit Elements. Prior logs
contained counts only; there is no raw OCR string in them, so they
cannot establish whether Paddle itself omitted the prefix or the
geometric mapping dropped it.

`OcrV3LatinTokenRecovery` (pure Java) now restores **already recognized**
but geometrically orphaned Paddle glyphs only when they belong to one
unbroken original ASCII alphanumeric token that has at least two
independent glyph-to-ML anchors, at least half of the source glyphs
are anchored, and every anchor agrees on exactly one ML Element.
It does **not** recover missing recognition text, invent brand names,
reassign characters across multiple ML Elements, or join labels
separated by a source space. Original ML Element/Symbol coordinates
remain the only source of the blue UI.

`OCR_V3_ALIGNMENT` now includes the number of source glyphs recovered;
single-word `OCR_V3_MENU` events include **counts only** for ML
symbols, Paddle mapped glyph characters, and Paddle's original ASCII
token length. This makes it possible to distinguish “Paddle source
already short” from “Paddle source complete but mapping incomplete”
on the user's next actual screenshot without exposing recognized text
into app diagnostics. A truly missing Paddle character still requires
recognizer/image-model accuracy work, not alignment heuristics.

## 2026-10-20 — Independent ML selection + detection-free Paddle ROI pipeline

This replaces the preceding **Latin-token prefix recovery patch**, which
could only recover Paddle characters already found on the full-screen pass.
The patch and its tests have been REMOVED from the active source tree.

**Selection correctness and text recognition have independent owners:**

1. `OcrV3MlWordVisibility.select` (implemented as
   `OcrV3WordVisibility`) retains every geometrically valid ML Kit Element
   regardless of Paddle mapping, and geometrically deduplicates duplicate
   Chinese/Latin ML recognizer passes. This means even a Paddle detector
   miss does not make a visible word untappable.
2. `OcrV3Selection.buildUnits()` builds every text-handle unit from ML Kit
   Symbols. A missing Paddle glyph uses a `glyphId=-1` reference rather
   than eliminating a handle unit. The user can still extend and shrink
   the blue ML-only selection; the source UI never paints Paddle glyphs.
3. The normal full-image PaddleOCR scan remains fast and cached. A single
   ML word with insufficient Paddle source coverage is flagged using ONLY
   the count of ML Symbols, not their recognized contents. The existing
   Paddle source text stays the first candidate.
4. The selection-specific `OcrV3LocalRegion` expands the genuine ML word
   bounds inside the ORIGINAL frozen screenshot, retaining leading/trailing
   glyph pixels that the full-screen Paddle detector may have trimmed.
   The workspace crops only this region, not another whole-screen bitmap.
5. `PaddleOCR.recognizeRegion(bitmap, rect)` reaches
   `OCREngine.recognizeRegion`, which uses `RecognitionEngine` on that
   source crop directly. It does NOT run the DB text detector a second time,
   and uses the same downloaded Paddle recognizer and ONNX session.
6. `OcrV3LocalTextDecision` conservatively chooses between TWO original
   Paddle transcriptions; it never copies ML text, adds a brand dictionary,
   invents characters, modifies OCR spacing, or overrides a complete
   original with a shorter result. Only actual source-prefix/suffix
   recovery with sufficient confidence may replace a partial source.
7. Selection-local OCR requests are asynchronous and cancelled logically
   using workspace generation, selected word ID and captured selection
   owner. UI menus never show a known incomplete source while its local
   refinement is pending. When the region OCR fails, the original Paddle
   result is retained. Cropped temporary bitmaps are always recycled.
8. Unit tests exercise geometry independence, duplicate ML detections,
   cropping bounds, missing prefixes and no invented text without
   requiring Android graphics stubs.

The raw-text boundary is **Paddle-only**. An ML word which both Paddle
passes completely fail to recognize remains selectable as a blue UI
target, but no text can be copied until Paddle provides actual source
characters. Cross-block and partial-character output still follows
the original Paddle spans, with ML handles controlling geometry.
This targeted pathway is an improvement for a detector-truncated word,
not a guarantee against wrong model recognition. A screenshot-level
device comparison is still needed to verify the real WhatsApp case.
