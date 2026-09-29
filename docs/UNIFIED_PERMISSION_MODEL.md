# Unified host permission model

YSuite reads package-level and framework state centrally. Root, LSPosed, overlay, runtime notification permission, diagnostics and the shared accessibility grant are host-level states. Feature screens may display the same state, but they do not invent separate YSuite accessibility grants.

Accessibility is special because Android authorizes a concrete service component. YSuite therefore exposes one component, `com.yagay.YSuite.accessibility.SuiteAccessibilityService`, and both YFloat and YNotify resolve to that component when embedded. Standalone APKs continue to resolve to their own services.

Notification listener access remains a separate Android service-level grant for YNotify and is surfaced in the YSuite permission card rather than being treated as the shared accessibility grant.
