# Platform boundaries

Root, LSPosed, logging and permissions use the same split:

- pure Kotlin API module,
- Android adapter module.

## Root / LSPosed

`core:platform:api` owns:
- capability kinds and status,
- RootGateway,
- HookGateway,
- PlatformServices,
- PlatformCapabilityMonitor.

`core:platform:android` owns Android-specific adapters.

The clean foundation still uses deliberately unconfigured Root/Hook adapters. Real implementations are
added later without changing feature contracts.

## Logging

`core:logging:api` owns:
- LogRecord,
- LogSink,
- YSuiteLogger,
- CompositeYSuiteLogger,
- bounded InMemoryLogStore.

`core:logging:android` owns Logcat output.

The application composition root combines Logcat + in-memory history into one logger.

## Permissions

`core:permissions:api` owns requirements and results.

`core:permissions:android` owns permission state checks.

`core:ui` owns the Activity Result request bridge through
`rememberYSuitePermissionRequester`, keeping feature screens on one permission-request flow.
