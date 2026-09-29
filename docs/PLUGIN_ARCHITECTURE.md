# YSuite Plugin Architecture

YSuite is the only host. Feature projects are plugins.

## Non-negotiable rule

> A plugin may request a capability; only YSuite may own a capability.

The combined application always follows this direction:

```
Android / LSPosed / Root / system services
                  |
                  v
                YSuite
                  |
            plugin contracts
                  |
      +-----------+-----------+
      v           v           v
    YFloat      YParam      YNotify ...
```

A feature repository may still ship a standalone APK. The standalone app owns the platform entry
for that APK only. When the same feature sources are embedded in YSuite, YSuite owns the entry and
the feature becomes a logical plugin.

## Host-owned capabilities

YSuite is the only owner of these capabilities in the combined APK:

- libxposed module entry and physical hooks
- LSPosed framework-service listener and scope policy
- root shell and privileged command execution
- target-process reload/restart coordination
- AccessibilityService
- NotificationListenerService
- package-level permission state
- shared overlay/window host policy
- cross-process bridge policy
- crash attribution
- diagnostic/log export policy
- common UI theme, system bars, page shell and navigation policy

Plugins must not introduce a second host for one of these resources.

## Xposed model

`META-INF/xposed/java_init.list` contains exactly one entry:

```
com.yagay.YSuite.xposed.SuiteXposedModule
```

Existing feature `XposedModule` classes remain useful for standalone builds. In YSuite they are
instantiated as logical plugins and attached to a YSuite-owned `XposedInterface` proxy.

All `hook()` calls therefore enter `SuiteHookRegistry`. The registry guarantees:

- one physical libxposed hook for one target executable/class initializer;
- any number of logical feature handlers behind that hook;
- priority ordering inside the YSuite plugin graph;
- argument/result/`this` forwarding through one logical chain;
- independent plugin exception isolation;
- one physical unhook only after the last logical subscriber is removed.

A feature must never be added directly to the YSuite `java_init.list`.

## Root and process operations

Combined-host root operations go through `SuiteRootGateway`. Target-process restarts/reloads go
through `SuiteProcessManager`, which coalesces concurrent reloads for the same package.

Feature-local root executors may exist only for standalone compatibility while migration is in
progress. New combined-host code must not create another `su` process, libsu default builder, or
process-kill coordinator.

## Accessibility and notification listener

The combined manifest exposes only:

- `SuiteAccessibilityService`
- `SuiteNotificationListenerService`

Feature services are removed from the merged manifest. YSuite fans platform events out to feature
bridges.

## UI ownership

YUI/YSuite owns:

- theme and dark mode;
- status/navigation bar handling and insets;
- standard top bars and screen scaffolds;
- list/settings/detail page spacing;
- loading/empty/error states;
- standard buttons, cards, settings rows and action rows;
- shared plugin page structure.

Normal plugin pages should use `YComposeActivity`, `YScaffold`/`YPluginScaffold`, `YPluginList`, and
YUI components. Transparent overlays, probes and other special windows may explicitly opt out via
`YUiWindowOptOut`.

A plugin owns its business UI content, not the surrounding application/window framework.

## Plugin boundary

A plugin should contain:

- business logic and rules;
- data/repositories/databases;
- OCR or feature-specific algorithms;
- feature-specific models/state;
- feature-specific UI content;
- logical Hook handlers.

A plugin should not own:

- a second system integration entry;
- global process configuration in the combined host;
- duplicate platform services;
- duplicate physical hooks;
- duplicate system-bar/theme/page-shell infrastructure.

## CI guarantees

`tools/verify_host_ownership.py` verifies the source-level single-host invariants. The build workflow
also verifies the final APK's Xposed entry and merged special-access services. Any change that
reintroduces multiple system entries must fail CI.
