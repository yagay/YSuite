package com.yagay.YNotify.util;

import android.content.ComponentName;
import android.content.Context;
import android.provider.Settings;
import android.text.TextUtils;

import com.yagay.YNotify.collector.NotificationCaptureService;
import com.yagay.YNotify.collector.UiAccessibilityService;

/** Exact component-level grant checks for standalone and merged YSuite hosts. */
public final class ServiceGrantStatus {
    private static final String ENABLED_NOTIFICATION_LISTENERS = "enabled_notification_listeners";
    private static final String SUITE_PACKAGE = "com.yagay.YSuite";
    private static final String SUITE_ACCESSIBILITY_CLASS =
            "com.yagay.YSuite.accessibility.SuiteAccessibilityService";
    private static final String SUITE_NOTIFICATION_LISTENER_CLASS =
            "com.yagay.YSuite.notification.SuiteNotificationListenerService";
    private static final String YFLOAT_ACCESSIBILITY_CLASS =
            "com.yagay.YFloat.LensAccessibilityService";

    private ServiceGrantStatus() {}

    public static ComponentName notificationListenerComponent(Context context) {
        if (context != null && SUITE_PACKAGE.equals(context.getPackageName())) {
            return new ComponentName(SUITE_PACKAGE, SUITE_NOTIFICATION_LISTENER_CLASS);
        }
        return new ComponentName(context, NotificationCaptureService.class);
    }

    public static boolean notificationListenerEnabled(Context context) {
        String raw = Settings.Secure.getString(
                context.getContentResolver(), ENABLED_NOTIFICATION_LISTENERS);
        return containsComponent(raw, notificationListenerComponent(context));
    }

    public static ComponentName accessibilityComponent(Context context) {
        if (context != null && SUITE_PACKAGE.equals(context.getPackageName())) {
            return new ComponentName(SUITE_PACKAGE, SUITE_ACCESSIBILITY_CLASS);
        }
        return new ComponentName(context, UiAccessibilityService.class);
    }

    public static boolean accessibilityEnabled(Context context) {
        String raw = Settings.Secure.getString(
                context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return containsComponent(raw, accessibilityComponent(context));
    }

    public static boolean otherHostNotificationListenerEnabled(Context context) {
        String raw = Settings.Secure.getString(
                context.getContentResolver(), ENABLED_NOTIFICATION_LISTENERS);
        ComponentName expected = notificationListenerComponent(context);
        if (raw == null || raw.isBlank()) return false;
        TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(':');
        splitter.setString(raw);
        for (String flat : splitter) {
            ComponentName component = ComponentName.unflattenFromString(flat);
            if (component == null || expected.equals(component)) continue;
            if (!expected.getPackageName().equals(component.getPackageName())
                    && isKnownNotificationListener(component)) {
                return true;
            }
        }
        return false;
    }

    public static boolean otherHostAccessibilityEnabled(Context context) {
        String raw = Settings.Secure.getString(
                context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        ComponentName expected = accessibilityComponent(context);
        if (raw == null || raw.isBlank()) return false;
        TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(':');
        splitter.setString(raw);
        for (String flat : splitter) {
            ComponentName component = ComponentName.unflattenFromString(flat);
            if (component == null || expected.equals(component)) continue;
            if (!expected.getPackageName().equals(component.getPackageName())
                    && isKnownYNotifyAccessibility(component)) {
                return true;
            }
        }
        return false;
    }

    /** Old per-feature YSuite accessibility grants that should be migrated to the shared service. */
    public static boolean legacySuiteAccessibilityEnabled(Context context) {
        if (context == null || !SUITE_PACKAGE.equals(context.getPackageName())) return false;
        String raw = Settings.Secure.getString(
                context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (raw == null || raw.isBlank()) return false;
        ComponentName expected = accessibilityComponent(context);
        TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(':');
        splitter.setString(raw);
        for (String flat : splitter) {
            ComponentName component = ComponentName.unflattenFromString(flat);
            if (component == null || expected.equals(component)) continue;
            if (!SUITE_PACKAGE.equals(component.getPackageName())) continue;
            String cls = component.getClassName();
            if (UiAccessibilityService.class.getName().equals(cls)
                    || YFLOAT_ACCESSIBILITY_CLASS.equals(cls)) {
                return true;
            }
        }
        return false;
    }

    /** Old YNotify listener grant from pre-unification YSuite builds. */
    public static boolean legacySuiteNotificationListenerEnabled(Context context) {
        if (context == null || !SUITE_PACKAGE.equals(context.getPackageName())) return false;
        String raw = Settings.Secure.getString(
                context.getContentResolver(), ENABLED_NOTIFICATION_LISTENERS);
        if (raw == null || raw.isBlank()) return false;
        ComponentName expected = notificationListenerComponent(context);
        TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(':');
        splitter.setString(raw);
        for (String flat : splitter) {
            ComponentName component = ComponentName.unflattenFromString(flat);
            if (component == null || expected.equals(component)) continue;
            if (SUITE_PACKAGE.equals(component.getPackageName())
                    && NotificationCaptureService.class.getName().equals(component.getClassName())) {
                return true;
            }
        }
        return false;
    }

    static boolean containsComponent(String raw, ComponentName expected) {
        if (raw == null || raw.isBlank() || expected == null) return false;
        TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(':');
        splitter.setString(raw);
        for (String flat : splitter) {
            ComponentName component = ComponentName.unflattenFromString(flat);
            if (expected.equals(component)) return true;
        }
        return false;
    }

    private static boolean isKnownNotificationListener(ComponentName component) {
        if (component == null) return false;
        String cls = component.getClassName();
        return NotificationCaptureService.class.getName().equals(cls)
                || SUITE_NOTIFICATION_LISTENER_CLASS.equals(cls);
    }

    private static boolean isKnownYNotifyAccessibility(ComponentName component) {
        if (component == null) return false;
        String cls = component.getClassName();
        return UiAccessibilityService.class.getName().equals(cls)
                || SUITE_ACCESSIBILITY_CLASS.equals(cls);
    }
}
