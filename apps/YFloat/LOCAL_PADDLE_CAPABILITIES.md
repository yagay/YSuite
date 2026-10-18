# YFloat: offline Paddle capabilities (development batch)

This record differentiates genuine local inference from optional models, geometry
heuristics, and modules not implemented. Do not mark an untested model as complete.

| Capability | Source | Implementation | Validation |
|---|---|---|---|
| Text detection and recognition | PP-OCRv6 Tiny / Small / Medium | ONNX + OpenCV, existing runtime | Previously built, phone accuracy unverified |
| Mobile OCR (additional choice) | PP-OCRv5_mobile_det_onnx + PP-OCRv5_mobile_rec_onnx | Independently downloaded model pair and inference.yml; reused existing local ONNX + CTC engine | Source changes staged; not yet compiled or phone tested |
| CTC glyph quads and word grouping | Paddle SDK | CTC alignment + grouping | Previously built, geometry estimated |
| Spacing refinement | PaddleSpacingV2 | CTC-confidence/geometry heuristics | Previously built, requires real-image validation |
| Layout areas | PP-DocLayout-S | ONNX; 23 layout categories | Previously built, real-image validation pending |
| Document orientation | PP-LCNet_x1_0_doc_ori ONNX | Independent 224x224 classifier, 4 classes | **Staged; not compiled or phone-tested** |
| Table structure | SLANet-1M ONNX | 488x488 ONNX, vocabulary decoding, cells + OCR association | **Staged; not compiled or phone-tested** |
| Multicolumn reading order | Local geometrical grouping | Conservative two-column heuristic | **Staged; not model-based** |
| UVDoc dewarping | UVDoc ONNX | **Staged:** offline BGR image-to-image inference and second OCR pass | Corrected image text kept separate to avoid incorrect source-screen coordinates; not compiled or phone-tested |
| Formula to LaTeX | PP-FormulaNet_plus-S converted ONNX | **In development:** independent ONNX+tokenizer download and offline region recognition | ONNX/UniMERNet ByteLevel local decoder wired into formula layout areas; compile, phone validation and memory profiling pending |
| Chart labels and titles | PP-DocLayout-S + PP-OCRv6 | **Staged:** original-image chart regions and visible OCR labels | NOT chart-to-table, no inferred plot values |
| Chart-to-table generative model | PP-Chart2Table | **Not implemented** | 1.43 GB, 0.58B multimodal Paddle weights with no Android inference backend; not mislabelled as complete |
| Seal text detection | PP-OCRv4_mobile_seal_det converted ONNX | **Staged:** offline DB detector in PP-DocLayout-S seal regions; separate crop OCR | Curved text remains challenging; not compiled or phone-tested |

No remote recognition /layout-parsing service remains.

Model sources:
- https://huggingface.co/ningpp/PP-LCNet_x1_0_doc_ori-ONNX
- https://huggingface.co/bdatdo0601/slanet-1m-onnx
- https://huggingface.co/marsena/paddleocr-onnx-models/blob/main/UVDoc_infer.onnx
- https://huggingface.co/x3zvawq/paddleocr-js-onnx/blob/main/ppocr_v4_mobile_seal_det/PP-OCRv4_mobile_seal_det_infer.onnx
- https://huggingface.co/x3zvawq/paddleocr-js-onnx/tree/main/PP_FormulaNet_plus_S
- https://huggingface.co/PaddlePaddle/PP-Chart2Table

## CI policy

Do not run automatic CI for partial steps in this offline Paddle build-out.
Use a deliberate final ARM64 debug validation after the missing supported
runtime modules and integration tests have been completed. Any intermediate
commits must have `[skip ci]` in the commit message.

## Model distribution policy

- **No OCR/Structure/UVDoc/SLANet/seal weights are embedded in the APK.**
- PP-OCRv6 Tiny, Small and Medium each have separate download/delete controls.
- **PP-OCRv5 Mobile** is a fourth independent OCR model. It does not overwrite or change the current default or existing downloaded models.
- PP-DocLayout-S downloads and enables separately from OCR.
- PP-LCNet document orientation, SLANet table structure, UVDoc and seal ONNX
  have independent download/enable/delete controls. The table's dictionary is
  downloaded only with SLANet and removed with it.
- Inference uses only local files. It does not download automatically or call
  Google App, Lens or an HTTP processing service.
- FormulaNet_plus-S (converted ONNX) downloads independently together with a
  version-pinned UniMERNet tokenizer. It is a large optional model, never bundled.
- Chart2Table remains unsupported; downloading its
  heavyweight raw weights without an Android runtime would be misleading.
  They must be made independent optional downloads *after* the inference
  implementation and its resource checks work.
- This development batch uses [skip ci]; no partial GitHub CI builds.

## Paddle Mobile addition

Original PaddleOCR models and optional layout/table/formula/OCR extensions remain
present in this branch. Mobile is an additional selectable model; full-screen,
circle and text correction use the same local Paddle OCR pipeline and geometry.

Official model sources (runtime files downloaded only when the user taps Download):
- https://huggingface.co/PaddlePaddle/PP-OCRv5_mobile_det_onnx
- https://huggingface.co/PaddlePaddle/PP-OCRv5_mobile_rec_onnx

The older RapidOCR migration lives on a separate untouched branch. No weights are
committed to this repository. The next CI run is deliberately deferred.
