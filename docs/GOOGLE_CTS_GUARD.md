# YFloat Google CTS result guard

YSuite/YFloat owns only sessions explicitly marked with a YFloat CTS token. Native Home/gesture Circle to Search sessions remain untouched.

For a marked session, Google App result navigation is fail-closed:

- The guard correlates ownership from `VoiceInteractionSession` and Omnient markers.
- `PendingLensQuery` is allowed to continue, but the request for Google's rendered result panel is disabled when possible.
- `LensQueryResult` may update intermediate OCR/selection state, but a result with `presentationResult` is consumed before LensientActivity can switch to Google's search-result panel.
- Explicit Google search-result activity launches are also suppressed as a fallback.
- Selection binding tolerates Google 17.58 obfuscated method renames when the stable `(SelectionMetadata, boolean) -> void` ABI remains.

Useful runtime log markers:

- `GOOGLE_SEARCH_GUARD_ARM`
- `GOOGLE_SEARCH_REQUEST_SUPPRESS`
- `GOOGLE_SEARCH_SUPPRESS`

If these markers are missing in a future Google App build, inspect the Omnient and `dscu`/`dtqj`/`dtqi` profile before adding broad process-wide blocking.
