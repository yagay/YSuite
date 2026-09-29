# Root capability migration

YSuite owns root in the combined host. Feature repositories remain independently buildable, so a
feature may keep its standalone root implementation as a fallback, but it must prefer the YSuite
root gateway whenever `com.yagay.suite.core.SuiteRootGateway` is present.

The compatibility entry is:

```text
SuiteRootGateway.executeFromPlugin(pluginId, operation, command, timeoutSeconds)
```

This design keeps the feature source host-neutral:

- inside YSuite, the reflection target exists and YSuite executes the privileged operation;
- inside a standalone APK, the target class does not exist and the feature uses its local fallback;
- if the YSuite class exists but the gateway call fails, a plugin must not silently launch its own
  `su` process, because that would reintroduce a second system entry.

Target-process reloads should use `SuiteProcessManager` rather than issuing process kills directly.
