package com.yagay.YNFC.xposed;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.util.Log;

import com.yagay.YNFC.BuildConfig;
import com.yagay.YNFC.NfcControlPolicy;
import com.yagay.YNFC.xposed.discovery.HookDiscoveryEngine;
import com.yagay.YNFC.xposed.discovery.HookTarget;
import com.yagay.YNFC.xposed.payload.RewriteResult;
import com.yagay.YNFC.xposed.payload.RfPayloadEngine;
import com.yagay.YNFC.xposed.payload.UidPayloadProbe;
import com.yagay.YNFC.xposed.profile.HookProfileStore;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static com.yagay.YNFC.xposed.NfcHookUtils.*;

import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

/** Capability-based NFC RF override engine with controller lifecycle restoration. */
public class NfcInjectionModule extends XposedModule {
    private static final String TAG = "NfcUIDSim";
    private static final int HOOK_BUILD = BuildConfig.HOOK_BUILD;
    private static final int MAX_LEARNING_HOOKS = 4;
    private static final int MAX_TRIGGER_HOOKS = 4;
    private static final long TRIGGER_RF_WINDOW_MS = 3_000L;
    private static final long LIFECYCLE_TRIGGER_RF_WINDOW_MS = 5_000L;
    private static final long LIFECYCLE_NATURAL_WAIT_MS = 1_200L;
    private static final long LIFECYCLE_TRIGGER_WAIT_MS = 1_600L;
    private static final long LIFECYCLE_TOTAL_TIMEOUT_MS = 9_000L;
    private static final long LIFECYCLE_REPLAY_DELAY_MS = 600L;
    private static final long LIFECYCLE_FINAL_REPLAY_GRACE_MS = 1_200L;
    private static final int LIFECYCLE_MAX_TRIGGER_ATTEMPTS = 3;
    private static final long EARLY_CONFIG_WAIT_MS = 200L;
    private static final long EARLY_CONFIG_RETRY_MS = 20L;
    private static final long EARLY_REPLAY_TIMEOUT_MS = 8_000L;
    private static final long EARLY_REPLAY_RETRY_MS = 50L;
    private static final long CONTROLLER_LIFECYCLE_DEBOUNCE_MS = 1_200L;

    private final ExecutorService commandExecutor = Executors.newSingleThreadExecutor(r -> daemon(r, "NfcUIDSim-Command"));
    private final ExecutorService pollingExecutor = Executors.newSingleThreadExecutor(r -> daemon(r, "YNFC-PollingGuard"));
    private final ExecutorService lifecycleExecutor = Executors.newSingleThreadExecutor(r -> daemon(r, "NfcUIDSim-Lifecycle"));
    private final ExecutorService earlyReplayExecutor = Executors.newSingleThreadExecutor(r -> daemon(r, "NfcUIDSim-EarlyReplay"));
    // Read-only ColorOS inspection runs on a separate daemon, never on a Binder/NCI thread.
    private final ExecutorService colorOsObserveExecutor = Executors.newSingleThreadExecutor(
            r -> daemon(r, "YNFC-ColorOS-Observe"));
    private final HookDiscoveryEngine discoveryEngine = new HookDiscoveryEngine();
    private final RfPayloadEngine payloadEngine = new RfPayloadEngine();
    private final HookProfileStore profileStore = new HookProfileStore();
    private final HookConfigStore configStore = new HookConfigStore();
    private final HookStateWriter stateWriter = new HookStateWriter();
    private final NfcProcessVendorController vendorController = new NfcProcessVendorController();
    private final RefreshTriggerEngine refreshTriggerEngine = new RefreshTriggerEngine();
    private final RfReplayEngine replayEngine = new RfReplayEngine();
    // Observe the actual NFC-A/NCI writer separately from hook installation.
    // A discovered method is not evidence that the controller ever received an
    // eligible UID-setting packet.
    private final Set<String> observedUidWriterShapes = new HashSet<>();

    private volatile HookTarget activeTarget;
    private volatile ClassLoader nfcClassLoader;
    private volatile boolean fullTriggerDiscoveryDone;
    private volatile String activeCodec = "";
    private volatile SimConfig cachedConfig = SimConfig.uninitialized();
    private volatile ContentObserver commandObserver;
    private volatile ContentObserver pollingGuardObserver;
    private final NfcControlPolicy pollingPolicy = new NfcControlPolicy();
    private volatile String pollingGuardLastProof = "";
    private volatile String pollingCapabilitySummary = "UNDETECTED";
    private final Handler pollingMainHandler = new Handler(Looper.getMainLooper());
    private volatile BroadcastReceiver adapterStateReceiver;
    private volatile boolean observerRegistered;
    private volatile boolean controllerInvalid;
    private volatile boolean controllerReadyObserved;
    private volatile int controllerLifecycleHookCount;
    private volatile long lastControllerInvalidAt;
    private volatile long lastControllerReadyAt;
    private volatile boolean lifecycleReapplyPending;
    private volatile long lifecycleRecoveryGeneration = Long.MIN_VALUE;
    private volatile long lifecycleRecoveryStartedAt;
    private volatile boolean lifecycleWorkerRunning;
    private volatile long lifecycleWorkerGeneration = Long.MIN_VALUE;
    // Only an explicitly armed recovery write may publish lifecycle VERIFIED state.
    // Natural startup writes are still rewritten, while controller-ready exact replay is
    // the deterministic proof point for OFF -> ON recovery.
    private volatile boolean recoveryWriteArmed;
    private volatile String earlyHookFingerprint = "";
    private volatile long lastTriggeredGeneration = Long.MIN_VALUE;
    private volatile long completedGeneration = Long.MIN_VALUE;
    private volatile int completedPid;
    private volatile int installedHookCount;
    private volatile boolean learningMode;
    private volatile boolean disabledAfterFailure;
    private volatile String disabledFailureUid;
    private volatile long disabledFailureGeneration = Long.MIN_VALUE;
    private volatile long pendingTriggerGeneration = Long.MIN_VALUE;
    private volatile long pendingTriggerStartedAt;
    private volatile long pendingTriggerWindowMs = TRIGGER_RF_WINDOW_MS;
    private volatile long confirmedTriggerGeneration = Long.MIN_VALUE;
    private volatile String pendingTriggerTarget = "";

    // Startup and controller-ready replay share the same tested replay engine.
    // Share-mode/Binder triggering remains compatibility fallback only.
    private volatile boolean earlyReplayWorkerScheduled;
    private volatile long lifecycleFailureGeneration = Long.MIN_VALUE;
    private volatile long lifecycleFailureControllerEpoch = Long.MIN_VALUE;

    private volatile byte[] reversibleStockPayload;
    private volatile String reversibleTargetFingerprint;
    private volatile long reversibleCapturedGeneration = Long.MIN_VALUE;
    private volatile boolean controllerReinitRequired;
    private volatile String restoreMode = "NONE";
    // Conservative in-process safety barriers. A failed/throwing native UID
    // write might already have affected the controller: only a generation with
    // *no attempted write* may skip restoration/restart.
    private volatile boolean nativeUidMutationAttempted;
    private volatile boolean vendorTypeAWriteAttempted;

    @Override public void onModuleLoaded(XposedModuleInterface.ModuleLoadedParam param) {
        super.onModuleLoaded(param);
        Log.i(TAG, "PROD MODULE loaded build=" + HOOK_BUILD + " process=" + param.getProcessName());
    }

    @Override public void onPackageLoaded(XposedModuleInterface.PackageLoadedParam lp) {
        super.onPackageLoaded(lp);
        if (!"com.android.nfc".equals(lp.getPackageName())) return;
        final int pid = Process.myPid();
        final ClassLoader cl = lp.getDefaultClassLoader();
        nfcClassLoader = cl;
        // A fresh com.android.nfc process is always a fresh physical-controller proof domain.
        // Provider state may not be readable this early, so mark memory invalid now and commit the
        // epoch barrier again once the command bridge has a Context.
        markControllerInvalid("process_start_early");
        installEarlyKnownRfHook(cl, pid);
        installControllerLifecycleHooks(cl, pid);
        installPollingGuardHooks(cl);
        // Independent of UID emulation initialization: a missing RF writer does not
        // prevent persistent reader-block enforcement after a service restart.
        pollingExecutor.execute(this::startPollingGuardBridge);
        installEarlyAdapterStateBridge(pid);
        commandExecutor.execute(() -> initializeRuntime(cl, pid));
    }

    /**
     * Install bounded, capability-discovered NFC hooks. Standard AOSP service
     * entry points are preferred; vendor-specific controller classes are only
     * optional, structurally verified backstops. Never hook unrelated methods.
     */
    private void installPollingGuardHooks(ClassLoader cl) {
        Class<?> service = null;
        Class<?> parameters = null;
        try {
            service = Class.forName("com.android.nfc.NfcService", false, cl);
            parameters = Class.forName("com.android.nfc.NfcDiscoveryParameters", false, cl);
        } catch (Throwable t) {
            Log.w(TAG, "POLLING_GUARD standard NFC types unavailable", t);
        }
        final Method compute = NfcDiscoveryCapabilityRegistry.findCompute(service, parameters);
        int installed = 0;
        if (compute != null) {
            try {
                compute.setAccessible(true);
                hook(compute).intercept(chain -> {
                    Object original = chain.proceed();
                    if (HookConfigStore.isPassive() || !pollingBlocked() || original == null) {
                        return original;
                    }
                    try {
                        return NfcPollingGuard.withoutPolling(original);
                    } catch (Throwable t) {
                        Log.w(TAG, "POLLING_GUARD compute compatibility failure", t);
                        return original;
                    }
                });
                installed++;
                Log.i(TAG, "POLLING_GUARD compute target="
                        + compute.getDeclaringClass().getName() + "#" + compute.getName());
            } catch (Throwable t) {
                Log.w(TAG, "POLLING_GUARD compute hook rejected", t);
            }
        }

        List<NfcDiscoveryCapabilityRegistry.Candidate> drivers =
                NfcDiscoveryCapabilityRegistry.findDrivers(cl,
                        discoveryEngine.discoverNfcDiscoveryDriverNames(cl), parameters);
        int driverHooks = 0;
        for (NfcDiscoveryCapabilityRegistry.Candidate candidate : drivers) {
            try {
                final Method method = candidate.method;
                hook(method).intercept(chain -> {
                    if (HookConfigStore.isPassive()) return chain.proceed();
                    boolean blocked = pollingBlocked();
                    Object original = chain.getArg(0);
                    Object updated = original;
                    int previousMask = -1;
                    boolean hostRouting = false;
                    try {
                        if (original != null) {
                            previousMask = NfcPollingGuard.mask(original);
                            hostRouting = NfcPollingGuard.hostRouting(original);
                            if (blocked) updated = NfcPollingGuard.withoutPolling(original);
                        }
                    } catch (Throwable t) {
                        Log.w(TAG, "POLLING_GUARD driver parameter compatibility failure "
                                + candidate.fingerprint(), t);
                        recordPollingGuardState("UNVERIFIED",
                                "discovery parameter incompatible: " + candidate.fingerprint(), blocked);
                        return chain.proceed();
                    }

                    Object result;
                    if (updated == original) {
                        result = chain.proceed();
                    } else {
                        Object[] args = chain.getArgs().toArray();
                        args[0] = updated;
                        result = chain.proceed(args);
                    }
                    if (blocked) {
                        // A Hooked Java call is not native NFC controller proof.
                        recordPollingGuardState("PARAMETERS_INTERCEPTED",
                                candidate.fingerprint() + " oldMask=0x"
                                        + Integer.toHexString(previousMask)
                                        + " newMask=0x0 hostRouting=" + hostRouting,
                                true);
                    } else {
                        recordPollingGuardState("SYSTEM_DEFAULT_REQUESTED",
                                candidate.fingerprint() + " pollMask=0x"
                                        + Integer.toHexString(previousMask), false);
                    }
                    return result;
                });
                driverHooks++;
                installed++;
                Log.i(TAG, "POLLING_GUARD driver capability installed "
                        + candidate.fingerprint() + " source=" + candidate.source);
            } catch (Throwable t) {
                Log.w(TAG, "POLLING_GUARD optional driver hook skipped "
                        + candidate.fingerprint(), t);
            }
        }

        final NfcDiscoveryCapabilityRegistry.ServiceAdapter adapter =
                NfcDiscoveryCapabilityRegistry.resolveRouting(service);
        pollingCapabilitySummary = "compute=" + (compute != null)
                + ";driverHooks=" + driverHooks
                + ";routeRefresh=" + adapter.available()
                + ";sdk=" + Build.VERSION.SDK_INT + ";detail=" + adapter.detail;
        Log.i(TAG, "POLLING_GUARD CAPABILITIES "
                + pollingCapabilitySummary + " pid=" + Process.myPid());
        // No hook or route refresh is not success; mark capability unsupported
        // and keep the existing NFC service fully operational.
        if (installed == 0) {
            recordPollingGuardState("UNSUPPORTED",
                    "No safe NFC discovery interception target", false);
        }
    }

