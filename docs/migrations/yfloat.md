# Feature migration: YFloat

Product surface: Settings  
Upstream project: alorma/Compose-Settings  
Secondary layout reference: rhengtl/textsnip  
Upstream license: MIT

## Scope

YFloat keeps the existing floating-button service, gesture engine, accessibility capture, full/region
capture, OCR pipelines, result surfaces, Google Circle-to-Search bridge, secure-layer screenshot
Hook, Root/LSPosed enhancements and diagnostics. The legacy runtime is isolated in
`feature/yfloat/runtime`; the product UI is rebuilt in `feature/yfloat/impl`.

## Product UI

The product entry point uses `YFloatWorkspace` and shared Compose settings primitives. The legacy
View activities remain runtime-only for transient floating/result flows and are not used as the
YSuite settings UI.

## Platform capabilities

Root execution is routed through the shared `RootGateway` via the internal runtime host bridge.
LSPosed scope/configuration uses `HookGateway`; target-process Hook code remains in the runtime
module and is not referenced directly by the feature implementation.

## Localization

English and Simplified Chinese resources are maintained together. Runtime legacy strings remain
packaged for transient OCR/result surfaces.

## Tests

The runtime remains covered by the integrated Debug build while architecture checks ensure the
Compose implementation does not import the legacy runtime package directly.

## Standalone

The generic standalone host injects the same Root/Hook services and initializes the YFloat runtime
bridge before the feature screen is shown.
