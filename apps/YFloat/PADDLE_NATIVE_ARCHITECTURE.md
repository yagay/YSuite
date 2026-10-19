> **Current Circle OCR architecture (raw Paddle SDK):** New `CircleRawPaddleOcr` calls `PaddleOCR.recognize(bitmap)` directly. No `CircleStableOcr`, `PaddleRecognitionRuntime`, `PaddlePipelineV2`, CJK spacing correction, manual word regrouping, orientation reruns, or document analysis are executed by the Circle engine. The on-demand model manager and the public OCR document output schema remain compatible. Optional document/PDF stages are separately managed.

# YFloat Paddle-native OCR architecture

All new recognition entrypoints run **fully offline PaddleOCR**. Model files
remain individually downloaded; no weights are built into the APK.

## One shared runtime

- `PaddleRecognitionRuntime` owns local model resolution, Paddle-only Auto
  retries and conservative confidence escalation.
- `PaddlePipelineV2` alone owns the native ONNX/OpenCV OCR session cache,
  serialized inference and optional PP-DocLayout / SLANet / UVDoc / formula /
  seal processing. There is no second independent Paddle session.
- `PaddlePageV2.Page.toDocument()` converts the **actual Paddle**
  Block / Line / Word / Symbol hierarchy to an immutable `OcrDocument`.
  Recognition text and real detector / CTC bounds are separate data, never
  rebuilt using ML Kit `Text.Element` semantics.
- `OcrEngine` owns latest-wins screenshot requests, document epochs, and
  UI result delivery. `CircleRawPaddleOcr` owns both Circle entrypoints and
  forwards to that same runtime. No ML Kit recognizers or cross-script fusion
  are instantiated.
- `CircleTextSelectionModel` uses `OcrSelectionText` UTF-16 source spans.
  Group/box boundaries determine *what can be selected*; selected text is
  retrieved from the exact OCR source, preserving meaningful whitespace.
  Never synthesize per-glyph boxes when Paddle supplies only word/line bounds.
- `PaddleDocumentExport` exports the same document into JSON / Markdown /
  HTML / CSV, keeping original SLANet HTML and geometry provenance.

## Migration and removal

Previous screenshot mode 3 (ML Kit) and retired mode 4 (cloud) map to
**Paddle Auto**. Previous Circle full-screen mode 0 (ML Kit) maps to
**Paddle Auto**. All explicit Paddle Tiny/Small/Medium/Mobile settings are
preserved; Circle correction 0 still means **Off**, not an engine. There is no
silent Google or network fallback when local Paddle models are missing.

The ML Kit parsing helper, canonical ML-like geometry normalization, fusion
pass, unused language switches, and ML Kit recognition dependencies have
been removed. The old `PaddleOcrBridge` is a deprecated forwarding-only
compatibility entrypoint; it cannot create its own model session or fabricate
text boxes.

## Validating a change

1. Run source contracts `tools/verify_paddle_independent_models.py`.
2. Run real feature and Paddle SDK debug JUnit regression suites.
3. Build **ARM64 Debug** once, after completing the source batch.
4. Validate screenshots on Android: Chinese contiguous text, mixed Chinese /
   English, multiple Latin word spaces, rotated text, partial drag selection,
   unaligned CTC characters and after model switching.
5. Check model downloads, failure recovery, memory pressure, structure
   exports and Circle session invalidation on a physical device.

The following require a separate verified Android inference path and are not
promised as complete by this refactor: full learned PP-TableMagic graph,
Chart2Table generative model, and UVDoc non-rigid inverse source coordinates.
