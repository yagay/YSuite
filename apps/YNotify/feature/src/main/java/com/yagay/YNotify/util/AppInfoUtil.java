package com.yagay.YNotify.util;

import android.content.Context;
import android.graphics.drawable.Drawable;
import com.yagay.suite.api.InstalledAppCatalog;

public final class AppInfoUtil {
    private AppInfoUtil() {}

    public static String label(Context context, String pkg) {
        if (pkg == null || pkg.isEmpty()) {
            return com.yagay.suite.api.YLocale.text(
                    com.yagay.YNotify.R.string.ynotify_generated_e5ec6ab52278);
        }
        String standard = InstalledAppCatalog.label(context, pkg);
        if (standard != null) return standard;
        // Historic notifications may reference apps that have been uninstalled.
        if ("android".equals(pkg)) {
            return com.yagay.suite.api.YLocale.text(
                    com.yagay.YNotify.R.string.ynotify_generated_49fe92fc83df);
        }
        return "com.android.systemui".equals(pkg) ? "System UI" : pkg;
    }

    public static Drawable icon(Context context, String pkg) {
        Drawable shared = InstalledAppCatalog.icon(context, pkg, 64);
        return shared != null ? shared
                : context.getApplicationInfo().loadIcon(context.getPackageManager());
    }

    public static void clearCache() {
        InstalledAppCatalog.invalidate();
    }
}
