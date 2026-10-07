package com.yagay.YFloat;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.GestureDescription;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Path;
import android.graphics.Rect;
import android.hardware.HardwareBuffer;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Display;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * YFloat Accessibility host.
 *
 * This service owns Android capabilities only: overlay token, environment observation, global
 * actions, gestures and screenshot capture. Screen-candidate semantics live exclusively in
 * AccessibilityCandidateCollector/AccessibilityNodeSemantics.
 */
public class LensAccessibilityService extends AccessibilityService {
    private static volatile LensAccessibilityService s;
    private static final long ENV_INSPECT_MIN_MS = 180L;
    /**
     * Accessibility package transitions are not a reliable task-switch boundary. Some apps briefly
     * become the reported package while Google's frozen CTS surface is still the active window.
     * Re-check the active/focused window after this grace period before tearing the session down.
     */
    private static final long GOOGLE_EXIT_GRACE_MS = 320L;

    private volatile EnvironmentState env = new EnvironmentState("", false, 0, true, false, false);
    private final Set<String> homePackages = new HashSet<>();
    private final Handler main = new Handler(Looper.getMainLooper());
    private long lastEnvironmentInspectAt;
    private Runnable pendingGoogleExit;
    private int googleExitGeneration;

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        s = this;
        AccessibilityState.notifyServiceStateChanged();
        try {
            AccessibilityServiceInfo info = getServiceInfo();
            if (info != null) {
                info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
                        | AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
                        | AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;
                setServiceInfo(info);
            }
        } catch (Throwable t) {
            DiagnosticLog.i(this, "ACCESSIBILITY", "setServiceInfo flags failed=" + t);
        }
        // Do not compete with the host Activity's first frame. When accessibility was already
        // enabled before an app update, Android can reconnect this service while MainActivity is
        // being created. Window-tree inspection and launcher queries are intentionally deferred.
        main.postDelayed(() -> {
            if (s != this) return;
            try { refreshHomePackages(); }
            catch (Throwable t) {
                DiagnosticLog.i(this, "ACCESSIBILITY", "home package query failed=" + t);
            }
            try {
                env = inspect(env.topPackage());
                publishEnvironment();
            } catch (Throwable t) {
                DiagnosticLog.i(this, "ACCESSIBILITY", "publish on connect failed=" + t);
            }
        }, 750L);
        FloatService service = FloatService.get();
        if (service != null) service.onAccessibilityOverlayHostChanged(true);
        OverlayRegistry.onAccessibilityHostChanged(true);
    }

    @Override public boolean onUnbind(Intent intent) {
        cancelPendingGoogleExit("service_unbind");
        FloatService service = FloatService.get();
        if (service != null) service.onAccessibilityOverlayHostChanged(false);
        OverlayRegistry.onAccessibilityHostChanged(false);
        FlSystemPanelController.onAccessibilityDisconnected(this);
        if (s == this) s = null;
        AccessibilityState.notifyServiceStateChanged();
        return super.onUnbind(intent);
    }

    @Override public void onDestroy() {
        cancelPendingGoogleExit("service_destroy");
        FloatService service = FloatService.get();
        if (service != null) service.onAccessibilityOverlayHostChanged(false);
        OverlayRegistry.onAccessibilityHostChanged(false);
        FlSystemPanelController.onAccessibilityDisconnected(this);
        if (s == this) s = null;
        AccessibilityState.notifyServiceStateChanged();
        super.onDestroy();
    }

    /** Host an overlay with the AccessibilityService window token when available. */
    public boolean addAccessibilityOverlay(View view, WindowManager.LayoutParams lp) {
        if (view == null || lp == null) return false;
        try {
            lp.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
            ((WindowManager) getSystemService(WINDOW_SERVICE)).addView(view, lp);
            DiagnosticLog.i(this, "FL_WINDOW", "add accessibility overlay type=" + lp.type);
            return true;
        } catch (Throwable t) {
            DiagnosticLog.i(this, "FL_WINDOW", "add accessibility overlay failed=" + t);
            return false;
        }
    }

    public boolean updateAccessibilityOverlay(View view, WindowManager.LayoutParams lp) {
        if (view == null || lp == null) return false;
        try {
            lp.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
            ((WindowManager) getSystemService(WINDOW_SERVICE)).updateViewLayout(view, lp);
            return true;
        } catch (Throwable t) {
            DiagnosticLog.i(this, "FL_WINDOW", "update accessibility overlay failed=" + t);
            return false;
        }
    }

    public boolean removeAccessibilityOverlay(View view) {
        if (view == null) return true;
        try {
            ((WindowManager) getSystemService(WINDOW_SERVICE)).removeViewImmediate(view);
            boolean detached = !view.isAttachedToWindow();
            DiagnosticLog.i(this, "FL_WINDOW",
                    "remove accessibility overlay detached=" + detached);
            return detached;
        } catch (Throwable t) {
            DiagnosticLog.i(this, "FL_WINDOW",
                    "remove accessibility overlay failed=" + t
                            + " attached=" + view.isAttachedToWindow());
            return false;
        }
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        try {
            String eventPkg = eventPackage(event);
            String eventCls = eventClass(event);
            if (GoogleCtsContract.GOOGLE_PACKAGE.equals(eventPkg)) {
                cancelPendingGoogleExit("google_window_returned");
            }

            // Only a live Google CTS workflow needs navigation release handling. Home/launcher
            // window churn outside a CTS session must not repeatedly clear bridge state or logs.
            if (WorkflowSessionManager.googleCtsInFlight() && isSystemNavigationEvent(event)) {
                DiagnosticLog.i(this, "CIRCLE_SELECT",
                        "navigation candidate pkg=" + eventPkg + " cls=" + eventCls);
                scheduleGoogleCtsRelease("system_navigation", eventPkg, eventCls);
            }

            String oldTop = env.topPackage();
            String top = oldTop;
            if (event != null && event.getPackageName() != null) {
                String pkg = event.getPackageName().toString();
                if (!pkg.equals(getPackageName()) && !pkg.equals("com.android.systemui")) top = pkg;
            }
            boolean topChanged = !top.equals(oldTop);

            if (topChanged
                    && WorkflowSessionManager.googleCtsInFlight()
                    && GoogleCtsContract.GOOGLE_PACKAGE.equals(oldTop)
                    && !GoogleCtsContract.GOOGLE_PACKAGE.equals(top)
                    && !getPackageName().equals(top)
                    && !"com.android.systemui".equals(top)) {
                scheduleGoogleCtsRelease("top_package_changed", top, eventCls);
            }

            long now = SystemClock.uptimeMillis();
            if (!topChanged && now - lastEnvironmentInspectAt < ENV_INSPECT_MIN_MS) return;
            lastEnvironmentInspectAt = now;
            EnvironmentState next = inspect(top);
            if (sameEnvironment(env, next)) return;
            env = next;
            publishEnvironment();
        } catch (Throwable t) {
            DiagnosticLog.i(this, "ACCESSIBILITY", "event failed=" + t);
        }
    }

    private void scheduleGoogleCtsRelease(String reason, String observedPkg, String observedCls) {
        if (!WorkflowSessionManager.googleCtsInFlight()) return;
        cancelPendingGoogleExit(null);
        final int generation = ++googleExitGeneration;
        final Runnable[] holder = new Runnable[1];
        holder[0] = () -> {
            if (pendingGoogleExit != holder[0] || generation != googleExitGeneration) return;
            pendingGoogleExit = null;
            if (!WorkflowSessionManager.googleCtsInFlight()) return;

            String activePkg = activeWindowPackage();
            String settledPkg = activePkg == null || activePkg.isBlank()
                    ? env.topPackage() : activePkg;
            if (GoogleCtsContract.GOOGLE_PACKAGE.equals(settledPkg)) {
                DiagnosticLog.i(this, "GOOGLE_CTS_RELEASE_GUARD",
                        "cancelled reason=" + reason
                                + " observed=" + observedPkg
                                + " active=" + settledPkg
                                + " graceMs=" + GOOGLE_EXIT_GRACE_MS);
                return;
            }

            new FloatSettings(this).clearGoogleCtsSession();
            boolean remoteCleared = LsposedStatusManager.clearGoogleCtsSessionRemoteNow();
            DiagnosticLog.i(this, "GOOGLE_CTS_NATIVE_RELEASE",
                    "reason=" + reason + " remoteCleared=" + remoteCleared
                            + " observed=" + observedPkg + " cls=" + observedCls
                            + " settled=" + settledPkg
                            + " graceMs=" + GOOGLE_EXIT_GRACE_MS);
            GoogleCtsBridgeController.onNativeRelease(this, reason);
            FLCircleInlineOverlay.dismissActive(reason);
        };
        pendingGoogleExit = holder[0];
        main.postDelayed(holder[0], GOOGLE_EXIT_GRACE_MS);
    }

    private void cancelPendingGoogleExit(String reason) {
        Runnable pending = pendingGoogleExit;
        pendingGoogleExit = null;
        googleExitGeneration++;
        if (pending != null) {
            main.removeCallbacks(pending);
            if (reason != null && !reason.isBlank()) {
                DiagnosticLog.i(this, "GOOGLE_CTS_RELEASE_GUARD",
                        "pending release cancelled reason=" + reason);
            }
        }
    }

    /** Return the package owning the currently active/focused application window, if available. */
    private String activeWindowPackage() {
        try {
            List<AccessibilityWindowInfo> windows = getWindows();
            if (windows == null) return "";
            String focusedFallback = "";
            for (AccessibilityWindowInfo window : windows) {
                if (window == null || (!window.isActive() && !window.isFocused())) continue;
                AccessibilityNodeInfo root = null;
                try {
                    root = window.getRoot();
                    if (root == null || root.getPackageName() == null) continue;
                    String pkg = root.getPackageName().toString();
                    if (pkg.isBlank() || "com.android.systemui".equals(pkg)
                            || getPackageName().equals(pkg)) continue;
                    if (window.isActive()) return pkg;
                    if (focusedFallback.isBlank()) focusedFallback = pkg;
                } catch (Throwable ignored) {
                } finally {
                    try { if (root != null) root.recycle(); } catch (Throwable ignored) {}
                }
            }
            return focusedFallback;
        } catch (Throwable ignored) {
            return "";
        }
    }

    private void refreshHomePackages() {
        homePackages.clear();
        Intent home = new Intent(Intent.ACTION_MAIN);
        home.addCategory(Intent.CATEGORY_HOME);
        PackageManager pm = getPackageManager();
        try {
            ResolveInfo resolved = pm.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
            if (resolved != null && resolved.activityInfo != null
                    && resolved.activityInfo.packageName != null) {
                String pkg = resolved.activityInfo.packageName;
                if (!pkg.equals("android")) homePackages.add(pkg);
            }
        } catch (Throwable ignored) {}
        try {
            List<ResolveInfo> homes = pm.queryIntentActivities(home, PackageManager.MATCH_DEFAULT_ONLY);
            if (homes != null) {
                for (ResolveInfo info : homes) {
                    if (info == null || info.activityInfo == null || info.activityInfo.packageName == null) continue;
                    String pkg = info.activityInfo.packageName;
                    if (!pkg.equals("android")) homePackages.add(pkg);
                }
            }
        } catch (Throwable ignored) {}
        DiagnosticLog.i(this, "ACCESSIBILITY", "home packages=" + homePackages);
    }

    private boolean isSystemNavigationEvent(AccessibilityEvent event) {
        if (event == null) return false;
        int type = event.getEventType();
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && type != AccessibilityEvent.TYPE_WINDOWS_CHANGED) return false;
        String pkg = eventPackage(event);
        if (pkg == null || pkg.isBlank() || getPackageName().equals(pkg)) return false;
        String cls = eventClass(event);
        return isHomePackage(pkg) || isRecentsWindow(pkg, cls);
    }

    private boolean isHomePackage(String pkg) {
        if (pkg == null || pkg.isBlank()) return false;
        if (homePackages.isEmpty()) {
            try { refreshHomePackages(); } catch (Throwable ignored) {}
        }
        return homePackages.contains(pkg);
    }

    private boolean isRecentsWindow(String pkg, String cls) {
        String p = pkg == null ? "" : pkg.toLowerCase(Locale.ROOT);
        String c = cls == null ? "" : cls.toLowerCase(Locale.ROOT);
        boolean recentsName = c.contains("recents") || c.contains("overview")
                || c.contains("recenttask") || c.contains("taskoverview")
                || c.contains("task_switch") || c.contains("taskswitch");
        if (!recentsName) return false;
        return "com.android.systemui".equals(p) || isHomePackage(pkg)
                || p.contains("launcher") || p.contains("quickstep") || p.contains("systemui");
    }

    private String eventPackage(AccessibilityEvent event) {
        try {
            return event != null && event.getPackageName() != null
                    ? event.getPackageName().toString() : "";
        } catch (Throwable ignored) { return ""; }
    }

    private String eventClass(AccessibilityEvent event) {
        try {
            return event != null && event.getClassName() != null
                    ? event.getClassName().toString() : "";
        } catch (Throwable ignored) { return ""; }
    }

    private boolean sameEnvironment(EnvironmentState a, EnvironmentState b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return a.topPackage().equals(b.topPackage())
                && a.imeVisible() == b.imeVisible()
                && a.imeTopPx() == b.imeTopPx()
                && a.statusBarVisible() == b.statusBarVisible()
                && a.notificationExpanded() == b.notificationExpanded()
                && a.fullscreen() == b.fullscreen();
    }

    private EnvironmentState inspect(String top) {
        boolean ime = false;
        boolean status = false;
        boolean shade = false;
        int imeTop = 0;
        Rect display = ScreenGeometry.displayBounds(this);
        int screenHeight = display.isEmpty() ? 1 : display.height();
        try {
            List<AccessibilityWindowInfo> windows = getWindows();
            if (windows != null) {
                for (AccessibilityWindowInfo window : windows) {
                    if (window == null) continue;
                    Rect bounds = new Rect();
                    window.getBoundsInScreen(bounds);
                    String pkg = "";
                    try {
                        AccessibilityNodeInfo root = window.getRoot();
                        if (root != null && root.getPackageName() != null) {
                            pkg = root.getPackageName().toString();
                        }
                    } catch (Throwable ignored) {}
                    if (window.getType() == AccessibilityWindowInfo.TYPE_INPUT_METHOD) {
                        ime = true;
                        if (imeTop == 0 || bounds.top < imeTop) imeTop = bounds.top;
                    }
                    if ("com.android.systemui".equals(pkg)) {
                        if (bounds.height() <= Math.max(200, screenHeight / 5)
                                && bounds.top <= screenHeight / 10) status = true;
                        if ((window.isActive() || window.isFocused())
                                && bounds.height() > screenHeight * 0.45f) shade = true;
                    }
                }
            }
        } catch (Throwable ignored) {}
        boolean fullscreen = !status && !shade && top != null && !top.isBlank();
        return new EnvironmentState(top == null ? "" : top,
                ime, imeTop, status, shade, fullscreen);
    }

    public Rect screenBounds() { return currentScreenBounds(); }

    private Rect currentScreenBounds() {
        Rect bounds = ScreenGeometry.displayBounds(this);
        return bounds.isEmpty()
                ? new Rect(0, 0, Integer.MAX_VALUE / 4, Integer.MAX_VALUE / 4)
                : bounds;
    }

    private String nodePackage(AccessibilityNodeInfo node) {
        return AccessibilityNodeSemantics.packageName(node);
    }

    private void publishEnvironment() {
        FlSystemPanelController.onAccessibilityEnvironment(this, env);
        FloatService service = FloatService.get();
        if (service != null) service.onAccessibilityEnvironment(env);
    }

    @Override public void onInterrupt() {}

    public static LensAccessibilityService get() { return s; }
    public static boolean ready() { return s != null; }
    public boolean global(int action) { return performGlobalAction(action); }
    public EnvironmentState environment() { return env; }

    public boolean tap(float x, float y) {
        try {
            Path path = new Path();
            path.moveTo(x, y);
            GestureDescription gesture = new GestureDescription.Builder()
                    .addStroke(new GestureDescription.StrokeDescription(path, 0, 50))
                    .build();
            return dispatchGesture(gesture, null, null);
        } catch (Throwable t) {
            return false;
        }
    }

    public void capture(Consumer<Bitmap> ok, Consumer<Throwable> fail) {
        Executor executor = getMainExecutor();
        takeScreenshot(Display.DEFAULT_DISPLAY, executor, new TakeScreenshotCallback() {
            @Override public void onSuccess(ScreenshotResult result) {
                try (HardwareBuffer buffer = result.getHardwareBuffer()) {
                    Bitmap hardware = Bitmap.wrapHardwareBuffer(buffer, result.getColorSpace());
                    if (hardware == null) {
                        throw new IllegalStateException("wrapHardwareBuffer returned null");
                    }
                    ok.accept(hardware.copy(Bitmap.Config.ARGB_8888, false));
                } catch (Throwable t) {
                    fail.accept(t);
                }
            }

            @Override public void onFailure(int errorCode) {
                fail.accept(new IllegalStateException("takeScreenshot error=" + errorCode));
            }
        });
    }
}
