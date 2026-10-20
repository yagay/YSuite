# YFloat OCR V4 — ML selection document + Paddle original line text

Implementation branch: `experiment/yfloat-ocr-v4`.

## Goals and invariants

- One stable, immutable ML Kit `OcrV4Document` owns ALL tappable
  word rectangles, lines, symbol ordinal IDs, reading order, blue
  highlights and selection handles.
- Paddle OCR is the exclusive authority for text. No ML Kit recognized
  string is ever copied, translated into a correction, or returned
  as the selected text.
- Detection/recognition are not mixed via glyph-rectangle geometry.
  A V4 symbol has no Paddle `glyphId`. V3's per-character
  `OcrV3WordMapper`, `OcrV3LocalTextDecision`,
  `OcrV3LocalSymbolSlice` and `OcrV3Selection` are not executed.
- A document line stores actual Paddle source text only if
  `OcrV4Alignment` reliably maps the Unicode code-point sequence
  to the ML Kit symbol positions. It explicitly rejects source length
  omissions, insertions, missing glyphs, and major substitution errors.
  Normalized case folding is for *comparison only*; the returned text
  always preserves original Paddle case and whitespace.
- No silent fallback to a one-character or unrelated whole-screen
  Paddle glyph when alignment fails. No application-name dictionary.
- The image crop, screenshot menu, screen-to-bitmap transform,
  underline stroke and normal YFloat controls retain their existing
  behavior. V4 exclusively replaces the native Circle OCR workspace.

## Runtime

`FLCircleController` launches `OcrV4Workspace`, retaining the same
capture transaction, window hosting, popup and image crop actions.

`OcrV3MlLayout` remains a geometry-only ML Kit reader. It runs both
Latin/Chinese ML recognizers; `OcrV3WordVisibility` deduplicates
overlapping candidates. V4 builds a document directly from the
surviving block/line/element/symbol geometry.

Only *after* a user selects text, the workspace examines the selected
document line(s). Missing lines are queued **one at a time**, with
cached successful results. Paddle's `OcrV3Paddle.recognizeRegion`
uses the existing warmed ONNX SDK to run detection-free CTC on the
complete ML line rectangle, padded from the original bitmap.
If alignment fails, one bounded horizontally wider retry runs.
A failure leaves the blue visual selection intact but does NOT copy
an unverified source. This prevents misleading clipboard content.

`OcrV3StrokeRange`, `OcrV3ExpansionController` and
`OcrV3MlSelectionVisual` are reused only as pure gesture and
geometry primitives, not their old Paddle glyph mapping path.
The OcrV4Selection adapter converts each ML Symbol to an ordinal
unit with `glyphId=-1`, so grip movement cannot reintroduce old
Paddle source indexing. `OcrV4Document.selectedText` uses the
stored Paddle original UTF-16 slice spanning selected ML Symbols
(Unicode-safe for surrogate pairs) and preserves actual
Paddle spaces between words. Every required line must be verified.

## Regression suite

- `OcrV4AlignmentTest`: missing WhatsApp prefix; middle `rog`
  slice; equal-length unrelated transcription; case/whitespace
  preserved; CJK; surrogate pairs; multi-codepoint ML symbol.
- `OcrV4DocumentTest`: ML eligibility without Paddle mappings;
  two words on one line; multiple line selection; missing prefix
  rejects unsafe copy; duplicate ML recognizers; empty symbol text;
  rejected replacement preserves prior verified source.
- `OcrV4RegionTest`: expanded line pixel bounds, bounded
  retry, screen edge clipping and invalid geometry.
- `tools/verify_paddle_independent_models.py` asserts that the
  only live Circle controller path is V4 and excludes legacy
  Paddle-character mapping from the new workspace.

**Limitations:** A high-confidence Paddle recognizer can still make
recognition mistakes, and ML Kit can miss a line or misdetect Symbols.
The V4 policy makes disagreements observable and never claims a
copy result it cannot align. On-device screenshot tests are still
needed for actual small-screen fonts, layouts and localized text.

## Lifecycle

V3 source remains in this experimental branch as a non-dispatched
reference while V4 undergoes real-device acceptance. Once V4
passes user verification, remove the non-dispatched V3 workflow
and old OCR heuristics in a separate cleanup without regressing
the reusable Paddle SDK service.
