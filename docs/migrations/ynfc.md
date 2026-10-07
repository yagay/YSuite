# Feature migration: YNFC

Product surface: Tool  
Upstream project: nfcgate/nfcgate  
Upstream license: Apache-2.0

## Scope

YNFC keeps NFC-A scanning, saved access-card profiles, one-tap switching, API-102
`com.android.nfc` RF Hooking, Root NFC-process restart, stock-RF recovery, runtime verification
and diagnostic export. Payment HCE routing is not replaced or disabled.

## Product UI

The feature uses `YNfcWorkspace` for status, cards and diagnostics. NFCGate is used only as a
product-layout reference; YSuite retains its own RF and recovery implementation.

## Platform capabilities

Root process control uses the shared `RootGateway`, and LSPosed scope requests use
`HookGateway`. The API-102 NFC Hook and ConfigProvider are isolated in
`feature/ynfc/runtime` behind a YSuite runtime bridge.

## Localization

English and Simplified Chinese strings are maintained in the feature implementation.

## Tests

The runtime is compiled transitively by integrated and standalone builds. Runtime status,
generation and RF verification remain explicit so apply/restore cannot report success from only a
UI-side write.

## Standalone

The standalone host injects shared Root/Hook services and merges the same ConfigProvider/NFC runtime
manifest used by the integrated YSuite app.
