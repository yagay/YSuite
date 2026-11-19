# YSuite embedded runtime contract

When the same feature source is built as a standalone APK or embedded in YSuite, **the
compiled APK owns its IPC endpoint**. Preferences are user configuration, not identity.

## Rules

- Use `HostIdentity.compiledPackage(BuildConfig.HOST_PACKAGE, standalonePackage)` to route
  Xposed broadcasts and provider calls. Define `HOST_PACKAGE` in the feature Gradle build,
  with an embedded YSuite variant and a standalone variant. Never silently revert to the
  standalone package because remote preferences have not synchronized.
- Reserve generated manifest `applicationId` authorities for host-owned providers. Feature
  code must consume the corresponding compiled authority, not a stale local setting.
- Register LSPosed listeners through the single YSuite broker; a failed initial attach must be
  retryable and the broker registration must be idempotent.
- Report a periodic engine as healthy only after a recent heartbeat **from this boot**.
  A timestamp updated only on user/app events denotes *last activity*, not a live heartbeat.
- A standalone process should yield to the integrated YSuite process only after a validated
  live-process handoff. Stale Settings.Global ownership markers alone are not runtime proof.
- Keep standalone APK functionality, debug diagnostics, and existing hook behaviors intact.

## Regression validation

`python3 tools/verify_host_runtime_contracts.py` runs in architecture validation and
the ARM64 Debug build. It checks package identity, IPC registrations, broker retry contracts,
health freshness, and NFC provider fallback. Existing feature and UI contract checks remain.

**Not covered by source validation:** an actual OxygenOS/ColorOS engine launch, LSPosed
system_server injection, NFC RF hardware behaviour, or Google Circle-to-Search capture.
Those require device-side runtime testing after installing the Debug APK.
