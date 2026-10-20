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
4. **`OcrV3Selection`** maps matching ML Symbols to Paddle character
   references by exact text and nearby bounding boxes. A single
   ML Element (word) is the default tap selection. The two standard
   blue handles extend within the corresponding ML TextBlock; touching
   another TextBlock switches to it instead of implicitly selecting
   intervening desktop-icon labels. Dragging over blank wallpaper does
   not snap to distant words.
5. **`OcrV3Workspace`** renders a frozen screenshot only once and
   overlays original ML-style blue rounded word rectangles and blue
   stem/circle handles, plus text actions (copy, search, translate,
   explicit Select All). Paddle strings alone supply those actions.
   Screenshot rectangle selection remains separate with Done, Copy
   and Save actions.

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
- `OcrV3HandleRangeTest`: text-handle selection expansion,
  shrinking, crossing, and unrelated TextBlock isolation.
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
