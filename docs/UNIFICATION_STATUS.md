# YSuite unification status

This file tracks ownership, not feature completeness.

| Capability | Combined-host owner | Plugin model |
| --- | --- | --- |
| Xposed module entry | YSuite | feature XposedModule attached as logical plugin |
| Physical method hooks | YSuite `SuiteHookRegistry` | logical handlers share one physical hook |
| LSPosed app-side service listener | YSuite broker | feature listeners receive dispatched lifecycle |
| Root shell | YSuite `SuiteRootGateway` | standalone fallback only when host is absent |
| Process reload | YSuite `SuiteProcessManager` | requests are coalesced by package |
| AccessibilityService | YSuite | feature accessibility bridges |
| NotificationListenerService | YSuite | feature listener bridges |
| Crash attribution | YSuite | feature id attached to host log |
| Diagnostic export | YSuite | per-plugin tags/data sources |
| UI theme/window/page shell | YUI/YSuite | plugin supplies content only |

The source and APK build are guarded by `tools/verify_host_ownership.py` and GitHub Actions checks.
