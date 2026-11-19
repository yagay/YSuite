package com.yagay.YSuite

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.yagay.YMiniGuard.EngineStatusProvider
import com.yagay.YMiniGuard.YMiniMediaBridge

/**
 * Only react to a fresh media-panel launch signal verified in system_server.
 * A task's baseIntent is from the *original* launcher and must not be used
 * to infer the source of a later START_TASK_TO_FRONT event.
 *
 * ActivityLifecycleCallbacks plus provider notifications cover both an
 * existing YMini settings Activity being resumed and a YSuite Activity
 * already resumed while the notification shade is open.
 */
internal class YMiniMediaTaskRouter(private val host: Application) :
    Application.ActivityLifecycleCallbacks {

    private val main = Handler(Looper.getMainLooper())
    private var resumedActivity: Activity? = null

    fun onMediaPanelSignal() {
        main.post { resumedActivity?.let(::tryRedirect) }
        main.postDelayed({ resumedActivity?.let(::tryRedirect) }, 120L)
        main.postDelayed({ resumedActivity?.let(::tryRedirect) }, 350L)
    }

    override fun onActivityResumed(activity: Activity) {
        if (activity.packageName != host.packageName || activity.isFinishing) return
        resumedActivity = activity
        tryRedirect(activity)
        // system_server writes the signal on its main handler, which may
        // arrive slightly *after* this Activity's onResume.
        main.postDelayed({ if (resumedActivity === activity) tryRedirect(activity) }, 100L)
        main.postDelayed({ if (resumedActivity === activity) tryRedirect(activity) }, 400L)
        main.postDelayed({ if (resumedActivity === activity) tryRedirect(activity) }, 900L)
    }

    private fun tryRedirect(activity: Activity) {
        if (resumedActivity !== activity || activity.isFinishing ||
            activity.packageName != host.packageName) return
        val target = YMiniMediaBridge.activeTargetForMediaPanel(activity)
        if (target.isNullOrBlank() || target == host.packageName) return
        // Do not consume a verified click until we have a real target
        // launcher intent; otherwise a late Activity can still try it.
        val launch = runCatching {
            activity.packageManager.getLaunchIntentForPackage(target)
        }.getOrNull()
        if (launch == null || launch.component?.packageName != target) {
            if (EngineStatusProvider.hasPendingMediaPanelLaunch(activity)) {
                Log.w(TAG, "MEDIA_PANEL_REDIRECT_SKIP no_launcher target=$target")
            }
            return
        }
        if (!EngineStatusProvider.consumeMediaPanelLaunch(activity)) return
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        try {
            Log.i(TAG, "MEDIA_PANEL_UID_VERIFIED_REDIRECT target=$target" +
                " fromActivity=${activity.javaClass.name}" +
                " taskId=${activity.taskId}")
            activity.startActivity(launch)
            // Keep the YSuite / YMini settings task intact in the back
            // stack. The player's existing task normally returns to front
            // with its current episode, instead of opening a fresh player.
        } catch (error: Throwable) {
            Log.w(TAG, "MEDIA_PANEL_UID_VERIFIED_REDIRECT_FAILED target=$target", error)
        }
    }

    override fun onActivityPaused(activity: Activity) {
        if (resumedActivity === activity) resumedActivity = null
    }
    override fun onActivityDestroyed(activity: Activity) {
        if (resumedActivity === activity) resumedActivity = null
    }
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    private companion object {
        const val TAG = "YMiniMedia"
    }
}
