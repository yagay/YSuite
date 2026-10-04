# Platform boundaries

Root and LSPosed are infrastructure, not feature-owned utilities.

`core:platform:api` defines:
- capability status,
- RootGateway,
- HookGateway,
- PlatformServices.

`core:platform:android` is the replaceable adapter layer.

The clean foundation currently ships deliberately unconfigured adapters that report unavailable.
Real Root and LSPosed adapters will be implemented later without changing feature contracts.
