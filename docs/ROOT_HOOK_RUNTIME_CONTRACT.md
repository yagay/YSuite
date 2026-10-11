# Root and LSPosed runtime contracts

These contracts protect against false "Root denied" and "Hook not injected" diagnoses on Android 16 / KernelSU / LSPosed API 102.

## Independent evidence layers

1. Host attached: a FeatureHost exists. Missing Host is **not** revoked Root.
2. Root authorization: the process-wide Host libsu shell is alive and privileged. Store this evidence with a short TTL, and invalidate on explicit authorization/session changes. Never infer revocation from a task timeout.
3. Root execution: returns its own exit code, timeout and I/O failure independently of authorization. Long-running TaskManager scans use an isolated libsu shell with at most two simultaneous jobs.
4. Framework: LSPosed service connection and API version.
5. Target: scope, running process, loaded build/version.
6. Feature: verified Hook callback from the correct PID/build. Missing callback is **unconfirmed**, not proof of denied scope or absent injection.
7. Feature effect: independently reported success/failure of the requested operation.

## Responsibilities

* `RootManager`: owns libsu configuration, authorization cache and isolated shell creation.
* `SuiteRootGateway`: only privileged command entry; it owns timeouts, queue protection, stdout caps and per-feature attribution.
* `HostRootProbe`: a single cache per actual Host, using verified Host capability where available. It must never start a per-feature probe storm.
* `SuiteXposedServiceBroker`: only LSPosed service listener in the combined APK; framework connectivity does not prove a feature Hook.
* `FeatureHookEvidence`: feature-specific observed report. Only confirmed installed reports can become STALE; unknown reports remain UNCONFIRMED.

## Regression gates

CI must run `python3 tools/verify_host_runtime_contracts.py` and `python3 tools/verify_shared_infrastructure.py` before building `:suite:assembleDebug`. Test on device by temporarily saturating process scanning and verifying that Root remains GRANTED while individual commands report BUSY or TIMEOUT. Restart NFC/SystemUI and verify that a loaded LSPosed target is not confused with a confirmed feature Hook.
