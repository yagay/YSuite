# YSuite shared accessibility

YSuite owns exactly one Android `AccessibilityService`:

`com.yagay.YSuite.accessibility.SuiteAccessibilityService`

The merged host removes the standalone feature declarations for:

- `com.yagay.YFloat.LensAccessibilityService`
- `com.yagay.YNotify.collector.UiAccessibilityService`

`SuiteAccessibilityService` extends YFloat's accessibility host so YFloat keeps ownership of gestures, screenshots, interactive windows and accessibility overlays. The same `AccessibilityEvent` stream is forwarded to `YNotify.collector.UiAccessibilityBridge` for YNotify classification and storage.

Standalone APKs are unchanged architecturally: YFloat still registers `LensAccessibilityService`; YNotify still registers `UiAccessibilityService`. Their status helpers resolve the expected component from the current host package, so the same feature source works in both standalone and YSuite builds.

After upgrading from the older multi-service YSuite build, Android cannot silently grant the new shared accessibility component. The host detects legacy per-feature grants and directs the user to enable the shared YSuite service once.
