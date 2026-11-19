package com.yagay.yui

import android.content.Context

/**
 * Optional host-owned Context localization for Android 12 and below.
 * Standalone apps default to identity unless their own Application installs a resolver.
 */
object YUiLocaleBridge {
    @Volatile private var baseContextResolver: ((Context) -> Context)? = null

    @JvmStatic fun install(resolver: (Context) -> Context) {
        baseContextResolver = resolver
    }

    @JvmStatic fun wrap(context: Context): Context =
        baseContextResolver?.invoke(context) ?: context
}
