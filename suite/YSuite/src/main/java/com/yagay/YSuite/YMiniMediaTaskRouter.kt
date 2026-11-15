package com.yagay.YSuite

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.util.Log
import com.yagay.YMiniGuard.YMiniMediaBridge
import com.yagay.YMiniGuard.YMiniMediaLaunchPolicy

/**
 * OnePlus/OxygenOS can bring an existing YSuite task forward for a media-card
 * click without delivering onCreate/onNewIntent to its root activity.
 * Observe task restoration in the host process instead: works even when a
 * YMini settings activity was the last visible screen.
 */
internal class YMiniMediaTaskRouter(private val host: Application) :
    Application.ActivityLifecycleCallbacks {
    private val lastTaskIntent = mutableMapOf<Int, String>()

    override fun onActivityResumed(activity: Activity) {
        if (activity.packageName != host.packageName || activity.isFinishing) return
        try {
            val tasks = host.getSystemService(ActivityManager::class.java)?.appTasks ?: return
            val baseIntent = tasks.firstNotNullOfOrNull { task ->
                val info = task.taskInfo
                if (info.taskId == activity.taskId) info.baseIntent else null
            } ?: return
            val signature = "${activity.taskId}|${baseIntent.toUri(0)}"
            // Consume each observed launcher/task state once. Merely resuming
            // the same task (configuration, recents, lock/unlock, dialog) must
            // not repeatedly open a video app.
            if (lastTaskIntent.put(activity.taskId, signature) == signature) return

            val flags = baseIntent.flags
            val mediaPanelStyle = YMiniMediaLaunchPolicy.shouldRedirect(
                baseIntent.action == Intent.ACTION_MAIN,
                baseIntent.hasCategory(Intent.CATEGORY_LAUNCHER),
                baseIntent.component?.packageName == host.packageName &&
                    baseIntent.component?.className == MainActivity::class.java.name,
                flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0,
                flags and Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED != 0,
                // The existing Activity's original caller is stale on a
                // START_TASK_TO_FRONT launch; baseIntent is our fresh evidence.
                null,
            )
            if (!mediaPanelStyle) return

            val target = YMiniMediaBridge.activeTargetForMediaPanel(activity)
            if (target.isNullOrBlank() || target == host.packageName) {
                Log.i(TAG, "MEDIA_TASK_RESUME_SKIP no_active_target taskId=${activity.taskId}")
                return
            }
            val launch = activity.packageManager.getLaunchIntentForPackage(target)
            if (launch == null || launch.component?.packageName != target) {
                Log.w(TAG, "MEDIA_TASK_RESUME_SKIP no_launcher target=$target")
                return
            }
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            Log.i(TAG, "MEDIA_TASK_RESUME_REDIRECT target=$target" +
                    " taskId=${activity.taskId} flags=0x${flags.toString(16)}" +
                    " topActivity=${activity.javaClass.name}")
            activity.startActivity(launch)
            activity.finish()
        } catch (error: Throwable) {
            Log.w(TAG, "MEDIA_TASK_RESUME_FAILED", error)
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit

    private companion object {
        const val TAG = "YMiniMedia"
    }
}
