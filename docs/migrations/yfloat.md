# Feature migration: YFloat

Product surface: Settings
Layout references: alorma/Compose-Settings (MIT) and rhengtl/textsnip (MIT).

YFloat keeps the existing YSuite floating-button service, gesture engine, accessibility capture,
screen/region capture, OCR pipelines (ML Kit and Paddle), result surfaces, Google Circle-to-Search
bridge, secure-layer screenshot Hook, Root/LSPosed enhancements and diagnostic runtime. The old
settings/main pages are no longer the product entry point; the feature uses YFloatWorkspace and
writes the same FloatSettings keys observed live by FloatService.

The legacy runtime is isolated in feature/yfloat/runtime. Product UI is implemented only in
feature/yfloat/impl.
