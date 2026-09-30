package com.yagay.YEntryCleaner

import android.app.Application

/** Standalone APK shell. Reusable state and initialization live in [YEntryCleanerRuntime]. */
class YEntryCleanerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        YEntryCleanerRuntime.get(this)
    }
}
