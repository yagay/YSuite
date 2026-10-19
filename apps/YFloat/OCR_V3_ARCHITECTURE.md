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
