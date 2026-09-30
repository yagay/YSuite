package com.yagay.yparam.ui;

import android.graphics.drawable.Drawable;

final class AppEntry {
    final String name;
    final String packageName;
    final Drawable icon;
    final boolean system;
    final boolean configured;

    AppEntry(String name, String packageName, Drawable icon, boolean system, boolean configured) {
        this.name = name;
        this.packageName = packageName;
        this.icon = icon;
        this.system = system;
        this.configured = configured;
    }
}