    /** Prefer the user's device-protected Provider setting; mirror it in the NFC
     * process to survive provider startup races and cold service restarts.
     */
    private Boolean readMirroredPollingPreference() {
        Application app = currentApplication();
        if (app == null) return null;
        try {
            SharedPreferences p = app.createDeviceProtectedStorageContext()
                    .getSharedPreferences("ynfc_polling_guard_mirror", Context.MODE_PRIVATE);
            return p.contains("polling_disabled") ? p.getBoolean("polling_disabled", false) : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void mirrorPollingPreference(boolean disabled) {
        Application app = currentApplication();
        if (app == null) return;
        try {
            SharedPreferences p = app.createDeviceProtectedStorageContext()
                    .getSharedPreferences("ynfc_polling_guard_mirror", Context.MODE_PRIVATE);
            if (!p.contains("polling_disabled")
                    || p.getBoolean("polling_disabled", false) != disabled) {
                p.edit().putBoolean("polling_disabled", disabled).commit();
            }
        } catch (Throwable t) {
            Log.w(TAG, "POLLING_GUARD NFC-process mirror unavailable", t);
        }
    }

    private boolean pollingBlocked() {
        Boolean cached = pollingPolicy.desired();
        if (cached != null) return cached;
        // Provider is authoritative. An NFC-process mirror may be stale if the
        // user toggled the setting immediately before a forced process restart.
        Boolean provider = configStore.readPollingDisabled();
        if (provider != null) {
            pollingPolicy.setDesired(provider);
            mirrorPollingPreference(provider);
            return provider;
        }
        Boolean mirror = readMirroredPollingPreference();
        if (mirror != null) {
            pollingPolicy.setDesired(mirror);
            return mirror;
        }
        // Fail open rather than break NFC / HCE when configuration is unknown.
        return false;
    }

    private void startPollingGuardBridge() {
        Application app = waitForApplication(8_000L);
        if (app == null) {
            Log.w(TAG, "POLLING_GUARD bridge waiting for NFC process context");
            return;
        }
        try {
            ContentObserver observer = new ContentObserver(null) {
                @Override public void onChange(boolean selfChange) {
                    pollingExecutor.execute(() -> refreshPollingPreference("provider_change"));
                }
                @Override public void onChange(boolean selfChange, Uri uri) {
                    pollingExecutor.execute(() -> refreshPollingPreference("provider_change"));
                }
            };
            for (Uri uri : new Uri[]{
                    HookConfigStore.suiteUri(), HookConfigStore.standaloneUri()}) {
                if (uri != null) app.getContentResolver().registerContentObserver(uri, true, observer);
            }
            pollingGuardObserver = observer;
            ContentValues capabilities = new ContentValues();
            capabilities.put("polling_capabilities", pollingCapabilitySummary);
            writeValuesWithRetry(capabilities, 5, 75L);
            refreshPollingPreference("startup");
        } catch (Throwable t) {
            Log.w(TAG, "POLLING_GUARD bridge registration failed", t);
        }
    }

    private void refreshPollingPreference(String reason) {
        if (HookConfigStore.isPassive()) return;
        Boolean provider = configStore.readPollingDisabled();
        if (provider == null) return;
        boolean changed = pollingPolicy.setDesired(provider);
        mirrorPollingPreference(provider);
        if (changed) {
            pollingGuardLastProof = "";
            Log.i(TAG, "POLLING_GUARD desired=" + provider + " reason=" + reason);
            requestPollingRoutingRefresh("desired_change:" + reason);
        }
    }

    /**
     * Live toggling must not rely on restarting com.android.nfc. NFC routing
     * belongs to the NFC main looper; invoking it from the Provider IO thread
     * races the controller's queued discovery / HCE state transitions.
     */
    private void requestPollingRoutingRefresh(String reason) {
        if (HookConfigStore.isPassive()) return;
        final long revision = pollingPolicy.nextRevision();
        final boolean blocked = pollingBlocked();
        Log.i(TAG, "POLLING_GUARD live refresh scheduled revision=" + revision
                + " disabled=" + blocked + " reason=" + reason);
        pollingMainHandler.post(() -> applyPollingRoutingOnMain(revision, blocked, reason, 0));
    }

    private void applyPollingRoutingOnMain(long revision, boolean blocked, String reason,
                                           int attempt) {
        if (!pollingPolicy.isCurrent(revision) || HookConfigStore.isPassive()
                || blocked != pollingBlocked()) return;
        try {
            ClassLoader loader = nfcClassLoader;
            if (loader == null) throw new IllegalStateException("NFC class loader unavailable");
            Class<?> service = Class.forName("com.android.nfc.NfcService", false, loader);
            NfcDiscoveryCapabilityRegistry.ServiceAdapter adapter =
                    NfcDiscoveryCapabilityRegistry.resolveRouting(service);
            if (!adapter.available()) {
                recordPollingGuardState("REFRESH_UNSUPPORTED",
                        "No safe NFC routing refresh method: " + adapter.detail, blocked);
                return;
            }
            Object owner = adapter.getInstance.invoke(null);
            if (owner == null) {
                // NFC service creation can complete after the first Provider signal.
                schedulePollingRetry(revision, blocked, reason, attempt, "service_unavailable");
                return;
            }
            if (!Boolean.TRUE.equals(adapter.isEnabled.invoke(owner))) {
                // Desired preference is persisted and will be applied on adapter ON.
                recordPollingGuardState("WAITING_FOR_NFC_ON",
                        "disabled=" + blocked + " reason=" + reason, blocked);
                return;
            }
            // Retain existing routing and card emulation; apply only a new
            // discovery policy. Never restart NFC as an automatic fallback.
            adapter.applyRouting.invoke(owner, true);

            int observedMask = readServiceDiscoveryMask(service, owner);
            Log.i(TAG, "POLLING_GUARD LIVE_REFRESH revision=" + revision
                    + " disabled=" + blocked + " currentMask=0x"
                    + Integer.toHexString(observedMask) + " attempt=" + attempt
                    + " thread=" + Thread.currentThread().getName()
                    + " reason=" + reason);
            if (blocked && observedMask != 0) {
                schedulePollingRetry(revision, blocked, reason, attempt,
                        "java_poll_mask_not_zero_0x" + Integer.toHexString(observedMask));
                return;
            }
            // This confirms a Java-side routing refresh only. The OEM controller
            // still needs hardware verification; never claim native RF success.
            recordPollingGuardState("LIVE_REFRESH_REQUESTED",
                    "disabled=" + blocked + " currentMask=0x"
                            + Integer.toHexString(observedMask)
                            + " revision=" + revision + " reason=" + reason,
                    blocked);
        } catch (Throwable t) {
            Log.w(TAG, "POLLING_GUARD live refresh failed revision=" + revision
                    + " attempt=" + attempt + " reason=" + reason, t);
            schedulePollingRetry(revision, blocked, reason, attempt,
                    t.getClass().getSimpleName());
        }
    }

    private int readServiceDiscoveryMask(Class<?> service, Object instance) {
        try {
            Field current = service.getDeclaredField("mCurrentDiscoveryParameters");
            current.setAccessible(true);
            Object params = current.get(instance);
            return params == null ? -1 : NfcPollingGuard.mask(params);
        } catch (Throwable t) {
            return -1; // Unknown, never confuse this with an actual zero mask.
        }
    }

    private void schedulePollingRetry(long revision, boolean blocked, String reason,
                                      int attempt, String detail) {
        if (!pollingPolicy.isCurrent(revision)) return;
        if (attempt >= 2) {
            recordPollingGuardState("LIVE_REFRESH_UNCONFIRMED",
                    "disabled=" + blocked + " reason=" + reason
                            + " error=" + detail + " retries=" + attempt, blocked);
            return;
        }
        pollingMainHandler.postDelayed(
                () -> applyPollingRoutingOnMain(revision, blocked, reason, attempt + 1),
                350L);
    }

    private void recordPollingGuardState(String status, String detail, boolean blocked) {
        String proof = status + ":" + detail;
        if (proof.equals(pollingGuardLastProof)) return;
        pollingGuardLastProof = proof;
        Log.i(TAG, "POLLING_GUARD " + proof + " pid=" + Process.myPid());
        pollingExecutor.execute(() -> {
            if (HookConfigStore.isPassive()) return;
            ContentValues values = new ContentValues();
            values.put("polling_guard_status", status);
            values.put("polling_guard_pid", Process.myPid());
            values.put("polling_guard_detail", detail);
            writeValuesWithRetry(values, 3, 75L);
        });
    }

    /**
     * Install the highest-confidence known RF writer before waiting for the NFC Application object.
     * This lets us catch the controller's natural startup RF configuration instead of always trying
     * to manufacture a second refresh after boot. Payload inspection remains the final safety gate.
     */
    private void installEarlyKnownRfHook(ClassLoader cl, int pid) {
        try {
            List<HookTarget> known = discoveryEngine.discoverKnownRfCandidates(cl);
            if (known.isEmpty()) return;
            HookTarget target = known.get(0);
            activeTarget = target;
            installRfHook(cl, pid, target);
            earlyHookFingerprint = target.fingerprint();
            installedHookCount = 1;
            Log.i(TAG, "EARLY RF HOOK installed target=" + target + " pid=" + pid);
        } catch (Throwable t) {
            Log.w(TAG, "EARLY RF HOOK unavailable " + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

    /**
     * Observe real NFC controller/service lifecycle methods in addition to adapter broadcasts.
     * We never alter return values or arguments here: these hooks are read-only lifecycle signals.
     * This covers controller resets where OxygenOS/NXP reinitializes NFC without a reliable public
     * ACTION_ADAPTER_STATE_CHANGED OFF/ON pair.
     */
    private void installControllerLifecycleHooks(ClassLoader cl, int pid) {
        String[] classes = {
                "com.android.nfc.dhimpl.NativeNfcManager",
                "com.android.nfc.dhimpl.NxpNativeNfcManager",
                "com.android.nfc.NfcService"
        };
        int installed = 0;
        for (String className : classes) {
            try {
                Class<?> cls = Class.forName(className, false, cl);
                for (Method method : cls.getDeclaredMethods()) {
                    String name = method.getName();
                    boolean invalidator = "deinitialize".equals(name) || "disableInternal".equals(name);
                    boolean ready = "initialize".equals(name) || "enableInternal".equals(name);
                    if (!invalidator && !ready) continue;
                    try {
                        method.setAccessible(true);
                        final String signal = className + "#" + name + methodSignature(method);
                        hook(method).intercept(chain -> {
                            if (invalidator) markControllerInvalid("hook:" + signal);
                            Object result = chain.proceed();
                            if (ready && controllerCallSucceeded(method, result)) {
                                markControllerReady("hook:" + signal);
                            }
                            return result;
                        });
                        installed++;
                        Log.i(TAG, "CONTROLLER LIFECYCLE hook installed signal=" + signal + " pid=" + pid);
                    } catch (Throwable hookError) {
                        Log.w(TAG, "CONTROLLER LIFECYCLE hook skipped " + className + "#" + name + " " +
                                hookError.getClass().getSimpleName() + ": " + hookError.getMessage());
                    }
                }
            } catch (Throwable ignored) {
                // Capability not present on this NFC stack. Other known classes/broadcast fallback
                // remain available, so absence is not treated as a module failure.
            }
        }
        controllerLifecycleHookCount = installed;
        persistControllerLifecycle("HOOKS_READY", "discovery", "count=" + installed);
        Log.i(TAG, "CONTROLLER LIFECYCLE hooks=" + installed + " pid=" + pid);
    }

    private static String methodSignature(Method method) {
        StringBuilder sb = new StringBuilder("(");
        Class<?>[] params = method.getParameterTypes();
        for (int i = 0; i < params.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(params[i].getSimpleName());
        }
        return sb.append(")->").append(method.getReturnType().getSimpleName()).toString();
    }

    private static boolean controllerCallSucceeded(Method method, Object result) {
        Class<?> returnType = method.getReturnType();
        if (returnType == void.class) return true;
        if (returnType == boolean.class || returnType == Boolean.class) {
            return Boolean.TRUE.equals(result);
        }
        if (result instanceof Number) {
            // Native NFC wrappers conventionally use 0 as success; positive values are also
            // accepted here for Java service methods that return an enabled/state token.
            return ((Number) result).longValue() >= 0L;
        }
        return result != null;
    }

    private void markControllerInvalid(String reason) {
        long now = System.currentTimeMillis();
        boolean firstInvalid;
        synchronized (this) {
            firstInvalid = !controllerInvalid;
            controllerInvalid = true;
            controllerReadyObserved = false;
            if (firstInvalid) lastControllerInvalidAt = now;
        }
        if (!firstInvalid) return;
        pollingGuardLastProof = "";
        pollingPolicy.invalidateRefresh();
        lifecycleFailureGeneration = Long.MIN_VALUE;
        lifecycleFailureControllerEpoch = Long.MIN_VALUE;
        invalidateRfEvidenceForControllerReset(reason);
        persistControllerLifecycle("INVALID", reason, "pid=" + Process.myPid());
        finishLifecycleRecovery(lifecycleRecoveryGeneration);
        Log.i(TAG, "CONTROLLER INVALID reason=" + reason + " pid=" + Process.myPid());
    }

    private void markControllerReady(String reason) {
        long now = System.currentTimeMillis();
        boolean wasInvalid;
        synchronized (this) {
            // initialize() and enableInternal() can be nested in one enable transaction. Treat the
            // pair as one physical ready edge rather than advancing the epoch twice.
            if (lastControllerReadyAt > 0L && now - lastControllerReadyAt < CONTROLLER_LIFECYCLE_DEBOUNCE_MS) {
                return;
            }
            wasInvalid = controllerInvalid;
            controllerInvalid = false;
            controllerReadyObserved = true;
            lastControllerReadyAt = now;
        }
        // Some OEM paths expose only a ready/initialize edge. Conservatively invalidate old proof
        // before recovery when no matching disable/deinitialize edge was observed.
        if (!wasInvalid) invalidateRfEvidenceForControllerReset("implicit_invalid_before_" + reason);
        persistControllerLifecycle("READY", reason,
                "pid=" + Process.myPid() + ";wasInvalid=" + wasInvalid);
        Log.i(TAG, "CONTROLLER READY reason=" + reason + " pid=" + Process.myPid() +
                " wasInvalid=" + wasInvalid);
        scheduleLifecycleRecovery("controller_ready:" + reason);
        pollingExecutor.execute(() -> requestPollingRoutingRefresh("controller_ready"));
    }

    private void persistControllerLifecycle(String status, String source, String detail) {
        ContentValues v = baseHookState();
        SimConfig cfg = cachedConfig;
        if (cfg.initialized && cfg.generation > 0L) v.put("state_generation", cfg.generation);
        v.put("controller_lifecycle_status", status == null ? "" : status);
        v.put("controller_lifecycle_source", source == null ? "" : source);
        v.put("controller_lifecycle_detail", detail == null ? "" : detail);
        v.put("controller_lifecycle_hook_count", controllerLifecycleHookCount);
        v.put("controller_lifecycle_invalid", controllerInvalid);
        v.put("controller_lifecycle_ready_observed", controllerReadyObserved);
        v.put("controller_lifecycle_last_invalid_at", lastControllerInvalidAt);
        v.put("controller_lifecycle_last_ready_at", lastControllerReadyAt);
        writeValuesWithRetry(v, 8, 75L);
    }

    /** Register adapter OFF/ON tracking as early as the system context becomes available.
     * This is deliberately independent of waitForApplication(): controller power transitions can
     * happen before the NFC Application object is published, and missing OFF would leave stale RF
     * evidence valid for the following ON cycle.
     */
    private void installEarlyAdapterStateBridge(int pid) {
        lifecycleExecutor.execute(() -> {
            long end = System.currentTimeMillis() + 2_000L;
            while (adapterStateReceiver == null && System.currentTimeMillis() < end) {
                Context ctx = currentContext();
                if (ctx != null) {
                    registerAdapterStateReceiver(ctx);
                    if (adapterStateReceiver != null) {
                        Log.i(TAG, "EARLY ADAPTER STATE bridge ready pid=" + pid);
                        return;
                    }
                }
                sleep(25L);
            }
            if (adapterStateReceiver == null) {
                Log.w(TAG, "EARLY ADAPTER STATE bridge unavailable pid=" + pid);
            }
        });
    }

    private void initializeRuntime(ClassLoader cl, int pid) {
        reportStatusWithRetry(pid, installedHookCount > 0, installedHookCount,
                installedHookCount > 0 ? "EARLY_HOOK_READY" : "INITIALIZING",
                installedHookCount > 0 ? "Known RF_CONFIG_WRITE hook installed before NFC Application startup" : "Waiting for NFC application context",
                activeTarget == null ? null : activeTarget.fingerprint());
        Application app = waitForApplication(8_000L);
        if (app == null) {
            reportStatusWithRetry(pid, installedHookCount > 0, installedHookCount, "INIT_FAILED",
                    "NFC Application context unavailable", activeTarget == null ? null : activeTarget.fingerprint());
            return;
        }

        // Read-only inventory differentiates the old NXP changeRfParamsByConfig
        // from today's NativeNfcManager#setNciConfig. Historical replay
        // snapshots from a previous PID are not usable RF evidence.
        inspectUidEntryPoints(cl, pid);

        // Install passive observers on the exact methods present in ColorOS 17's
        // NFC Service APK. Never invoke Type-A configuration or vendor NCI commands.
        // The separate read-only region preflight invokes only pure OEM getters.
        inspectColorOsRegionSupport(cl, pid);
        installColorOsReadOnlyHooks(cl, pid);

        // Vendor capability discovery is informational; the user-selected RF
        // backend continues to own only the UID (NCI LA_NFCID1) mutation.
        NfcProcessVendorController.Result typeA = vendorController.probeTypeA();
        // Save the actual ColorOS AIDL descriptor and probe PID in the existing
        // diagnostic provider; method presence is not external RF UID proof.
        ContentValues colorOsProbe = baseHookState();
        colorOsProbe.put("coloros_type_a_probe_pid", pid);
        colorOsProbe.put("coloros_type_a_probe_stage", typeA.stage);
        colorOsProbe.put("coloros_type_a_probe_callable", typeA.success);
        colorOsProbe.put("coloros_type_a_probe_descriptor",
                typeA.vendorDescriptor == null ? "" : typeA.vendorDescriptor);
        colorOsProbe.put("coloros_type_a_probe_detail", typeA.detail);
        writeValuesWithRetry(colorOsProbe, 8, 75L);
        Log.i(TAG, "TYPE_A_CAPABILITY stage=" + typeA.stage
                + " callable=" + typeA.success
                + " detail=" + typeA.detail
                + " selectedBackend=CONFIG_DRIVEN"
                + " note=uid_only_rf_does_not_require_atqa_sak");

        List<HookTarget> installTargets = new ArrayList<>();
        List<HookTarget> triggerTargets = discoveryEngine.discoverKnownTriggerCandidates(cl);
        persistTriggerCandidates(pid, triggerTargets);
        HookTarget cached = profileStore.loadValid(app, cl);
        if (cached != null) {
            learningMode = false;
            activeTarget = cached;
            if (!cached.fingerprint().equals(earlyHookFingerprint)) installTargets.add(cached);
            persistProfileStatus("CACHED_VERIFIED", cached);
            Log.i(TAG, "PROFILE HIT target=" + cached + " pid=" + pid + " early=" + earlyHookFingerprint);
        } else {
            learningMode = true;
            reportStatusWithRetry(pid, installedHookCount > 0, installedHookCount, "DISCOVERING",
                    "Profile invalid/missing; learning deepest RF_CONFIG_WRITE", null);
            List<HookTarget> candidates = discoveryEngine.discoverRfCandidates(cl);
            persistDiscoveryCandidates(pid, candidates);
            if (candidates.isEmpty() && installedHookCount == 0) {
                reportStatusWithRetry(pid, false, 0, "UNSUPPORTED", "No RF_CONFIG_WRITE candidate discovered", null);
                return;
            }
            if (!candidates.isEmpty()) {
                activeTarget = candidates.get(0);
                profileStore.save(app, activeTarget, "LEARNING");
                int added = 0;
                for (HookTarget candidate : candidates) {
                    if (candidate.fingerprint().equals(earlyHookFingerprint)) continue;
                    installTargets.add(candidate);
                    if (++added >= MAX_LEARNING_HOOKS - (installedHookCount > 0 ? 1 : 0)) break;
                }
                Log.i(TAG, "LEARNING additionalInstallCount=" + installTargets.size() + " mutationOwner=" + activeTarget + " pid=" + pid);
            }
        }

        int installed = installedHookCount;
        for (HookTarget target : installTargets) {
            try {
                installRfHook(cl, pid, target);
                installed++;
            } catch (Throwable t) {
                Log.e(TAG, "HOOK CANDIDATE FAILED target=" + target + " " + t.getClass().getSimpleName() + ": " + t.getMessage(), t);
            }
        }
        installedHookCount = installed;
        int triggerHookCount = 0;
        for (int i = 0; i < Math.min(MAX_TRIGGER_HOOKS, triggerTargets.size()); i++) {
            HookTarget target = triggerTargets.get(i);
            try {
                installRefreshTriggerHook(cl, pid, target);
                triggerHookCount++;
            } catch (Throwable t) {
                Log.w(TAG, "REFRESH TRIGGER HOOK skipped target=" + target + " " + t.getClass().getSimpleName() + ": " + t.getMessage());
            }
        }
        persistRefreshRuntime("DISCOVERED", "", "hook-probe", 0L, false);
        Log.i(TAG, "REFRESH TRIGGER hooks=" + triggerHookCount + " candidates=" + triggerTargets.size() + " pid=" + pid);
        if (installed == 0) {
            reportStatusWithRetry(pid, false, 0, "HOOK_FAILED", "All RF_CONFIG_WRITE candidates failed to install", null);
            return;
        }

        reportStatusWithRetry(pid, true, installed, "READY",
                learningMode ? "RF_CONFIG_WRITE learning mode; highest-score target owns mutation" : "RF_CONFIG_WRITE verified profile ready",
                activeTarget == null ? null : activeTarget.fingerprint());
        startCommandBridge(app, pid);
    }

    /**
     * ColorOS 17 NFC Service 17.0.0.260114.apk exposes these methods:
     * OplusNfcAdapterService#setHceTypeAConfig(boolean,atqa,sak,uid),
     * HceAccessCard#doSetHceTypeAConfig(boolean,atqa,sak,uid),
     * NfcChipDeviceImpl#doSetHceTypeAConfig(boolean,atqa,sak,uid),
     * NfcFeatureFetcher#isSupportHceAccessCardByRegion().
     * Observer callbacks MUST invoke the original exactly once and never alter
     * arguments, results or exceptions. They only record execution evidence.
     */
    private void installColorOsReadOnlyHooks(ClassLoader cl, int pid) {
        final int serviceHook = hookColorOsReadOnly(cl, "com.oplus.nfc.OplusNfcAdapterService",
                "setHceTypeAConfig", "service",
                boolean.class, byte[].class, byte[].class, byte[].class);
        final int hceHook = hookColorOsReadOnly(cl, "com.oplus.nfc.hceaccesscard.HceAccessCard",
                "doSetHceTypeAConfig", "hce",
                boolean.class, byte[].class, byte[].class, byte[].class);
        final int chipHook = hookColorOsReadOnly(cl, "com.oplus.nfc.common.NfcChipDeviceImpl",
                "doSetHceTypeAConfig", "chip",
                boolean.class, byte[].class, byte[].class, byte[].class);
        final int regionHook = hookColorOsReadOnly(cl, "com.oplus.nfc.NfcFeatureFetcher",
                "isSupportHceAccessCardByRegion", "region");
        // The NCI vendor sender is a general-purpose Android API. Observe it
        // only when called *from* HceAccessCard's Type-A method on this thread.
        final int nciHook = hookColorOsReadOnly(cl, "android.nfc.NfcAdapter",
                "sendVendorNciMessage", "nci",
                int.class, int.class, int.class, byte[].class);
        final int count = serviceHook + hceHook + chipHook + regionHook + nciHook;
        colorOsObserveExecutor.execute(() -> {
            ContentValues v = baseHookState();
            v.put("coloros_observe_pid", pid);
            v.put("coloros_observe_hook_count", count);
            v.put("coloros_observe_service_hook_installed", serviceHook == 1);
            v.put("coloros_observe_hce_hook_installed", hceHook == 1);
            v.put("coloros_observe_chip_hook_installed", chipHook == 1);
            v.put("coloros_observe_region_hook_installed", regionHook == 1);
            v.put("coloros_observe_nci_hook_installed", nciHook == 1);
            v.put("coloros_observe_status", count > 0 ? "READ_ONLY_HOOKS_INSTALLED" : "METHODS_UNAVAILABLE");
            v.put("coloros_observe_note", "No Type-A commands were initiated by the observer; "
                    + "absence of a callback is not proof of absent native HAL traffic");
            writeValuesWithRetry(v, 5, 75L);
        });
    }

    private int hookColorOsReadOnly(ClassLoader cl, String className, String methodName,
                                    String key, Class<?>... parameters) {
        try {
            Class<?> cls = Class.forName(className, false, cl);
            Method method = cls.getDeclaredMethod(methodName, parameters);
            method.setAccessible(true);
            hook(method).intercept(chain -> {
                // A native vendor message can serve several OEM functions.
                // Do not attribute an unrelated message to Type-A configuration.
                boolean relevant = !"nci".equals(key) || isColorOsTypeACallOnStack();
                if (!relevant || HookConfigStore.isPassive()) return chain.proceed();
                // Only the HCE implementation entrypoint is allowed to replace
                // parameters. All other hooks remain read-only observers.
                SimConfig intercepted = cachedConfig;
                Object[] replacement = null;
                if ("hce".equals(key) && intercepted.initialized && intercepted.active
                        && intercepted.useInterceptTypeA()
                        && intercepted.generation > 0L
                        && intercepted.handledGeneration != intercepted.generation
                        && !"FAILED".equals(intercepted.commandStatus)) {
                    Object[] originalArgs = chain.getArgs().toArray();
                    if (originalArgs.length == 4 && Boolean.TRUE.equals(originalArgs[0])
                            && originalArgs[1] instanceof byte[]
                            && originalArgs[2] instanceof byte[]
                            && originalArgs[3] instanceof byte[]) {
                        byte[] atqa = strictTypeAHex(intercepted.atqa, 2);
                        byte[] sak = strictTypeAHex(intercepted.sak, 1);
                        byte[] uid = strictTypeAHex(intercepted.uid, 4, 7, 10);
                        if (atqa != null && sak != null && uid != null) {
                            replacement = originalArgs.clone();
                            replacement[1] = atqa;
                            replacement[2] = sak;
                            replacement[3] = uid;
                            // A failed OEM call can still have partially changed the
                            // controller. STOP must not claim a stock restore.
                            vendorTypeAWriteAttempted = true;
                        }
                    }
                }
                final boolean replaced = replacement != null;
                final long appliedGeneration = replaced ? intercepted.generation : 0L;
                Object outcome = null;
                Throwable failure = null;
                try {
                    outcome = replaced ? chain.proceed(replacement) : chain.proceed();
                    return outcome;
                } catch (Throwable error) {
                    failure = error;
                    throw error;
                } finally {
                    final String result = failure == null
                            ? readOnlyResult(outcome) : "THREW_" + failure.getClass().getSimpleName();
                    final boolean success = failure == null;
                    final Object finalOutcome = outcome;
                    final Throwable finalFailure = failure;
                    // Do not record raw UID/ATQA/SAK, NCI payload, or arbitrary OEM bytes.
                    // Queued writes are non-blocking and cannot change the Binder result.
                    try {
                        colorOsObserveExecutor.execute(() -> {
                            persistColorOsReadOnlyEvent(key, result, success);
                            if (replaced) {
                                publishTypeAInterceptOutcome(appliedGeneration, finalOutcome,
                                        finalFailure, result);
                            }
                        });
                    } catch (Throwable ignored) {
                        // Failed telemetry must never affect the NFC service.
                    }
                }
            });
            Log.i(TAG, "COLOROS_READ_ONLY_HOOK installed=" + className + "#" + methodName);
            return 1;
        } catch (Throwable error) {
            Log.i(TAG, "COLOROS_READ_ONLY_HOOK unavailable=" + className + "#" + methodName
                    + " reason=" + error.getClass().getSimpleName());
            return 0;
        }
    }

    /**
     * A successful intercepted Java call does not prove the UID on the air.
     * Publish a non-verified state only for the exact generation still selected.
     * Never mark RF accepted or effective ACTIVE.
     */
    private void publishTypeAInterceptOutcome(long generation, Object outcome,
                                              Throwable error, String result) {
        SimConfig latest = readConfig();
        if (!latest.initialized || !latest.active || !latest.useInterceptTypeA()
                || latest.generation != generation) return;
        boolean accepted = error == null && Boolean.TRUE.equals(outcome);
        String stage = accepted ? "INTERCEPT_CALL_ACCEPTED" : "INTERCEPT_CALL_REJECTED";
        ContentValues v = baseHookState();
        v.put("state_generation", generation);
        v.put("command_consumed_generation", generation);
        v.put("command_handled_generation", generation);
        v.put("command_action", "APPLY");
        v.put("command_status", accepted ? "INTERCEPT_CALL_ACCEPTED" : "FAILED");
        v.put("command_detail", "HceAccessCard original method returned " + result
                + "; external NFC reader validation required");
        v.put("command_pid", Process.myPid());
        v.put("operation_state", accepted ? "VERIFYING" : "FAILED");
        v.put("effective_state", "UNKNOWN");
        v.put("verification_confidence", accepted ? "UNVERIFIED" : stage);
        v.put("rf_accepted", false);
        v.put("rf_status", stage);
        v.put("rf_uid", "");
        v.put("rf_source", "file-intercept-hce");
        v.put("rf_generation", generation);
        v.put("rf_pid", Process.myPid());
        v.put("rf_verification", "EXTERNAL_READER_REQUIRED");
        v.put("full_diag_stage", stage);
        v.put("full_diag_summary", "Type-A parameters substituted at the OEM HCE entrypoint; "
                + "method result=" + result + "; over-the-air UID unknown");
        writeValuesWithRetry(v, 12, 75L);
        completedGeneration = generation;
        completedPid = Process.myPid();
    }

    /**
     * INTERCEPT never calls the privileged Vendor API or triggers applyRouting.
     * It only arms an in-process replacement for the next natural OEM HCE call.
     */
    private void runFileInterceptCommand(SimConfig cfg) {
        if (!cfg.active) {
            if (noUidWriteToRestore()) {
                completeNoOverrideStop(cfg, "No Type-A parameters were replaced in this NFC process");
            } else {
                requestControllerRestart(cfg, "OEM HCE parameters were intercepted; stock restoration "
                        + "requires explicit NFC service reload and external verification");
            }
            return;
        }
        if (strictTypeAHex(cfg.uid, 4, 7, 10) == null ||
                strictTypeAHex(cfg.atqa, 2) == null ||
                strictTypeAHex(cfg.sak, 1) == null) {
            failVendorTypeACommand(cfg, "INTERCEPT_INVALID_PARAMETERS",
                    "File profile requires NFC-A UID (4/7/10), ATQA (2) and SAK (1) bytes");
            return;
        }
        writeSimpleCommandState("INTERCEPT_WAITING",
                "Waiting for a natural ColorOS HceAccessCard Type-A configuration call; "
                        + "no OEM request was generated", cfg.generation, "APPLY", false);
        lifecycleExecutor.execute(() -> {
            sleep(6_000L);
            SimConfig latest = readConfig();
            if (!latest.initialized || latest.generation != cfg.generation ||
                    latest.handledGeneration == cfg.generation ||
                    !latest.active || !latest.useInterceptTypeA() ||
                    isGenerationCompleted(cfg.generation, Process.myPid())) return;
            failVendorTypeACommand(latest, "INTERCEPT_NO_OEM_CALL",
                    "No ColorOS HceAccessCard Type-A configuration call occurred "
                            + "after the file interception was armed; nothing was replaced");
            completedGeneration = cfg.generation;
            completedPid = Process.myPid();
        });
    }

    private static boolean isColorOsTypeACallOnStack() {
        for (StackTraceElement frame : Thread.currentThread().getStackTrace()) {
            if ("com.oplus.nfc.hceaccesscard.HceAccessCard".equals(frame.getClassName())
                    && "doSetHceTypeAConfig".equals(frame.getMethodName())) return true;
        }
        return false;
    }

    private static String readOnlyResult(Object value) {
        if (value instanceof Boolean) return (Boolean) value ? "TRUE" : "FALSE";
        if (value instanceof Integer) return "INT_" + value;
        if (value == null) return "VOID_OR_NULL";
        // Never serialize arbitrary object.toString() into diagnostics.
        return "RETURNED_" + value.getClass().getSimpleName();
    }

    private void persistColorOsReadOnlyEvent(String key, String result, boolean completed) {
        ContentValues v = baseHookState();
        v.put("coloros_observe_" + key + "_pid", Process.myPid());
        v.put("coloros_observe_" + key + "_at_ms", System.currentTimeMillis());
        v.put("coloros_observe_" + key + "_result", result);
        v.put("coloros_observe_" + key + "_completed", completed);
        writeValuesWithRetry(v, 4, 75L);
    }

    /**
     * Read-only availability inspection. The OEM region getter and Android
     * read-only system property are invoked, but NO Type-A configuration,
     * permission grant, routing change or NFC restart is performed.
     * A region getter's boolean is a policy hint, not an RF UID proof.
     */
    private void inspectColorOsRegionSupport(ClassLoader cl, int pid) {
        String cn = "UNAVAILABLE";
        String supported = "UNAVAILABLE";
        String property = "UNAVAILABLE";
        try {
            Class<?> cls = Class.forName("com.oplus.nfc.util.NfcRegionUtils", false, cl);
            Method getter = cls.getDeclaredMethod("isRegionCN");
            getter.setAccessible(true);
            cn = readOnlyResult(getter.invoke(null));
        } catch (Throwable error) {
            cn = "ERROR_" + error.getClass().getSimpleName();
        }
        try {
            Class<?> cls = Class.forName("android.os.SystemProperties");
            Method getter = cls.getDeclaredMethod("getBoolean", String.class, boolean.class);
            getter.setAccessible(true);
            property = readOnlyResult(getter.invoke(null, "ro.oplus.nfc.hce_accesscard", false));
        } catch (Throwable error) {
            property = "ERROR_" + error.getClass().getSimpleName();
        }
        try {
            Class<?> cls = Class.forName("com.oplus.nfc.NfcFeatureFetcher", false, cl);
            Method getter = cls.getDeclaredMethod("isSupportHceAccessCardByRegion");
            getter.setAccessible(true);
            supported = readOnlyResult(getter.invoke(null));
        } catch (Throwable error) {
            supported = "ERROR_" + error.getClass().getSimpleName();
        }
        ContentValues v = baseHookState();
        v.put("coloros_region_probe_pid", pid);
        v.put("coloros_region_cn", cn);
        v.put("coloros_region_hce_property", property);
        v.put("coloros_region_support", supported);
        v.put("coloros_region_source", "OEM getters only; no Type-A command invoked");
        writeValuesWithRetry(v, 5, 75L);
        Log.i(TAG, "COLOROS_REGION_READ_ONLY pid=" + pid
                + " cn=" + cn + " property=" + property + " support=" + supported);
    }

    private void installRfHook(ClassLoader cl, int pid, HookTarget target) throws Exception {
        Method method = target.resolve(cl);
        hook(method).intercept(chain -> {
            // YSuite already owns the hook graph; never rewrite or trigger STOP from the
            // still-loaded standalone hook during a live owner handoff.
            if (HookConfigStore.isPassive()) return chain.proceed();
            Object[] args = chain.getArgs().toArray();
            int payloadArg = findSingleByteArrayArg(args);
            if (payloadArg < 0) {
                observeUidWriter(target, pid, -1, 0, "NO_SINGLE_BYTE_ARRAY", "");
                return chain.proceed();
            }
            byte[] original = (byte[]) args[payloadArg];
            int payloadScore = payloadEngine.inspectScore(original);
            // Distinguish a complete CORE_SET_CONFIG command from TLV-only
            // or vendor-private byte arrays before making any RF assumptions.
            observeUidWriter(target, pid, original == null ? -1 : original.length,
                    payloadScore, UidPayloadProbe.shape(original),
                    UidPayloadProbe.prefixHex(original, 12));
            if (payloadScore <= 0) return chain.proceed();

            HookTarget owner = activeTarget;
            if (owner != null && !owner.fingerprint().equals(target.fingerprint())) {
                SimConfig passiveCfg = currentConfig();
                if (passiveCfg.diagnostics) {
                    Log.i(TAG, "RFPROBE PASSIVE target=" + target.fingerprint() + " owner=" + owner.fingerprint() +
                            " payloadScore=" + payloadScore + " pid=" + pid);
                }
                return chain.proceed();
            }

            SimConfig cfg = currentConfig();
            if (!cfg.initialized) {
                // The first controller RF configuration can race Provider availability during a
                // cold com.android.nfc startup. Spend only a small bounded budget here so the NFC
                // service is never stalled for seconds. If config is still unavailable, let the
                // OEM call proceed unchanged but retain this exact verified RF writer invocation
                // for one in-process replay as soon as durable desired state becomes readable.
                cfg = awaitEarlyConfig();
                if (!cfg.initialized) {
                    captureRfInvocationSnapshot(method, chain.getThisObject(), args, payloadArg, target, pid);
                    return chain.proceed();
                }
            }

            // Vendor Type-A owns its controller writes. Never mutate RF_CONFIG_WRITE
            // on the same command generation or during Vendor stop/recovery.
            if (cfg.useVendorTypeA()) return chain.proceed();

            // A terminal failed APPLY is not a pending request. If a later OEM
            // RF write happens naturally, it must not silently apply a UID
            // while the UI still reports FAILED; only a new APPLY generation
            // can authorize another UID configuration attempt.
            if (cfg.active && cfg.generation == cfg.handledGeneration &&
                    !"SUCCESS".equals(cfg.commandStatus)) {
                if (cfg.diagnostics) Log.i(TAG, "RFPROBE SKIP_TERMINAL_APPLY status="
                        + cfg.commandStatus + " generation=" + cfg.generation);
                return chain.proceed();
            }

            // A natural RF write in a new NFC process is the best lifecycle recovery trigger. Mark
            // it as a lifecycle reapply before mutating so a successful native result updates only
            // observed RF evidence; the original user APPLY command history remains untouched.
            // Any terminal desired APPLY whose RF proof is no longer fresh is a lifecycle
            // reapply, even when com.android.nfc kept the same PID. This is crucial for adapter
            // OFF -> ON: the controller resets but the Java service process may survive. The
            // controller epoch makes stale proof detectable, and this natural OEM RF write is the
            // preferred place to reapply instead of manufacturing a share-mode refresh.
            boolean terminalDesiredApply = cfg.active && cfg.generation > 0L &&
                    cfg.handledGeneration == cfg.generation && "SUCCESS".equals(cfg.commandStatus);
            if (terminalDesiredApply && !isLifecycleVerified(cfg.generation, pid, cfg.uid)) {
                synchronized (this) {
                    lifecycleReapplyPending = true;
                    lifecycleRecoveryGeneration = cfg.generation;
                    if (lifecycleRecoveryStartedAt == 0L) lifecycleRecoveryStartedAt = System.currentTimeMillis();
                }
            }

            markRfObservedForPendingTrigger(cfg, target, pid);
            if (cfg.diagnostics) {
                String caller = compactCallStack(24);
                Log.i(TAG, "RFPROBE ACTIVE target=" + target.fingerprint() + " targetScore=" + target.score +
                        " payloadScore=" + payloadScore + " pid=" + pid + " generation=" + cfg.generation + " stack=" + caller);
                persistRfCaller(pid, caller);
            }

            if (!cfg.active) {
                if ("STOP".equals(cfg.commandAction) && !isGenerationCompleted(cfg.generation, pid)) {
                    byte[] stock = reversibleStockPayload;
                    if (stock != null && target.fingerprint().equals(reversibleTargetFingerprint)) {
                        Object[] stockArgs = args.clone();
                        stockArgs[payloadArg] = stock.clone();
                        Object result = chain.proceed(stockArgs);
                        NativeOutcome outcome = interpretNativeResult(method, result);
                        if (outcome.accepted) {
                            clearRestoreState();
                            completeCommand(cfg, "RF_STOCK_RESTORED", "", "explicit-stock-nfcid1|" + target.fingerprint(),
                                    outcome, "Original LA_NFCID1 explicitly restored by verified RF_CONFIG_WRITE target");
                        } else {
                            failCommand(cfg, "RF_STOCK_FAILED", "", "Native rejected explicit stock LA_NFCID1 restore", outcome);
                        }
                        return result;
                    }
                }
                return chain.proceed();
            }

            if (cfg.uid == null) return chain.proceed();
            String uidHex = normalizeUid(cfg.uid);
            if (uidHex.length() != 8 && uidHex.length() != 14 && uidHex.length() != 20) {
                failCommand(cfg, "UID_INVALID", uidHex, "UID must be 4, 7 or 10 bytes", NativeOutcome.notInvoked());
                return chain.proceed();
            }

            if (disabledAfterFailure) {
                boolean same = disabledFailureGeneration == cfg.generation && uidHex.equals(disabledFailureUid);
                if (same) return chain.proceed();
                disabledAfterFailure = false;
                disabledFailureUid = null;
                disabledFailureGeneration = Long.MIN_VALUE;
            }

            // Replacement only: never append a second NFCID1 when the real controller
            // does not supply a fully parsed, unique existing LA_NFCID1 field.
            RewriteResult rewritten = payloadEngine.replaceExisting(original, hexToBytes(uidHex));
            if (!rewritten.changed) {
                writeRfProgress(cfg, "WAITING", uidHex, target.methodName + ": " + rewritten.reason, rewritten.codecId);
                return chain.proceed();
            }

            activeCodec = rewritten.codecId;
            writeRfProgress(cfg, "APPLYING", uidHex, rewritten.reason, rewritten.codecId);
            persistRewriteDiagnostics(rewritten);
            Log.i(TAG, "RF_REWRITE codec=" + rewritten.codecId + " reason=" + rewritten.reason +
                    " oldPayload=" + rewritten.oldPayloadLength + " newPayload=" + rewritten.newPayloadLength +
                    " oldCount=" + rewritten.oldParamCount + " newCount=" + rewritten.newParamCount +
                    " originalBytes=" + original.length + " rewrittenBytes=" + rewritten.data.length);
            Log.i(TAG, "NFCID1 APPLY target=" + target.fingerprint() + " codec=" + rewritten.codecId +
                    " reason=" + rewritten.reason + " pid=" + pid + " generation=" + cfg.generation + " uid=" + uidHex +
                    " lifecycle=" + lifecycleReapplyPending);

            Object[] rewrittenArgs = args.clone();
            rewrittenArgs[payloadArg] = rewritten.data;
            nativeUidMutationAttempted = true;
            Object result = chain.proceed(rewrittenArgs);
            NativeOutcome outcome = interpretNativeResult(method, result);
            if (outcome.accepted) {
                disabledAfterFailure = false;
                disabledFailureUid = null;
                disabledFailureGeneration = Long.MIN_VALUE;

                boolean reversibleExisting = rewritten.reason != null &&
                        (rewritten.reason.contains("REPLACED_EXISTING_LA_NFCID1") ||
                                rewritten.reason.contains("RESIZED_EXISTING_LA_NFCID1"));
                synchronized (this) {
                    if (learningMode) {
                        markTargetVerified(target);
                        learningMode = false;
                    }
                    if (reversibleExisting) {
                        // Both same-length replacement and structurally verified resize mutate an
                        // existing LA_NFCID1 parameter. The exact original payload is therefore a
                        // safe inverse, including stock 33 00 -> 33 04/07/0A transitions.
                        reversibleStockPayload = original.clone();
                        reversibleTargetFingerprint = target.fingerprint();
                        reversibleCapturedGeneration = cfg.generation;
                        controllerReinitRequired = false;
                        restoreMode = "EXPLICIT_LA_NFCID1";
                    } else {
                        reversibleStockPayload = null;
                        reversibleTargetFingerprint = null;
                        reversibleCapturedGeneration = Long.MIN_VALUE;
                        controllerReinitRequired = true;
                        restoreMode = "CONTROLLER_REINIT";
                    }
                }
                persistRestoreState();
                captureVerifiedRfInvocation(method, chain.getThisObject(), args, target, pid);
                replayEngine.clearPending(target.fingerprint());
                if (lifecycleReapplyPending) {
                    if (recoveryWriteArmed) {
                        completeLifecycleReapply(cfg, uidHex, rewritten.codecId, outcome, target);
                        finishLifecycleRecovery(cfg.generation);
                    } else if (cfg.diagnostics) {
                        Log.i(TAG, "LIFECYCLE natural RF write accepted before replay generation=" +
                                cfg.generation + " uid=" + uidHex + " target=" + target.fingerprint());
                    }
                } else if (!isGenerationCompleted(cfg.generation, pid)) {
                    completeCommand(cfg, "RF_UID_APPLIED", uidHex, rewritten.codecId, outcome,
                            "UID applied by verified target " + target.className + "#" + target.methodName +
                                    "; stopMode=" + restoreMode);
                }
            } else if (lifecycleReapplyPending) {
                publishLifecycleFailure(cfg, uidHex, "Native rejected lifecycle RF payload from " + rewritten.codecId, outcome);
                finishLifecycleRecovery(cfg.generation);
            } else if (!isGenerationCompleted(cfg.generation, pid)) {
                disabledAfterFailure = true;
                disabledFailureUid = uidHex;
                disabledFailureGeneration = cfg.generation;
                failCommand(cfg, "RF_UID_FAILED", uidHex,
                        "native rejected payload from " + rewritten.codecId + "; retry allowed on next generation", outcome);
            }
            return result;
        });
    }

    /**
     * UID-only RF capability evidence for the current NFC process.
     *
     * The historical replay target may belong to another PID/controller
     * generation; never consider it proof. Emit one observation per unique
     * target and payload shape, without invoking or modifying any RF method.
     */
    private void observeUidWriter(HookTarget target, int pid, int bytes, int score,
                                  String shape, String prefix) {
        String fingerprint = target == null ? "unknown" : target.fingerprint();
        String key = pid + "|" + fingerprint + "|" + shape;
        synchronized (observedUidWriterShapes) {
            // Bounded per-process records. No repeated Provider writes on the
            // NFC native configuration call stack.
            if (observedUidWriterShapes.size() >= 20 || !observedUidWriterShapes.add(key)) return;
        }
        Log.i(TAG, "UID_WRITER_OBSERVED pid=" + pid + " target=" + fingerprint
                + " shape=" + shape + " bytes=" + bytes + " score=" + score
                + " prefix=" + prefix);
        commandExecutor.execute(() -> {
            if (Process.myPid() != pid) return;
            ContentValues v = baseHookState();
            v.put("rf_uid_writer_observed_pid", pid);
            v.put("rf_uid_writer_target", fingerprint);
            v.put("rf_uid_writer_payload_bytes", bytes);
            v.put("rf_uid_writer_payload_score", score);
            v.put("rf_uid_writer_payload_shape", shape);
            v.put("rf_uid_writer_payload_prefix", prefix);
            v.put("rf_uid_writer_observed_at", System.currentTimeMillis());
            writeValuesWithRetry(v, 3, 50L);
        });
    }

    /**
     * Class inventory only: never call an OEM method just because it shares a
     * familiar name. A discovered method can still be unsuitable for rewriting.
     */
    private void inspectUidEntryPoints(ClassLoader cl, int pid) {
        String[] classes = {
                "com.android.nfc.dhimpl.NxpNativeNfcManager",
                "com.android.nfc.dhimpl.NativeNfcManager",
                "com.android.nfc.nxp.NxpNfcService$NxpNfcAdapterService"
        };
        String legacy = describeUidMethod(cl, classes, "changeRfParamsByConfig");
        String modern = describeUidMethod(cl, classes, "setNciConfig");
        ContentValues values = baseHookState();
        values.put("rf_uid_inventory_pid", pid);
        values.put("rf_uid_legacy_entry", legacy);
        values.put("rf_uid_modern_entry", modern);
        writeValuesWithRetry(values, 8, 75L);
        Log.i(TAG, "UID_ENTRY_INVENTORY pid=" + pid + " legacy=" + legacy
                + " modern=" + modern);
    }

    private static String describeUidMethod(ClassLoader cl, String[] candidates, String targetName) {
        StringBuilder result = new StringBuilder();
        for (String className : candidates) {
            try {
                Class<?> c = Class.forName(className, false, cl);
                boolean found = false;
                for (Method method : c.getDeclaredMethods()) {
                    if (!targetName.equals(method.getName())) continue;
                    if (result.length() != 0) result.append(" | ");
                    result.append(className).append('#').append(targetName).append('(');
                    Class<?>[] args = method.getParameterTypes();
                    for (int i = 0; i < args.length; i++) {
                        if (i != 0) result.append(',');
                        result.append(args[i].getSimpleName());
                    }
                    result.append("):").append(method.getReturnType().getSimpleName());
                    found = true;
                }
                if (!found) {
                    if (result.length() != 0) result.append(" | ");
                    result.append(className).append(":METHOD_ABSENT");
                }
            } catch (ClassNotFoundException error) {
                if (result.length() != 0) result.append(" | ");
                result.append(className).append(":CLASS_ABSENT");
            } catch (Throwable error) {
                if (result.length() != 0) result.append(" | ");
                result.append(className).append(":INSPECT_ERROR_")
                        .append(error.getClass().getSimpleName());
            }
        }
        return result.toString();
    }

    private void installRefreshTriggerHook(ClassLoader cl, int pid, HookTarget target) throws Exception {
        Method method = target.resolve(cl);
        hook(method).intercept(chain -> {
            Object receiver = chain.getThisObject();
            Object result = chain.proceed();
            if (refreshTriggerEngine.observe(target, method, receiver, result)) {
                persistRefreshRuntime("VERIFIED_INSTANCE", target.fingerprint(), "java-observed", 0L, false);
                Log.i(TAG, "REFRESH TRIGGER VERIFIED target=" + target.fingerprint() + " pid=" + pid);
            }
            return result;
        });
    }

    private synchronized void ensureFullTriggerDiscovery() {
        if (fullTriggerDiscoveryDone) return;
        fullTriggerDiscoveryDone = true;
        ClassLoader cl = nfcClassLoader;
        if (cl == null) return;
        try {
            List<HookTarget> candidates = discoveryEngine.discoverTriggerCandidates(cl);
            persistTriggerCandidates(Process.myPid(), candidates);
            int installed = 0;
            for (int i = 0; i < Math.min(MAX_TRIGGER_HOOKS, candidates.size()); i++) {
                try {
                    installRefreshTriggerHook(cl, Process.myPid(), candidates.get(i));
                    installed++;
                } catch (Throwable t) {
                    Log.w(TAG, "LAZY REFRESH TRIGGER hook skipped target=" + candidates.get(i) + " " +
                            t.getClass().getSimpleName() + ": " + t.getMessage());
                }
            }
            Log.i(TAG, "LAZY REFRESH TRIGGER discovery installed=" + installed +
                    " candidates=" + candidates.size() + " pid=" + Process.myPid());
        } catch (Throwable t) {
            Log.w(TAG, "LAZY REFRESH TRIGGER discovery failed " + t.getClass().getSimpleName() +
                    ": " + t.getMessage());
        }
    }

    private boolean hasVerifiedReplayForCurrentProcess() {
        return replayEngine.hasVerified(Process.myPid());
    }

    private NfcProcessVendorController.Result triggerRfRefresh(SimConfig cfg, boolean enabled, String reason) {
        return triggerRfRefresh(cfg, enabled, reason, TRIGGER_RF_WINDOW_MS);
    }

    private NfcProcessVendorController.Result triggerRfRefresh(SimConfig cfg, boolean enabled, String reason, long windowMs) {
        armTriggerWindow(cfg.generation, reason, windowMs);
        RefreshTriggerEngine.Invocation javaTrigger = refreshTriggerEngine.invoke(enabled);
        if (!javaTrigger.success && !fullTriggerDiscoveryDone) {
            ensureFullTriggerDiscovery();
            javaTrigger = refreshTriggerEngine.invoke(enabled);
        }
        if (javaTrigger.success) {
            pendingTriggerTarget = javaTrigger.targetFingerprint;
            persistRefreshRuntime("TRIGGERED", javaTrigger.targetFingerprint, "java-verified", cfg.generation, false);
            Log.i(TAG, "REFRESH TRIGGER java success generation=" + cfg.generation + " target=" + javaTrigger.targetFingerprint);
            return new NfcProcessVendorController.Result(true, "JAVA_TRIGGERED", javaTrigger.detail, null);
        }

        // NfcService exposes a verified, no-Binder Java routing refresh on
        // this firmware. Run on its own main looper, not the Provider/Hook IO
        // thread; actual RF/UID success still needs a native writer callback.
        NfcProcessVendorController.Result routing = triggerRfRoutingOnMain();
        if (routing.success) {
            pendingTriggerTarget = "nfc-service:applyRouting(boolean)";
            persistRefreshRuntime("TRIGGERED", pendingTriggerTarget,
                    "rf-java-routing", cfg.generation, false);
            return routing;
        }

        // RF UID-only must not depend on the ColorOS Vendor Binder: on this
        // firmware OPlusNfcAdapterService is signature-privileged and the
        // OEM tx6 getter is unavailable. A candidate method discovered in
        // dex is not a callable *observed instance*. Never fake a successful
        // trigger, guess an OEM Binder transaction, or restart NFC to retry.
        String detail = javaTrigger.stage + ": " + javaTrigger.detail
                + "; NFC routing fallback: " + routing.stage + ": " + routing.detail
                + "; Vendor Binder fallback disabled for RF UID-only. "
                + "Waiting for an actual native NFC-A configuration write is "
                + "not evidence that UID emulation succeeded.";
        persistRefreshRuntime("RF_TRIGGER_UNAVAILABLE", "",
                "rf-no-vendor-fallback", cfg.generation, false);
        clearTriggerWindow(cfg.generation);
        Log.w(TAG, "RF_TRIGGER_UNAVAILABLE generation=" + cfg.generation
                + " pid=" + Process.myPid() + " " + detail);
        return new NfcProcessVendorController.Result(
                false, "RF_TRIGGER_UNAVAILABLE", detail, null);
    }

    /**
     * Safe, bounded fallback through the already-known NfcService routing
     * method. It never fabricates an OEM Binder transaction or guesses a
     * NativeNfcManager argument schema. Success only proves applyRouting()
     * was invoked; the RF write Hook must still supply independent evidence.
     */
    private NfcProcessVendorController.Result triggerRfRoutingOnMain() {
        final ClassLoader loader = nfcClassLoader;
        if (loader == null) {
            return new NfcProcessVendorController.Result(false,
                    "RF_ROUTING_UNAVAILABLE", "NFC class loader unavailable", null);
        }
        final AtomicReference<NfcProcessVendorController.Result> outcome = new AtomicReference<>();
        final CountDownLatch done = new CountDownLatch(1);
        boolean queued = pollingMainHandler.post(() -> {
            try {
                Class<?> service = Class.forName("com.android.nfc.NfcService", false, loader);
                NfcDiscoveryCapabilityRegistry.ServiceAdapter adapter =
                        NfcDiscoveryCapabilityRegistry.resolveRouting(service);
                if (!adapter.available()) {
                    outcome.set(new NfcProcessVendorController.Result(false,
                            "RF_ROUTING_UNAVAILABLE", adapter.detail, null));
                    return;
                }
                Object owner = adapter.getInstance.invoke(null);
                if (owner == null || !Boolean.TRUE.equals(adapter.isEnabled.invoke(owner))) {
                    outcome.set(new NfcProcessVendorController.Result(false,
                            "RF_ROUTING_NFC_NOT_READY", "NfcService is disabled or unavailable", null));
                    return;
                }
                adapter.applyRouting.invoke(owner, true);
                outcome.set(new NfcProcessVendorController.Result(true,
                        "RF_ROUTING_REQUESTED",
                        "NfcService.applyRouting(true) invoked on NFC main looper; "
                        + "awaiting a native RF_UID write, not claiming UID acceptance", null));
            } catch (Throwable error) {
                Throwable cause = error.getCause() == null ? error : error.getCause();
                outcome.set(new NfcProcessVendorController.Result(false,
                        "RF_ROUTING_EXCEPTION",
                        cause.getClass().getSimpleName() + ": " + cause.getMessage(), null));
            } finally {
                done.countDown();
            }
        });
        if (!queued) return new NfcProcessVendorController.Result(false,
                "RF_ROUTING_UNAVAILABLE", "NFC main handler rejected refresh request", null);
        try {
            if (!done.await(1_800L, TimeUnit.MILLISECONDS)) {
                return new NfcProcessVendorController.Result(false,
                        "RF_ROUTING_TIMEOUT", "NFC main looper did not complete routing refresh", null);
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            return new NfcProcessVendorController.Result(false,
                    "RF_ROUTING_INTERRUPTED", "Interrupted while waiting for NFC main looper", null);
        }
        NfcProcessVendorController.Result result = outcome.get();
        return result != null ? result : new NfcProcessVendorController.Result(false,
                "RF_ROUTING_UNAVAILABLE", "No NFC routing outcome recorded", null);
    }

    /** A routing request is not UID proof. Terminalize if no native RF write occurred. */
    private void scheduleRfTriggerTimeout(SimConfig cfg, int pid) {
        lifecycleExecutor.execute(() -> {
            sleep(TRIGGER_RF_WINDOW_MS + 500L);
            if (Process.myPid() != pid || isGenerationCompleted(cfg.generation, pid)) return;
            SimConfig latest = readConfig();
            if (!latest.initialized || latest.generation != cfg.generation ||
                    latest.handledGeneration == cfg.generation ||
                    !("TRIGGERED".equals(latest.commandStatus) ||
                            "RUNNING".equals(latest.commandStatus))) return;
            boolean observed = confirmedTriggerGeneration == cfg.generation;
            NfcProcessVendorController.Result timeout =
                    new NfcProcessVendorController.Result(false,
                            observed ? "RF_UID_WRITE_NOT_ACCEPTED" : "RF_NATIVE_WRITE_NOT_OBSERVED",
                            observed
                                    ? "RF configuration writer invoked but no native-accepted UID change recorded"
                                    : "Java routing refresh completed without a current-PID NFC-A UID configuration writer callback",
                            null);
            failRfTriggerCommand(latest, timeout);
        });
    }

    private void armTriggerWindow(long generation, String source, long windowMs) {
        pendingTriggerGeneration = generation;
        pendingTriggerStartedAt = System.currentTimeMillis();
        pendingTriggerWindowMs = Math.max(TRIGGER_RF_WINDOW_MS, windowMs);
        confirmedTriggerGeneration = Long.MIN_VALUE;
        pendingTriggerTarget = source == null ? "" : source;
        persistRefreshRuntime("ARMED", pendingTriggerTarget, "command", generation, false);
    }

    private void markRfObservedForPendingTrigger(SimConfig cfg, HookTarget rfTarget, int pid) {
        long generation = pendingTriggerGeneration;
        if (!cfg.initialized || generation <= 0L || cfg.generation != generation) return;
        long elapsed = System.currentTimeMillis() - pendingTriggerStartedAt;
        if (elapsed < 0L || elapsed > pendingTriggerWindowMs) return;
        if (confirmedTriggerGeneration == generation) return;
        confirmedTriggerGeneration = generation;
        persistRefreshRuntime("RF_WRITE_OBSERVED", pendingTriggerTarget, "causal-window", generation, true);
        Log.i(TAG, "REFRESH TRIGGER RF confirmed generation=" + generation + " elapsedMs=" + elapsed +
                " trigger=" + pendingTriggerTarget + " rfTarget=" + rfTarget.fingerprint() + " pid=" + pid);
    }

    private void clearTriggerWindow(long generation) {
        if (pendingTriggerGeneration != generation) return;
        pendingTriggerGeneration = Long.MIN_VALUE;
        pendingTriggerStartedAt = 0L;
        pendingTriggerWindowMs = TRIGGER_RF_WINDOW_MS;
        pendingTriggerTarget = "";
    }

    private void persistRefreshRuntime(String status, String target, String source, long generation, boolean rfConfirmed) {
        ContentValues v = baseHookState();
        if (generation > 0L) v.put("state_generation", generation);
        v.put("refresh_trigger_status", status == null ? "" : status);
        v.put("refresh_trigger_target", target == null ? "" : target);
        v.put("refresh_trigger_source", source == null ? "" : source);
        v.put("refresh_trigger_generation", generation);
        v.put("refresh_trigger_rf_confirmed", rfConfirmed);
        writeValuesWithRetry(v, 8, 75L);
    }

    private void startCommandBridge(Application app, int pid) {
        try {
            ContentObserver observer = new ContentObserver(null) {
                @Override public void onChange(boolean selfChange) { scheduleCommandRefresh("provider_change"); }
                @Override public void onChange(boolean selfChange, Uri uri) { scheduleCommandRefresh("provider_change"); }
            };
            app.getContentResolver().registerContentObserver(
                    HookConfigStore.activeUri(), true, observer);
            // Listen for handoff/fallback changes without restarting the NFC process.
            if (!HookConfigStore.standaloneUri().equals(HookConfigStore.activeUri())) {
                app.getContentResolver().registerContentObserver(
                        HookConfigStore.standaloneUri(), true, observer);
            }
            if (!HookConfigStore.suiteUri().equals(HookConfigStore.activeUri())) {
                app.getContentResolver().registerContentObserver(
                        HookConfigStore.suiteUri(), true, observer);
            }
            commandObserver = observer;
            observerRegistered = true;
            registerAdapterStateReceiver(app);
            // New process == new physical controller proof domain. Do this after Context/provider
            // availability so an early package-load invalidation cannot be lost before Direct Boot
            // storage is readable.
            invalidateRfEvidenceForControllerReset("process_start");
            refreshConfigAndProcess("startup");
        } catch (Throwable t) {
            writeSimpleCommandState("OBSERVER_FAILED", "Cannot register ConfigProvider observer: " + t.getMessage(), 0L, "", false);
        }
    }

    private synchronized void registerAdapterStateReceiver(Context app) {
        if (adapterStateReceiver != null) return;
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                if (intent == null || !"android.nfc.action.ADAPTER_STATE_CHANGED".equals(intent.getAction())) return;
                int state = intent.getIntExtra("android.nfc.extra.ADAPTER_STATE", -1);
                if (state == 3) {
                    // Broadcast is now a fallback/secondary ready signal. The primary signals are
                    // initialize()/enableInternal() hooks, but an ON broadcast still forces a
                    // closed-loop recovery when a vendor stack does not expose those methods.
                    markControllerReady("adapter_state_on");
                } else if (state == 1 || state == 4) {
                    markControllerInvalid(state == 4 ? "adapter_turning_off" : "adapter_off");
                }
            }
        };
        IntentFilter filter = new IntentFilter("android.nfc.action.ADAPTER_STATE_CHANGED");
        if (Build.VERSION.SDK_INT >= 33) app.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        else app.registerReceiver(receiver, filter);
        adapterStateReceiver = receiver;
        Log.i(TAG, "ADAPTER STATE receiver registered pid=" + Process.myPid());
    }

    private void scheduleLifecycleRecovery(String reason) {
        lifecycleExecutor.execute(() -> runLifecycleRecovery(reason));
    }

    /**
     * NFC adapter OFF / TURNING_OFF resets the controller RF configuration but may leave the
     * com.android.nfc Java process alive. PID-based verification therefore cannot prove that the
     * previously applied NFCID1 is still present. Preserve the durable desired APPLY command and
     * selected UID, but explicitly invalidate only observed RF evidence. The next adapter ON event
     * will then run the closed-loop lifecycle recovery and require a fresh native-accepted RF write.
     */
    private void invalidateRfEvidenceForControllerReset(String reason) {
        SimConfig cfg = readConfig();
        if (!cfg.initialized || !cfg.active || cfg.generation <= 0L) return;
        long nextEpoch = Math.max(cfg.controllerEpoch + 1L, System.currentTimeMillis());
        ContentValues v = baseHookState();
        v.put("state_generation", cfg.generation);
        // controller_epoch is lifecycle metadata, not terminal command/RF evidence. ConfigProvider
        // intentionally permits it to advance after a successful APPLY. Existing RF evidence is
        // then stale because rf_controller_epoch no longer matches.
        v.put("controller_epoch", nextEpoch);
        // Epoch invalidation is a correctness barrier, not diagnostic telemetry. Commit it
        // synchronously so an immediately following ADAPTER_STATE_ON or OEM RF write cannot read
        // the previous epoch. Normal status/log writes remain asynchronous.
        if (!writeValuesSynchronously(v, 5, 25L)) {
            Log.w(TAG, "CONTROLLER EPOCH synchronous write failed reason=" + reason +
                    " generation=" + cfg.generation + " nextEpoch=" + nextEpoch);
        }
        cachedConfig = cfg.withControllerEpoch(nextEpoch);
        persistRefreshRuntime("LIFECYCLE_INVALIDATED", "", reason, cfg.generation, false);
        synchronized (this) {
            lifecycleReapplyPending = false;
            lifecycleRecoveryGeneration = Long.MIN_VALUE;
            lifecycleRecoveryStartedAt = 0L;
            lifecycleWorkerRunning = false;
            lifecycleWorkerGeneration = Long.MIN_VALUE;
            recoveryWriteArmed = false;
        }
        clearTriggerWindow(cfg.generation);
        Log.i(TAG, "CONTROLLER EPOCH advanced reason=" + reason + " generation=" + cfg.generation +
                " oldEpoch=" + cfg.controllerEpoch + " newEpoch=" + nextEpoch + " uid=" + cfg.uid +
                " pid=" + Process.myPid());
    }

    /**
     * Closed-loop lifecycle APPLY recovery. A trigger returning true is never treated as success;
     * only a rewritten RF_CONFIG_WRITE accepted by native code can move observed state to ACTIVE.
     */
    private void runLifecycleRecovery(String reason) {
        SimConfig cfg = readConfig();
        if (cfg.useVendorTypeA()) {
            Log.i(TAG, "VENDOR_TYPE_A lifecycle: RF replay skipped reason=" + reason
                    + " generation=" + cfg.generation + "; revalidation required after controller reset");
            return;
        }
        int pid = Process.myPid();
        if (!cfg.initialized || !cfg.active || cfg.uid == null || cfg.generation <= 0L ||
                cfg.handledGeneration != cfg.generation || !"SUCCESS".equals(cfg.commandStatus)) return;
        String uidHex = normalizeUid(cfg.uid);
        if (uidHex.length() != 8 && uidHex.length() != 14 && uidHex.length() != 20) return;
        if (isLifecycleVerified(cfg.generation, pid, uidHex)) {
            finishLifecycleRecovery(cfg.generation);
            return;
        }

        synchronized (this) {
            if (lifecycleWorkerRunning && lifecycleWorkerGeneration == cfg.generation) return;
            lifecycleWorkerRunning = true;
            lifecycleWorkerGeneration = cfg.generation;
            lifecycleReapplyPending = true;
            lifecycleRecoveryGeneration = cfg.generation;
            lifecycleRecoveryStartedAt = System.currentTimeMillis();
            recoveryWriteArmed = false;
        }

        persistRefreshRuntime("LIFECYCLE_REPLAY_WAIT", "", reason, cfg.generation, false);
        Log.i(TAG, "LIFECYCLE RECOVERY start reason=" + reason + " generation=" + cfg.generation +
                " uid=" + uidHex + " pid=" + pid);

        boolean replayAttempted = false;
        int fallbackAttempts = 0;
        String lastFailure = "No recovery action executed";
        long deadline = System.currentTimeMillis() + LIFECYCLE_TOTAL_TIMEOUT_MS;
        try {
            sleep(LIFECYCLE_REPLAY_DELAY_MS);
            while (System.currentTimeMillis() < deadline) {
                SimConfig latest = readConfig();
                boolean commandSucceeded = latest.initialized && latest.active &&
                        latest.generation == cfg.generation && latest.handledGeneration == latest.generation &&
                        "SUCCESS".equals(latest.commandStatus);
                boolean verified = commandSucceeded && isLifecycleVerified(cfg.generation, pid, uidHex);
                RecoveryStateMachine.Action action = RecoveryStateMachine.next(
                        new RecoveryStateMachine.Snapshot(
                                latest.initialized && latest.active,
                                commandSucceeded,
                                verified,
                                hasVerifiedReplayForCurrentProcess(),
                                replayAttempted,
                                fallbackAttempts,
                                LIFECYCLE_MAX_TRIGGER_ATTEMPTS));

                if (action == RecoveryStateMachine.Action.NONE) return;
                if (action == RecoveryStateMachine.Action.WAIT_FOR_REPLAY) break;
                if (action == RecoveryStateMachine.Action.MARK_FAILED) break;

                synchronized (this) { recoveryWriteArmed = true; }
                if (action == RecoveryStateMachine.Action.EXACT_REPLAY) {
                    replayAttempted = true;
                    boolean replayed = replayVerifiedRfInvocation(latest, uidHex);
                    lastFailure = replayed ?
                            "Exact RF replay invoked but no fresh native proof was observed" :
                            "Exact RF replay unavailable/failed";
                    if (replayed && waitForLifecycleVerified(
                            cfg.generation, pid, uidHex, LIFECYCLE_TRIGGER_WAIT_MS)) {
                        lifecycleFailureGeneration = Long.MIN_VALUE;
                        lifecycleFailureControllerEpoch = Long.MIN_VALUE;
                        return;
                    }
                } else {
                    fallbackAttempts++;
                    NfcProcessVendorController.Result trigger = triggerRfRefresh(
                            latest, true,
                            "lifecycle-fallback:" + reason + ":attempt-" + fallbackAttempts,
                            LIFECYCLE_TRIGGER_RF_WINDOW_MS);
                    lastFailure = trigger.stage + ": " + trigger.detail;
                    long remaining = Math.max(0L, deadline - System.currentTimeMillis());
                    long wait = Math.min(LIFECYCLE_TRIGGER_WAIT_MS, remaining);
                    if (wait > 0L && waitForLifecycleVerified(cfg.generation, pid, uidHex, wait)) return;
                }
                synchronized (this) { recoveryWriteArmed = false; }
            }

            // A verified invocation snapshot can arrive just after the last vendor trigger. Give
            // that exact replay path one bounded chance before publishing a user-visible failure.
            // The observed native RF proof remains the only success condition.
            if (!isLifecycleVerified(cfg.generation, pid, uidHex) && !replayAttempted &&
                    awaitLateReplayAvailability(cfg.generation, pid)) {
                SimConfig latest = readConfig();
                boolean commandStillCurrent = latest.initialized && latest.active &&
                        latest.generation == cfg.generation && latest.handledGeneration == latest.generation &&
                        "SUCCESS".equals(latest.commandStatus);
                if (!commandStillCurrent) return;
                synchronized (this) { recoveryWriteArmed = true; }
                replayAttempted = true;
                boolean replayed = replayVerifiedRfInvocation(latest, uidHex);
                lastFailure = replayed ?
                        "Late exact RF replay invoked but no fresh native proof was observed" :
                        "Late exact RF replay unavailable/failed";
                if (replayed && waitForLifecycleVerified(
                        cfg.generation, pid, uidHex, LIFECYCLE_TRIGGER_WAIT_MS)) {
                    lifecycleFailureGeneration = Long.MIN_VALUE;
                    lifecycleFailureControllerEpoch = Long.MIN_VALUE;
                    return;
                }
            }

            SimConfig finalConfig = readConfig();
            boolean commandStillCurrent = finalConfig.initialized && finalConfig.active &&
                    finalConfig.generation == cfg.generation &&
                    finalConfig.handledGeneration == finalConfig.generation &&
                    "SUCCESS".equals(finalConfig.commandStatus);
            if (commandStillCurrent && !isLifecycleVerified(cfg.generation, pid, uidHex)) {
                publishLifecycleFailure(cfg, uidHex,
                        "No verified RF_CONFIG_WRITE after controller READY; lastRecovery=" + lastFailure,
                        NativeOutcome.notInvoked());
            }
        } finally {
            synchronized (this) { recoveryWriteArmed = false; }
            if (!isLifecycleVerified(cfg.generation, pid, uidHex)) finishLifecycleRecovery(cfg.generation);
        }
    }

    private boolean awaitLateReplayAvailability(long generation, int pid) {
        long end = System.currentTimeMillis() + LIFECYCLE_FINAL_REPLAY_GRACE_MS;
        while (System.currentTimeMillis() < end) {
            if (hasVerifiedReplayForCurrentProcess()) return true;
            SimConfig latest = readConfig();
            boolean commandStillCurrent = latest.initialized && latest.active &&
                    latest.generation == generation && latest.handledGeneration == generation &&
                    "SUCCESS".equals(latest.commandStatus) && Process.myPid() == pid;
            if (!commandStillCurrent) return false;
            sleep(100L);
        }
        return hasVerifiedReplayForCurrentProcess();
    }

    private boolean waitForLifecycleVerified(long generation, int pid, String uid, long timeoutMs) {
        long end = System.currentTimeMillis() + Math.max(0L, timeoutMs);
        while (System.currentTimeMillis() < end) {
            if (isLifecycleVerified(generation, pid, uid)) return true;
            sleep(100L);
        }
        return isLifecycleVerified(generation, pid, uid);
    }

    private boolean isLifecycleVerified(long generation, int pid, String uid) {
        Context ctx = currentContext();
        if (ctx == null) return false;
        long rfGeneration = Long.MIN_VALUE;
        long controllerEpoch = 0L, rfControllerEpoch = Long.MIN_VALUE;
        int rfPid = 0;
        boolean accepted = false;
        String effective = "", confidence = "", rfUid = "";
        try (Cursor c = ctx.getContentResolver().query(HookConfigStore.activeUri(), null, null, null, null)) {
            if (c == null) return false;
            while (c.moveToNext()) {
                String key = c.getString(0), value = c.getString(1);
                if ("rf_generation".equals(key)) rfGeneration = parseLong(value, Long.MIN_VALUE);
                else if ("rf_pid".equals(key)) rfPid = (int) parseLong(value, 0L);
                else if ("rf_accepted".equals(key)) accepted = Boolean.parseBoolean(value);
                else if ("effective_state".equals(key)) effective = value == null ? "" : value;
                else if ("verification_confidence".equals(key)) confidence = value == null ? "" : value;
                else if ("rf_uid".equals(key)) rfUid = normalizeUid(value);
                else if ("controller_epoch".equals(key)) controllerEpoch = parseLong(value, 0L);
                else if ("rf_controller_epoch".equals(key)) rfControllerEpoch = parseLong(value, Long.MIN_VALUE);
            }
        } catch (Throwable ignored) { return false; }
        return rfGeneration == generation && rfPid == pid && accepted && controllerEpoch > 0L &&
                rfControllerEpoch == controllerEpoch && "ACTIVE".equals(effective) &&
                "VERIFIED".equals(confidence) && normalizeUid(uid).equals(rfUid);
    }

    private synchronized void finishLifecycleRecovery(long generation) {
        if (generation != Long.MIN_VALUE && lifecycleRecoveryGeneration != Long.MIN_VALUE &&
                lifecycleRecoveryGeneration != generation) return;
        lifecycleReapplyPending = false;
        lifecycleRecoveryGeneration = Long.MIN_VALUE;
        lifecycleRecoveryStartedAt = 0L;
        lifecycleWorkerRunning = false;
        lifecycleWorkerGeneration = Long.MIN_VALUE;
        recoveryWriteArmed = false;
        if (generation > 0L) clearTriggerWindow(generation);
    }

    private void publishLifecycleFailure(SimConfig cfg, String uid, String detail, NativeOutcome outcome) {
        persistRefreshRuntime("LIFECYCLE_FAILED", pendingTriggerTarget, "lifecycle-recovery", cfg.generation, false);
        ContentValues v = baseHookState();
        v.put("state_generation", cfg.generation);
        v.put("rf_status", "RF_LIFECYCLE_REAPPLY_FAILED");
        v.put("rf_uid", uid == null ? "" : uid);
        v.put("rf_source", "lifecycle-recovery");
        v.put("rf_result", "");
        v.put("rf_native_result", outcome == null ? "" : outcome.rawValue);
        v.put("rf_native_result_type", outcome == null ? "not-invoked" : outcome.resultType);
        v.put("rf_accepted", false);
        v.put("rf_error", detail == null ? "" : detail);
        v.put("rf_pid", Process.myPid());
        v.put("rf_generation", cfg.generation);
        v.put("rf_controller_epoch", cfg.controllerEpoch);
        v.put("rf_verification", "LIFECYCLE_REAPPLY_FAILED");
        v.put("full_diag_stage", "LIFECYCLE_REAPPLY_FAILED");
        v.put("full_diag_summary", detail == null ? "" : detail);
        lifecycleFailureGeneration = cfg.generation;
        lifecycleFailureControllerEpoch = cfg.controllerEpoch;
        writeValuesWithRetry(v, 8, 75L);
        Log.w(TAG, "LIFECYCLE RECOVERY failed generation=" + cfg.generation + " uid=" + uid + " detail=" + detail);
    }

    private void scheduleCommandRefresh(String reason) {
        if (observerRegistered) commandExecutor.execute(() -> refreshConfigAndProcess(reason));
    }

    private void refreshConfigAndProcess(String reason) {
        SimConfig cfg = readConfig();
        if (!cfg.initialized) return;
        cachedConfig = cfg;
        int pid = Process.myPid();

        // Provider is the durable command authority. A new com.android.nfc process must not replay
        // an already handled generation just because this Java instance lost its in-memory cache.
        boolean persistedTerminal = cfg.generation > 0L && cfg.handledGeneration == cfg.generation &&
                ("SUCCESS".equals(cfg.commandStatus) || "FAILED".equals(cfg.commandStatus)
                        || "NO_OVERRIDE".equals(cfg.commandStatus));
        if (persistedTerminal) {
            completedGeneration = cfg.generation;
            completedPid = pid;
            lastTriggeredGeneration = cfg.generation;
            if ("SUCCESS".equals(cfg.commandStatus) && cfg.active) {
                if (!isLifecycleVerified(cfg.generation, pid, cfg.uid)) {
                    boolean suppressSelfRetry = "provider_change".equals(reason) &&
                            lifecycleFailureGeneration == cfg.generation &&
                            lifecycleFailureControllerEpoch == cfg.controllerEpoch;
                    if (suppressSelfRetry) {
                        Log.i(TAG, "LIFECYCLE APPLY self-retry suppressed generation=" + cfg.generation +
                                " epoch=" + cfg.controllerEpoch + " pid=" + pid);
                    } else {
                        Log.i(TAG, "LIFECYCLE APPLY adoption generation=" + cfg.generation + " uid=" + cfg.uid + " pid=" + pid + " reason=" + reason);
                        scheduleLifecycleRecovery("process_" + reason);
                    }
                }
            } else {
                Log.i(TAG, "COMMAND terminal adopted without replay status=" + cfg.commandStatus +
                        " action=" + cfg.commandAction + " generation=" + cfg.generation + " pid=" + pid);
            }
            return;
        }

        if ("RESTART_REQUIRED".equals(cfg.commandStatus) &&
                cfg.consumedGeneration == cfg.generation && cfg.handledGeneration != cfg.generation) {
            return;
        }
        if (isGenerationCompleted(cfg.generation, pid) || cfg.generation == lastTriggeredGeneration) return;
        lastTriggeredGeneration = cfg.generation;

        String action = cfg.active ? "APPLY" : "STOP";
        writeSimpleCommandState("RUNNING", "Executing " + action + " inside com.android.nfc", cfg.generation, action, false);
        Log.i(TAG, "COMMAND " + action + " reason=" + reason + " generation=" + cfg.generation + " uid=" + cfg.uid + " pid=" + pid +
                " restoreMode=" + restoreMode);

        if (cfg.useVendorTypeA()) {
            runVendorTypeACommand(cfg, reason);
            return;
        }
        if (cfg.useInterceptTypeA()) {
            runFileInterceptCommand(cfg);
            return;
        }

        if (!cfg.active) {
            if (noUidWriteToRestore()) {
                completeNoOverrideStop(cfg, "No UID RF/NCI configuration was written in this NFC process; no restart required");
                return;
            }
            if (controllerReinitRequired || (nativeUidMutationAttempted && reversibleStockPayload == null)) {
                requestControllerRestart(cfg,
                        "A UID RF/NCI write was attempted without a reversible stock snapshot; NFC controller restart required");
                return;
            }
        }

        // RF UID-only is fully independent of the ColorOS Vendor backend.
        // Do not probe or call OEM Type-A/ShareMode just to start RF mode.
        NfcProcessVendorController.Result trigger = triggerRfRefresh(cfg, cfg.active, "command:" + reason);
        if (isGenerationCompleted(cfg.generation, pid)) return;
        if (trigger.success) {
            writeSimpleCommandState("TRIGGERED", trigger.detail + "; waiting for RF confirmation", cfg.generation, action, false);
            scheduleRfTriggerTimeout(cfg, pid);
        } else {
            failRfTriggerCommand(cfg, trigger);
        }
    }

    /**
     * Persist RF trigger errors as a single terminal generation update.
     * A split TRIGGER_FAILED + semantic FAILED write left command_handled_generation
     * stale, full_diag_stage stuck at COMMAND_PENDING and UI waiting indefinitely.
     */
    private void failRfTriggerCommand(SimConfig cfg, NfcProcessVendorController.Result trigger) {
        String detail = trigger.stage + ": " + trigger.detail;
        ContentValues v = baseHookState();
        v.put("state_generation", cfg.generation);
        v.put("command_consumed_generation", cfg.generation);
        v.put("command_handled_generation", cfg.generation);
        v.put("command_action", cfg.active ? "APPLY" : "STOP");
        v.put("command_status", "FAILED");
        v.put("command_detail", detail);
        v.put("command_pid", Process.myPid());
        v.put("operation_state", "FAILED");
        v.put("effective_state", "UNKNOWN");
        v.put("verification_confidence", "TRIGGER_FAILED");
        v.put("rf_accepted", false);
        v.put("rf_status", "TRIGGER_FAILED");
        v.put("rf_uid", "");
        v.put("rf_source", "rf-trigger");
        v.put("rf_pid", Process.myPid());
        v.put("rf_generation", cfg.generation);
        v.put("rf_verification", "FAILED");
        v.put("rf_error", detail);
        v.put("full_diag_stage", "TRIGGER_FAILED");
        v.put("full_diag_summary", detail);
        writeValuesWithRetry(v, 20, 100L);
        completedGeneration = cfg.generation;
        completedPid = Process.myPid();
    }

    private boolean noUidWriteToRestore() {
        return !nativeUidMutationAttempted && !vendorTypeAWriteAttempted
                && !controllerReinitRequired && reversibleStockPayload == null;
    }

    /**
     * Terminal STOP acknowledgement for a controller never modified by YNFC
     * in this process. This is NOT a VERIFIED stock RF response: it only proves
     * that stopping needs no UID rollback or NFC service restart.
     */
    private void completeNoOverrideStop(SimConfig cfg, String detail) {
        int pid = Process.myPid();
        ContentValues v = baseHookState();
        v.put("state_generation", cfg.generation);
        v.put("command_consumed_generation", cfg.generation);
        v.put("command_handled_generation", cfg.generation);
        v.put("command_action", "STOP");
        v.put("command_status", "NO_OVERRIDE");
        v.put("command_detail", detail);
        v.put("command_pid", pid);
        v.put("operation_state", "IDLE");
        v.put("effective_state", "UNKNOWN");
        v.put("verification_confidence", "NO_MODIFICATION");
        v.put("rf_status", "NO_OVERRIDE");
        v.put("rf_accepted", false);
        v.put("rf_uid", "");
        v.put("rf_source", "no-uid-write");
        v.put("rf_pid", pid);
        v.put("rf_generation", cfg.generation);
        v.put("rf_controller_epoch", cfg.controllerEpoch);
        v.put("rf_verification", "NO_UID_WRITE");
        v.put("rf_error", "");
        v.put("full_diag_stage", "STOP_NO_OVERRIDE");
        v.put("full_diag_summary", detail);
        writeValuesWithRetry(v, 20, 100L);
        completedGeneration = cfg.generation;
        completedPid = pid;
        Log.i(TAG, "STOP_NO_OVERRIDE generation=" + cfg.generation
                + " pid=" + pid + " no NFC restart");
    }

    /**
     * Opt-in ColorOS Vendor Type-A. A Vendor return value proves only acceptance
     * by the OEM API, not external RF identity. RF Hook and replay stay idle.
     * STOP always hands off to the existing controller restart/stock restoration.
     */
    private void runVendorTypeACommand(SimConfig cfg, String reason) {
        // A rejected Vendor APPLY (e.g. OEM signature permission denial) has
        // never modified Type-A hardware. Do not restart NFC merely to STOP.
        if (!cfg.active && noUidWriteToRestore()) {
            completeNoOverrideStop(cfg, "Vendor Type-A never changed the controller in this NFC process; no restart required");
            return;
        }
        byte[] uid = strictTypeAHex(cfg.uid, 4, 7, 10);
        byte[] sak = strictTypeAHex(cfg.sak, 1);
        byte[] atqa = strictTypeAHex(cfg.atqa, 2);
        String action = cfg.active ? "APPLY" : "STOP";
        if (uid == null || sak == null || atqa == null) {
            String error = "Vendor Type-A requires explicit valid UID (4/7/10 bytes), "
                    + "SAK (1 byte), and ATQA (2 bytes); source=stored card";
            Log.w(TAG, "VENDOR_TYPE_A_INVALID " + error);
            if (!cfg.active) {
                requestControllerRestart(cfg, error + "; recover stock controller");
            } else {
                failVendorTypeACommand(cfg, "TYPE_A_INVALID_PARAMETERS", error);
            }
            return;
        }

        if (!cfg.active) {
            // Never assume the vendor disable call alone restores eSE and RF
            // routing. Always restart the NFC process afterward for stock state.
            NfcProcessVendorController.Result disabled =
                    vendorController.setTypeAConfig(false, atqa, sak, uid);
            Log.i(TAG, "VENDOR_TYPE_A_STOP stage=" + disabled.stage
                    + " accepted=" + disabled.success + " reason=" + reason);
            if (!disabled.success) {
                // ColorOS persists its access-card mode. An NFC-process restart by itself
                // cannot prove that a rejected OEM disable restored the stock RF state.
                failVendorTypeACommand(cfg, "TYPE_A_DISABLE_REJECTED",
                        "ColorOS Type-A disable failed: " + disabled.stage + ": " + disabled.detail);
                return;
            }
            requestControllerRestart(cfg, "Vendor Type-A disable accepted: " + disabled.stage
                    + "; stock NFC process/controller restart required");
            return;
        }

        NfcProcessVendorController.Result capability = vendorController.probeTypeA();
        Log.i(TAG, "VENDOR_TYPE_A_DISCOVERY generation=" + cfg.generation
                + " selectedBackend=VENDOR capability=" + capability.stage
                + " methodPresent=" + capability.success + " detail=" + capability.detail);
        if (!capability.success) {
            String stage = "TYPE_A_PERMISSION_DENIED".equals(capability.stage)
                    ? "TYPE_A_PERMISSION_DENIED" : "TYPE_A_UNAVAILABLE";
            failVendorTypeACommand(cfg, stage,
                    "Vendor Type-A unavailable: " + capability.stage + ": " + capability.detail);
            return;
        }

        boolean previousAttempt = vendorTypeAWriteAttempted;
        vendorTypeAWriteAttempted = true;
        NfcProcessVendorController.Result result =
                vendorController.setTypeAConfig(true, atqa, sak, uid);
        // Permission denial occurs before an OEM configuration is dispatched.
        // Keep an older successful/uncertain attempt sticky if one existed.
        if ("TYPE_A_PERMISSION_DENIED".equals(result.stage)) {
            vendorTypeAWriteAttempted = previousAttempt;
        }
        Log.i(TAG, "VENDOR_TYPE_A_APPLY generation=" + cfg.generation
                + " stage=" + result.stage + " accepted=" + result.success
                + " uid=" + cfg.uid + " reason=" + reason);
        if (!result.success) {
            boolean permissionDenied = "TYPE_A_PERMISSION_DENIED".equals(result.stage);
            failVendorTypeACommand(cfg,
                    permissionDenied ? "TYPE_A_PERMISSION_DENIED" : "TYPE_A_REJECTED",
                    (permissionDenied ? "Vendor Type-A permission denied: " : "Vendor Type-A rejected: ")
                            + result.stage + ": " + result.detail);
            return;
        }

        ContentValues v = baseHookState();
        v.put("state_generation", cfg.generation);
        v.put("command_consumed_generation", cfg.generation);
        v.put("command_status", "VENDOR_API_ACCEPTED");
        v.put("command_detail", "OEM accepted Type-A configuration; external reader verification required");
        v.put("command_action", "APPLY");
        v.put("command_pid", Process.myPid());
        v.put("operation_state", "VERIFYING");
        v.put("effective_state", "UNKNOWN");
        v.put("verification_confidence", "UNVERIFIED");
        v.put("rf_status", "VENDOR_API_ACCEPTED");
        v.put("rf_accepted", false);
        v.put("rf_uid", "");
        v.put("rf_source", "vendor-type-a");
        v.put("rf_generation", cfg.generation);
        v.put("rf_verification", "EXTERNAL_READER_REQUIRED");
        v.put("full_diag_stage", "VENDOR_API_ACCEPTED");
        v.put("full_diag_summary", "Vendor method accepted; NFC controller/reader not verified");
        writeValuesWithRetry(v, 20, 100L);
    }

    private void failVendorTypeACommand(SimConfig cfg, String stage, String detail) {
        // Publish all terminal fields together. ConfigProvider protects terminal
        // generations against subsequent partial writes, so separate status and
        // semantic updates could leave the runtime showing stale PENDING values.
        ContentValues v = baseHookState();
        v.put("state_generation", cfg.generation);
        v.put("command_consumed_generation", cfg.generation);
        v.put("command_handled_generation", cfg.generation);
        v.put("command_action", cfg.active ? "APPLY" : "STOP");
        v.put("command_status", "FAILED");
        v.put("command_detail", detail);
        v.put("command_pid", Process.myPid());
        v.put("operation_state", "FAILED");
        v.put("effective_state", "UNKNOWN");
        v.put("verification_confidence", stage);
        v.put("rf_accepted", false);
        v.put("rf_status", stage);
        v.put("rf_uid", "");
        v.put("rf_source", "vendor-type-a");
        v.put("rf_pid", Process.myPid());
        v.put("rf_generation", cfg.generation);
        v.put("rf_error", detail);
        v.put("rf_verification", "FAILED");
        v.put("full_diag_stage", stage);
        v.put("full_diag_summary", detail);
        writeValuesWithRetry(v, 20, 100L);
    }

    private static byte[] strictTypeAHex(String raw, int... allowedBytes) {
        if (raw == null) return null;
        String hex = raw.replaceAll("[\\s:.-]", "").trim();
        if (hex.isEmpty() || !hex.matches("(?i)[0-9a-f]+") || (hex.length() & 1) != 0)
            return null;
        boolean validLength = false;
        for (int length : allowedBytes) {
            if (hex.length() == length * 2) {
                validLength = true;
                break;
            }
        }
        if (!validLength) return null;
        byte[] result = new byte[hex.length() / 2];
        for (int i = 0; i < result.length; i++)
            result[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        return result;
    }

    private synchronized void clearRestoreState() {
        // Explicit stock restoration was accepted on the same native writer.
        nativeUidMutationAttempted = false;
        reversibleStockPayload = null;
        reversibleTargetFingerprint = null;
        reversibleCapturedGeneration = Long.MIN_VALUE;
        controllerReinitRequired = false;
        restoreMode = "NONE";
        persistRestoreState();
    }

    private void persistRestoreState() {
        ContentValues v = new ContentValues();
        SimConfig cfg = cachedConfig;
        if (cfg.initialized && cfg.generation > 0L) v.put("state_generation", cfg.generation);
        v.put("rf_restore_mode", restoreMode);
        v.put("rf_controller_reinit_required", controllerReinitRequired);
        v.put("stock_snapshot_available", reversibleStockPayload != null);
        v.put("stock_snapshot_generation", reversibleCapturedGeneration == Long.MIN_VALUE ? 0L : reversibleCapturedGeneration);
        v.put("stock_snapshot_target", reversibleTargetFingerprint == null ? "" : reversibleTargetFingerprint);
        writeValuesWithRetry(v, 8, 75L);
    }

    private boolean isGenerationCompleted(long generation, int pid) {
        return completedPid == pid && completedGeneration == generation;
    }

    private SimConfig currentConfig() {
        SimConfig cfg = readConfig();
        if (cfg.initialized) cachedConfig = cfg;
        return cfg.initialized ? cfg : cachedConfig;
    }

    private SimConfig awaitEarlyConfig() {
        long end = System.currentTimeMillis() + EARLY_CONFIG_WAIT_MS;
        SimConfig cfg;
        do {
            cfg = readConfig();
            if (cfg.initialized) {
                cachedConfig = cfg;
                return cfg;
            }
            if (System.currentTimeMillis() >= end) break;
            sleep(EARLY_CONFIG_RETRY_MS);
        } while (true);
        return SimConfig.uninitialized();
    }

    private void captureRfInvocationSnapshot(Method method, Object receiver, Object[] args, int payloadArg,
                                          HookTarget target, int pid) {
        if (method == null || args == null || payloadArg < 0 || target == null) return;
        RfReplayEngine.Snapshot snapshot = replayEngine.capturePending(
                method, receiver, args, target.fingerprint(), System.currentTimeMillis(), pid);
        if (snapshot == null) return;
        synchronized (this) {
            // Keep the newest invocation from the active verified writer. During startup an OEM can
            // emit several config writes; replaying the latest one minimizes stale side effects.
            if (earlyReplayWorkerScheduled) return;
            earlyReplayWorkerScheduled = true;
        }
        Log.i(TAG, "EARLY RF REPLAY captured target=" + target.fingerprint() + " pid=" + pid +
                " payloadArg=" + payloadArg);
        earlyReplayExecutor.execute(this::runEarlyRfReplayWorker);
    }

    private void runEarlyRfReplayWorker() {
        long deadline = System.currentTimeMillis() + EARLY_REPLAY_TIMEOUT_MS;
        RfReplayEngine.Snapshot snapshot = null;
        boolean shouldFallback = false;
        try {
            while (System.currentTimeMillis() < deadline) {
                snapshot = replayEngine.pending();
                if (snapshot == null) return;
                if (snapshot.capturedPid != Process.myPid()) {
                    replayEngine.clearPending(snapshot.targetFingerprint);
                    return;
                }
                SimConfig cfg = readConfig();
                if (!cfg.initialized) {
                    sleep(EARLY_REPLAY_RETRY_MS);
                    continue;
                }
                cachedConfig = cfg;
                // Early replay is lifecycle recovery, not a second command executor. Only replay a
                // durable successful APPLY whose RF proof is stale. Pending/failed generations are
                // left to the normal command bridge so replay cannot silently change command semantics.
                boolean terminalDesiredApply = cfg.active && cfg.uid != null && cfg.generation > 0L &&
                        cfg.handledGeneration == cfg.generation && "SUCCESS".equals(cfg.commandStatus);
                if (!terminalDesiredApply) {
                    replayEngine.clearPending(snapshot.targetFingerprint);
                    return;
                }
                String uidHex = normalizeUid(cfg.uid);
                if (uidHex.length() != 8 && uidHex.length() != 14 && uidHex.length() != 20) {
                    replayEngine.clearPending(snapshot.targetFingerprint);
                    return;
                }
                if (isLifecycleVerified(cfg.generation, Process.myPid(), uidHex)) {
                    replayEngine.clearPending(snapshot.targetFingerprint);
                    return;
                }
                synchronized (this) { recoveryWriteArmed = true; }
                Log.i(TAG, "EARLY RF REPLAY invoke target=" + snapshot.targetFingerprint +
                        " generation=" + cfg.generation + " uid=" + uidHex + " pid=" + Process.myPid());
                // Exactly one replay attempt. The reflected call re-enters the installed RF hook,
                // which applies the current UID and records native/controller-epoch proof.
                RfReplayEngine.ReplayResult replay = replayEngine.invoke(snapshot);
                if (!replay.invoked && replay.error != null) {
                    Log.w(TAG, "EARLY RF REPLAY failed target=" + snapshot.targetFingerprint + " " +
                            replay.error.getClass().getSimpleName() + ": " + replay.error.getMessage());
                }
                if (waitForLifecycleVerified(cfg.generation, Process.myPid(), uidHex, LIFECYCLE_NATURAL_WAIT_MS)) {
                    replayEngine.clearPending(snapshot.targetFingerprint);
                    return;
                }
                replayEngine.clearPending(snapshot.targetFingerprint);
                shouldFallback = true;
                break;
            }
            if (replayEngine.pending() != null) {
                snapshot = replayEngine.pending();
                Log.w(TAG, "EARLY RF REPLAY config timeout target=" + snapshot.targetFingerprint +
                        " ageMs=" + (System.currentTimeMillis() - snapshot.capturedAt));
                replayEngine.clearPending(snapshot.targetFingerprint);
            }
        } finally {
            synchronized (this) {
                earlyReplayWorkerScheduled = false;
                recoveryWriteArmed = false;
            }
        }
        // Never monopolize the lifecycle executor. If the exact OEM invocation could not produce
        // fresh native proof, fall back to the existing natural-wait -> Java trigger -> vendor path.
        if (shouldFallback) scheduleLifecycleRecovery("early_rf_replay_fallback");
    }

    private void captureVerifiedRfInvocation(Method method, Object receiver, Object[] args,
                                             HookTarget target, int pid) {
        if (method == null || args == null || target == null) return;
        RfReplayEngine.Snapshot snapshot = replayEngine.captureVerified(
                method, receiver, args, target.fingerprint(), System.currentTimeMillis(), pid);
        if (snapshot == null) return;
        ContentValues v = baseHookState();
        SimConfig cfg = cachedConfig;
        if (cfg.initialized && cfg.generation > 0L) v.put("state_generation", cfg.generation);
        v.put("rf_replay_status", "CAPTURED");
        v.put("rf_replay_target", target.fingerprint());
        v.put("rf_replay_captured_at", snapshot.capturedAt);
        v.put("rf_replay_pid", pid);
        writeValuesWithRetry(v, 8, 75L);
        Log.i(TAG, "RF REPLAY captured target=" + target.fingerprint() + " pid=" + pid);
    }

    private boolean replayVerifiedRfInvocation(SimConfig cfg, String uidHex) {
        RfReplayEngine.Snapshot snapshot = replayEngine.verified(Process.myPid());
        if (snapshot == null) {
            Log.i(TAG, "RF REPLAY unavailable generation=" + cfg.generation + " pid=" + Process.myPid());
            return false;
        }
        ContentValues v = baseHookState();
        v.put("state_generation", cfg.generation);
        v.put("rf_replay_status", "INVOKING");
        v.put("rf_replay_target", snapshot.targetFingerprint);
        v.put("rf_replay_captured_at", snapshot.capturedAt);
        v.put("rf_replay_pid", snapshot.capturedPid);
        writeValuesWithRetry(v, 8, 75L);
        Log.i(TAG, "RF REPLAY invoke target=" + snapshot.targetFingerprint +
                " generation=" + cfg.generation + " uid=" + uidHex +
                " pid=" + Process.myPid());
        RfReplayEngine.ReplayResult replay = replayEngine.invoke(snapshot);
        if (replay.invoked) return true;

        Throwable cause = replay.error;
        ContentValues failure = baseHookState();
        failure.put("state_generation", cfg.generation);
        failure.put("rf_replay_status", "INVOKE_FAILED");
        failure.put("rf_replay_target", snapshot.targetFingerprint);
        failure.put("rf_replay_error", cause == null ? "unknown" :
                cause.getClass().getSimpleName() + ": " + cause.getMessage());
        writeValuesWithRetry(failure, 8, 75L);
        Log.w(TAG, "RF REPLAY failed target=" + snapshot.targetFingerprint + " " +
                (cause == null ? "unknown" : cause.getClass().getSimpleName() + ": " + cause.getMessage()));
        return false;
    }

    private Application waitForApplication(long timeoutMs) {
        long end = System.currentTimeMillis() + timeoutMs;
        Application app;
        while ((app = currentApplication()) == null && System.currentTimeMillis() < end) sleep(100L);
        return app;
    }

    private String compactCallStack(int maxFrames) {
        StringBuilder sb = new StringBuilder();
        int kept = 0;
        for (StackTraceElement e : Thread.currentThread().getStackTrace()) {
            String c = e.getClassName();
            if (c.equals(Thread.class.getName()) || c.equals(NfcInjectionModule.class.getName()) || c.startsWith("java.lang.Thread")) continue;
            if (kept++ > 0) sb.append(" <- ");
            sb.append(c).append('#').append(e.getMethodName()).append(':').append(e.getLineNumber());
            if (kept >= maxFrames) break;
        }
        return sb.length() == 0 ? "unknown" : sb.toString();
    }

    private void persistRewriteDiagnostics(RewriteResult rewritten) {
        if (rewritten == null) return;
        ContentValues v = new ContentValues();
        v.put("rf_rewrite_reason", rewritten.reason == null ? "" : rewritten.reason);
        v.put("rf_rewrite_codec", rewritten.codecId == null ? "" : rewritten.codecId);
        v.put("rf_rewrite_old_payload_len", rewritten.oldPayloadLength);
        v.put("rf_rewrite_new_payload_len", rewritten.newPayloadLength);
        v.put("rf_rewrite_old_param_count", rewritten.oldParamCount);
        v.put("rf_rewrite_new_param_count", rewritten.newParamCount);
        writeValuesWithRetry(v, 8, 75L);
    }

    private void persistRfCaller(int pid, String caller) {
        ContentValues v = new ContentValues();
        v.put("rf_caller", caller.length() > 3500 ? caller.substring(0, 3500) : caller);
        v.put("rf_caller_pid", pid);
        writeValuesWithRetry(v, 8, 100L);
    }

    private void persistDiscoveryCandidates(int pid, List<HookTarget> candidates) {
        ContentValues v = new ContentValues();
        v.put("rf_hook_candidates", candidateReport(candidates, 6000));
        v.put("rf_hook_candidate_count", candidates.size());
        v.put("rf_hook_discovery_pid", pid);
        v.put("profile_status", "LEARNING");
        if (!candidates.isEmpty()) putTarget(v, candidates.get(0));
        writeValuesWithRetry(v, 20, 100L);
    }

    private void persistTriggerCandidates(int pid, List<HookTarget> candidates) {
        ContentValues v = new ContentValues();
        v.put("refresh_probe_candidates", candidateReport(candidates, 6000));
        v.put("refresh_probe_count", candidates.size());
        v.put("refresh_probe_pid", pid);
        writeValuesWithRetry(v, 20, 100L);
    }

    private String candidateReport(List<HookTarget> candidates, int max) {
        StringBuilder report = new StringBuilder();
        for (int i = 0; i < candidates.size(); i++) {
            if (i > 0) report.append(" | ");
            report.append('#').append(i + 1).append(' ').append(candidates.get(i));
        }
        String text = report.toString();
        return text.length() > max ? text.substring(0, max) : text;
    }

    private void putTarget(ContentValues v, HookTarget t) {
        v.put("rf_hook_class", t.className);
        v.put("rf_hook_method", t.methodName);
        v.put("rf_hook_param_signature", t.parameterSignature);
        v.put("rf_hook_return_type", t.returnType);
        v.put("rf_hook_signature", t.parameterSignature + "->" + t.returnType);
        v.put("rf_hook_score", t.score);
        v.put("rf_hook_source", t.source);
        v.put("rf_hook_fingerprint", t.fingerprint());
    }

    private void persistProfileStatus(String status, HookTarget target) {
        ContentValues v = new ContentValues();
        v.put("profile_status", status);
        v.put("profile_hook_build", HOOK_BUILD);
        v.put("profile_schema", 3);
        if (target != null) putTarget(v, target);
        writeValuesWithRetry(v, 8, 75L);
    }

    private void markTargetVerified(HookTarget target) {
        activeTarget = target;
        Application app = currentApplication();
        if (app != null) profileStore.save(app, target, "VERIFIED");
        persistProfileStatus("VERIFIED", target);
    }

    private void reportStatusWithRetry(int pid, boolean ready, int count, String stage, String summary, String targetId) {
        ContentValues v = new ContentValues();
        v.put("hook_build", HOOK_BUILD);
        v.put("scope_ok", true);
        v.put("scope_process", "com.android.nfc");
        v.put("scope_pid", pid);
        v.put("hook_installed", ready);
        v.put("hook_class", "NfcInjectionModule");
        v.put("hook_count", count);
        v.put("hook_pid", pid);
        v.put("runtime_pid", pid);
        v.put("hook_runtime_stage", stage);
        v.put("hook_runtime_summary", summary);
        SimConfig cfg = readConfig();
        boolean commandOwnsSummary = cfg.initialized && cfg.generation > 0L &&
                cfg.commandStatus != null && !cfg.commandStatus.isEmpty() && !"SUCCESS".equals(cfg.commandStatus);
        if (!commandOwnsSummary) {
            v.put("full_diag_stage", stage);
            v.put("full_diag_summary", summary);
        }
        if (targetId != null) v.put("rf_hook_fingerprint", targetId);
        writeValuesWithRetry(v, 20, 150L);
    }

    private void writeRfProgress(SimConfig cfg, String state, String uid, String detail, String codecId) {
        ContentValues v = baseHookState();
        v.put("state_generation", cfg.generation);
        v.put("rf_status", state);
        v.put("rf_uid", uid == null ? "" : uid);
        v.put("rf_source", codecId == null ? "" : codecId);
        v.put("rf_result", "");
        v.put("rf_native_result", "");
        v.put("rf_native_result_type", "");
        v.put("rf_accepted", false);
        v.put("rf_error", state.contains("FAILED") ? detail : "");
        v.put("rf_pid", Process.myPid());
        v.put("rf_generation", cfg.generation);
        v.put("rf_controller_epoch", cfg.controllerEpoch);
        v.put("rf_verification", state.equals("APPLYING") ? "CONFIG_WRITE_PENDING" : "LIFECYCLE_PENDING");
        v.put("operation_state", operationForRfState(state));
        v.put("effective_state", "UNKNOWN");
        v.put("verification_confidence", state.equals("APPLYING") ? "CONFIG_PENDING" : "LIFECYCLE_PENDING");
        v.put("full_diag_stage", state);
        v.put("full_diag_summary", detail == null ? "" : detail);
        writeValuesWithRetry(v, 20, 100L);
    }

    private void completeCommand(SimConfig cfg, String rfState, String uid, String source, NativeOutcome outcome, String detail) {
        String verification = confirmedTriggerGeneration == cfg.generation ?
                "TRIGGER_CONFIRMED_NATIVE_RESULT" : "NATIVE_RESULT";
        completeCommandWithVerification(cfg, rfState, uid, source, outcome, detail, verification);
        clearTriggerWindow(cfg.generation);
    }

    private void completeLifecycleReapply(SimConfig cfg, String uid, String source,
                                          NativeOutcome outcome, HookTarget target) {
        ContentValues v = baseHookState();
        v.put("state_generation", cfg.generation);
        v.put("rf_status", "RF_UID_APPLIED");
        v.put("rf_uid", uid);
        v.put("rf_source", "lifecycle-replay|" + (source == null ? "" : source));
        v.put("rf_result", outcome.rawValue);
        v.put("rf_native_result", outcome.rawValue);
        v.put("rf_native_result_type", outcome.resultType);
        v.put("rf_accepted", outcome.accepted);
        v.put("rf_error", "");
        v.put("rf_pid", Process.myPid());
        v.put("rf_generation", cfg.generation);
        v.put("rf_controller_epoch", cfg.controllerEpoch);
        v.put("rf_verification", "LIFECYCLE_REPLAY_NATIVE_RESULT");
        v.put("operation_state", "IDLE");
        v.put("effective_state", "ACTIVE");
        v.put("verification_confidence", "VERIFIED");
        v.put("full_diag_stage", "LIFECYCLE_REPLAY_SUCCESS");
        v.put("full_diag_summary", "Saved UID replayed after controller READY via " +
                target.className + "#" + target.methodName);
        writeValuesWithRetry(v, 20, 100L);
        clearTriggerWindow(cfg.generation);
        Log.i(TAG, "LIFECYCLE REPLAY success generation=" + cfg.generation + " uid=" + uid +
                " target=" + target.fingerprint() + " pid=" + Process.myPid());
    }

    private void completeControllerReinit(SimConfig cfg, String detail) {
        completeCommandWithVerification(cfg, "RF_STOCK_RESTORED_BY_RESTART", "", "controller-reinit",
                NativeOutcome.lifecycleAccepted("controller-reinit"),
                detail + "; appended LA_NFCID1 cleared by NFC controller close/init", "PROCESS_RESTART");
    }

    private void completeCommandWithVerification(SimConfig cfg, String rfState, String uid, String source,
                                                 NativeOutcome outcome, String detail, String verification) {
        int pid = Process.myPid();
        completedGeneration = cfg.generation;
        completedPid = pid;
        ContentValues v = baseHookState();
        v.put("state_generation", cfg.generation);
        v.put("rf_status", rfState);
        v.put("rf_uid", uid == null ? "" : uid);
        v.put("rf_source", source == null ? "" : source);
        v.put("rf_result", outcome.accepted ? "0" : "");
        v.put("rf_native_result", outcome.rawValue);
        v.put("rf_native_result_type", outcome.resultType);
        v.put("rf_accepted", outcome.accepted);
        v.put("rf_error", "");
        v.put("rf_pid", pid);
        v.put("rf_generation", cfg.generation);
        v.put("rf_controller_epoch", cfg.controllerEpoch);
        v.put("rf_verification", verification);
        v.put("operation_state", "IDLE");
        v.put("effective_state", cfg.active ? "ACTIVE" : "STOCK");
        v.put("verification_confidence", "VERIFIED");
        v.put("command_consumed_generation", cfg.generation);
        v.put("command_handled_generation", cfg.generation);
        v.put("command_action", cfg.active ? "APPLY" : "STOP");
        v.put("command_status", "SUCCESS");
        v.put("command_detail", detail == null ? "" : detail);
        v.put("command_pid", pid);
        v.put("full_diag_stage", rfState);
        v.put("full_diag_summary", detail == null ? "" : detail);
        writeValuesWithRetry(v, 20, 100L);
    }

    private void requestControllerRestart(SimConfig cfg, String detail) {
        int pid = Process.myPid();
        ContentValues v = baseHookState();
        v.put("state_generation", cfg.generation);
        v.put("command_consumed_generation", cfg.generation);
        v.put("command_action", "STOP");
        v.put("command_status", "RESTART_REQUIRED");
        v.put("command_detail", detail == null ? "Controller restart required" : detail);
        v.put("command_pid", pid);
        v.put("rf_status", "RF_CONTROLLER_RESTART_REQUIRED");
        v.put("rf_uid", "");
        v.put("rf_source", activeCodec == null ? "" : activeCodec);
        v.put("rf_result", "");
        v.put("rf_native_result", "");
        v.put("rf_native_result_type", "not-invoked");
        v.put("rf_accepted", false);
        v.put("rf_error", "");
        v.put("rf_pid", pid);
        v.put("rf_generation", cfg.generation);
        v.put("rf_verification", "LIFECYCLE_PENDING");
        v.put("operation_state", "RESETTING_CONTROLLER");
        v.put("effective_state", "UNKNOWN");
        v.put("verification_confidence", "LIFECYCLE_PENDING");
        v.put("full_diag_stage", "RF_CONTROLLER_RESTART_REQUIRED");
        v.put("full_diag_summary", detail == null ? "Controller restart required" : detail);
        writeValuesWithRetry(v, 20, 100L);
    }

    private void failCommand(SimConfig cfg, String rfState, String uid, String detail, NativeOutcome outcome) {
        int pid = Process.myPid();
        completedGeneration = cfg.generation;
        completedPid = pid;
        ContentValues v = baseHookState();
        v.put("state_generation", cfg.generation);
        v.put("rf_status", rfState);
        v.put("rf_uid", uid == null ? "" : uid);
        v.put("rf_source", activeCodec == null ? "" : activeCodec);
        v.put("rf_result", "");
        v.put("rf_native_result", outcome.rawValue);
        v.put("rf_native_result_type", outcome.resultType);
        v.put("rf_accepted", false);
        v.put("rf_error", detail == null ? "" : detail);
        v.put("rf_pid", pid);
        v.put("rf_generation", cfg.generation);
        v.put("rf_verification", "FAILED");
        v.put("operation_state", "FAILED");
        v.put("effective_state", "UNKNOWN");
        v.put("verification_confidence", "FAILED");
        v.put("command_consumed_generation", cfg.generation);
        v.put("command_handled_generation", cfg.generation);
        v.put("command_action", cfg.active ? "APPLY" : "STOP");
        v.put("command_status", "FAILED");
        v.put("command_detail", detail == null ? "" : detail);
        v.put("command_pid", pid);
        v.put("full_diag_stage", rfState);
        v.put("full_diag_summary", detail == null ? "" : detail);
        writeValuesWithRetry(v, 20, 100L);
    }

    private ContentValues baseHookState() {
        int pid = Process.myPid();
        ContentValues v = new ContentValues();
        v.put("hook_build", HOOK_BUILD);
        v.put("scope_ok", true);
        v.put("scope_process", "com.android.nfc");
        v.put("scope_pid", pid);
        v.put("hook_installed", installedHookCount > 0);
        v.put("hook_class", "NfcInjectionModule");
        v.put("hook_count", installedHookCount);
        v.put("hook_pid", pid);
        v.put("runtime_pid", pid);
        if (activeTarget != null) putTarget(v, activeTarget);
        // controller_epoch is authoritative lifecycle metadata. Never copy it from cachedConfig
        // into generic asynchronous status writes, because a late status write must not roll back
        // a newer adapter-reset epoch. Lifecycle/RF paths write epochs explicitly.
        v.put("rf_restore_mode", restoreMode);
        v.put("rf_controller_reinit_required", controllerReinitRequired);
        v.put("stock_snapshot_available", reversibleStockPayload != null);
        v.put("stock_snapshot_generation", reversibleCapturedGeneration == Long.MIN_VALUE ? 0L : reversibleCapturedGeneration);
        v.put("stock_snapshot_target", reversibleTargetFingerprint == null ? "" : reversibleTargetFingerprint);
        return v;
    }

    private void writeSimpleCommandState(String status, String detail, long generation, String action, boolean handled) {
        ContentValues v = baseHookState();
        if (generation > 0L) v.put("state_generation", generation);
        v.put("command_status", status);
        v.put("command_detail", detail == null ? "" : detail);
        v.put("command_pid", Process.myPid());
        v.put("command_action", action == null ? "" : action);
        if ("RUNNING".equals(status) || "TRIGGERED".equals(status)) {
            v.put("command_consumed_generation", generation);
            v.put("operation_state", "APPLY".equals(action) ? "APPLYING" : "STOPPING");
            v.put("verification_confidence", "PENDING");
        }
        if (handled) {
            v.put("command_consumed_generation", generation);
            v.put("command_handled_generation", generation);
        }
        writeValuesWithRetry(v, 20, 100L);
    }

    private void writeSemanticState(String operation, String effective, String confidence,
                                    boolean accepted, NativeOutcome outcome) {
        ContentValues v = baseHookState();
        SimConfig cfg = cachedConfig;
        if (cfg.initialized && cfg.generation > 0L) v.put("state_generation", cfg.generation);
        v.put("operation_state", operation);
        v.put("effective_state", effective);
        v.put("verification_confidence", confidence);
        v.put("rf_accepted", accepted);
        if (outcome != null) {
            v.put("rf_native_result", outcome.rawValue);
            v.put("rf_native_result_type", outcome.resultType);
        }
        writeValuesWithRetry(v, 8, 75L);
    }

    private static String operationForRfState(String state) {
        if ("APPLYING".equals(state) || "WAITING".equals(state)) return "APPLYING";
        if ("STOPPING".equals(state)) return "STOPPING";
        if (state != null && state.contains("FAILED")) return "FAILED";
        return "IDLE";
    }

    private void writeValuesWithRetry(ContentValues values, int attempts, long delayMs) {
        stateWriter.writeAsync(values, attempts, delayMs);
    }

    private boolean writeValuesSynchronously(ContentValues values, int attempts, long delayMs) {
        return stateWriter.writeSynchronously(values, attempts, delayMs);
    }

    private SimConfig readConfig() {
        return configStore.read();
    }

}
