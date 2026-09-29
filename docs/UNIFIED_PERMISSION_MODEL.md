# Unified host permission model

YSuite reads package-level and framework state centrally. Root, LSPosed, overlay, runtime notification permission, diagnostics, accessibility and notification-listener access are host-level capabilities. Feature screens may display the same state, but they do not invent separate YSuite grants.

Accessibility is component-scoped on Android, so YSuite exposes one component: `com.yagay.YSuite.accessibility.SuiteAccessibilityService`. Embedded features consume its event stream and, when needed, the live `AccessibilityService` instance through `SuiteAccessibilityBroker`. Standalone APKs continue to use their own services.

Notification-listener access is also component-scoped, so YSuite exposes one component: `com.yagay.YSuite.notification.SuiteNotificationListenerService`. Embedded features consume it through `SuiteNotificationListenerBroker`; standalone APKs keep their own notification-listener services.

Package-level grants such as overlay and runtime notification permission are automatically shared by every embedded feature because they run under `com.yagay.YSuite`. Root and LSPosed remain process-level host capabilities managed by their existing YSuite brokers.

A feature declares its requirements with `FeatureSpec.sharedCapabilities`. Optional accessibility and notification-listener bridge class names make event/capability sharing opt-in without adding a dependency from the standalone feature onto YSuite.
