# Adding a feature to YSuite

The merged APK owns shared permissions and process-global services. A new feature should keep its standalone build, but reuse YSuite capabilities when embedded.

## 1. Register the feature

Add one `FeatureSpec` entry with the feature ID, entry activity, optional runtime initializer, and `sharedCapabilities`.

Available host capabilities:

- `ROOT` — one KernelSU/root grant for `com.yagay.YSuite`; YSuite owns the process-wide libsu defaults.
- `LSPOSED` — one host LSPosed service/broker; feature runtimes are captured by `SuiteXposedServiceBroker`.
- `ACCESSIBILITY` — one `SuiteAccessibilityService` grant for the whole APK.
- `OVERLAY` — package-level overlay grant shared automatically by all embedded features.
- `NOTIFICATIONS` — package-level runtime notification grant shared automatically by all embedded features.
- `NOTIFICATION_LISTENER` — one `SuiteNotificationListenerService` grant for the whole APK.

## 2. Accessibility consumers

Do not add another enabled AccessibilityService to the merged YSuite APK. If the feature needs accessibility events, set `accessibilityBridgeClassName` to a class inside the feature.

Preferred public static methods are:

```text
onServiceConnected(AccessibilityService)
onServiceDisconnected(AccessibilityService)
onAccessibilityEvent(AccessibilityService, AccessibilityEvent)
```

The live service parameter lets the feature reuse the shared grant for windows, global actions, gestures and screenshots. Lightweight consumers may use `Context` instead of `AccessibilityService` for the first parameter.

The bridge class remains in the feature project, so the feature still builds independently.

## 3. Notification-listener consumers

Do not add another enabled NotificationListenerService to the merged YSuite APK. Set `notificationListenerBridgeClassName` and expose any callbacks the feature needs:

```text
onListenerConnected(NotificationListenerService)
onListenerDisconnected(NotificationListenerService)
onNotificationPosted(NotificationListenerService, StatusBarNotification, RankingMap)
onNotificationRemoved(NotificationListenerService, StatusBarNotification, RankingMap, int)
onNotificationRankingUpdate(NotificationListenerService, RankingMap)
```

YSuite dispatches only to features that are currently enabled.

## 4. Standalone special services

A standalone APK may keep its own AccessibilityService or NotificationListenerService. When the same feature is embedded, its standalone special-service declarations must be disabled or removed so only the YSuite host service is exposed. Current YFloat/YNotify services are removed by the YSuite manifest; new features should follow the same convention or use a host-controlled manifest placeholder.

## 5. No duplicate permission pages

Feature screens may show the host state, but should not treat another component in the same package as their own grant. When embedded, open the YSuite shared service component for accessibility/notification-listener settings. Package-level grants naturally resolve against the YSuite package.

This keeps adding/removing a feature limited to its submodule + `FeatureSpec` registration + optional bridge, without adding another user authorization flow.
